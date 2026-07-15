package com.system.complaints.dto;

import java.sql.Date;

public class VisitPlanComplaintDTO {
    private Long id;
    private String complaintId;
    private Date date;
    private int complaintAge;
    private Date hardwareReceivedOutwardDate;
    private int hardwareDeliveryAge;
    private String bankName;
    private String branchCode;
    private String branchName;
    private String city;
    private String complaintStatus;
    private Date scheduleDate;
    private String courierStatus;
    private Long visitorId;
    private String visitorName;
    private String priorityType;
    private String priorityLabel;
    private String priorityDetail;
    private int priorityScore;
    private boolean urgent;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(String complaintId) {
        this.complaintId = complaintId;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public int getComplaintAge() {
        return complaintAge;
    }

    public void setComplaintAge(int complaintAge) {
        this.complaintAge = complaintAge;
    }

    public Date getHardwareReceivedOutwardDate() {
        return hardwareReceivedOutwardDate;
    }

    public void setHardwareReceivedOutwardDate(Date hardwareReceivedOutwardDate) {
        this.hardwareReceivedOutwardDate = hardwareReceivedOutwardDate;
    }

    public int getHardwareDeliveryAge() {
        return hardwareDeliveryAge;
    }

    public void setHardwareDeliveryAge(int hardwareDeliveryAge) {
        this.hardwareDeliveryAge = hardwareDeliveryAge;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public void setBranchCode(String branchCode) {
        this.branchCode = branchCode;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getComplaintStatus() {
        return complaintStatus;
    }

    public void setComplaintStatus(String complaintStatus) {
        this.complaintStatus = complaintStatus;
    }

    public Date getScheduleDate() {
        return scheduleDate;
    }

    public void setScheduleDate(Date scheduleDate) {
        this.scheduleDate = scheduleDate;
    }

    public String getCourierStatus() {
        return courierStatus;
    }

    public void setCourierStatus(String courierStatus) {
        this.courierStatus = courierStatus;
    }

    public Long getVisitorId() {
        return visitorId;
    }

    public void setVisitorId(Long visitorId) {
        this.visitorId = visitorId;
    }

    public String getVisitorName() {
        return visitorName;
    }

    public void setVisitorName(String visitorName) {
        this.visitorName = visitorName;
    }

    public String getPriorityType() {
        return priorityType;
    }

    public void setPriorityType(String priorityType) {
        this.priorityType = priorityType;
    }

    public String getPriorityLabel() {
        return priorityLabel;
    }

    public void setPriorityLabel(String priorityLabel) {
        this.priorityLabel = priorityLabel;
    }

    public String getPriorityDetail() {
        return priorityDetail;
    }

    public void setPriorityDetail(String priorityDetail) {
        this.priorityDetail = priorityDetail;
    }

    public int getPriorityScore() {
        return priorityScore;
    }

    public void setPriorityScore(int priorityScore) {
        this.priorityScore = priorityScore;
    }

    public boolean isUrgent() {
        return urgent;
    }

    public void setUrgent(boolean urgent) {
        this.urgent = urgent;
    }
}
