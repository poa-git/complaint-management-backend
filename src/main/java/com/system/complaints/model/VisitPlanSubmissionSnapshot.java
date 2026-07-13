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
import java.sql.Timestamp;

@Entity
@org.hibernate.annotations.Immutable
@Table(
        name = "visit_plan_submission_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_visit_submission_snapshot_version",
                columnNames = {"plan_id", "snapshot_version"}
        ),
        indexes = @Index(name = "idx_visit_submission_schedule_date", columnList = "schedule_date")
)
public class VisitPlanSubmissionSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;
    @Column(name = "snapshot_version", nullable = false)
    private Integer snapshotVersion;
    @Column(name = "schedule_date", nullable = false)
    private Date scheduleDate;
    @Column(name = "submitted_by", nullable = false)
    private String submittedBy;
    @Column(name = "captured_at", nullable = false)
    private Timestamp capturedAt;
    @Column(name = "eligible_count", nullable = false)
    private Integer eligibleCount;

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long value) { planId = value; }
    public Integer getSnapshotVersion() { return snapshotVersion; }
    public void setSnapshotVersion(Integer value) { snapshotVersion = value; }
    public Date getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(Date value) { scheduleDate = value; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String value) { submittedBy = value; }
    public Timestamp getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Timestamp value) { capturedAt = value; }
    public Integer getEligibleCount() { return eligibleCount; }
    public void setEligibleCount(Integer value) { eligibleCount = value; }
}
