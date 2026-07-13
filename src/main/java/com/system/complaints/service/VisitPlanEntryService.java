package com.system.complaints.service;

import com.system.complaints.dto.VisitPlanApproveRequest;
import com.system.complaints.dto.VisitPlanWorkflowResponse;
import com.system.complaints.model.ComplaintLog;
import com.system.complaints.model.VisitPlan;
import com.system.complaints.model.VisitPlanEntry;
import com.system.complaints.repository.VisitPlanEntryRepository;
import com.system.complaints.repository.VisitPlanRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.List;

@Service
public class VisitPlanEntryService {
    private final VisitPlanEntryRepository entryRepository;
    private final VisitPlanRepository planRepository;

    public VisitPlanEntryService(
            VisitPlanEntryRepository entryRepository,
            VisitPlanRepository planRepository
    ) {
        this.entryRepository = entryRepository;
        this.planRepository = planRepository;
    }

    @Transactional
    public VisitPlanWorkflowResponse saveToPlan(
            ComplaintLog complaint,
            Date scheduleDate,
            String courierStatus,
            String visitorStation,
            int complaintAge,
            int hardwareDeliveryAge,
            String priorityType,
            String priorityLabel,
            String priorityDetail,
            boolean urgent,
            VisitPlanApproveRequest request
    ) {
        String username = currentUsername();
        VisitPlan plan = findEditablePlan(username, scheduleDate);
        if (plan == null) {
            plan = new VisitPlan();
            plan.setScheduleDate(scheduleDate);
            plan.setCreatedBy(username);
            plan.setStatus("DRAFT");
            plan = planRepository.save(plan);
        } else if ("REJECTED".equals(plan.getStatus())) {
            plan.setStatus("DRAFT");
            plan = planRepository.save(plan);
        }

        VisitPlanEntry entry = entryRepository
                .findByPlanIdAndComplaintId(plan.getId(), complaint.getComplaintId())
                .orElse(null);
        Long previousPlanId = null;
        if (entry == null) {
            for (VisitPlanEntry candidate : entryRepository
                    .findByComplaintIdOrderByIdDesc(complaint.getComplaintId())) {
                if (candidate.getPlanId() == null) continue;
                VisitPlan candidatePlan = planRepository.findByIdForUpdate(candidate.getPlanId())
                        .orElse(null);
                if (candidatePlan != null
                        && username.equalsIgnoreCase(candidatePlan.getCreatedBy())
                        && !"APPROVED".equals(candidatePlan.getStatus())) {
                    entry = candidate;
                    previousPlanId = candidatePlan.getId();
                    break;
                }
            }
        }
        if (entry == null) entry = new VisitPlanEntry();
        if ("APPROVED".equals(entry.getApprovalStatus())) {
            throw new IllegalArgumentException("An approved complaint cannot be changed.");
        }
        entry.setPlanId(plan.getId());
        fillSnapshot(
                entry, complaint, scheduleDate, courierStatus, visitorStation,
                complaintAge, hardwareDeliveryAge, priorityType, priorityLabel,
                priorityDetail, urgent, request
        );
        entry.setApprovalStatus(plan.getStatus());
        entryRepository.save(entry);
        if (previousPlanId != null
                && !previousPlanId.equals(plan.getId())
                && entryRepository.countByPlanId(previousPlanId) == 0) {
            planRepository.deleteById(previousPlanId);
        }
        return response(plan);
    }

    @Transactional
    public VisitPlanEntry createApprovedEntry(
            ComplaintLog complaint,
            Date scheduleDate,
            String courierStatus,
            String visitorStation,
            int complaintAge,
            int hardwareDeliveryAge,
            String priorityType,
            String priorityLabel,
            String priorityDetail,
            boolean urgent,
            VisitPlanApproveRequest request
    ) {
        VisitPlanEntry entry = new VisitPlanEntry();
        fillSnapshot(
                entry, complaint, scheduleDate, courierStatus, visitorStation,
                complaintAge, hardwareDeliveryAge, priorityType, priorityLabel,
                priorityDetail, urgent, request
        );
        entry.setApprovedBy(currentUsername());
        entry.setApprovedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        entry.setApprovalStatus("APPROVED");
        return entryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public VisitPlanWorkflowResponse getMyEditablePlan(Date scheduleDate) {
        VisitPlan plan = planRepository
                .findByCreatedByAndScheduleDateOrderByIdDesc(currentUsername(), scheduleDate)
                .stream()
                .filter(item -> !"APPROVED".equals(item.getStatus()))
                .findFirst()
                .orElse(null);
        return plan == null ? null : response(plan);
    }

    @Transactional
    public VisitPlanWorkflowResponse removeItem(Long planId, Long entryId) {
        VisitPlan plan = requireEditableOwnedPlan(planId);
        VisitPlanEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Planned complaint not found."));
        if (!planId.equals(entry.getPlanId())) {
            throw new IllegalArgumentException("Complaint does not belong to this plan.");
        }
        if ("APPROVED".equals(entry.getApprovalStatus())) {
            throw new IllegalArgumentException("An approved complaint cannot be removed.");
        }
        entryRepository.deleteByPlanIdAndId(planId, entryId);
        return response(plan);
    }

    @Transactional
    public void updateOutcome(String complaintId, Date scheduleDate, String outcome) {
        entryRepository.findTopByComplaintIdAndScheduleDateOrderByIdDesc(complaintId, scheduleDate)
                .filter(entry -> "APPROVED".equals(entry.getApprovalStatus()))
                .ifPresent(entry -> {
                    entry.setOutcomeStatus(outcome);
                    entryRepository.save(entry);
                });
    }

    public VisitPlan requireEditableOwnedPlan(Long planId) {
        VisitPlan plan = planRepository.findByIdForUpdate(planId)
                .orElseThrow(() -> new IllegalArgumentException("Visit plan not found."));
        if ("APPROVED".equals(plan.getStatus())) {
            throw new IllegalArgumentException("Approved visit plans cannot be changed.");
        }
        if (!isAdmin() && !currentUsername().equalsIgnoreCase(plan.getCreatedBy())) {
            throw new IllegalArgumentException("You cannot change another user's visit plan.");
        }
        return plan;
    }

    public VisitPlanWorkflowResponse response(VisitPlan plan) {
        return VisitPlanWorkflowResponse.from(
                plan,
                entryRepository.findByPlanIdOrderByIdAsc(plan.getId())
        );
    }

    public List<VisitPlanEntry> entries(Long planId) {
        return entryRepository.findByPlanIdOrderByIdAsc(planId);
    }

    public void setEntryStatus(Long planId, String status) {
        for (VisitPlanEntry entry : entries(planId)) {
            entry.setApprovalStatus(status);
            entryRepository.save(entry);
        }
    }

    private VisitPlan findEditablePlan(String username, Date scheduleDate) {
        return planRepository.findByCreatedByAndScheduleDateOrderByIdDesc(username, scheduleDate)
                .stream()
                .filter(plan -> !"APPROVED".equals(plan.getStatus()))
                .findFirst()
                .flatMap(plan -> planRepository.findByIdForUpdate(plan.getId()))
                .orElse(null);
    }

    private void fillSnapshot(
            VisitPlanEntry entry,
            ComplaintLog complaint,
            Date scheduleDate,
            String courierStatus,
            String visitorStation,
            int complaintAge,
            int hardwareDeliveryAge,
            String priorityType,
            String priorityLabel,
            String priorityDetail,
            boolean urgent,
            VisitPlanApproveRequest request
    ) {
        entry.setComplaintId(complaint.getComplaintId());
        entry.setScheduleDate(scheduleDate);
        entry.setVisitorId(complaint.getVisitorId());
        entry.setVisitorName(complaint.getVisitorName());
        entry.setVisitorStation(visitorStation);
        entry.setBankName(complaint.getBankName());
        entry.setBranchCode(complaint.getBranchCode());
        entry.setBranchName(complaint.getBranchName());
        entry.setCity(complaint.getCity());
        entry.setComplaintStatus(complaint.getComplaintStatus());
        entry.setCourierStatus(courierStatus);
        entry.setComplaintAge(complaintAge);
        entry.setHardwareDeliveryAge(hardwareDeliveryAge);
        entry.setPriorityType(priorityType);
        entry.setPriorityLabel(priorityLabel);
        entry.setPriorityDetail(priorityDetail);
        entry.setUrgent(urgent);
        entry.setOutcomeStatus("Scheduled");
        if (request != null) {
            entry.setRouteOrigin(request.getRouteOrigin());
            entry.setRouteDestination(request.getRouteDestination());
            entry.setRouteDistanceKm(request.getRouteDistanceKm());
            entry.setRouteDurationMinutes(request.getRouteDurationMinutes());
        }
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ADMIN".equals(authority.getAuthority()));
    }
}
