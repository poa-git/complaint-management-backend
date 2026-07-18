package com.system.complaints.dto;

import java.sql.Date;

public class ScheduleDTO {
    public String activityType;
    public String complaintId;
    public String city;
    public String bank;
    public String branchName;
    public String branchCode;
    public String engineerName;
    public Date scheduledFor;
    public String status;
    public String performedBy;

    public ScheduleDTO(
            String complaintId,
            String city,
            String bank,
            String branchName,
            String branchCode,
            String engineerName,
            Date scheduledFor,
            String status,
            String performedBy
    ) {
        this.activityType = "Complaint";
        this.complaintId = complaintId;
        this.city = city;
        this.bank = bank;
        this.branchName = branchName;
        this.branchCode = branchCode;
        this.engineerName = engineerName;
        this.scheduledFor = scheduledFor;
        this.status = status;
        this.performedBy = performedBy;
    }

    // Or add a constructor that takes Schedule and ComplaintLog
    public ScheduleDTO(com.system.complaints.model.Schedule s, com.system.complaints.model.ComplaintLog c) {
        this.activityType = "Complaint";
        this.complaintId = s.getComplaintId();
        this.city = c.getCity();
        this.bank = c.getBankName();
        this.branchName = c.getBranchName();
        this.branchCode = c.getBranchCode();
        this.engineerName = c.getVisitorName();
        this.scheduledFor = (s.getScheduledFor() == null)
                ? null
                : new java.sql.Date(s.getScheduledFor().getTime());
        this.status = s.getOutcomeStatus();
        this.performedBy = s.getPerformedBy();
    }

    public static ScheduleDTO manualActivity(com.system.complaints.model.VisitPlanEntry entry) {
        boolean installation = "NEW_INSTALLATION".equalsIgnoreCase(entry.getEntryType());
        String activity = installation ? "New Installation" : "No Pending Complaint";
        String city = installation ? entry.getCity() : entry.getVisitorStation();
        ScheduleDTO dto = new ScheduleDTO(
                null,
                city,
                activity,
                installation
                        ? (entry.getRouteDestination() == null ? entry.getCity() : entry.getRouteDestination())
                        : "No pending complaint",
                "-",
                entry.getVisitorName(),
                entry.getScheduleDate(),
                entry.getOutcomeStatus(),
                entry.getApprovedBy()
        );
        dto.activityType = activity;
        return dto;
    }

}
