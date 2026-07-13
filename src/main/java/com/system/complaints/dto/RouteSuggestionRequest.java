package com.system.complaints.dto;

import java.util.List;

public class RouteSuggestionRequest {
    private String stationCity;
    private List<String> complaintCities;
    private Integer maxExtraMinutes;
    private Double maxExtraKm;

    public String getStationCity() {
        return stationCity;
    }

    public void setStationCity(String stationCity) {
        this.stationCity = stationCity;
    }

    public List<String> getComplaintCities() {
        return complaintCities;
    }

    public void setComplaintCities(List<String> complaintCities) {
        this.complaintCities = complaintCities;
    }

    public Integer getMaxExtraMinutes() {
        return maxExtraMinutes;
    }

    public void setMaxExtraMinutes(Integer maxExtraMinutes) {
        this.maxExtraMinutes = maxExtraMinutes;
    }

    public Double getMaxExtraKm() {
        return maxExtraKm;
    }

    public void setMaxExtraKm(Double maxExtraKm) {
        this.maxExtraKm = maxExtraKm;
    }
}
