package com.system.complaints.service;

import com.system.complaints.model.ComplaintLog;
import com.system.complaints.model.Schedule;
import com.system.complaints.model.Visitor;
import com.system.complaints.model.VisitPlanEntry;
import com.system.complaints.repository.ComplaintLogRepository;
import com.system.complaints.repository.ScheduleRepository;
import com.system.complaints.repository.VisitorRepository;
import com.system.complaints.repository.VisitPlanEntryRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class VisitPlanReportService {
    private static final List<String> DETAIL_HEADERS = List.of(
            "S.No", "Complaint ID", "Schedule Date", "Visitor", "Station",
            "Bank", "Branch Code", "Branch Name", "Complaint City", "Courier Status", "Complaint Age",
            "HW Delivery Age", "Priority", "Priority Detail", "Route",
            "Route Distance (km)", "Route Time (min)", "Outcome", "Scheduled By"
    );
    private static final List<String> SUMMARY_HEADERS = List.of(
            "Visitor", "Station", "Total Visits", "Scheduled", "Successful", "Expired", "Canceled"
    );
    private static final List<String> EXCEPTION_HEADERS = List.of(
            "Complaint ID", "Visitor", "Station", "Bank", "Branch", "City",
            "Priority", "Outcome", "Attention Required"
    );

    private final ScheduleRepository scheduleRepository;
    private final ComplaintLogRepository complaintLogRepository;
    private final VisitorRepository visitorRepository;
    private final VisitPlanEntryRepository visitPlanEntryRepository;

    public VisitPlanReportService(
            ScheduleRepository scheduleRepository,
            ComplaintLogRepository complaintLogRepository,
            VisitorRepository visitorRepository,
            VisitPlanEntryRepository visitPlanEntryRepository
    ) {
        this.scheduleRepository = scheduleRepository;
        this.complaintLogRepository = complaintLogRepository;
        this.visitorRepository = visitorRepository;
        this.visitPlanEntryRepository = visitPlanEntryRepository;
    }

    public String buildFileName(LocalDate reportDate) {
        return "Visit_Plan_" + reportDate + ".xlsx";
    }

    @Transactional(readOnly = true)
    public boolean hasSchedules(LocalDate reportDate) {
        Date date = Date.valueOf(reportDate);
        return visitPlanEntryRepository.existsByScheduleDateAndApprovalStatus(date, "APPROVED")
                || scheduleRepository.existsByScheduledFor(date);
    }

    @Transactional(readOnly = true)
    public void writeDailyReport(LocalDate reportDate, OutputStream outputStream) throws IOException {
        List<ReportRow> rows = loadRows(reportDate);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle bodyStyle = createBodyStyle(workbook);

            writeDetailSheet(workbook, reportDate, rows, titleStyle, headerStyle, bodyStyle);
            writeSummarySheet(workbook, reportDate, rows, titleStyle, headerStyle, bodyStyle);
            writeExceptionsSheet(workbook, reportDate, rows, titleStyle, headerStyle, bodyStyle);

            workbook.write(outputStream);
            workbook.dispose();
        }
    }

    private List<ReportRow> loadRows(LocalDate reportDate) {
        Date date = Date.valueOf(reportDate);
        List<VisitPlanEntry> entries = visitPlanEntryRepository
                .findByScheduleDateAndApprovalStatusOrderByVisitorStationAscVisitorNameAscCityAsc(
                        date,
                        "APPROVED"
                );
        List<ReportRow> rows = entries.stream()
                .map(entry -> new ReportRow(
                        safe(entry.getComplaintId()),
                        reportDate.toString(),
                        safe(entry.getVisitorName()),
                        safe(entry.getVisitorStation()),
                        safe(entry.getBankName()),
                        safe(entry.getBranchCode()),
                        safe(entry.getBranchName()),
                        safe(entry.getCity()),
                        safe(entry.getCourierStatus()),
                        entry.getComplaintAge() == null ? -1 : entry.getComplaintAge(),
                        entry.getHardwareDeliveryAge() == null ? -1 : entry.getHardwareDeliveryAge(),
                        safe(entry.getPriorityLabel()),
                        safe(entry.getPriorityDetail()),
                        safe(entry.getRouteOrigin()),
                        safe(entry.getRouteDestination()),
                        entry.getRouteDistanceKm(),
                        entry.getRouteDurationMinutes(),
                        blankToDefault(entry.getOutcomeStatus(), "Scheduled"),
                        safe(entry.getApprovedBy())
                ))
                .collect(Collectors.toCollection(ArrayList::new));

        Set<String> capturedComplaintIds = entries.stream()
                .map(VisitPlanEntry::getComplaintId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        List<Schedule> schedules = scheduleRepository.findByScheduledFor(date).stream()
                .filter(schedule -> !capturedComplaintIds.contains(schedule.getComplaintId()))
                .toList();
        List<String> complaintIds = schedules.stream()
                .map(Schedule::getComplaintId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        Map<String, ComplaintLog> complaints = complaintIds.isEmpty()
                ? Map.of()
                : complaintLogRepository.findByComplaintIdIn(complaintIds)
                    .stream()
                    .collect(Collectors.toMap(
                            ComplaintLog::getComplaintId,
                            Function.identity(),
                            (first, second) -> first
                    ));

        Set<Long> visitorIds = complaints.values().stream()
                .map(ComplaintLog::getVisitorId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, Visitor> visitors = visitorRepository.findAllById(visitorIds).stream()
                .collect(Collectors.toMap(Visitor::getId, Function.identity()));

        for (Schedule schedule : schedules) {
            ComplaintLog complaint = complaints.get(schedule.getComplaintId());
            if (complaint == null) {
                continue;
            }
            Visitor visitor = complaint.getVisitorId() == null
                    ? null
                    : visitors.get(complaint.getVisitorId());
            rows.add(new ReportRow(
                    safe(schedule.getComplaintId()),
                    reportDate.toString(),
                    safe(complaint.getVisitorName()),
                    visitor == null ? "" : safe(visitor.getCity()),
                    safe(complaint.getBankName()),
                    safe(complaint.getBranchCode()),
                    safe(complaint.getBranchName()),
                    safe(complaint.getCity()),
                    "",
                    calculateAgeAt(complaint.getDate(), reportDate),
                    -1,
                    "Legacy schedule",
                    "Historical priority and route were not captured",
                    "",
                    "",
                    null,
                    null,
                    blankToDefault(schedule.getOutcomeStatus(), "Scheduled"),
                    safe(schedule.getPerformedBy())
            ));
        }

        rows.sort(Comparator
                .comparing(ReportRow::station, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ReportRow::visitor, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ReportRow::city, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ReportRow::complaintId, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private void writeDetailSheet(
            SXSSFWorkbook workbook,
            LocalDate reportDate,
            List<ReportRow> rows,
            CellStyle titleStyle,
            CellStyle headerStyle,
            CellStyle bodyStyle
    ) {
        Sheet sheet = workbook.createSheet("Daily Visit Plan");
        writeTitle(sheet, "Visit Plan - " + reportDate, DETAIL_HEADERS.size(), titleStyle);
        writeHeaders(sheet.createRow(1), DETAIL_HEADERS, headerStyle);

        int rowIndex = 2;
        for (int index = 0; index < rows.size(); index++) {
            ReportRow value = rows.get(index);
            Row row = sheet.createRow(rowIndex++);
            writeValues(row, List.of(
                    index + 1,
                    value.complaintId(),
                    value.scheduleDate(),
                    value.visitor(),
                    value.station(),
                    value.bank(),
                    value.branchCode(),
                    value.branchName(),
                    value.city(),
                    value.courierStatus(),
                    displayAge(value.complaintAge()),
                    displayAge(value.hardwareDeliveryAge()),
                    value.priorityLabel(),
                    value.priorityDetail(),
                    formatRoute(value.routeOrigin(), value.routeDestination()),
                    value.routeDistanceKm() == null ? "" : value.routeDistanceKm(),
                    value.routeDurationMinutes() == null ? "" : value.routeDurationMinutes(),
                    value.outcome(),
                    value.performedBy()
            ), bodyStyle);
        }

        setColumnWidths(sheet, new int[]{
                8, 24, 16, 24, 18, 18, 14, 34, 20, 18, 15, 17, 24, 34, 28, 20, 18, 16, 20
        });
        sheet.createFreezePane(0, 2);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(1, Math.max(1, rowIndex - 1), 0, DETAIL_HEADERS.size() - 1));
    }

    private void writeSummarySheet(
            SXSSFWorkbook workbook,
            LocalDate reportDate,
            List<ReportRow> rows,
            CellStyle titleStyle,
            CellStyle headerStyle,
            CellStyle bodyStyle
    ) {
        Sheet sheet = workbook.createSheet("Visitor Summary");
        writeTitle(sheet, "Visitor Workload - " + reportDate, SUMMARY_HEADERS.size(), titleStyle);
        writeHeaders(sheet.createRow(1), SUMMARY_HEADERS, headerStyle);

        Map<String, VisitorSummary> summaries = new LinkedHashMap<>();
        for (ReportRow row : rows) {
            String key = normalize(row.visitor()) + "|" + normalize(row.station());
            summaries.computeIfAbsent(
                    key,
                    ignored -> new VisitorSummary(row.visitor(), row.station())
            ).add(row.outcome());
        }

        int rowIndex = 2;
        for (VisitorSummary summary : summaries.values()) {
            Row row = sheet.createRow(rowIndex++);
            writeValues(row, List.of(
                    summary.visitor,
                    summary.station,
                    summary.total,
                    summary.scheduled,
                    summary.successful,
                    summary.expired,
                    summary.canceled
            ), bodyStyle);
        }

        setColumnWidths(sheet, new int[]{26, 20, 16, 14, 14, 12, 12});
        sheet.createFreezePane(0, 2);
    }

    private void writeExceptionsSheet(
            SXSSFWorkbook workbook,
            LocalDate reportDate,
            List<ReportRow> rows,
            CellStyle titleStyle,
            CellStyle headerStyle,
            CellStyle bodyStyle
    ) {
        Sheet sheet = workbook.createSheet("Exceptions");
        writeTitle(sheet, "Visit Plan Exceptions - " + reportDate, EXCEPTION_HEADERS.size(), titleStyle);
        writeHeaders(sheet.createRow(1), EXCEPTION_HEADERS, headerStyle);

        int rowIndex = 2;
        for (ReportRow value : rows) {
            String exception = getExceptionReason(value);
            if (exception.isBlank()) {
                continue;
            }
            writeValues(sheet.createRow(rowIndex++), List.of(
                    value.complaintId(),
                    value.visitor(),
                    value.station(),
                    value.bank(),
                    value.branchCode() + " " + value.branchName(),
                    value.city(),
                    value.priorityLabel(),
                    value.outcome(),
                    exception
            ), bodyStyle);
        }

        setColumnWidths(sheet, new int[]{24, 24, 18, 18, 36, 20, 26, 16, 42});
        sheet.createFreezePane(0, 2);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                1, Math.max(1, rowIndex - 1), 0, EXCEPTION_HEADERS.size() - 1
        ));
    }

    private String getExceptionReason(ReportRow row) {
        List<String> reasons = new ArrayList<>();
        String outcome = normalize(row.outcome());
        String priority = normalize(row.priorityLabel());

        if ("expired".equals(outcome)) reasons.add("Visit expired");
        if ("canceled".equals(outcome) || "cancelled".equals(outcome)) reasons.add("Visit canceled");
        if (priority.contains("deadline")) reasons.add("Bank deadline requires attention");
        if (row.hardwareDeliveryAge() >= 5) reasons.add("Hardware installation overdue");
        if (row.complaintAge() >= 10) reasons.add("Long-pending complaint");
        return String.join("; ", reasons);
    }

    private void writeTitle(Sheet sheet, String title, int columnCount, CellStyle style) {
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(style);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, columnCount - 1));
    }

    private void writeHeaders(Row row, List<String> headers, CellStyle style) {
        for (int index = 0; index < headers.size(); index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(headers.get(index));
            cell.setCellStyle(style);
        }
    }

    private void writeValues(Row row, List<?> values, CellStyle style) {
        for (int index = 0; index < values.size(); index++) {
            Cell cell = row.createCell(index);
            Object value = values.get(index);
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else {
                cell.setCellValue(value == null ? "" : value.toString());
            }
            cell.setCellStyle(style);
        }
    }

    private void setColumnWidths(Sheet sheet, int[] widths) {
        for (int index = 0; index < widths.length; index++) {
            sheet.setColumnWidth(index, widths[index] * 256);
        }
    }

    private CellStyle createTitleStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private CellStyle createHeaderStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createBodyStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        return style;
    }

    private String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private String formatRoute(String origin, String destination) {
        if (safe(origin).isBlank() || safe(destination).isBlank()) {
            return "";
        }
        return safe(origin) + " to " + safe(destination);
    }

    private Object displayAge(int age) {
        return age < 0 ? "" : age;
    }

    private int calculateAgeAt(Date date, LocalDate reportDate) {
        if (date == null) return -1;
        return (int) Math.max(0, ChronoUnit.DAYS.between(date.toLocalDate(), reportDate));
    }

    private String blankToDefault(String value, String fallback) {
        String cleaned = safe(value);
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record ReportRow(
            String complaintId,
            String scheduleDate,
            String visitor,
            String station,
            String bank,
            String branchCode,
            String branchName,
            String city,
            String courierStatus,
            int complaintAge,
            int hardwareDeliveryAge,
            String priorityLabel,
            String priorityDetail,
            String routeOrigin,
            String routeDestination,
            Double routeDistanceKm,
            Integer routeDurationMinutes,
            String outcome,
            String performedBy
    ) {}

    private static class VisitorSummary {
        private final String visitor;
        private final String station;
        private int total;
        private int scheduled;
        private int successful;
        private int expired;
        private int canceled;

        private VisitorSummary(String visitor, String station) {
            this.visitor = visitor;
            this.station = station;
        }

        private void add(String outcome) {
            total++;
            String normalized = outcome == null ? "" : outcome.trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "successful" -> successful++;
                case "expired" -> expired++;
                case "canceled", "cancelled" -> canceled++;
                default -> scheduled++;
            }
        }
    }
}
