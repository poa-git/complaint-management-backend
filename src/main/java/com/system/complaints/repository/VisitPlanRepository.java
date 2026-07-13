package com.system.complaints.repository;

import com.system.complaints.model.VisitPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.sql.Date;
import java.util.List;

public interface VisitPlanRepository extends JpaRepository<VisitPlan, Long> {
    List<VisitPlan> findByCreatedByAndScheduleDateOrderByIdDesc(String createdBy, Date scheduleDate);
    List<VisitPlan> findByStatusOrderBySubmittedAtAsc(String status);
    long countByStatus(String status);
    List<VisitPlan> findByScheduleDateBetweenOrderByScheduleDateAscIdAsc(Date from, Date to);
    List<VisitPlan> findByScheduleDateBetweenAndStatusOrderByScheduleDateAscIdAsc(
            Date from,
            Date to,
            String status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from VisitPlan plan where plan.id = :id")
    java.util.Optional<VisitPlan> findByIdForUpdate(@Param("id") Long id);
}
