package com.system.complaints.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.sql.Date;

@Entity
@org.hibernate.annotations.Immutable
@Table(
        name = "visit_plan_submission_snapshot_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_visit_submission_snapshot_complaint",
                columnNames = {"snapshot_id", "complaint_id"}
        ),
        indexes = @Index(name = "idx_visit_submission_snapshot_id", columnList = "snapshot_id")
)
public class VisitPlanSubmissionSnapshotItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false)
    private Long snapshotId;
    @Column(name = "complaint_id", nullable = false)
    private String complaintId;
    private Date complaintDate;
    private String bankName;
    private String branchCode;
    private String branchName;
    private String city;
    private String complaintStatus;
    private String courierStatus;
    private String visitorName;
    private Integer complaintAge;
    private Integer hardwareDeliveryAge;
    private String priorityLabel;
    private String priorityDetail;

    public Long getId() { return id; }
    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long value) { snapshotId = value; }
    public String getComplaintId() { return complaintId; }
    public void setComplaintId(String value) { complaintId = value; }
    public Date getComplaintDate() { return complaintDate; }
    public void setComplaintDate(Date value) { complaintDate = value; }
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
    public String getVisitorName() { return visitorName; }
    public void setVisitorName(String value) { visitorName = value; }
    public Integer getComplaintAge() { return complaintAge; }
    public void setComplaintAge(Integer value) { complaintAge = value; }
    public Integer getHardwareDeliveryAge() { return hardwareDeliveryAge; }
    public void setHardwareDeliveryAge(Integer value) { hardwareDeliveryAge = value; }
    public String getPriorityLabel() { return priorityLabel; }
    public void setPriorityLabel(String value) { priorityLabel = value; }
    public String getPriorityDetail() { return priorityDetail; }
    public void setPriorityDetail(String value) { priorityDetail = value; }
}
