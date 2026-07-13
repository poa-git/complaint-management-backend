package com.system.complaints.service;

import com.system.complaints.dto.ExcelReportRequest;
import com.system.complaints.model.ComplaintHistory;
import com.system.complaints.model.ComplaintLog;
import com.system.complaints.model.HardwareLog;
import com.system.complaints.model.RemarksUpdate;
import com.system.complaints.repository.ComplaintHistoryRepository;
import com.system.complaints.repository.ComplaintLogRepository;
import com.system.complaints.repository.HardwareLogRepository;
import com.system.complaints.repository.RemarksUpdateRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExcelReportService {
    private static final int EXPORT_PAGE_SIZE = 1000;
    private static final int BATCH_SIZE = 1000;
    private static final List<String> CITY_ORDER = List.of(
            "Karachi", "Lahore", "Islamabad", "Peshawar", "Hyderabad", "Quetta",
            "Sukkur", "Sadiqabad", "Bahawalpur", "Multan", "Sahiwal", "Jhang",
            "Faisalabad", "Sargodha", "Sialkot", "Jhelum", "Abbottabad", "Others"
    );

    @Autowired
    private ComplaintLogRepository complaintLogRepository;

    @Autowired
    private RemarksUpdateRepository remarksUpdateRepository;

    @Autowired
    private ComplaintHistoryRepository complaintHistoryRepository;

    @Autowired
    private HardwareLogRepository hardwareLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public String buildFileName(ExcelReportRequest request) {
        String status = blankToDefault(request.getViewStatus(), "All");
        String reportDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        return status.replaceAll("[^A-Za-z0-9_-]+", "_") + "_Complaints_Report_" + reportDate + ".xlsx";
    }

    public void writeComplaintReport(ExcelReportRequest request, OutputStream outputStream) throws IOException {
        String selectedReport = blankToDefault(request.getSelectedReport(), "standard");

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);
            Sheet worksheet = workbook.createSheet("Complaints Report");

            CellStyle dateHeaderStyle = createDateHeaderStyle(workbook);
            CellStyle statusHeaderStyle = createStatusHeaderStyle(workbook);
            CellStyle groupHeaderStyle = createGroupHeaderStyle(workbook);
            CellStyle columnHeaderStyle = createColumnHeaderStyle(workbook);
            CellStyle dataCellStyle = createDataCellStyle(workbook);

            int rowIndex = 0;
            String reportDate = LocalDate.now().format(DateTimeFormatter.ofPattern("M/d/yyyy"));
            Row dateRow = worksheet.createRow(rowIndex);
            createCell(dateRow, 0, "Date: " + reportDate, dateHeaderStyle);
            worksheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 6));
            rowIndex++;

            String displayStatus = blankToDefault(request.getViewStatus(), "All");
            Row statusRow = worksheet.createRow(rowIndex);
            createCell(statusRow, 0, displayStatus + " Complaints Status", statusHeaderStyle);
            worksheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 6));
            rowIndex++;

            if (request.getComplaintIds() != null && !request.getComplaintIds().isEmpty()) {
                rowIndex = writeProvidedComplaintRows(request, selectedReport, worksheet, groupHeaderStyle, columnHeaderStyle, dataCellStyle, rowIndex);
            } else {
                rowIndex = writeFilteredComplaintRows(request, selectedReport, worksheet, groupHeaderStyle, columnHeaderStyle, dataCellStyle, rowIndex);
            }

            int maxColumns = buildHeaders(displayStatus, selectedReport).size();
            for (int i = 0; i < maxColumns; i++) {
                worksheet.setColumnWidth(i, 18 * 256);
            }

            workbook.write(outputStream);
            workbook.dispose();
        }
    }

    private int writeProvidedComplaintRows(
            ExcelReportRequest request,
            String selectedReport,
            Sheet worksheet,
            CellStyle groupHeaderStyle,
            CellStyle columnHeaderStyle,
            CellStyle dataCellStyle,
            int rowIndex
    ) {
        List<ComplaintLog> complaints = resolveComplaints(request);
        if ("untouched".equalsIgnoreCase(selectedReport)) {
            complaints = complaints.stream()
                    .filter(c -> "Open".equalsIgnoreCase(safe(c.getComplaintStatus())))
                    .collect(Collectors.toList());
        }

        String displayStatus = blankToDefault(request.getViewStatus(), "All");
        boolean untouchedReport = "untouched".equalsIgnoreCase(selectedReport);
        Map<Long, String> remarksMap = untouchedReport ? Map.of() : loadAllRemarks(complaints);
        Map<Long, String> latestRemarksMap = untouchedReport ? loadLatestRemarks(complaints) : Map.of();
        Map<String, String> visitorsMap = loadAssignedVisitors(complaints);

        List<Map.Entry<String, List<ComplaintLog>>> groupedComplaints = shouldGroupByBank(displayStatus)
                ? groupComplaintsByBankName(complaints)
                : groupComplaintsByCity(complaints);

        for (Map.Entry<String, List<ComplaintLog>> entry : groupedComplaints) {
            rowIndex = writeGroup(worksheet, groupHeaderStyle, columnHeaderStyle, rowIndex, displayStatus, selectedReport, entry.getKey());
            int serial = 1;
            for (ComplaintLog complaint : entry.getValue()) {
                rowIndex = writeComplaintRow(
                        worksheet, dataCellStyle, rowIndex, complaint, serial++, displayStatus, selectedReport,
                        remarksMap.getOrDefault(complaint.getId(), ""),
                        latestRemarksMap.getOrDefault(complaint.getId(), ""),
                        visitorsMap.getOrDefault(complaint.getComplaintId(), "")
                );
            }
            worksheet.createRow(rowIndex++);
        }
        return rowIndex;
    }

    private int writeFilteredComplaintRows(
            ExcelReportRequest request,
            String selectedReport,
            Sheet worksheet,
            CellStyle groupHeaderStyle,
            CellStyle columnHeaderStyle,
            CellStyle dataCellStyle,
            int rowIndex
    ) {
        String displayStatus = blankToDefault(request.getViewStatus(), "All");
        Specification<ComplaintLog> baseSpec = buildExportSpecification(request, selectedReport);

        if (shouldGroupByBank(displayStatus)) {
            for (String bankName : findDistinctBankNames(baseSpec)) {
                Specification<ComplaintLog> groupSpec = baseSpec.and((root, query, cb) ->
                        cb.equal(cb.trim(root.get("bankName")), bankName));
                rowIndex = writePagedGroup(
                        worksheet, groupHeaderStyle, columnHeaderStyle, dataCellStyle,
                        rowIndex, displayStatus, selectedReport, bankName, groupSpec
                );
            }

            Specification<ComplaintLog> othersSpec = baseSpec.and((root, query, cb) -> cb.or(
                    cb.isNull(root.get("bankName")),
                    cb.equal(cb.trim(root.get("bankName")), "")
            ));
            return writePagedGroup(
                    worksheet, groupHeaderStyle, columnHeaderStyle, dataCellStyle,
                    rowIndex, displayStatus, selectedReport, "Others", othersSpec
            );
        }

        List<String> knownCities = CITY_ORDER.stream()
                .filter(city -> !"Others".equals(city))
                .toList();
        List<String> lowerKnownCities = knownCities.stream()
                .map(city -> city.toLowerCase(Locale.ROOT))
                .toList();

        for (String city : knownCities) {
            Specification<ComplaintLog> groupSpec = baseSpec.and((root, query, cb) ->
                    cb.equal(cb.lower(cb.trim(root.get("city"))), city.toLowerCase(Locale.ROOT)));
            rowIndex = writePagedGroup(
                    worksheet, groupHeaderStyle, columnHeaderStyle, dataCellStyle,
                    rowIndex, displayStatus, selectedReport, city, groupSpec
            );
        }

        Specification<ComplaintLog> othersSpec = baseSpec.and((root, query, cb) -> cb.or(
                cb.isNull(root.get("city")),
                cb.equal(cb.trim(root.get("city")), ""),
                cb.not(cb.lower(cb.trim(root.get("city"))).in(lowerKnownCities))
        ));
        return writePagedGroup(
                worksheet, groupHeaderStyle, columnHeaderStyle, dataCellStyle,
                rowIndex, displayStatus, selectedReport, "Others", othersSpec
        );
    }

    private int writePagedGroup(
            Sheet worksheet,
            CellStyle groupHeaderStyle,
            CellStyle columnHeaderStyle,
            CellStyle dataCellStyle,
            int rowIndex,
            String displayStatus,
            String selectedReport,
            String groupKey,
            Specification<ComplaintLog> groupSpec
    ) {
        Sort sort = Sort.by("date").descending().and(Sort.by("id").descending());
        int pageNumber = 0;
        int serial = 1;
        boolean groupWritten = false;

        while (true) {
            Page<ComplaintLog> page = complaintLogRepository.findAll(
                    groupSpec,
                    PageRequest.of(pageNumber, EXPORT_PAGE_SIZE, sort)
            );
            List<ComplaintLog> complaints = page.getContent();
            if (complaints.isEmpty()) break;

            if (!groupWritten) {
                rowIndex = writeGroup(
                        worksheet, groupHeaderStyle, columnHeaderStyle, rowIndex,
                        displayStatus, selectedReport, groupKey
                );
                groupWritten = true;
            }

            attachLatestHardwareData(complaints);
            boolean untouchedReport = "untouched".equalsIgnoreCase(selectedReport);
            Map<Long, String> remarksMap = untouchedReport ? Map.of() : loadAllRemarks(complaints);
            Map<Long, String> latestRemarksMap = untouchedReport ? loadLatestRemarks(complaints) : Map.of();
            Map<String, String> visitorsMap = loadAssignedVisitors(complaints);

            for (ComplaintLog complaint : complaints) {
                rowIndex = writeComplaintRow(
                        worksheet, dataCellStyle, rowIndex, complaint, serial++, displayStatus, selectedReport,
                        remarksMap.getOrDefault(complaint.getId(), ""),
                        latestRemarksMap.getOrDefault(complaint.getId(), ""),
                        visitorsMap.getOrDefault(complaint.getComplaintId(), "")
                );
            }

            if (!page.hasNext()) break;
            pageNumber++;
        }

        if (groupWritten) worksheet.createRow(rowIndex++);
        return rowIndex;
    }

    private List<String> findDistinctBankNames(Specification<ComplaintLog> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<String> query = cb.createQuery(String.class);
        Root<ComplaintLog> root = query.from(ComplaintLog.class);
        Predicate basePredicate = spec.toPredicate(root, query, cb);
        Predicate namedBank = cb.and(
                cb.isNotNull(root.get("bankName")),
                cb.notEqual(cb.trim(root.get("bankName")), "")
        );

        query.select(cb.trim(root.get("bankName"))).distinct(true);
        query.where(basePredicate == null ? namedBank : cb.and(basePredicate, namedBank));
        query.orderBy(cb.asc(cb.trim(root.get("bankName"))));

        TypedQuery<String> typedQuery = entityManager.createQuery(query);
        return typedQuery.getResultList();
    }

    private int writeGroup(
            Sheet worksheet,
            CellStyle groupHeaderStyle,
            CellStyle columnHeaderStyle,
            int rowIndex,
            String displayStatus,
            String selectedReport,
            String groupKey
    ) {
        List<String> headers = buildHeaders(displayStatus, selectedReport);
        Row groupRow = worksheet.createRow(rowIndex);
        createCell(groupRow, 0, groupKey, groupHeaderStyle);
        worksheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, headers.size() - 1));
        rowIndex++;

        Row headerRow = worksheet.createRow(rowIndex++);
        for (int i = 0; i < headers.size(); i++) {
            createCell(headerRow, i, headers.get(i), columnHeaderStyle);
        }
        return rowIndex;
    }

    private int writeComplaintRow(
            Sheet worksheet,
            CellStyle dataCellStyle,
            int rowIndex,
            ComplaintLog complaint,
            int serial,
            String displayStatus,
            String selectedReport,
            String allRemarks,
            String latestRemark,
            String allAssignedVisitors
    ) {
        Row dataRow = worksheet.createRow(rowIndex++);
        List<String> values = buildRowValues(
                complaint,
                serial,
                displayStatus,
                selectedReport,
                allRemarks,
                latestRemark,
                allAssignedVisitors
        );
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (isNumericReportCell(i, displayStatus, selectedReport, value)) {
                createNumericCell(dataRow, i, value, dataCellStyle);
            } else {
                createCell(dataRow, i, value, dataCellStyle);
            }
        }
        return rowIndex;
    }

    private boolean isNumericReportCell(int column, String viewStatus, String selectedReport, String value) {
        if (value == null || value.isBlank()) return false;
        if (column == 0) return true;
        if ("ageing".equalsIgnoreCase(selectedReport) || "untouched".equalsIgnoreCase(selectedReport)) {
            return column == 2;
        }
        return hasAgeingColumn(viewStatus) && column == 1;
    }

    private List<ComplaintLog> resolveComplaints(ExcelReportRequest request) {
        Map<Long, ComplaintLog> byId = complaintLogRepository.findAllById(request.getComplaintIds()).stream()
                .collect(Collectors.toMap(ComplaintLog::getId, c -> c));
        List<ComplaintLog> complaints = request.getComplaintIds().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        attachLatestHardwareData(complaints);

        Set<String> excluded = Optional.ofNullable(request.getExcludeStatuses()).orElse(List.of()).stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!excluded.isEmpty()) {
            complaints = complaints.stream()
                    .filter(c -> !excluded.contains(safe(c.getComplaintStatus()).toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return complaints;
    }

    private Specification<ComplaintLog> buildExportSpecification(ExcelReportRequest request, String selectedReport) {
        Specification<ComplaintLog> spec = Specification.where(null);

        String status = request.getStatus();
        if (status != null && !status.isEmpty()) {
            if (status.equalsIgnoreCase("Open")) {
                spec = spec.and((root, query, cb) -> root.get("complaintStatus").in(openStatuses()));
            } else if (status.equalsIgnoreCase("FOC_APPROVED")) {
                spec = spec.and((root, query, cb) -> root.get("complaintStatus").in(List.of("FOC", "Approved")));
            } else if (!status.equalsIgnoreCase("Overall")) {
                spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("complaintStatus")), status.toLowerCase(Locale.ROOT)));
            }
        }

        if ("untouched".equalsIgnoreCase(selectedReport)) {
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("complaintStatus")), "open"));
        }

        if (request.getBankName() != null && !request.getBankName().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("bankName")), "%" + request.getBankName().toLowerCase(Locale.ROOT) + "%"));
        }
        if (request.getBranchCode() != null && !request.getBranchCode().trim().isEmpty()) {
            String trimmed = request.getBranchCode().trim();
            if (trimmed.matches("\\d+")) {
                String unpadded = trimmed.replaceFirst("^0+(?!$)", "");
                String padded = String.format("%04d", Integer.parseInt(unpadded.isEmpty() ? "0" : unpadded));
                spec = spec.and((root, query, cb) -> cb.or(
                        cb.equal(root.get("branchCode"), padded),
                        cb.equal(root.get("branchCode"), unpadded)
                ));
            } else {
                spec = spec.and((root, query, cb) -> cb.equal(root.get("branchCode"), trimmed));
            }
        }
        if (request.getBranchName() != null && !request.getBranchName().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("branchName")), "%" + request.getBranchName().toLowerCase(Locale.ROOT) + "%"));
        }
        if (request.getEngineerName() != null && !request.getEngineerName().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("visitorName")), "%" + request.getEngineerName().toLowerCase(Locale.ROOT) + "%"));
        }
        List<String> cities = normalizeCities(request.getCity());
        if (cities != null && !cities.isEmpty()) {
            List<String> lowerCities = cities.stream().map(city -> city.toLowerCase(Locale.ROOT)).toList();
            if (lowerCities.size() == 1) {
                String city = lowerCities.get(0);
                spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("city")), "%" + city + "%"));
            } else {
                spec = spec.and((root, query, cb) -> cb.lower(root.get("city")).in(lowerCities));
            }
        }

        if (request.getComplaintStatus() != null && !request.getComplaintStatus().isEmpty()) {
            if (request.getComplaintStatus().equalsIgnoreCase("FOC_APPROVED")) {
                spec = spec.and((root, query, cb) -> root.get("complaintStatus").in(List.of("FOC", "Approved")));
            } else {
                spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("complaintStatus")), request.getComplaintStatus().toLowerCase(Locale.ROOT)));
            }
        }
        if (request.getSubStatus() != null && !request.getSubStatus().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("complaintStatus")), "%" + request.getSubStatus().toLowerCase(Locale.ROOT) + "%"));
        }
        if (request.getPriority() != null && !request.getPriority().isEmpty()) {
            boolean priority = Boolean.parseBoolean(request.getPriority());
            spec = spec.and((root, query, cb) -> cb.equal(root.get("priority"), priority));
        }
        if (request.getInPool() != null && !request.getInPool().isEmpty()) {
            boolean inPool = Boolean.parseBoolean(request.getInPool());
            spec = spec.and((root, query, cb) -> cb.equal(root.get("markedInPool"), inPool));
        }
        if (Boolean.TRUE.equals(request.getHasReport())) {
            spec = spec.and((root, query, cb) -> {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<HardwareLog> hardwareLogRoot = subquery.from(HardwareLog.class);
                Join<HardwareLog, ?> reportJoin = hardwareLogRoot.join("reports", JoinType.INNER);
                subquery.select(hardwareLogRoot.get("id"))
                        .where(
                                cb.equal(hardwareLogRoot.get("complaintLog"), root),
                                cb.isNotNull(reportJoin.get("id"))
                        );
                return cb.exists(subquery);
            });
        }
        if (request.getReportType() != null && !request.getReportType().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("reportType")), "%" + request.getReportType().toLowerCase(Locale.ROOT) + "%"));
        }

        spec = addDateRange(spec, "date", request.getDateFrom(), request.getDateTo());
        spec = addDateRange(spec, "approvedDate", request.getApprovedDateFrom(), request.getApprovedDateTo());
        spec = addDateRange(spec, "closedDate", request.getClosedDateFrom(), request.getClosedDateTo());
        spec = addDateRange(spec, "quotationDate", request.getQuotationDateFrom(), request.getQuotationDateTo());
        spec = addDateRange(spec, "pendingForClosedDate", request.getPendingForClosedDateFrom(), request.getPendingForClosedDateTo());
        spec = addExactDate(spec, "date", request.getDate());
        spec = addExactDate(spec, "approvedDate", request.getApprovedDate());
        spec = addExactDate(spec, "closedDate", request.getClosedDate());
        spec = addExactDate(spec, "quotationDate", request.getQuotationDate());
        spec = addExactDate(spec, "pendingForClosedDate", request.getPendingForClosedDate());

        Set<String> excluded = Optional.ofNullable(request.getExcludeStatuses()).orElse(List.of()).stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!excluded.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.not(cb.lower(root.get("complaintStatus")).in(excluded)));
        }

        return spec;
    }

    private Specification<ComplaintLog> addDateRange(Specification<ComplaintLog> spec, String field, String from, String to) {
        Date fromDate = parseDate(from);
        if (fromDate != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get(field), fromDate));
        }
        Date toDate = parseDate(to);
        if (toDate != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get(field), toDate));
        }
        return spec;
    }

    private Specification<ComplaintLog> addExactDate(Specification<ComplaintLog> spec, String field, String value) {
        Date date = parseDate(value);
        if (date != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get(field), date));
        }
        return spec;
    }

    private Date parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return Date.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private List<String> openStatuses() {
        return List.of(
                "Open", "FOC", "Quotation", "Network Issue", "Visit Schedule", "Hardware Picked",
                "Visit On Hold", "Dispatched", "Delivered", "Received Inward", "Dispatch Inward",
                "Marked In Pool", "On Call", "Testing", "Renovation", "Disapproved",
                "Additional Counter", "Verify Approval", "BFC Approval", "AHO Approval", "BFC/AHO",
                "Approved", "Wait For Approval", "Pre Approved"
        );
    }

    private void attachLatestHardwareData(List<ComplaintLog> complaints) {
        for (List<ComplaintLog> batch : batches(complaints)) {
            List<Long> ids = batch.stream().map(ComplaintLog::getId).collect(Collectors.toList());
            List<HardwareLog> logs = hardwareLogRepository.findByComplaintLogIdIn(ids);
            Map<Long, Optional<HardwareLog>> latestByComplaintId = logs.stream()
                    .collect(Collectors.groupingBy(log -> log.getComplaintLog().getId(),
                            Collectors.maxBy(Comparator.comparing(HardwareLog::getId))));
            for (ComplaintLog complaint : batch) {
                latestByComplaintId.getOrDefault(complaint.getId(), Optional.empty()).ifPresent(log -> {
                    complaint.setCourierStatus(log.getCourierStatus());
                    complaint.setEquipmentDescription(log.getEquipmentDescription());
                });
            }
        }
    }

    private Map<Long, String> loadAllRemarks(List<ComplaintLog> complaints) {
        Map<Long, String> result = new HashMap<>();
        for (List<ComplaintLog> batch : batches(complaints)) {
            List<Long> ids = batch.stream().map(ComplaintLog::getId).collect(Collectors.toList());
            List<RemarksUpdate> updates = remarksUpdateRepository.findByComplaintLogIds(ids);
            Map<Long, List<RemarksUpdate>> grouped = updates.stream()
                    .collect(Collectors.groupingBy(r -> r.getComplaintLog().getId()));
            for (ComplaintLog complaint : batch) {
                List<RemarksUpdate> remarks = grouped.getOrDefault(complaint.getId(), List.of()).stream()
                        .sorted(Comparator.comparing(RemarksUpdate::getTimestamp))
                        .collect(Collectors.toList());
                result.put(complaint.getId(), remarks.stream()
                        .map(r -> r.getTimestamp().toString().replace("T", " ") + ": " + safe(r.getRemarks()))
                        .collect(Collectors.joining("\n")));
            }
        }
        return result;
    }

    private Map<Long, String> loadLatestRemarks(List<ComplaintLog> complaints) {
        Map<Long, String> result = new HashMap<>();
        for (List<ComplaintLog> batch : batches(complaints)) {
            List<Long> ids = batch.stream().map(ComplaintLog::getId).collect(Collectors.toList());
            List<RemarksUpdate> updates = remarksUpdateRepository.findByComplaintLogIds(ids);
            Map<Long, Optional<RemarksUpdate>> latest = updates.stream()
                    .collect(Collectors.groupingBy(r -> r.getComplaintLog().getId(),
                            Collectors.maxBy(Comparator.comparing(RemarksUpdate::getTimestamp))));
            for (ComplaintLog complaint : batch) {
                latest.getOrDefault(complaint.getId(), Optional.empty()).ifPresentOrElse(
                        r -> result.put(complaint.getId(), r.getTimestamp().toString().replace("T", " ") + ": " + safe(r.getRemarks())),
                        () -> result.put(complaint.getId(), "")
                );
            }
        }
        return result;
    }

    private Map<String, String> loadAssignedVisitors(List<ComplaintLog> complaints) {
        Map<String, String> result = new HashMap<>();
        List<String> complaintIds = complaints.stream()
                .map(ComplaintLog::getComplaintId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        for (int i = 0; i < complaintIds.size(); i += BATCH_SIZE) {
            List<String> batch = complaintIds.subList(i, Math.min(i + BATCH_SIZE, complaintIds.size()));
            List<ComplaintHistory> histories = complaintHistoryRepository
                    .findByComplaintIdInAndFieldNameOrderByChangeDateAsc(batch, "visitorName");
            Map<String, LinkedHashSet<String>> names = new HashMap<>();
            for (ComplaintHistory history : histories) {
                String visitorName = safe(history.getNewValue()).trim();
                if (!visitorName.isEmpty() && !"N/A".equalsIgnoreCase(visitorName)) {
                    names.computeIfAbsent(history.getComplaintId(), key -> new LinkedHashSet<>()).add(visitorName);
                }
            }
            names.forEach((id, set) -> result.put(id, String.join(", ", set)));
        }
        return result;
    }

    private List<String> buildHeaders(String viewStatus, String selectedReport) {
        List<String> headers = new ArrayList<>();
        headers.add("Serial No.");
        if ("ageing".equalsIgnoreCase(selectedReport)) {
            headers.addAll(List.of("Date", "Ageing", "Bank Name", "Branch Code", "Branch Name", "Remarks", "Status"));
            return headers;
        }
        if ("untouched".equalsIgnoreCase(selectedReport)) {
            headers.addAll(List.of("Date", "Ageing", "Bank Name", "Branch Code", "Branch Name", "Latest Remarks", "Status"));
            return headers;
        }
        if (hasAgeingColumn(viewStatus)) headers.add("Ageing");
        headers.addAll(List.of("Date", "Bank Name", "Branch Code", "Branch Name", "City", "Reference Number",
                "Details", "Equipment Description", "Remarks", "Engineer Names", "Repeat Complaint",
                "Status", "Courier Status"));
        if (hasQuotationDate(viewStatus)) headers.add("Quotation Date");
        headers.add("Last Action Date");
        if (hasClosedDate(viewStatus)) headers.add("Closed Date");
        headers.add("Complaint ID");
        return headers;
    }

    private List<String> buildRowValues(
            ComplaintLog complaint,
            int serial,
            String viewStatus,
            String selectedReport,
            String allRemarks,
            String latestRemark,
            String allAssignedVisitors
    ) {
        List<String> row = new ArrayList<>();
        row.add(String.valueOf(serial));
        String ageing = calculateAgeing(complaint.getDate());
        if ("ageing".equalsIgnoreCase(selectedReport)) {
            row.addAll(List.of(dateString(complaint.getDate()), ageing, safe(complaint.getBankName()),
                    safe(complaint.getBranchCode()), safe(complaint.getBranchName()), sanitizeText(allRemarks),
                    safe(complaint.getComplaintStatus())));
            return row;
        }
        if ("untouched".equalsIgnoreCase(selectedReport)) {
            row.addAll(List.of(dateString(complaint.getDate()), ageing, safe(complaint.getBankName()),
                    safe(complaint.getBranchCode()), safe(complaint.getBranchName()), sanitizeText(latestRemark),
                    safe(complaint.getComplaintStatus())));
            return row;
        }
        if (hasAgeingColumn(viewStatus)) row.add(ageing);
        row.addAll(List.of(
                dateString(complaint.getDate()),
                safe(complaint.getBankName()),
                safe(complaint.getBranchCode()),
                safe(complaint.getBranchName()),
                safe(complaint.getCity()),
                safe(complaint.getReferenceNumber()),
                sanitizeText(complaint.getDetails()),
                safe(complaint.getEquipmentDescription()),
                sanitizeText(allRemarks),
                !allAssignedVisitors.isBlank() ? allAssignedVisitors : safe(complaint.getVisitorName()),
                Boolean.TRUE.equals(complaint.getRepeatComplaint()) ? "Yes" : "No",
                safe(complaint.getComplaintStatus()),
                safe(complaint.getCourierStatus())
        ));
        if (hasQuotationDate(viewStatus)) row.add(dateString(complaint.getQuotationDate()));
        row.add(lastActionDate(complaint));
        if (hasClosedDate(viewStatus)) row.add(dateString(complaint.getClosedDate()));
        row.add(safe(complaint.getComplaintId()));
        return row;
    }

    private List<Map.Entry<String, List<ComplaintLog>>> groupComplaintsByBankName(List<ComplaintLog> complaints) {
        Map<String, List<ComplaintLog>> grouped = new HashMap<>();
        for (ComplaintLog complaint : complaints) {
            grouped.computeIfAbsent(blankToDefault(complaint.getBankName(), "Others"), key -> new ArrayList<>()).add(complaint);
        }
        return grouped.entrySet().stream()
                .sorted((a, b) -> {
                    if ("Others".equals(a.getKey())) return 1;
                    if ("Others".equals(b.getKey())) return -1;
                    return a.getKey().compareToIgnoreCase(b.getKey());
                })
                .collect(Collectors.toList());
    }

    private List<Map.Entry<String, List<ComplaintLog>>> groupComplaintsByCity(List<ComplaintLog> complaints) {
        Map<String, List<ComplaintLog>> grouped = new HashMap<>();
        for (ComplaintLog complaint : complaints) {
            grouped.computeIfAbsent(unifyCityName(complaint.getCity()), key -> new ArrayList<>()).add(complaint);
        }
        return grouped.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> CITY_ORDER.indexOf(e.getKey()) >= 0 ? CITY_ORDER.indexOf(e.getKey()) : CITY_ORDER.size()))
                .collect(Collectors.toList());
    }

    private String unifyCityName(String city) {
        if (city == null || city.isBlank()) return "Others";
        for (String knownCity : CITY_ORDER) {
            if (knownCity.equalsIgnoreCase(city.trim())) return knownCity;
        }
        return "Others";
    }

    private List<String> normalizeCities(List<String> cities) {
        if (cities == null) return null;
        return cities.stream()
                .filter(Objects::nonNull)
                .flatMap(city -> Arrays.stream(city.split(",")))
                .map(String::trim)
                .filter(city -> !city.isEmpty())
                .collect(Collectors.toList());
    }

    private <T> List<List<T>> batches(List<T> values) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < values.size(); i += BATCH_SIZE) {
            batches.add(values.subList(i, Math.min(i + BATCH_SIZE, values.size())));
        }
        return batches;
    }

    private boolean shouldGroupByBank(String viewStatus) {
        return "Approved".equalsIgnoreCase(viewStatus) || "Wait For Approval".equalsIgnoreCase(viewStatus);
    }

    private boolean hasAgeingColumn(String viewStatus) {
        return Set.of("wait for approval", "approved", "open", "overall").contains(viewStatus.toLowerCase(Locale.ROOT));
    }

    private boolean hasQuotationDate(String viewStatus) {
        return Set.of("wait for approval", "approved", "overall").contains(viewStatus.toLowerCase(Locale.ROOT));
    }

    private boolean hasClosedDate(String viewStatus) {
        return Set.of("closed", "overall").contains(viewStatus.toLowerCase(Locale.ROOT));
    }

    private String lastActionDate(ComplaintLog complaint) {
        return switch (safe(complaint.getComplaintStatus())) {
            case "Open" -> dateString(complaint.getDate());
            case "Visit Schedule" -> dateString(complaint.getScheduleDate());
            case "Closed" -> dateString(complaint.getClosedDate());
            case "Wait For Approval" -> dateString(complaint.getQuotationDate());
            case "Approved" -> dateString(complaint.getApprovedDate());
            case "FOC" -> dateString(complaint.getFocDate());
            default -> "";
        };
    }

    private String calculateAgeing(Date date) {
        if (date == null) return "";
        long days = LocalDate.now().toEpochDay() - date.toLocalDate().toEpochDay();
        return days == 0 ? "" : String.valueOf(days);
    }

    private String dateString(Date date) {
        return date == null ? "" : date.toString();
    }

    private String sanitizeText(String text) {
        return safe(text).replaceAll("[\\t]+", " ");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void createNumericCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(Double.parseDouble(value));
        cell.setCellStyle(style);
    }

    private CellStyle createDateHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 10);
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createStatusHeaderStyle(Workbook workbook) {
        CellStyle style = createDateHeaderStyle(workbook);
        Font font = workbook.createFont();
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 12);
        font.setBold(true);
        style.setFont(font);
        setExactFill(style, "B6D7A8");
        return style;
    }

    private CellStyle createGroupHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 16);
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setExactFill(style, "6AA84F");
        return style;
    }

    private CellStyle createColumnHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 10);
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setExactFill(style, "FFF2CC");
        return style;
    }

    private void setExactFill(CellStyle style, String rgbHex) {
        XSSFCellStyle xssfStyle = (XSSFCellStyle) style;
        byte[] rgb = new byte[] {
                (byte) Integer.parseInt(rgbHex.substring(0, 2), 16),
                (byte) Integer.parseInt(rgbHex.substring(2, 4), 16),
                (byte) Integer.parseInt(rgbHex.substring(4, 6), 16)
        };
        xssfStyle.setFillForegroundColor(new XSSFColor(rgb, new DefaultIndexedColorMap()));
        xssfStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    private CellStyle createDataCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        return style;
    }
}
