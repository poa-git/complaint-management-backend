package com.system.complaints.dto;

public class VisitPlanApproveRequest {
    private String scheduleDate;
    private String routeOrigin;
    private String routeDestination;
    private Double routeDistanceKm;
    private Integer routeDurationMinutes;

    public String getScheduleDate() {
        return scheduleDate;
    }

    public void setScheduleDate(String scheduleDate) {
        this.scheduleDate = scheduleDate;
    }

    public String getRouteOrigin() { return routeOrigin; }
    public void setRouteOrigin(String routeOrigin) { this.routeOrigin = routeOrigin; }
    public String getRouteDestination() { return routeDestination; }
    public void setRouteDestination(String routeDestination) { this.routeDestination = routeDestination; }
    public Double getRouteDistanceKm() { return routeDistanceKm; }
    public void setRouteDistanceKm(Double routeDistanceKm) { this.routeDistanceKm = routeDistanceKm; }
    public Integer getRouteDurationMinutes() { return routeDurationMinutes; }
    public void setRouteDurationMinutes(Integer routeDurationMinutes) { this.routeDurationMinutes = routeDurationMinutes; }
}
