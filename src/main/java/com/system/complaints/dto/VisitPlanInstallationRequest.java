package com.system.complaints.dto;

public class VisitPlanInstallationRequest {
    private String entryType;
    private Long visitorId;
    private String scheduleDate;
    private String destination;

    public String getEntryType() { return entryType; }
    public void setEntryType(String value) { entryType = value; }
    public Long getVisitorId() { return visitorId; }
    public void setVisitorId(Long value) { visitorId = value; }
    public String getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(String value) { scheduleDate = value; }
    public String getDestination() { return destination; }
    public void setDestination(String value) { destination = value; }
}
