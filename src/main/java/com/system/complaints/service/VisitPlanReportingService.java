package com.system.complaints.service;

import com.system.complaints.dto.VisitPlanReportingResponse;
import com.system.complaints.model.VisitPlan;
import com.system.complaints.model.VisitPlanEntry;
import com.system.complaints.model.VisitPlanSubmissionSnapshot;
import com.system.complaints.model.VisitPlanSubmissionSnapshotItem;
import com.system.complaints.repository.VisitPlanEntryRepository;
import com.system.complaints.repository.VisitPlanRepository;
import com.system.complaints.repository.VisitPlanSubmissionSnapshotItemRepository;
import com.system.complaints.repository.VisitPlanSubmissionSnapshotRepository;
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
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class VisitPlanReportingService {
    private static final int PREVIEW_LIMIT = 500;
    private static final List<String> TYPES = List.of("DRAFT", "APPROVED", "ELIGIBLE", "TRAVEL");
    private static final List<String> HEADERS = List.of(
            "S.No", "Report Date", "Created By", "Bank", "Branch Code", "Branch Name", "City", "Visitor", "Station",
            "Complaint Status", "Courier Status", "Complaint Age", "HW Delivery Age",
            "Priority", "Priority Detail", "Complaint ID"
    );
    private static final List<String> TRAVEL_HEADERS = List.of(
            "S.No", "Visit Date", "Visitor", "Station", "Step", "From", "To", "Distance KM", "Duration",
            "Route Note", "Bank", "Branch Code", "Branch Name", "City", "Complaint Status", "Courier Status",
            "Priority", "Complaint Age", "HW Delivery Age", "Plan Status", "Created By", "Complaint ID"
    );

    private final VisitPlanRepository planRepository;
    private final VisitPlanEntryRepository entryRepository;
    private final VisitPlanSubmissionSnapshotRepository submissionSnapshotRepository;
    private final VisitPlanSubmissionSnapshotItemRepository submissionSnapshotItemRepository;

    public VisitPlanReportingService(
            VisitPlanRepository planRepository,
            VisitPlanEntryRepository entryRepository,
            VisitPlanSubmissionSnapshotRepository submissionSnapshotRepository,
            VisitPlanSubmissionSnapshotItemRepository submissionSnapshotItemRepository
    ) {
        this.planRepository = planRepository;
        this.entryRepository = entryRepository;
        this.submissionSnapshotRepository = submissionSnapshotRepository;
        this.submissionSnapshotItemRepository = submissionSnapshotItemRepository;
    }

    @Transactional(readOnly = true)
    public VisitPlanReportingResponse preview(String requestedType, LocalDate from, LocalDate to) {
        String type = validate(requestedType, from, to);
        List<VisitPlanReportingResponse.Row> allRows = loadRows(type, from, to);
        boolean truncated = allRows.size() > PREVIEW_LIMIT;
        List<VisitPlanReportingResponse.Row> previewRows = truncated
                ? new ArrayList<>(allRows.subList(0, PREVIEW_LIMIT))
                : allRows;
        return new VisitPlanReportingResponse(
                type,
                from.toString(),
                to.toString(),
                allRows.size(),
                truncated,
                previewRows
        );
    }

    public String fileName(String requestedType, LocalDate from, LocalDate to) {
        String type = normalizeType(requestedType);
        return "Visit_Plan_" + type + "_" + from + "_to_" + to + ".xlsx";
    }

    @Transactional(readOnly = true)
    public void writeExcel(String requestedType, LocalDate from, LocalDate to, OutputStream output)
            throws IOException {
        String type = validate(requestedType, from, to);
        List<VisitPlanReportingResponse.Row> rows = loadRows(type, from, to);
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);
            CellStyle titleStyle = titleStyle(workbook);
            CellStyle headerStyle = headerStyle(workbook);

            if ("TRAVEL".equals(type)) {
                writeTravelSheet(workbook, from, to, rows, titleStyle, headerStyle);
            } else {
                writeStandardSheet(workbook, type, from, to, rows, titleStyle, headerStyle);
            }

            workbook.write(output);
            workbook.dispose();
        }
    }

    private void writeStandardSheet(
            SXSSFWorkbook workbook,
            String type,
            LocalDate from,
            LocalDate to,
            List<VisitPlanReportingResponse.Row> rows,
            CellStyle titleStyle,
            CellStyle headerStyle
    ) {
        Sheet sheet = workbook.createSheet("Visit Plan Report");
        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue(type + " visit plan report: " + from + " to " + to);
        title.getCell(0).setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, HEADERS.size() - 1));

        Row header = sheet.createRow(1);
        for (int column = 0; column < HEADERS.size(); column++) {
            header.createCell(column).setCellValue(HEADERS.get(column));
            header.getCell(column).setCellStyle(headerStyle);
        }

        for (int index = 0; index < rows.size(); index++) {
            VisitPlanReportingResponse.Row value = rows.get(index);
            Row row = sheet.createRow(index + 2);
            List<Object> values = List.of(
                    index + 1, safe(value.reportDate()), safe(value.createdBy()),
                    safe(value.bankName()), safe(value.branchCode()),
                    safe(value.branchName()), safe(value.city()), safe(value.visitorName()), safe(value.station()),
                    safe(value.complaintStatus()), safe(value.courierStatus()), age(value.complaintAge()),
                    age(value.hardwareDeliveryAge()), safe(value.priority()), safe(value.priorityDetail()),
                    safe(value.complaintId())
            );
            writeValues(row, values);
        }

        int[] widths = {8, 15, 20, 16, 14, 32, 18, 22, 18, 20, 20, 15, 18, 24, 38, 24};
        for (int column = 0; column < widths.length; column++) sheet.setColumnWidth(column, widths[column] * 256);
        sheet.createFreezePane(0, 2);
        if (!rows.isEmpty()) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(1, rows.size() + 1, 0, HEADERS.size() - 1));
        }
    }

    private void writeTravelSheet(
            SXSSFWorkbook workbook,
            LocalDate from,
            LocalDate to,
            List<VisitPlanReportingResponse.Row> rows,
            CellStyle titleStyle,
            CellStyle headerStyle
    ) {
        Sheet sheet = workbook.createSheet("Visitor Travel Sequence");
        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue("Visitor travel sequence: " + from + " to " + to);
        title.getCell(0).setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, TRAVEL_HEADERS.size() - 1));

        Row header = sheet.createRow(1);
        for (int column = 0; column < TRAVEL_HEADERS.size(); column++) {
            header.createCell(column).setCellValue(TRAVEL_HEADERS.get(column));
            header.getCell(column).setCellStyle(headerStyle);
        }

        for (int index = 0; index < rows.size(); index++) {
            VisitPlanReportingResponse.Row value = rows.get(index);
            Row row = sheet.createRow(index + 2);
            List<Object> values = List.of(
                    index + 1, safe(value.reportDate()), safe(value.visitorName()), safe(value.station()),
                    value.stepNo() == null ? "" : value.stepNo(), safe(value.fromLocation()), safe(value.toLocation()),
                    value.routeDistanceKm() == null ? "" : value.routeDistanceKm(),
                    formatDuration(value.routeDurationMinutes()), safe(value.routeNote()),
                    safe(value.bankName()), safe(value.branchCode()), safe(value.branchName()), safe(value.city()),
                    safe(value.complaintStatus()), safe(value.courierStatus()), safe(value.priority()),
                    age(value.complaintAge()), age(value.hardwareDeliveryAge()), safe(value.planStatus()),
                    safe(value.createdBy()), safe(value.complaintId())
            );
            writeValues(row, values);
        }

        int[] widths = {8, 15, 24, 18, 10, 28, 34, 14, 14, 34, 16, 14, 34, 18, 18, 18, 24, 15, 17, 16, 20, 24};
        for (int column = 0; column < widths.length; column++) sheet.setColumnWidth(column, widths[column] * 256);
        sheet.createFreezePane(0, 2);
        if (!rows.isEmpty()) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(1, rows.size() + 1, 0, TRAVEL_HEADERS.size() - 1));
        }
    }

    private void writeValues(Row row, List<Object> values) {
        for (int column = 0; column < values.size(); column++) {
            Object cellValue = values.get(column);
            if (cellValue instanceof Number number) row.createCell(column).setCellValue(number.doubleValue());
            else row.createCell(column).setCellValue(cellValue.toString());
        }
    }

    private List<VisitPlanReportingResponse.Row> loadRows(String type, LocalDate from, LocalDate to) {
        Date sqlFrom = Date.valueOf(from);
        Date sqlTo = Date.valueOf(to);
        if ("TRAVEL".equals(type)) return loadTravelRows(sqlFrom, sqlTo);
        if ("ELIGIBLE".equals(type)) {
            List<VisitPlanSubmissionSnapshot> capturedSnapshots = submissionSnapshotRepository
                    .findByCapturedAtBetweenOrderByCapturedAtAscPlanIdAscSnapshotVersionAsc(
                            Timestamp.valueOf(from.atStartOfDay()),
                            Timestamp.valueOf(to.plusDays(1).atStartOfDay().minusNanos(1))
                    );
            Map<LocalDate, VisitPlanSubmissionSnapshot> latestSnapshotByDay = new LinkedHashMap<>();
            for (VisitPlanSubmissionSnapshot snapshot : capturedSnapshots) {
                if (snapshot.getCapturedAt() != null) {
                    latestSnapshotByDay.put(
                            snapshot.getCapturedAt().toLocalDateTime().toLocalDate(),
                            snapshot
                    );
                }
            }
            List<VisitPlanSubmissionSnapshot> snapshots = new ArrayList<>(latestSnapshotByDay.values());
            if (snapshots.isEmpty()) return List.of();
            Map<Long, VisitPlanSubmissionSnapshot> byId = snapshots.stream()
                    .collect(Collectors.toMap(VisitPlanSubmissionSnapshot::getId, Function.identity()));
            return submissionSnapshotItemRepository.findBySnapshotIdInOrderBySnapshotIdAscIdAsc(
                    snapshots.stream().map(VisitPlanSubmissionSnapshot::getId).toList()
            ).stream().map(item -> snapshotRow(item, byId.get(item.getSnapshotId()))).toList();
        }

        if ("DRAFT".equals(type)) {
            List<VisitPlan> plans = planRepository.findByScheduleDateBetweenOrderByScheduleDateAscIdAsc(sqlFrom, sqlTo);
            if (plans.isEmpty()) return List.of();
            Map<Long, VisitPlan> byId = plans.stream().collect(Collectors.toMap(VisitPlan::getId, Function.identity()));
            return entryRepository.findByPlanIdInOrderByScheduleDateAscPlanIdAscIdAsc(
                    plans.stream().map(VisitPlan::getId).toList()
            ).stream().map(entry -> entryRow(entry, byId.get(entry.getPlanId()))).toList();
        }

        List<VisitPlanEntry> entries = entryRepository
                .findByScheduleDateBetweenAndApprovalStatusOrderByScheduleDateAscIdAsc(sqlFrom, sqlTo, "APPROVED");
        List<Long> planIds = entries.stream().map(VisitPlanEntry::getPlanId).filter(id -> id != null).distinct().toList();
        Map<Long, VisitPlan> plans = planIds.isEmpty() ? Map.of() : planRepository.findAllById(planIds).stream()
                .collect(Collectors.toMap(VisitPlan::getId, Function.identity()));
        return entries.stream().map(entry -> entryRow(entry, plans.get(entry.getPlanId()))).toList();
    }

    private List<VisitPlanReportingResponse.Row> loadTravelRows(Date from, Date to) {
        List<VisitPlanEntry> entries = entryRepository
                .findByScheduleDateBetweenOrderByScheduleDateAscVisitorStationAscVisitorNameAscIdAsc(from, to)
                .stream()
                .filter(entry -> !"REJECTED".equalsIgnoreCase(safe(entry.getApprovalStatus())))
                .toList();
        if (entries.isEmpty()) return List.of();

        List<Long> planIds = entries.stream().map(VisitPlanEntry::getPlanId).filter(id -> id != null).distinct().toList();
        Map<Long, VisitPlan> plans = planIds.isEmpty() ? Map.of() : planRepository.findAllById(planIds).stream()
                .collect(Collectors.toMap(VisitPlan::getId, Function.identity()));

        Map<String, List<VisitPlanEntry>> grouped = new LinkedHashMap<>();
        for (VisitPlanEntry entry : entries) {
            String key = value(entry.getScheduleDate()) + "|" + safe(entry.getVisitorStation()) + "|"
                    + safe(entry.getVisitorName()) + "|" + value(entry.getPlanId());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        }

        List<VisitPlanReportingResponse.Row> rows = new ArrayList<>();
        for (List<VisitPlanEntry> group : grouped.values()) {
            group.sort(Comparator.comparing(VisitPlanEntry::getId, Comparator.nullsLast(Long::compareTo)));
            String previousLocation = safe(group.get(0).getVisitorStation());
            for (int index = 0; index < group.size(); index++) {
                VisitPlanEntry entry = group.get(index);
                VisitPlan plan = plans.get(entry.getPlanId());
                String fromLocation = index == 0 ? firstNonBlank(entry.getRouteOrigin(), previousLocation) : previousLocation;
                String toLocation = destinationLabel(entry);
                String note = routeNote(entry, index);
                rows.add(travelRow(entry, plan, index + 1, fromLocation, toLocation, note));
                previousLocation = safe(entry.getCity()).isBlank() ? toLocation : safe(entry.getCity());
            }
        }
        return rows;
    }

    private VisitPlanReportingResponse.Row entryRow(VisitPlanEntry entry, VisitPlan plan) {
        String complaintId = !"COMPLAINT".equalsIgnoreCase(entry.getEntryType())
                ? ""
                : safe(entry.getComplaintId());
        return new VisitPlanReportingResponse.Row(
                value(entry.getScheduleDate()),
                safe(entry.getApprovalStatus()),
                plan == null ? safe(entry.getApprovedBy()) : safe(plan.getCreatedBy()),
                complaintId, safe(entry.getBankName()), safe(entry.getBranchCode()),
                safe(entry.getBranchName()), safe(entry.getCity()), safe(entry.getVisitorName()),
                safe(entry.getVisitorStation()), safe(entry.getComplaintStatus()), safe(entry.getCourierStatus()),
                entry.getComplaintAge(), entry.getHardwareDeliveryAge(), safe(entry.getPriorityLabel()),
                safe(entry.getPriorityDetail()), null, "", "", null, null, ""
        );
    }

    private VisitPlanReportingResponse.Row travelRow(
            VisitPlanEntry entry,
            VisitPlan plan,
            int stepNo,
            String fromLocation,
            String toLocation,
            String routeNote
    ) {
        String complaintId = !"COMPLAINT".equalsIgnoreCase(entry.getEntryType())
                ? ""
                : safe(entry.getComplaintId());
        return new VisitPlanReportingResponse.Row(
                value(entry.getScheduleDate()),
                safe(entry.getApprovalStatus()),
                plan == null ? safe(entry.getApprovedBy()) : safe(plan.getCreatedBy()),
                complaintId, safe(entry.getBankName()), safe(entry.getBranchCode()),
                safe(entry.getBranchName()), safe(entry.getCity()), safe(entry.getVisitorName()),
                safe(entry.getVisitorStation()), safe(entry.getComplaintStatus()), safe(entry.getCourierStatus()),
                entry.getComplaintAge(), entry.getHardwareDeliveryAge(), safe(entry.getPriorityLabel()),
                safe(entry.getPriorityDetail()), stepNo, fromLocation, toLocation,
                entry.getRouteDistanceKm(), entry.getRouteDurationMinutes(), routeNote
        );
    }

    private VisitPlanReportingResponse.Row snapshotRow(
            VisitPlanSubmissionSnapshotItem row,
            VisitPlanSubmissionSnapshot snapshot
    ) {
        return new VisitPlanReportingResponse.Row(
                snapshot == null || snapshot.getCapturedAt() == null
                        ? ""
                        : snapshot.getCapturedAt().toLocalDateTime().toLocalDate().toString(),
                snapshot == null ? "ELIGIBLE" : "ELIGIBLE V" + snapshot.getSnapshotVersion(),
                snapshot == null ? "" : safe(snapshot.getSubmittedBy()),
                safe(row.getComplaintId()),
                safe(row.getBankName()), safe(row.getBranchCode()), safe(row.getBranchName()), safe(row.getCity()),
                safe(row.getVisitorName()), "", safe(row.getComplaintStatus()), safe(row.getCourierStatus()),
                row.getComplaintAge(), row.getHardwareDeliveryAge(), safe(row.getPriorityLabel()),
                safe(row.getPriorityDetail()), null, "", "", null, null, ""
        );
    }

    private String routeNote(VisitPlanEntry entry, int index) {
        if ("NO_PENDING_COMPLAINT".equalsIgnoreCase(entry.getEntryType())) {
            return "No travel required - visitor has no pending complaint";
        }
        if (entry.getRouteDistanceKm() != null || entry.getRouteDurationMinutes() != null) {
            return index == 0 ? "Stored route from selected route option" : "Stored route summary from selected route option";
        }
        return index == 0 ? "Starts from visitor station" : "Distance not captured for this leg";
    }

    private String destinationLabel(VisitPlanEntry entry) {
        if ("NO_PENDING_COMPLAINT".equalsIgnoreCase(entry.getEntryType())) {
            return "No pending complaint";
        }
        String branch = safe(entry.getBranchName());
        String city = safe(entry.getCity());
        if (!branch.isBlank() && !city.isBlank()) return branch + " / " + city;
        return firstNonBlank(branch, city, safe(entry.getRouteDestination()));
    }

    private String validate(String requestedType, LocalDate from, LocalDate to) {
        String type = normalizeType(requestedType);
        if (!TYPES.contains(type)) throw new IllegalArgumentException("Unknown report type.");
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("Choose a valid report date range.");
        }
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw new IllegalArgumentException("Report range cannot exceed 366 days.");
        }
        return type;
    }

    private String normalizeType(String value) { return safe(value).toUpperCase(Locale.ROOT); }

    private CellStyle titleStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private CellStyle headerStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private Object age(Integer value) { return value == null || value < 0 ? "" : value; }
    private String value(Object value) { return value == null ? "" : value.toString(); }
    private String safe(String value) { return value == null ? "" : value.trim(); }
    private String firstNonBlank(String... values) {
        for (String value : values) {
            String clean = safe(value);
            if (!clean.isBlank()) return clean;
        }
        return "";
    }
    private String formatDuration(Integer minutes) {
        if (minutes == null || minutes < 0) return "";
        if (minutes < 60) return minutes + " min";
        int hours = minutes / 60;
        int remaining = minutes % 60;
        return remaining == 0 ? hours + " hr" : hours + " hr " + remaining + " min";
    }
}
