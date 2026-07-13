package com.system.complaints.dto;

import java.util.ArrayList;
import java.util.List;

public class RouteSuggestionResponse {
    private String stationCity;
    private List<Suggestion> suggestions = new ArrayList<>();

    public String getStationCity() {
        return stationCity;
    }

    public void setStationCity(String stationCity) {
        this.stationCity = stationCity;
    }

    public List<Suggestion> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<Suggestion> suggestions) {
        this.suggestions = suggestions;
    }

    public static class Suggestion {
        private String destinationCity;
        private Double distanceKmFromStation;
        private Integer durationMinutesFromStation;
        private String distanceStatus;
        private String geocodeStatus;
        private String resolvedDestinationAddress;
        private String mapsUrl;
        private List<NearbyCity> nearbyCities = new ArrayList<>();

        public String getDestinationCity() {
            return destinationCity;
        }

        public void setDestinationCity(String destinationCity) {
            this.destinationCity = destinationCity;
        }

        public Double getDistanceKmFromStation() {
            return distanceKmFromStation;
        }

        public void setDistanceKmFromStation(Double distanceKmFromStation) {
            this.distanceKmFromStation = distanceKmFromStation;
        }

        public Integer getDurationMinutesFromStation() {
            return durationMinutesFromStation;
        }

        public void setDurationMinutesFromStation(Integer durationMinutesFromStation) {
            this.durationMinutesFromStation = durationMinutesFromStation;
        }

        public String getDistanceStatus() {
            return distanceStatus;
        }

        public void setDistanceStatus(String distanceStatus) {
            this.distanceStatus = distanceStatus;
        }

        public String getGeocodeStatus() {
            return geocodeStatus;
        }

        public void setGeocodeStatus(String geocodeStatus) {
            this.geocodeStatus = geocodeStatus;
        }

        public String getResolvedDestinationAddress() {
            return resolvedDestinationAddress;
        }

        public void setResolvedDestinationAddress(String resolvedDestinationAddress) {
            this.resolvedDestinationAddress = resolvedDestinationAddress;
        }

        public String getMapsUrl() {
            return mapsUrl;
        }

        public void setMapsUrl(String mapsUrl) {
            this.mapsUrl = mapsUrl;
        }

        public List<NearbyCity> getNearbyCities() {
            return nearbyCities;
        }

        public void setNearbyCities(List<NearbyCity> nearbyCities) {
            this.nearbyCities = nearbyCities;
        }
    }

    public static class NearbyCity {
        private String city;
        private Double distanceKmFromDestination;
        private Integer durationMinutesFromDestination;

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public Double getDistanceKmFromDestination() {
            return distanceKmFromDestination;
        }

        public void setDistanceKmFromDestination(Double distanceKmFromDestination) {
            this.distanceKmFromDestination = distanceKmFromDestination;
        }

        public Integer getDurationMinutesFromDestination() {
            return durationMinutesFromDestination;
        }

        public void setDurationMinutesFromDestination(Integer durationMinutesFromDestination) {
            this.durationMinutesFromDestination = durationMinutesFromDestination;
        }
    }
}
