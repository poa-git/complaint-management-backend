package com.system.complaints.controller;

import com.system.complaints.dto.RouteSuggestionRequest;
import com.system.complaints.dto.RouteSuggestionResponse;
import com.system.complaints.dto.NearestStationResponse;
import com.system.complaints.dto.VisitPlanApproveRequest;
import com.system.complaints.dto.VisitPlanApproveResponse;
import com.system.complaints.dto.VisitPlanReviewResponse;
import com.system.complaints.dto.VisitPlanWorkflowResponse;
import com.system.complaints.service.GoogleRouteSuggestionService;
import com.system.complaints.service.VisitPlanEntryService;
import com.system.complaints.service.VisitPlanReportService;
import com.system.complaints.service.VisitPlanReviewService;
import com.system.complaints.service.VisitPlanWorkflowService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;

@RestController
@RequestMapping("/visit-plan")
public class VisitPlanController {
    private final GoogleRouteSuggestionService googleRouteSuggestionService;
    private final VisitPlanReportService visitPlanReportService;
    private final VisitPlanReviewService visitPlanReviewService;
    private final VisitPlanEntryService visitPlanEntryService;
    private final VisitPlanWorkflowService visitPlanWorkflowService;

    public VisitPlanController(
            GoogleRouteSuggestionService googleRouteSuggestionService,
            VisitPlanReportService visitPlanReportService,
            VisitPlanReviewService visitPlanReviewService,
            VisitPlanEntryService visitPlanEntryService,
            VisitPlanWorkflowService visitPlanWorkflowService
    ) {
        this.googleRouteSuggestionService = googleRouteSuggestionService;
        this.visitPlanReportService = visitPlanReportService;
        this.visitPlanReviewService = visitPlanReviewService;
        this.visitPlanEntryService = visitPlanEntryService;
        this.visitPlanWorkflowService = visitPlanWorkflowService;
    }

    @GetMapping("/review")
    public ResponseEntity<VisitPlanReviewResponse> getReview(
            @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(visitPlanReviewService.buildReview(size));
    }

    @PostMapping("/complaints/{complaintId}/approve")
    public ResponseEntity<VisitPlanApproveResponse> approveComplaint(
            @PathVariable String complaintId,
            @RequestBody(required = false) VisitPlanApproveRequest request
    ) {
        return ResponseEntity.ok(visitPlanReviewService.approveComplaint(complaintId, request));
    }

    @PostMapping("/plans/items/{complaintId}")
    public ResponseEntity<VisitPlanWorkflowResponse> savePlanItem(
            @PathVariable String complaintId,
            @RequestBody(required = false) VisitPlanApproveRequest request
    ) {
        return ResponseEntity.ok(visitPlanReviewService.saveComplaintToPlan(complaintId, request));
    }

    @GetMapping("/plans/mine")
    public ResponseEntity<VisitPlanWorkflowResponse> getMyPlan(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate date
    ) {
        return ResponseEntity.ok(
                visitPlanEntryService.getMyEditablePlan(java.sql.Date.valueOf(date))
        );
    }

    @GetMapping("/plans/mine/editable")
    public ResponseEntity<java.util.List<VisitPlanWorkflowResponse>> getMyEditablePlans() {
        return ResponseEntity.ok(visitPlanEntryService.getMyEditablePlans());
    }

    @DeleteMapping("/plans/{planId}/items/{entryId}")
    public ResponseEntity<VisitPlanWorkflowResponse> removePlanItem(
            @PathVariable Long planId,
            @PathVariable Long entryId
    ) {
        return ResponseEntity.ok(visitPlanEntryService.removeItem(planId, entryId));
    }

    @PostMapping("/plans/{planId}/submit")
    public ResponseEntity<VisitPlanWorkflowResponse> submitPlan(@PathVariable Long planId) {
        return ResponseEntity.ok(visitPlanWorkflowService.submit(planId));
    }

    @GetMapping("/plans/pending")
    public ResponseEntity<java.util.List<VisitPlanWorkflowResponse>> getPendingPlans() {
        return ResponseEntity.ok(visitPlanWorkflowService.pendingApproval());
    }

    @GetMapping("/plans/pending/count")
    public ResponseEntity<java.util.Map<String, Long>> getPendingPlanCount() {
        return ResponseEntity.ok(java.util.Map.of("pendingCount", visitPlanWorkflowService.pendingApprovalCount()));
    }

    @PostMapping("/plans/{planId}/approve")
    public ResponseEntity<VisitPlanWorkflowResponse> approvePlan(@PathVariable Long planId) {
        return ResponseEntity.ok(visitPlanWorkflowService.approve(planId));
    }

    @PostMapping("/plans/{planId}/reject")
    public ResponseEntity<VisitPlanWorkflowResponse> rejectPlan(@PathVariable Long planId) {
        return ResponseEntity.ok(visitPlanWorkflowService.reject(planId));
    }

    @PostMapping("/plans/{planId}/items/{entryId}/approve")
    public ResponseEntity<VisitPlanWorkflowResponse> approvePlanItem(
            @PathVariable Long planId,
            @PathVariable Long entryId
    ) {
        return ResponseEntity.ok(visitPlanWorkflowService.approveItem(planId, entryId));
    }

    @PostMapping("/plans/{planId}/items/{entryId}/reject")
    public ResponseEntity<VisitPlanWorkflowResponse> rejectPlanItem(
            @PathVariable Long planId,
            @PathVariable Long entryId
    ) {
        return ResponseEntity.ok(visitPlanWorkflowService.rejectItem(planId, entryId));
    }

    @PostMapping("/route-suggestions")
    public ResponseEntity<RouteSuggestionResponse> getRouteSuggestions(
            @RequestBody RouteSuggestionRequest request
    ) {
        return ResponseEntity.ok(googleRouteSuggestionService.buildSuggestions(request));
    }

    @GetMapping("/nearest-stations")
    public ResponseEntity<NearestStationResponse> getNearestStations(
            @RequestParam String city
    ) {
        return ResponseEntity.ok(googleRouteSuggestionService.findNearestStations(city));
    }

    @GetMapping("/reports/daily")
    public ResponseEntity<StreamingResponseBody> getDailyReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate date
    ) {
        if (!visitPlanReportService.hasSchedules(date)) {
            return ResponseEntity.noContent().build();
        }

        String fileName = visitPlanReportService.buildFileName(date);
        StreamingResponseBody stream = outputStream ->
                visitPlanReportService.writeDailyReport(date, outputStream);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .body(stream);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<java.util.Map<String, String>> handleInvalidPlanAction(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest().body(java.util.Map.of("message", exception.getMessage()));
    }
}


