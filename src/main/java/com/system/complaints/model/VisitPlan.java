package com.system.complaints.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.sql.Date;
import java.sql.Timestamp;

@Entity
@Table(name = "visit_plans")
public class VisitPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Date scheduleDate;
    private String status;
    private String createdBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp submittedAt;
    private String approvedBy;
    private Timestamp approvedAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        createdAt = now;
        updatedAt = now;
        if (status == null || status.isBlank()) status = "DRAFT";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public Long getId() { return id; }
    public Date getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(Date value) { scheduleDate = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { createdBy = value; }
    public Timestamp getCreatedAt() { return createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public Timestamp getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Timestamp value) { submittedAt = value; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String value) { approvedBy = value; }
    public Timestamp getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Timestamp value) { approvedAt = value; }
    public Long getVersion() { return version; }
}
