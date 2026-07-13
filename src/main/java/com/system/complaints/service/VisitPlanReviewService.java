package com.system.complaints.service;

import com.system.complaints.dto.VisitPlanApproveRequest;
import com.system.complaints.dto.VisitPlanApproveResponse;
import com.system.complaints.dto.VisitPlanComplaintDTO;
import com.system.complaints.dto.VisitPlanReviewResponse;
import com.system.complaints.dto.VisitPlanWorkflowResponse;
import com.system.complaints.model.Branch;
import com.system.complaints.model.ComplaintLog;
import com.system.complaints.model.HardwareLog;
import com.system.complaints.model.Visitor;
import com.system.complaints.repository.BranchRepository;
import com.system.complaints.repository.ComplaintLogRepository;
import com.system.complaints.repository.HardwareLogRepository;
import com.system.complaints.repository.VisitorRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class VisitPlanReviewService {
    private static final int DEFAULT_LIMIT = 5000;
    private static final int MAX_LIMIT = 10000;

    private static final List<String> ELIGIBLE_STATUS_LABELS = List.of("Open", "Delivered");
    private static final List<String> OPEN_FAMILY_STATUS_LABELS = List.of(
            "Open",
            "FOC",
            "Quotation",
            "Network Issue",
            "Visit Schedule",
            "Hardware Picked",
            "Visit On Hold",
            "Dispatched",
            "Delivered"
    );
    private static final Set<String> ELIGIBLE_COMPLAINT_STATUSES = Set.of("open", "delivered");

    private static final Set<String> EXCLUDED_COURIER_STATUSES = Set.of(
            "received inward",
            "dispatch outward",
            "dispatch inward",
            "hardware ready",
            "out of stock",
            "observation"
    );

    private static final Set<String> BAHL_BANK_LABELS = Set.of("bah", "bahl", "bank al habib", "bank al-habib");
    private static final Set<String> ABL_BANK_LABELS = Set.of("abl", "allied bank", "allied bank limited");
    private static final Set<String> SINDH_CITY_LABELS = Set.of(
            "badin",
            "dadu",
            "daharki",
            "ghotki",
            "hyderabad",
            "jacobabad",
            "jamshoro",
            "karachi",
            "kashmore",
            "khairpur",
            "larkana",
            "matiari",
            "mirpur khas",
            "moro",
            "naushahro feroze",
            "nawabshah",
            "qambar",
            "qasimabad",
            "sanghar",
            "sehwan",
            "shahdadkot",
            "shahdadpur",
            "shikarpur",
            "sukkur",
            "tando adam",
            "tando allahyar",
            "tando muhammad khan",
            "thatta",
            "tharparkar",
            "umar kot",
            "umerkot"
    );

    private final ComplaintLogRepository complaintLogRepository;
    private final HardwareLogRepository hardwareLogRepository;
    private final ComplaintLogService complaintLogService;
    private final BranchRepository branchRepository;
    private final VisitorRepository visitorRepository;
    private final VisitPlanEntryService visitPlanEntryService;

    public VisitPlanReviewService(
            ComplaintLogRepository complaintLogRepository,
            HardwareLogRepository hardwareLogRepository,
            ComplaintLogService complaintLogService,
            BranchRepository branchRepository,
            VisitorRepository visitorRepository,
            VisitPlanEntryService visitPlanEntryService
    ) {
        this.complaintLogRepository = complaintLogRepository;
        this.hardwareLogRepository = hardwareLogRepository;
        this.complaintLogService = complaintLogService;
        this.branchRepository = branchRepository;
        this.visitorRepository = visitorRepository;
        this.visitPlanEntryService = visitPlanEntryService;
    }

    @Transactional(readOnly = true)
    public VisitPlanReviewResponse buildReview(Integer requestedLimit) {
        int limit = normalizeLimit(requestedLimit);
        List<ComplaintLog> complaints = complaintLogRepository
                .findByComplaintStatusInOrderByIdDesc(ELIGIBLE_STATUS_LABELS, PageRequest.of(0, limit))
                .getContent();

        Map<Long, HardwareLog> latestHardwareLogs = loadLatestHardwareLogs(complaints);
        Map<String, String> bahlRegionByBranchCode = loadBahlRegionByBranchCode();
        List<VisitPlanComplaintDTO> eligible = new ArrayList<>();

        for (ComplaintLog complaint : complaints) {
            HardwareLog latestHardwareLog = latestHardwareLogs.get(complaint.getId());
            String courierStatus = latestHardwareLog == null ? null : latestHardwareLog.getCourierStatus();
            if (!isComplaintEligible(complaint, courierStatus)) {
                continue;
            }

            eligible.add(toDto(complaint, latestHardwareLog, courierStatus, bahlRegionByBranchCode));
        }

        eligible.sort(
                Comparator.comparingInt(VisitPlanComplaintDTO::getPriorityScore)
                        .reversed()
                        .thenComparing(VisitPlanComplaintDTO::getId, Comparator.nullsLast(Comparator.reverseOrder()))
        );
        VisitPlanReviewResponse response = new VisitPlanReviewResponse();
        response.setReviewedAt(LocalDateTime.now().toString());
        response.setTotal(eligible.size());
        response.setSourceTotal(complaintLogRepository.countByComplaintStatusIn(OPEN_FAMILY_STATUS_LABELS));
        response.setComplaints(eligible);
        return response;
    }

    @Transactional
    public VisitPlanApproveResponse approveComplaint(String complaintId, VisitPlanApproveRequest request) {
        ComplaintLog complaint = complaintLogRepository.findByComplaintId(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));

        if (!hasVisitor(complaint)) {
            throw new IllegalArgumentException("Assign a visitor before approving this complaint for visit plan.");
        }

        Date scheduleDate = resolveScheduleDate(request);
        HardwareLog latestHardwareLog = hardwareLogRepository
                .findTopByComplaintLog_ComplaintIdOrderByIdDesc(complaintId)
                .orElse(null);
        String courierStatus = latestHardwareLog == null
                ? null
                : latestHardwareLog.getCourierStatus();
        if (!isComplaintEligible(complaint, courierStatus)) {
            throw new IllegalArgumentException("Complaint is not eligible for visit plan approval.");
        }
        int complaintAge = calculateAge(complaint.getDate());
        int hardwareDeliveryAge = calculateAge(
                latestHardwareLog == null ? null : latestHardwareLog.getReceivedOutwardDate()
        );
        Priority priority = getVisitPriority(
                complaint,
                complaintAge,
                hardwareDeliveryAge,
                loadBahlRegionByBranchCode()
        );
        String visitorStation = resolveVisitorStation(complaint);
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("complaintStatus", "Visit Schedule");
        updates.put("scheduleDate", scheduleDate.toString());
        updates.put("visitorId", complaint.getVisitorId());
        updates.put("visitorName", complaint.getVisitorName());

        ComplaintLog updated = complaintLogService.updateComplaintLogFields(complaint.getId(), updates)
                .orElseThrow(() -> new IllegalArgumentException("Could not update complaint: " + complaintId));

        visitPlanEntryService.createApprovedEntry(
                updated,
                scheduleDate,
                courierStatus,
                visitorStation,
                complaintAge,
                hardwareDeliveryAge,
                priority.type,
                priority.label,
                priority.detail,
                priority.urgent,
                request
        );

        VisitPlanApproveResponse response = new VisitPlanApproveResponse();
        response.setComplaintId(updated.getComplaintId());
        response.setScheduleDate(scheduleDate.toString());
        response.setComplaintStatus(updated.getComplaintStatus());
        response.setVisitorName(updated.getVisitorName());
        return response;
    }

    @Transactional
    public VisitPlanWorkflowResponse saveComplaintToPlan(
            String complaintId,
            VisitPlanApproveRequest request
    ) {
        ComplaintLog complaint = complaintLogRepository.findByComplaintId(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));
        if (!hasVisitor(complaint)) {
            throw new IllegalArgumentException("Assign a visitor before adding this complaint to a plan.");
        }

        HardwareLog latestHardwareLog = hardwareLogRepository
                .findTopByComplaintLog_ComplaintIdOrderByIdDesc(complaintId)
                .orElse(null);
        String courierStatus = latestHardwareLog == null
                ? null
                : latestHardwareLog.getCourierStatus();
        if (!isComplaintEligible(complaint, courierStatus)) {
            throw new IllegalArgumentException("Complaint is no longer eligible for visit planning.");
        }

        Date scheduleDate = resolveScheduleDate(request);
        int complaintAge = calculateAge(complaint.getDate());
        int hardwareDeliveryAge = calculateAge(
                latestHardwareLog == null ? null : latestHardwareLog.getReceivedOutwardDate()
        );
        Priority priority = getVisitPriority(
                complaint,
                complaintAge,
                hardwareDeliveryAge,
                loadBahlRegionByBranchCode()
        );

        return visitPlanEntryService.saveToPlan(
                complaint,
                scheduleDate,
                courierStatus,
                resolveVisitorStation(complaint),
                complaintAge,
                hardwareDeliveryAge,
                priority.type,
                priority.label,
                priority.detail,
                priority.urgent,
                request
        );
    }

    private Map<Long, HardwareLog> loadLatestHardwareLogs(List<ComplaintLog> complaints) {
        List<Long> complaintIds = complaints.stream()
                .map(ComplaintLog::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());

        if (complaintIds.isEmpty()) {
            return Map.of();
        }

        return hardwareLogRepository.findByComplaintLogIdIn(complaintIds).stream()
                .filter(log -> log.getComplaintLog() != null && log.getComplaintLog().getId() != null)
                .collect(Collectors.toMap(
                        log -> log.getComplaintLog().getId(),
                        log -> log,
                        (left, right) -> {
                            Long leftId = left.getId();
                            Long rightId = right.getId();
                            if (leftId == null) return right;
                            if (rightId == null) return left;
                            return rightId > leftId ? right : left;
                        }
                ));
    }

    private VisitPlanComplaintDTO toDto(
            ComplaintLog complaint,
            HardwareLog latestHardwareLog,
            String courierStatus,
            Map<String, String> bahlRegionByBranchCode
    ) {
        Date hardwareReceivedOutwardDate =
                latestHardwareLog == null ? null : latestHardwareLog.getReceivedOutwardDate();
        int complaintAge = calculateAge(complaint.getDate());
        int hardwareDeliveryAge = calculateAge(hardwareReceivedOutwardDate);
        Priority priority = getVisitPriority(
                complaint,
                complaintAge,
                hardwareDeliveryAge,
                bahlRegionByBranchCode
        );

        VisitPlanComplaintDTO dto = new VisitPlanComplaintDTO();
        dto.setId(complaint.getId());
        dto.setComplaintId(complaint.getComplaintId());
        dto.setDate(complaint.getDate());
        dto.setComplaintAge(complaintAge);
        dto.setHardwareReceivedOutwardDate(hardwareReceivedOutwardDate);
        dto.setHardwareDeliveryAge(hardwareDeliveryAge);
        dto.setBankName(complaint.getBankName());
        dto.setBranchCode(complaint.getBranchCode());
        dto.setBranchName(complaint.getBranchName());
        dto.setCity(complaint.getCity());
        dto.setComplaintStatus(complaint.getComplaintStatus());
        dto.setCourierStatus(courierStatus);
        dto.setVisitorId(complaint.getVisitorId());
        dto.setVisitorName(complaint.getVisitorName());
        dto.setPriorityType(priority.type);
        dto.setPriorityLabel(priority.label);
        dto.setPriorityDetail(priority.detail);
        dto.setPriorityScore(priority.score);
        dto.setUrgent(priority.urgent);
        return dto;
    }

    private Priority getVisitPriority(
            ComplaintLog complaint,
            int complaintAge,
            int hardwareDeliveryAge,
            Map<String, String> bahlRegionByBranchCode
    ) {
        boolean delivered = "delivered".equals(normalize(complaint.getComplaintStatus()));
        boolean longPending = complaintAge >= 5;
        boolean urgent = (delivered && hardwareDeliveryAge >= 5) || complaintAge >= 10;

        int score = Math.max(0, complaintAge) * 2;
        String type = "normal";
        String label = "Normal";
        String detail = complaintAge >= 0 ? "Open " + complaintAge + " days" : "No age data";

        if (longPending) {
            type = "longPending";
            label = complaintAge >= 10 ? "Critical pending" : "Long pending";
            detail = "Open " + complaintAge + " days";
            score += complaintAge >= 10 ? 35 : 20;
        }

        if (delivered) {
            int hardwareAge = Math.max(0, hardwareDeliveryAge);
            type = "hardwareInstall";
            label = hardwareDeliveryAge >= 5
                    ? "Urgent hardware install"
                    : hardwareDeliveryAge >= 2
                    ? "Hardware install priority"
                    : "Hardware install";
            detail = hardwareDeliveryAge >= 0
                    ? "Delivered " + hardwareDeliveryAge + " days ago"
                    : "Delivered status";
            score += 50 + hardwareAge * 10;
        }

        if (urgent) {
            type = delivered ? "hardwareInstall" : "longPending";
            score += 50;
        }

        Priority slaPriority = delivered
                ? null
                : getSlaPriority(complaint, complaintAge, bahlRegionByBranchCode);
        if (slaPriority != null) {
            type = slaPriority.type;
            label = slaPriority.label;
            detail = slaPriority.detail;
            score += slaPriority.score;
            urgent = true;
        }

        return new Priority(type, label, detail, score, urgent);
    }

    private Priority getSlaPriority(
            ComplaintLog complaint,
            int complaintAge,
            Map<String, String> bahlRegionByBranchCode
    ) {
        if (complaintAge < 0 || !"open".equals(normalize(complaint.getComplaintStatus()))) {
            return null;
        }

        if (isBahlBank(complaint)
                && isBahlSindhBranch(complaint, bahlRegionByBranchCode)
                && complaintAge >= 2) {
            return new Priority(
                    "slaDue",
                    "BAHL Sindh deadline",
                    "Open " + complaintAge + " days; must be planned within 2 days",
                    140 + complaintAge * 8,
                    true
            );
        }

        if (isAblBank(complaint) && complaintAge >= 5) {
            return new Priority(
                    "slaDue",
                    "ABL deadline",
                    "Open " + complaintAge + " days; must be planned within 5 days",
                    120 + complaintAge * 6,
                    true
            );
        }

        return null;
    }

    private boolean isComplaintEligible(ComplaintLog complaint, String courierStatus) {
        String complaintStatus = normalize(complaint.getComplaintStatus());
        String normalizedCourierStatus = normalize(courierStatus);

        return ELIGIBLE_COMPLAINT_STATUSES.contains(complaintStatus)
                && !EXCLUDED_COURIER_STATUSES.contains(normalizedCourierStatus);
    }

    private boolean hasVisitor(ComplaintLog complaint) {
        return complaint.getVisitorId() != null || !normalize(complaint.getVisitorName()).isBlank();
    }

    private String resolveVisitorStation(ComplaintLog complaint) {
        if (complaint.getVisitorId() != null) {
            return visitorRepository.findById(complaint.getVisitorId())
                    .map(Visitor::getCity)
                    .orElse("");
        }

        String visitorName = safe(complaint.getVisitorName());
        if (visitorName.isBlank()) {
            return "";
        }
        return visitorRepository.findFirstByNameIgnoreCase(visitorName)
                .map(Visitor::getCity)
                .orElse("");
    }

    private Date resolveScheduleDate(VisitPlanApproveRequest request) {
        String requestedDate = request == null ? "" : safe(request.getScheduleDate());
        if (requestedDate.isBlank()) {
            return Date.valueOf(LocalDate.now().plusDays(1));
        }
        return Date.valueOf(requestedDate);
    }

    private boolean isBahlBank(ComplaintLog complaint) {
        return BAHL_BANK_LABELS.contains(normalize(complaint.getBankName()));
    }

    private boolean isAblBank(ComplaintLog complaint) {
        return ABL_BANK_LABELS.contains(normalize(complaint.getBankName()));
    }

    private Map<String, String> loadBahlRegionByBranchCode() {
        Map<String, String> regions = new LinkedHashMap<>();
        for (Branch branch : branchRepository.findByNormalizedBanks(BAHL_BANK_LABELS)) {
            String branchCode = normalizeBranchCode(branch.getBranchCode());
            String region = safe(branch.getRegion());
            regions.merge(
                    branchCode,
                    region,
                    (existing, replacement) -> existing.isBlank() ? replacement : existing
            );
        }
        return regions;
    }

    private boolean isBahlSindhBranch(
            ComplaintLog complaint,
            Map<String, String> bahlRegionByBranchCode
    ) {
        String region = bahlRegionByBranchCode.get(normalizeBranchCode(complaint.getBranchCode()));
        if (region != null && !region.isBlank()) {
            return normalize(region).contains("sindh");
        }
        return isSindhCity(complaint);
    }

    private boolean isSindhCity(ComplaintLog complaint) {
        return SINDH_CITY_LABELS.contains(normalize(complaint.getCity()));
    }

    private String normalizeBranchCode(String branchCode) {
        String normalized = normalize(branchCode).replaceFirst("^0+", "");
        return normalized.isBlank() ? "0" : normalized;
    }

    private int calculateAge(Date date) {
        if (date == null) {
            return -1;
        }
        return (int) Math.max(0, ChronoUnit.DAYS.between(date.toLocalDate(), LocalDate.now()));
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(MAX_LIMIT, Math.max(1, requestedLimit));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static class Priority {
        private final String type;
        private final String label;
        private final String detail;
        private final int score;
        private final boolean urgent;

        private Priority(String type, String label, String detail, int score, boolean urgent) {
            this.type = type;
            this.label = label;
            this.detail = detail;
            this.score = score;
            this.urgent = urgent;
        }
    }
}


