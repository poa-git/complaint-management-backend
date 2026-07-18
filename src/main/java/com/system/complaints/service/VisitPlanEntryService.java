package com.system.complaints.service;

import com.system.complaints.dto.VisitPlanApproveRequest;
import com.system.complaints.dto.VisitPlanInstallationRequest;
import com.system.complaints.dto.VisitPlanWorkflowResponse;
import com.system.complaints.model.ComplaintLog;
import com.system.complaints.model.VisitPlan;
import com.system.complaints.model.VisitPlanEntry;
import com.system.complaints.model.Visitor;
import com.system.complaints.repository.VisitPlanEntryRepository;
import com.system.complaints.repository.VisitPlanRepository;
import com.system.complaints.repository.VisitorRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.List;
import java.util.UUID;

@Service
public class VisitPlanEntryService {
    private final VisitPlanEntryRepository entryRepository;
    private final VisitPlanRepository planRepository;
    private final VisitorRepository visitorRepository;

    public VisitPlanEntryService(
            VisitPlanEntryRepository entryRepository,
            VisitPlanRepository planRepository,
            VisitorRepository visitorRepository
    ) {
        this.entryRepository = entryRepository;
        this.planRepository = planRepository;
        this.visitorRepository = visitorRepository;
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
        VisitPlan plan = getOrCreateEditablePlan(username, scheduleDate);

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
    public VisitPlanWorkflowResponse saveInstallationToPlan(VisitPlanInstallationRequest request) {
        String entryType = safe(request == null ? null : request.getEntryType()).toUpperCase();
        if (entryType.isBlank()) entryType = "NEW_INSTALLATION";
        if (!List.of("NEW_INSTALLATION", "NO_PENDING_COMPLAINT").contains(entryType)) {
            throw new IllegalArgumentException("Unknown additional visit type.");
        }
        if (request == null || request.getVisitorId() == null) {
            throw new IllegalArgumentException("Select a visitor for the planned activity.");
        }
        String destination = safe(request.getDestination());
        if ("NEW_INSTALLATION".equals(entryType) && destination.isBlank()) {
            throw new IllegalArgumentException("Enter the installation destination.");
        }
        if (destination.length() > 255) {
            throw new IllegalArgumentException("Installation destination cannot exceed 255 characters.");
        }
        Date scheduleDate;
        try {
            scheduleDate = Date.valueOf(safe(request.getScheduleDate()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Select a valid activity schedule date.");
        }

        Visitor visitor = visitorRepository.findById(request.getVisitorId())
                .orElseThrow(() -> new IllegalArgumentException("Selected visitor was not found."));
        String visitorName = safe(visitor.getName());
        if (visitorName.isBlank()) {
            throw new IllegalArgumentException("Selected visitor has no name.");
        }

        VisitPlan plan = getOrCreateEditablePlan(currentUsername(), scheduleDate);
        VisitPlanEntry entry = new VisitPlanEntry();
        entry.setPlanId(plan.getId());
        entry.setEntryType(entryType);
        entry.setComplaintId("PLAN-ACTIVITY-" + UUID.randomUUID());
        entry.setScheduleDate(scheduleDate);
        entry.setVisitorId(visitor.getId());
        entry.setVisitorName(visitorName);
        entry.setVisitorStation(safe(visitor.getCity()));
        boolean installation = "NEW_INSTALLATION".equals(entryType);
        entry.setCity(installation ? destination : "");
        entry.setComplaintStatus(installation ? "New Installation" : "No Pending Complaint");
        entry.setPriorityType(installation ? "INSTALLATION" : "NO_PENDING_COMPLAINT");
        entry.setPriorityLabel(installation ? "New installation" : "No pending complaint");
        entry.setPriorityDetail(installation
                ? "Installation visit at " + destination
                : "No pending complaint for this visitor");
        entry.setRouteOrigin(safe(visitor.getCity()));
        entry.setRouteDestination(installation ? destination : "");
        entry.setOutcomeStatus("Scheduled");
        entry.setApprovalStatus(plan.getStatus());
        entryRepository.save(entry);
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

    @Transactional(readOnly = true)
    public List<VisitPlanWorkflowResponse> getMyEditablePlans() {
        return planRepository
                .findByCreatedByAndStatusNotOrderByScheduleDateAscIdAsc(
                        currentUsername(),
                        "APPROVED"
                )
                .stream()
                .map(this::response)
                .toList();
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

    private VisitPlan getOrCreateEditablePlan(String username, Date scheduleDate) {
        VisitPlan plan = findEditablePlan(username, scheduleDate);
        if (plan == null) {
            plan = new VisitPlan();
            plan.setScheduleDate(scheduleDate);
            plan.setCreatedBy(username);
            plan.setStatus("DRAFT");
            return planRepository.save(plan);
        }
        if ("REJECTED".equals(plan.getStatus())) {
            plan.setStatus("DRAFT");
            return planRepository.save(plan);
        }
        return plan;
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
