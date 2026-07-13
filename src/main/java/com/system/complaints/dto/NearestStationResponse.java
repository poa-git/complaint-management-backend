package com.system.complaints.dto;

import java.util.ArrayList;
import java.util.List;

public class NearestStationResponse {
    private String complaintCity;
    private List<Station> stations = new ArrayList<>();

    public String getComplaintCity() {
        return complaintCity;
    }

    public void setComplaintCity(String complaintCity) {
        this.complaintCity = complaintCity;
    }

    public List<Station> getStations() {
        return stations;
    }

    public void setStations(List<Station> stations) {
        this.stations = stations;
    }

    public static class Station {
        private String stationCity;
        private Double distanceKm;
        private Integer durationMinutes;
        private String distanceSource;
        private String mapsUrl;

        public String getStationCity() {
            return stationCity;
        }

        public void setStationCity(String stationCity) {
            this.stationCity = stationCity;
        }

        public Double getDistanceKm() {
            return distanceKm;
        }

        public void setDistanceKm(Double distanceKm) {
            this.distanceKm = distanceKm;
        }

        public Integer getDurationMinutes() {
            return durationMinutes;
        }

        public void setDurationMinutes(Integer durationMinutes) {
            this.durationMinutes = durationMinutes;
        }

        public String getDistanceSource() {
            return distanceSource;
        }

        public void setDistanceSource(String distanceSource) {
            this.distanceSource = distanceSource;
        }

        public String getMapsUrl() {
            return mapsUrl;
        }

        public void setMapsUrl(String mapsUrl) {
            this.mapsUrl = mapsUrl;
        }
    }
}
