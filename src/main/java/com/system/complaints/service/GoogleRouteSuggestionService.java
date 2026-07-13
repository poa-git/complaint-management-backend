package com.system.complaints.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.system.complaints.dto.NearestStationResponse;
import com.system.complaints.dto.RouteSuggestionRequest;
import com.system.complaints.dto.RouteSuggestionResponse;
import com.system.complaints.repository.VisitorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GoogleRouteSuggestionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleRouteSuggestionService.class);
    private static final String DISTANCE_MATRIX_URL =
            "https://maps.googleapis.com/maps/api/distancematrix/json";
    private static final String GEOCODING_URL =
            "https://maps.googleapis.com/maps/api/geocode/json";
    private static final int DEFAULT_MAX_EXTRA_MINUTES = 60;
    private static final double DEFAULT_MAX_EXTRA_KM = 100.0;
    private static final int MAX_CITIES_PER_REQUEST = 24;
    private static final int NEAREST_STATION_LIMIT = 3;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, CityLocation> geocodeCache = new ConcurrentHashMap<>();
    private final Map<String, MatrixValue> stationDistanceCache = new ConcurrentHashMap<>();
    private final VisitorRepository visitorRepository;

    @Value("${google.maps.api-key:}")
    private String googleMapsApiKey;

    public GoogleRouteSuggestionService(VisitorRepository visitorRepository) {
        this.visitorRepository = visitorRepository;
    }

    public NearestStationResponse findNearestStations(String requestedComplaintCity) {
        String complaintCity = cleanCity(requestedComplaintCity);
        if (complaintCity.isEmpty()) {
            throw new IllegalArgumentException("Complaint city is required.");
        }
        if (googleMapsApiKey == null || googleMapsApiKey.isBlank()) {
            throw new IllegalStateException("Google Maps API key is not configured.");
        }

        List<String> stationCities = uniqueCities(visitorRepository.findDistinctStationCities());
        Map<String, MatrixValue> distances = new java.util.LinkedHashMap<>();
        List<String> missingStations = new ArrayList<>();

        for (String stationCity : stationCities) {
            if (stationCity.equalsIgnoreCase(complaintCity)) {
                distances.put(stationCity, new MatrixValue(0.0, 0, true, "OK"));
                continue;
            }

            MatrixValue cached = stationDistanceCache.get(stationDistanceCacheKey(stationCity, complaintCity));
            if (cached != null) {
                distances.put(stationCity, cached);
            } else {
                missingStations.add(stationCity);
            }
        }

        Map<String, CityLocation> locations = Map.of();
        if (!missingStations.isEmpty()) {
            List<String> citiesToResolve = new ArrayList<>(missingStations);
            citiesToResolve.add(complaintCity);
            locations = resolveLocations(citiesToResolve);

            for (int start = 0; start < missingStations.size(); start += MAX_CITIES_PER_REQUEST) {
                List<String> stationBatch = missingStations.subList(
                        start,
                        Math.min(start + MAX_CITIES_PER_REQUEST, missingStations.size())
                );

                try {
                    Map<String, Map<String, MatrixValue>> matrix = fetchMatrix(
                            stationBatch,
                            List.of(complaintCity),
                            locations
                    );
                    for (String stationCity : stationBatch) {
                        MatrixValue value = matrix.getOrDefault(stationCity, Map.of()).get(complaintCity);
                        if (value == null || !value.ok()) {
                            value = fetchSingleMatrix(stationCity, complaintCity, locations);
                        }
                        cacheStationDistance(
                                stationCity,
                                complaintCity,
                                value,
                                distances
                        );
                    }
                } catch (RuntimeException exception) {
                    LOGGER.warn(
                            "Nearest-station batch distance lookup failed for {}: {}",
                            complaintCity,
                            exception.getMessage()
                    );
                    for (String stationCity : stationBatch) {
                        cacheStationDistance(
                                stationCity,
                                complaintCity,
                                fetchSingleMatrix(stationCity, complaintCity, locations),
                                distances
                        );
                    }
                }
            }
        }

        List<NearestStationResponse.Station> recommendations = new ArrayList<>(distances.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().ok())
                .sorted(Comparator
                        .comparing((Map.Entry<String, MatrixValue> entry) -> entry.getValue().distanceKm())
                        .thenComparing(Map.Entry::getKey))
                .limit(NEAREST_STATION_LIMIT)
                .map(entry -> toNearestStation(entry.getKey(), complaintCity, entry.getValue()))
                .toList());

        if (recommendations.size() < NEAREST_STATION_LIMIT) {
            if (locations.isEmpty()) {
                List<String> citiesToResolve = new ArrayList<>(stationCities);
                citiesToResolve.add(complaintCity);
                locations = resolveLocations(citiesToResolve);
            }
            addApproximateStations(
                    recommendations,
                    stationCities,
                    complaintCity,
                    locations,
                    NEAREST_STATION_LIMIT
            );
        }

        NearestStationResponse response = new NearestStationResponse();
        response.setComplaintCity(complaintCity);
        response.setStations(recommendations);
        return response;
    }

    private void addApproximateStations(
            List<NearestStationResponse.Station> recommendations,
            List<String> stationCities,
            String complaintCity,
            Map<String, CityLocation> locations,
            int limit
    ) {
        CityLocation complaintLocation = locations.get(complaintCity);
        if (complaintLocation == null || !complaintLocation.hasCoordinates()) {
            LOGGER.warn("No geocoded coordinates available for complaint city {}", complaintCity);
            return;
        }

        Set<String> includedStations = recommendations.stream()
                .map(station -> station.getStationCity().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        stationCities.stream()
                .filter(stationCity -> !includedStations.contains(stationCity.toLowerCase(Locale.ROOT)))
                .filter(stationCity -> {
                    CityLocation stationLocation = locations.get(stationCity);
                    return stationLocation != null && stationLocation.hasCoordinates();
                })
                .map(stationCity -> Map.entry(
                        stationCity,
                        haversineKm(locations.get(stationCity), complaintLocation)
                ))
                .sorted(Comparator
                        .comparing((Map.Entry<String, Double> entry) -> entry.getValue())
                        .thenComparing(Map.Entry::getKey))
                .limit(Math.max(0, limit - recommendations.size()))
                .map(entry -> toApproximateStation(entry.getKey(), complaintCity, entry.getValue()))
                .forEach(recommendations::add);
    }

    private Double haversineKm(CityLocation from, CityLocation to) {
        if (from == null || to == null || !from.hasCoordinates() || !to.hasCoordinates()) {
            return null;
        }

        double earthRadiusKm = 6371.0;
        double latitudeDelta = Math.toRadians(to.lat() - from.lat());
        double longitudeDelta = Math.toRadians(to.lng() - from.lng());
        double fromLatitude = Math.toRadians(from.lat());
        double toLatitude = Math.toRadians(to.lat());
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(fromLatitude) * Math.cos(toLatitude)
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return round(earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    public RouteSuggestionResponse buildSuggestions(RouteSuggestionRequest request) {
        String stationCity = cleanCity(request.getStationCity());
        if (stationCity.isEmpty()) {
            throw new IllegalArgumentException("Station city is required.");
        }
        if (googleMapsApiKey == null || googleMapsApiKey.isBlank()) {
            throw new IllegalStateException("Google Maps API key is not configured.");
        }

        List<String> cities = uniqueCities(request.getComplaintCities()).stream()
                .filter(city -> !city.equalsIgnoreCase(stationCity))
                .limit(MAX_CITIES_PER_REQUEST)
                .collect(Collectors.toList());

        RouteSuggestionResponse response = new RouteSuggestionResponse();
        response.setStationCity(stationCity);
        if (cities.isEmpty()) {
            return response;
        }

        List<String> matrixCities = new ArrayList<>();
        matrixCities.add(stationCity);
        matrixCities.addAll(cities);
        Map<String, CityLocation> locations = resolveLocations(matrixCities);

        Map<String, MatrixValue> fromStation = fetchMatrix(List.of(stationCity), cities, locations)
                .getOrDefault(stationCity, Map.of());
        Map<String, Map<String, MatrixValue>> betweenComplaintCities = fetchMatrix(cities, cities, locations);

        int maxMinutes = request.getMaxExtraMinutes() != null
                ? request.getMaxExtraMinutes()
                : DEFAULT_MAX_EXTRA_MINUTES;
        double maxKm = request.getMaxExtraKm() != null
                ? request.getMaxExtraKm()
                : DEFAULT_MAX_EXTRA_KM;

        List<RouteSuggestionResponse.Suggestion> suggestions = new ArrayList<>();

        for (String destinationCity : cities) {
            MatrixValue stationDistance = fromStation.get(destinationCity);
            CityLocation destinationLocation = locations.get(destinationCity);
            if (stationDistance == null || !stationDistance.ok()) {
                stationDistance = fetchSingleMatrix(stationCity, destinationCity, locations);
            }

            RouteSuggestionResponse.Suggestion suggestion = new RouteSuggestionResponse.Suggestion();
            suggestion.setDestinationCity(destinationCity);
            suggestion.setGeocodeStatus(destinationLocation != null ? destinationLocation.status() : "MISSING_LOCATION");
            suggestion.setResolvedDestinationAddress(
                    destinationLocation != null ? destinationLocation.formattedAddress() : ""
            );
            if (stationDistance != null && stationDistance.ok()) {
                suggestion.setDistanceKmFromStation(stationDistance.distanceKm());
                suggestion.setDurationMinutesFromStation(stationDistance.durationMinutes());
            }
            suggestion.setDistanceStatus(stationDistance != null ? stationDistance.status() : "MISSING_RESULT");

            List<RouteSuggestionResponse.NearbyCity> nearby = buildNearbyCities(
                    destinationCity,
                    cities,
                    betweenComplaintCities.getOrDefault(destinationCity, Map.of()),
                    locations,
                    maxMinutes,
                    maxKm
            );

            suggestion.setNearbyCities(nearby);
            suggestion.setMapsUrl(buildMapsUrl(stationCity, destinationCity, nearby));
            suggestions.add(suggestion);
        }

        suggestions.sort(Comparator
                .comparing(
                        RouteSuggestionResponse.Suggestion::getDurationMinutesFromStation,
                        Comparator.nullsLast(Integer::compareTo)
                )
                .thenComparing(RouteSuggestionResponse.Suggestion::getDestinationCity));
        response.setSuggestions(suggestions);
        return response;
    }

    private List<RouteSuggestionResponse.NearbyCity> buildNearbyCities(
            String destinationCity,
            List<String> cities,
            Map<String, MatrixValue> matrixRow,
            Map<String, CityLocation> locations,
            int maxMinutes,
            double maxKm
    ) {
        List<RouteSuggestionResponse.NearbyCity> nearby = new ArrayList<>();

        for (String city : cities) {
            if (city.equalsIgnoreCase(destinationCity)) {
                continue;
            }

            MatrixValue value = matrixRow.get(city);
            if (value == null || !value.ok()) {
                value = fetchSingleMatrix(destinationCity, city, locations);
            }

            RouteSuggestionResponse.NearbyCity candidate = toNearbyCity(city, value);
            if (candidate != null
                    && (candidate.getDurationMinutesFromDestination() <= maxMinutes
                    || candidate.getDistanceKmFromDestination() <= maxKm)) {
                nearby.add(candidate);
            }
        }

        nearby.sort(Comparator.comparing(RouteSuggestionResponse.NearbyCity::getDurationMinutesFromDestination));
        return nearby;
    }

    private RouteSuggestionResponse.NearbyCity toNearbyCity(String city, MatrixValue value) {
        if (value == null || !value.ok()) {
            return null;
        }

        RouteSuggestionResponse.NearbyCity nearby = new RouteSuggestionResponse.NearbyCity();
        nearby.setCity(city);
        nearby.setDistanceKmFromDestination(value.distanceKm());
        nearby.setDurationMinutesFromDestination(value.durationMinutes());
        return nearby;
    }

    private Map<String, Map<String, MatrixValue>> fetchMatrix(
            List<String> origins,
            List<String> destinations,
            Map<String, CityLocation> locations
    ) {
        String url = UriComponentsBuilder.fromHttpUrl(DISTANCE_MATRIX_URL)
                .queryParam("origins", joinMatrixQueries(origins, locations))
                .queryParam("destinations", joinMatrixQueries(destinations, locations))
                .queryParam("mode", "driving")
                .queryParam("units", "metric")
                .queryParam("key", googleMapsApiKey)
                .build()
                .encode()
                .toUriString();

        JsonNode root = restTemplate.getForObject(url, JsonNode.class);
        if (root == null || !"OK".equalsIgnoreCase(root.path("status").asText())) {
            String status = root == null ? "NO_RESPONSE" : root.path("status").asText("UNKNOWN");
            String detail = root == null ? "" : root.path("error_message").asText("");
            throw new IllegalStateException(
                    "Google Distance Matrix request failed: " + status
                            + (detail.isBlank() ? "" : " (" + detail + ")")
            );
        }

        JsonNode rows = root.path("rows");
        return origins.stream().collect(Collectors.toMap(
                Function.identity(),
                origin -> parseRow(rows.get(origins.indexOf(origin)), destinations),
                (a, b) -> a,
                java.util.LinkedHashMap::new
        ));
    }

    private MatrixValue fetchSingleMatrix(
            String origin,
            String destination,
            Map<String, CityLocation> locations
    ) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(DISTANCE_MATRIX_URL)
                    .queryParam("origins", joinMatrixQueries(List.of(origin), locations))
                    .queryParam("destinations", joinMatrixQueries(List.of(destination), locations))
                    .queryParam("mode", "driving")
                    .queryParam("units", "metric")
                    .queryParam("key", googleMapsApiKey)
                    .build()
                    .encode()
                    .toUriString();

            JsonNode root = restTemplate.getForObject(url, JsonNode.class);
            if (root == null || !"OK".equalsIgnoreCase(root.path("status").asText())) {
                String status = root == null ? "NO_RESPONSE" : root.path("status").asText("UNKNOWN");
                LOGGER.warn(
                        "Single Distance Matrix request failed for {} -> {}: {}",
                        origin,
                        destination,
                        status
                );
                return fetchSingleMatrixWithCityText(origin, destination);
            }

            MatrixValue value = parseElement(root.path("rows").path(0).path("elements").path(0));
            if (!value.ok()) {
                LOGGER.warn(
                        "Single Distance Matrix element unavailable for {} -> {} using coordinates: {}. Retrying with city text.",
                        origin,
                        destination,
                        value.status()
                );
                return fetchSingleMatrixWithCityText(origin, destination);
            }
            return value;
        } catch (Exception ex) {
            LOGGER.warn(
                    "Single Distance Matrix lookup errored for {} -> {}: {}. Retrying with city text.",
                    origin,
                    destination,
                    ex.getMessage()
            );
            return fetchSingleMatrixWithCityText(origin, destination);
        }
    }

    private MatrixValue fetchSingleMatrixWithCityText(String origin, String destination) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(DISTANCE_MATRIX_URL)
                    .queryParam("origins", origin + ", Pakistan")
                    .queryParam("destinations", destination + ", Pakistan")
                    .queryParam("mode", "driving")
                    .queryParam("units", "metric")
                    .queryParam("key", googleMapsApiKey)
                    .build()
                    .encode()
                    .toUriString();

            JsonNode root = restTemplate.getForObject(url, JsonNode.class);
            if (root == null || !"OK".equalsIgnoreCase(root.path("status").asText())) {
                String status = root == null ? "NO_RESPONSE" : root.path("status").asText("UNKNOWN");
                LOGGER.warn(
                        "Text Distance Matrix request failed for {} -> {}: {}",
                        origin,
                        destination,
                        status
                );
                return MatrixValue.unavailable("SINGLE_TEXT_MATRIX_FAILED:" + status);
            }

            MatrixValue value = parseElement(root.path("rows").path(0).path("elements").path(0));
            if (!value.ok()) {
                LOGGER.warn(
                        "Text Distance Matrix element unavailable for {} -> {}: {}",
                        origin,
                        destination,
                        value.status()
                );
            }
            return value;
        } catch (Exception ex) {
            LOGGER.warn(
                    "Text Distance Matrix lookup errored for {} -> {}: {}",
                    origin,
                    destination,
                    ex.getMessage()
            );
            return MatrixValue.unavailable("SINGLE_MATRIX_ERROR");
        }
    }

    private Map<String, MatrixValue> parseRow(JsonNode row, List<String> destinations) {
        JsonNode elements = row == null ? null : row.path("elements");
        return destinations.stream().collect(Collectors.toMap(
                Function.identity(),
                destination -> parseElement(elements == null ? null : elements.get(destinations.indexOf(destination))),
                (a, b) -> a,
                java.util.LinkedHashMap::new
        ));
    }

    private MatrixValue parseElement(JsonNode element) {
        String status = element == null ? "MISSING_ELEMENT" : element.path("status").asText("UNKNOWN");
        if (!"OK".equalsIgnoreCase(status)) {
            return MatrixValue.unavailable(status);
        }

        double distanceKm = element.path("distance").path("value").asDouble(0) / 1000.0;
        int durationMinutes = (int) Math.ceil(element.path("duration").path("value").asDouble(0) / 60.0);
        return new MatrixValue(round(distanceKm), durationMinutes, true, status);
    }

    private List<String> uniqueCities(List<String> cities) {
        if (cities == null) {
            return List.of();
        }

        Set<String> unique = new LinkedHashSet<>();
        for (String city : cities) {
            String clean = cleanCity(city);
            if (!clean.isEmpty()) {
                unique.add(clean);
            }
        }
        return new ArrayList<>(unique);
    }

    private String cleanCity(String city) {
        return city == null ? "" : city.trim();
    }

    private Map<String, CityLocation> resolveLocations(List<String> cities) {
        return cities.stream()
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(),
                        this::resolveLocation,
                        (a, b) -> a,
                        java.util.LinkedHashMap::new
                ));
    }

    private CityLocation resolveLocation(String city) {
        String clean = cleanCity(city);
        String cacheKey = clean.toLowerCase(Locale.ROOT);
        return geocodeCache.computeIfAbsent(cacheKey, ignored -> fetchLocation(clean));
    }

    private CityLocation fetchLocation(String city) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(GEOCODING_URL)
                    .queryParam("address", city + ", Pakistan")
                    .queryParam("components", "country:PK")
                    .queryParam("key", googleMapsApiKey)
                    .build()
                    .encode()
                    .toUriString();

            JsonNode root = restTemplate.getForObject(url, JsonNode.class);
            String status = root == null ? "MISSING_GEOCODE_RESPONSE" : root.path("status").asText("UNKNOWN");
            JsonNode result = root == null ? null : root.path("results").path(0);

            if (!"OK".equalsIgnoreCase(status) || result == null || result.isMissingNode()) {
                return CityLocation.raw(city, status);
            }

            JsonNode location = result.path("geometry").path("location");
            if (!location.hasNonNull("lat") || !location.hasNonNull("lng")) {
                return CityLocation.raw(city, "MISSING_GEOCODE_LOCATION");
            }

            return new CityLocation(
                    city,
                    location.path("lat").asDouble(),
                    location.path("lng").asDouble(),
                    result.path("formatted_address").asText(""),
                    status
            );
        } catch (Exception ex) {
            return CityLocation.raw(city, "GEOCODE_ERROR");
        }
    }

    private String joinMatrixQueries(List<String> cities, Map<String, CityLocation> locations) {
        return cities.stream()
                .map(city -> locations.getOrDefault(city, CityLocation.raw(city, "MISSING_LOCATION")).matrixQuery())
                .collect(Collectors.joining("|"));
    }

    private String buildMapsUrl(
            String stationCity,
            String destinationCity,
            List<RouteSuggestionResponse.NearbyCity> nearbyCities
    ) {
        List<String> orderedStops = new ArrayList<>();
        orderedStops.add(destinationCity);
        orderedStops.addAll(nearbyCities.stream()
                .limit(8)
                .map(RouteSuggestionResponse.NearbyCity::getCity)
                .toList());

        String finalDestination = orderedStops.get(orderedStops.size() - 1);
        String waypointText = orderedStops.subList(0, orderedStops.size() - 1).stream()
                .map(city -> city + ", Pakistan")
                .collect(Collectors.joining("|"));

        StringBuilder url = new StringBuilder("https://www.google.com/maps/dir/?api=1");
        url.append("&origin=").append(encode(stationCity + ", Pakistan"));
        url.append("&destination=").append(encode(finalDestination + ", Pakistan"));
        url.append("&travelmode=driving");
        if (!waypointText.isBlank()) {
            url.append("&waypoints=").append(encode(waypointText));
        }
        return url.toString();
    }

    private void cacheStationDistance(
            String stationCity,
            String complaintCity,
            MatrixValue value,
            Map<String, MatrixValue> distances
    ) {
        if (value == null || !value.ok()) {
            return;
        }
        distances.put(stationCity, value);
        stationDistanceCache.put(stationDistanceCacheKey(stationCity, complaintCity), value);
    }

    private String stationDistanceCacheKey(String stationCity, String complaintCity) {
        return cleanCity(stationCity).toLowerCase(Locale.ROOT)
                + "->"
                + cleanCity(complaintCity).toLowerCase(Locale.ROOT);
    }

    private NearestStationResponse.Station toNearestStation(
            String stationCity,
            String complaintCity,
            MatrixValue value
    ) {
        NearestStationResponse.Station station = new NearestStationResponse.Station();
        station.setStationCity(stationCity);
        station.setDistanceKm(value.distanceKm());
        station.setDurationMinutes(value.durationMinutes());
        station.setDistanceSource("DRIVING");
        station.setMapsUrl(buildDirectMapsUrl(stationCity, complaintCity));
        return station;
    }

    private NearestStationResponse.Station toApproximateStation(
            String stationCity,
            String complaintCity,
            Double distanceKm
    ) {
        NearestStationResponse.Station station = new NearestStationResponse.Station();
        station.setStationCity(stationCity);
        station.setDistanceKm(distanceKm);
        station.setDistanceSource("STRAIGHT_LINE");
        station.setMapsUrl(buildDirectMapsUrl(stationCity, complaintCity));
        return station;
    }

    private String buildDirectMapsUrl(String originCity, String destinationCity) {
        return "https://www.google.com/maps/dir/?api=1"
                + "&origin=" + encode(originCity + ", Pakistan")
                + "&destination=" + encode(destinationCity + ", Pakistan")
                + "&travelmode=driving";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record MatrixValue(Double distanceKm, Integer durationMinutes, boolean ok, String status) {
        static MatrixValue unavailable(String status) {
            return new MatrixValue(null, null, false, status);
        }
    }

    private record CityLocation(
            String city,
            Double lat,
            Double lng,
            String formattedAddress,
            String status
    ) {
        static CityLocation raw(String city, String status) {
            return new CityLocation(city, null, null, "", status);
        }

        boolean hasCoordinates() {
            return lat != null && lng != null;
        }

        String matrixQuery() {
            if (lat != null && lng != null) {
                return lat + "," + lng;
            }
            return city + ", Pakistan";
        }
    }
}
