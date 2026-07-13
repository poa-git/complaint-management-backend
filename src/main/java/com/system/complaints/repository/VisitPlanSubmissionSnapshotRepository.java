package com.system.complaints.repository;

import com.system.complaints.model.VisitPlanSubmissionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public interface VisitPlanSubmissionSnapshotRepository
        extends JpaRepository<VisitPlanSubmissionSnapshot, Long> {
    Optional<VisitPlanSubmissionSnapshot> findTopByPlanIdOrderBySnapshotVersionDesc(Long planId);
    List<VisitPlanSubmissionSnapshot> findByScheduleDateBetweenOrderByScheduleDateAscPlanIdAscSnapshotVersionAsc(
            Date from,
            Date to
    );
    List<VisitPlanSubmissionSnapshot> findByCapturedAtBetweenOrderByCapturedAtAscPlanIdAscSnapshotVersionAsc(
            Timestamp from,
            Timestamp to
    );
}
