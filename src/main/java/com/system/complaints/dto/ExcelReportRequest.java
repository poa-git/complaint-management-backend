package com.system.complaints.dto;

import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ExcelReportRequest {
    private List<Long> complaintIds;
    private String viewStatus;
    private String selectedReport;
    private List<String> excludeStatuses;

    private String status;
    private String bankName;
    private String branchCode;
    private String branchName;
    private String engineerName;
    private List<String> city;
    private String complaintStatus;
    private String subStatus;
    private String dateFrom;
    private String dateTo;
    private String approvedDateFrom;
    private String approvedDateTo;
    private String closedDateFrom;
    private String closedDateTo;
    private String quotationDateFrom;
    private String quotationDateTo;
    private String pendingForClosedDateFrom;
    private String pendingForClosedDateTo;
    private String date;
    private String approvedDate;
    private String closedDate;
    private String pendingForClosedDate;
    private String quotationDate;
    private String priority;
    private String inPool;
    private Boolean hasReport;
    private String reportType;

    public List<Long> getComplaintIds() { return complaintIds; }
    public void setComplaintIds(List<Long> complaintIds) { this.complaintIds = complaintIds; }
    public String getViewStatus() { return viewStatus; }
    public void setViewStatus(String viewStatus) { this.viewStatus = viewStatus; }
    public String getSelectedReport() { return selectedReport; }
    public void setSelectedReport(String selectedReport) { this.selectedReport = selectedReport; }
    public List<String> getExcludeStatuses() { return excludeStatuses; }
    public void setExcludeStatuses(List<String> excludeStatuses) { this.excludeStatuses = excludeStatuses; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getBranchCode() { return branchCode; }
    public void setBranchCode(String branchCode) { this.branchCode = branchCode; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public String getEngineerName() { return engineerName; }
    public void setEngineerName(String engineerName) { this.engineerName = engineerName; }
    public List<String> getCity() { return city; }

    @JsonSetter("city")
    public void setCity(Object city) {
        if (city == null) {
            this.city = null;
        } else if (city instanceof List<?> values) {
            this.city = values.stream()
                    .filter(value -> value != null && !value.toString().trim().isEmpty())
                    .map(value -> value.toString().trim())
                    .collect(Collectors.toList());
        } else {
            String raw = city.toString().trim();
            this.city = raw.isEmpty()
                    ? null
                    : Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .collect(Collectors.toList());
        }
    }
    public String getComplaintStatus() { return complaintStatus; }
    public void setComplaintStatus(String complaintStatus) { this.complaintStatus = complaintStatus; }
    public String getSubStatus() { return subStatus; }
    public void setSubStatus(String subStatus) { this.subStatus = subStatus; }
    public String getDateFrom() { return dateFrom; }
    public void setDateFrom(String dateFrom) { this.dateFrom = dateFrom; }
    public String getDateTo() { return dateTo; }
    public void setDateTo(String dateTo) { this.dateTo = dateTo; }
    public String getApprovedDateFrom() { return approvedDateFrom; }
    public void setApprovedDateFrom(String approvedDateFrom) { this.approvedDateFrom = approvedDateFrom; }
    public String getApprovedDateTo() { return approvedDateTo; }
    public void setApprovedDateTo(String approvedDateTo) { this.approvedDateTo = approvedDateTo; }
    public String getClosedDateFrom() { return closedDateFrom; }
    public void setClosedDateFrom(String closedDateFrom) { this.closedDateFrom = closedDateFrom; }
    public String getClosedDateTo() { return closedDateTo; }
    public void setClosedDateTo(String closedDateTo) { this.closedDateTo = closedDateTo; }
    public String getQuotationDateFrom() { return quotationDateFrom; }
    public void setQuotationDateFrom(String quotationDateFrom) { this.quotationDateFrom = quotationDateFrom; }
    public String getQuotationDateTo() { return quotationDateTo; }
    public void setQuotationDateTo(String quotationDateTo) { this.quotationDateTo = quotationDateTo; }
    public String getPendingForClosedDateFrom() { return pendingForClosedDateFrom; }
    public void setPendingForClosedDateFrom(String pendingForClosedDateFrom) { this.pendingForClosedDateFrom = pendingForClosedDateFrom; }
    public String getPendingForClosedDateTo() { return pendingForClosedDateTo; }
    public void setPendingForClosedDateTo(String pendingForClosedDateTo) { this.pendingForClosedDateTo = pendingForClosedDateTo; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getApprovedDate() { return approvedDate; }
    public void setApprovedDate(String approvedDate) { this.approvedDate = approvedDate; }
    public String getClosedDate() { return closedDate; }
    public void setClosedDate(String closedDate) { this.closedDate = closedDate; }
    public String getPendingForClosedDate() { return pendingForClosedDate; }
    public void setPendingForClosedDate(String pendingForClosedDate) { this.pendingForClosedDate = pendingForClosedDate; }
    public String getQuotationDate() { return quotationDate; }
    public void setQuotationDate(String quotationDate) { this.quotationDate = quotationDate; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getInPool() { return inPool; }
    public void setInPool(String inPool) { this.inPool = inPool; }
    public Boolean getHasReport() { return hasReport; }
    public void setHasReport(Boolean hasReport) { this.hasReport = hasReport; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
}
