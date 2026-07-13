package com.system.complaints.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import java.sql.Date;
import java.sql.Timestamp;

@Entity
@Table(name = "visit_plan_entries")
public class VisitPlanEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long planId;

    @Column(nullable = false)
    private String complaintId;

    @Column(nullable = false, columnDefinition = "date")
    private Date scheduleDate;

    @Temporal(TemporalType.TIMESTAMP)
    private java.util.Date approvedAt;

    private String approvedBy;
    private Long visitorId;
    private String visitorName;
    private String visitorStation;
    private String bankName;
    private String branchCode;
    private String branchName;
    private String city;
    private String complaintStatus;
    private String courierStatus;
    private Integer complaintAge;
    private Integer hardwareDeliveryAge;
    private String priorityType;
    private String priorityLabel;
    private String priorityDetail;
    private Boolean urgent;
    private String routeOrigin;
    private String routeDestination;
    private Double routeDistanceKm;
    private Integer routeDurationMinutes;
    private String outcomeStatus;
    private String approvalStatus;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    @PrePersist
    void onCreate() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        createdAt = now;
        updatedAt = now;
        if (outcomeStatus == null || outcomeStatus.isBlank()) outcomeStatus = "Scheduled";
    }

    @jakarta.persistence.PreUpdate
    void onUpdate() {
        updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long value) { planId = value; }
    public String getComplaintId() { return complaintId; }
    public void setComplaintId(String value) { complaintId = value; }
    public Date getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(Date value) { scheduleDate = value; }
    public java.util.Date getApprovedAt() { return approvedAt; }
    public void setApprovedAt(java.util.Date value) { approvedAt = value; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String value) { approvedBy = value; }
    public Long getVisitorId() { return visitorId; }
    public void setVisitorId(Long value) { visitorId = value; }
    public String getVisitorName() { return visitorName; }
    public void setVisitorName(String value) { visitorName = value; }
    public String getVisitorStation() { return visitorStation; }
    public void setVisitorStation(String value) { visitorStation = value; }
    public String getBankName() { return bankName; }
    public void setBankName(String value) { bankName = value; }
    public String getBranchCode() { return branchCode; }
    public void setBranchCode(String value) { branchCode = value; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String value) { branchName = value; }
    public String getCity() { return city; }
    public void setCity(String value) { city = value; }
    public String getComplaintStatus() { return complaintStatus; }
    public void setComplaintStatus(String value) { complaintStatus = value; }
    public String getCourierStatus() { return courierStatus; }
    public void setCourierStatus(String value) { courierStatus = value; }
    public Integer getComplaintAge() { return complaintAge; }
    public void setComplaintAge(Integer value) { complaintAge = value; }
    public Integer getHardwareDeliveryAge() { return hardwareDeliveryAge; }
    public void setHardwareDeliveryAge(Integer value) { hardwareDeliveryAge = value; }
    public String getPriorityType() { return priorityType; }
    public void setPriorityType(String value) { priorityType = value; }
    public String getPriorityLabel() { return priorityLabel; }
    public void setPriorityLabel(String value) { priorityLabel = value; }
    public String getPriorityDetail() { return priorityDetail; }
    public void setPriorityDetail(String value) { priorityDetail = value; }
    public Boolean getUrgent() { return urgent; }
    public void setUrgent(Boolean value) { urgent = value; }
    public String getRouteOrigin() { return routeOrigin; }
    public void setRouteOrigin(String value) { routeOrigin = value; }
    public String getRouteDestination() { return routeDestination; }
    public void setRouteDestination(String value) { routeDestination = value; }
    public Double getRouteDistanceKm() { return routeDistanceKm; }
    public void setRouteDistanceKm(Double value) { routeDistanceKm = value; }
    public Integer getRouteDurationMinutes() { return routeDurationMinutes; }
    public void setRouteDurationMinutes(Integer value) { routeDurationMinutes = value; }
    public String getOutcomeStatus() { return outcomeStatus; }
    public void setOutcomeStatus(String value) { outcomeStatus = value; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String value) { approvalStatus = value; }
    public Timestamp getCreatedAt() { return createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
}
