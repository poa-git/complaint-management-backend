package com.system.complaints.repository;

import com.system.complaints.model.VisitPlanEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.sql.Date;
import java.util.List;
import java.util.Optional;

public interface VisitPlanEntryRepository extends JpaRepository<VisitPlanEntry, Long> {
    List<VisitPlanEntry> findByScheduleDateAndApprovalStatusOrderByVisitorStationAscVisitorNameAscCityAsc(
            Date scheduleDate,
            String approvalStatus
    );
    boolean existsByScheduleDateAndApprovalStatus(Date scheduleDate, String approvalStatus);
    Optional<VisitPlanEntry> findTopByComplaintIdAndScheduleDateOrderByIdDesc(String complaintId, Date scheduleDate);
    List<VisitPlanEntry> findByPlanIdOrderByIdAsc(Long planId);
    List<VisitPlanEntry> findByPlanIdInOrderByScheduleDateAscPlanIdAscIdAsc(List<Long> planIds);
    List<VisitPlanEntry> findByScheduleDateBetweenAndApprovalStatusOrderByScheduleDateAscIdAsc(
            Date from,
            Date to,
            String approvalStatus
    );
    List<VisitPlanEntry> findByScheduleDateBetweenOrderByScheduleDateAscVisitorStationAscVisitorNameAscIdAsc(
            Date from,
            Date to
    );
    Optional<VisitPlanEntry> findByPlanIdAndComplaintId(Long planId, String complaintId);
    List<VisitPlanEntry> findByComplaintIdOrderByIdDesc(String complaintId);
    long countByPlanId(Long planId);
    void deleteByPlanIdAndId(Long planId, Long id);
}
