package com.system.complaints.service;

import com.system.complaints.dto.VisitPlanWorkflowResponse;
import com.system.complaints.model.ComplaintLog;
import com.system.complaints.model.VisitPlan;
import com.system.complaints.model.VisitPlanEntry;
import com.system.complaints.repository.ComplaintLogRepository;
import com.system.complaints.repository.VisitPlanEntryRepository;
import com.system.complaints.repository.VisitPlanRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VisitPlanWorkflowService {
    private final VisitPlanRepository planRepository;
    private final VisitPlanEntryRepository entryRepository;
    private final VisitPlanEntryService entryService;
    private final ComplaintLogRepository complaintLogRepository;
    private final ComplaintLogService complaintLogService;
    private final VisitPlanReviewService reviewService;
    private final VisitPlanSubmissionSnapshotService submissionSnapshotService;
    private final SimpMessagingTemplate messagingTemplate;

    public VisitPlanWorkflowService(
            VisitPlanRepository planRepository,
            VisitPlanEntryRepository entryRepository,
            VisitPlanEntryService entryService,
            ComplaintLogRepository complaintLogRepository,
            ComplaintLogService complaintLogService,
            VisitPlanReviewService reviewService,
            VisitPlanSubmissionSnapshotService submissionSnapshotService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.planRepository = planRepository;
        this.entryRepository = entryRepository;
        this.entryService = entryService;
        this.complaintLogRepository = complaintLogRepository;
        this.complaintLogService = complaintLogService;
        this.reviewService = reviewService;
        this.submissionSnapshotService = submissionSnapshotService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public VisitPlanWorkflowResponse submit(Long planId) {
        VisitPlan plan = entryService.requireEditableOwnedPlan(planId);
        if (entryService.entries(planId).isEmpty()) {
            throw new IllegalArgumentException("Add at least one complaint before submitting the plan.");
        }
        if ("SUBMITTED".equals(plan.getStatus())) {
            throw new IllegalArgumentException("This visit plan is already waiting for admin approval.");
        }
        Timestamp submittedAt = new Timestamp(System.currentTimeMillis());
        submissionSnapshotService.capture(
                plan,
                currentUsername(),
                submittedAt,
                reviewService.buildReview(null).getComplaints()
        );
        plan.setStatus("SUBMITTED");
        plan.setSubmittedAt(submittedAt);
        plan = planRepository.save(plan);
        entryService.setEntryStatus(planId, "SUBMITTED");
        VisitPlanWorkflowResponse response = entryService.response(plan);
        publishPendingApprovalUpdate(plan, "SUBMITTED");
        return response;
    }

    @Transactional(readOnly = true)
    public List<VisitPlanWorkflowResponse> pendingApproval() {
        requireAdmin();
        return planRepository.findByStatusOrderBySubmittedAtAsc("SUBMITTED")
                .stream()
                .map(entryService::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public long pendingApprovalCount() {
        requireAdmin();
        return planRepository.countByStatus("SUBMITTED");
    }

    @Transactional
    public VisitPlanWorkflowResponse reject(Long planId) {
        requireAdmin();
        VisitPlan plan = requireSubmittedPlan(planId);
        for (VisitPlanEntry entry : entryService.entries(planId)) {
            if (!"APPROVED".equals(entry.getApprovalStatus())) {
                entry.setApprovalStatus("REJECTED");
                entryRepository.save(entry);
            }
        }
        VisitPlanWorkflowResponse response = finalizePlan(plan);
        publishPendingApprovalUpdate(plan, plan.getStatus());
        return response;
    }

    @Transactional
    public VisitPlanWorkflowResponse approveItem(Long planId, Long entryId) {
        requireAdmin();
        VisitPlan plan = requireSubmittedPlan(planId);
        VisitPlanEntry entry = requireSubmittedEntry(planId, entryId);
        approveEntry(entry);
        VisitPlanWorkflowResponse response = finalizePlan(plan);
        publishPendingApprovalUpdate(plan, plan.getStatus());
        return response;
    }

    @Transactional
    public VisitPlanWorkflowResponse rejectItem(Long planId, Long entryId) {
        requireAdmin();
        VisitPlan plan = requireSubmittedPlan(planId);
        VisitPlanEntry entry = requireSubmittedEntry(planId, entryId);
        entry.setApprovalStatus("REJECTED");
        entryRepository.save(entry);
        VisitPlanWorkflowResponse response = finalizePlan(plan);
        publishPendingApprovalUpdate(plan, plan.getStatus());
        return response;
    }

    @Transactional
    public VisitPlanWorkflowResponse approve(Long planId) {
        requireAdmin();
        VisitPlan plan = requireSubmittedPlan(planId);
        List<VisitPlanEntry> entries = entryService.entries(planId);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Cannot approve an empty visit plan.");
        }

        for (VisitPlanEntry entry : entries) {
            if ("SUBMITTED".equals(entry.getApprovalStatus())) {
                approveEntry(entry);
            }
        }
        VisitPlanWorkflowResponse response = finalizePlan(plan);
        publishPendingApprovalUpdate(plan, plan.getStatus());
        return response;
    }

    private void approveEntry(VisitPlanEntry entry) {
        if (entry.getVisitorId() == null && safe(entry.getVisitorName()).isBlank()) {
            throw new IllegalArgumentException(
                    "Assign a visitor before approving " + entry.getComplaintId() + "."
            );
        }
        ComplaintLog complaint = complaintLogRepository.findByComplaintId(entry.getComplaintId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Complaint not found: " + entry.getComplaintId()
                ));
        String status = safe(complaint.getComplaintStatus()).toLowerCase();
        if (List.of("closed", "pending for closed", "visit schedule").contains(status)) {
            throw new IllegalArgumentException(
                    entry.getComplaintId() + " is no longer eligible for visit scheduling."
            );
        }

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("complaintStatus", "Visit Schedule");
        updates.put("scheduleDate", entry.getScheduleDate().toString());
        if (entry.getVisitorId() != null) updates.put("visitorId", entry.getVisitorId());
        updates.put("visitorName", entry.getVisitorName());
        complaintLogService.updateComplaintLogFields(complaint.getId(), updates)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Could not schedule complaint: " + entry.getComplaintId()
                ));

        entry.setApprovalStatus("APPROVED");
        entry.setApprovedBy(currentUsername());
        entry.setApprovedAt(new Timestamp(System.currentTimeMillis()));
        entry.setOutcomeStatus("Scheduled");
        entryRepository.save(entry);
    }

    private VisitPlanEntry requireSubmittedEntry(Long planId, Long entryId) {
        VisitPlanEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Planned complaint not found."));
        if (!planId.equals(entry.getPlanId())) {
            throw new IllegalArgumentException("Complaint does not belong to this plan.");
        }
        if (!"SUBMITTED".equals(entry.getApprovalStatus())) {
            throw new IllegalArgumentException("Only pending complaints can be reviewed.");
        }
        return entry;
    }

    private VisitPlanWorkflowResponse finalizePlan(VisitPlan plan) {
        List<VisitPlanEntry> entries = entryService.entries(plan.getId());
        boolean pending = entries.stream()
                .anyMatch(entry -> "SUBMITTED".equals(entry.getApprovalStatus()));
        boolean approved = entries.stream()
                .anyMatch(entry -> "APPROVED".equals(entry.getApprovalStatus()));

        if (pending) {
            plan.setStatus("SUBMITTED");
        } else if (approved) {
            plan.setStatus("APPROVED");
            plan.setApprovedBy(currentUsername());
            plan.setApprovedAt(new Timestamp(System.currentTimeMillis()));
        } else {
            plan.setStatus("REJECTED");
        }
        return entryService.response(planRepository.save(plan));
    }

    private void publishPendingApprovalUpdate(VisitPlan plan, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("pendingCount", planRepository.countByStatus("SUBMITTED"));
        payload.put("planId", plan.getId());
        payload.put("scheduleDate", plan.getScheduleDate() == null ? "" : plan.getScheduleDate().toString());
        payload.put("createdBy", safe(plan.getCreatedBy()));
        payload.put("submittedAt", plan.getSubmittedAt() == null ? "" : plan.getSubmittedAt().toString());
        messagingTemplate.convertAndSend("/topic/visit-plan-approvals", payload);
    }

    private VisitPlan requireSubmittedPlan(Long planId) {
        VisitPlan plan = planRepository.findByIdForUpdate(planId)
                .orElseThrow(() -> new IllegalArgumentException("Visit plan not found."));
        if (!"SUBMITTED".equals(plan.getStatus())) {
            throw new IllegalArgumentException("Only submitted visit plans can be reviewed.");
        }
        return plan;
    }

    private void requireAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ADMIN".equals(authority.getAuthority()));
        if (!admin) throw new IllegalArgumentException("Admin access is required.");
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
