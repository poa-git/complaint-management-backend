package com.system.complaints.dto;

import java.util.List;

public record VisitPlanReportingResponse(
        String reportType,
        String fromDate,
        String toDate,
        int totalRecords,
        boolean truncated,
        List<Row> rows
) {
    public record Row(
            String reportDate,
            String planStatus,
            String createdBy,
            String complaintId,
            String bankName,
            String branchCode,
            String branchName,
            String city,
            String visitorName,
            String station,
            String complaintStatus,
            String courierStatus,
            Integer complaintAge,
            Integer hardwareDeliveryAge,
            String priority,
            String priorityDetail,
            Integer stepNo,
            String fromLocation,
            String toLocation,
            Double routeDistanceKm,
            Integer routeDurationMinutes,
            String routeNote
    ) {}
}
