package com.system.complaints.dto;

import com.system.complaints.model.VisitPlan;
import com.system.complaints.model.VisitPlanEntry;

import java.util.List;

public class VisitPlanWorkflowResponse {
    public Long id;
    public String scheduleDate;
    public String status;
    public String createdBy;
    public String createdAt;
    public String updatedAt;
    public String submittedAt;
    public String approvedBy;
    public String approvedAt;
    public List<Item> entries;

    public static VisitPlanWorkflowResponse from(VisitPlan plan, List<VisitPlanEntry> entries) {
        VisitPlanWorkflowResponse response = new VisitPlanWorkflowResponse();
        response.id = plan.getId();
        response.scheduleDate = value(plan.getScheduleDate());
        response.status = plan.getStatus();
        response.createdBy = plan.getCreatedBy();
        response.createdAt = value(plan.getCreatedAt());
        response.updatedAt = value(plan.getUpdatedAt());
        response.submittedAt = value(plan.getSubmittedAt());
        response.approvedBy = plan.getApprovedBy();
        response.approvedAt = value(plan.getApprovedAt());
        response.entries = entries.stream().map(Item::from).toList();
        return response;
    }

    private static String value(Object value) {
        return value == null ? null : value.toString();
    }

    public static class Item {
        public Long id;
        public String complaintId;
        public String scheduleDate;
        public String visitorName;
        public String visitorStation;
        public String bankName;
        public String branchCode;
        public String branchName;
        public String city;
        public String courierStatus;
        public Integer complaintAge;
        public Integer hardwareDeliveryAge;
        public String priorityLabel;
        public String priorityDetail;
        public String routeOrigin;
        public String routeDestination;
        public Double routeDistanceKm;
        public Integer routeDurationMinutes;
        public String approvalStatus;

        static Item from(VisitPlanEntry entry) {
            Item item = new Item();
            item.id = entry.getId();
            item.complaintId = entry.getComplaintId();
            item.scheduleDate = value(entry.getScheduleDate());
            item.visitorName = entry.getVisitorName();
            item.visitorStation = entry.getVisitorStation();
            item.bankName = entry.getBankName();
            item.branchCode = entry.getBranchCode();
            item.branchName = entry.getBranchName();
            item.city = entry.getCity();
            item.courierStatus = entry.getCourierStatus();
            item.complaintAge = entry.getComplaintAge();
            item.hardwareDeliveryAge = entry.getHardwareDeliveryAge();
            item.priorityLabel = entry.getPriorityLabel();
            item.priorityDetail = entry.getPriorityDetail();
            item.routeOrigin = entry.getRouteOrigin();
            item.routeDestination = entry.getRouteDestination();
            item.routeDistanceKm = entry.getRouteDistanceKm();
            item.routeDurationMinutes = entry.getRouteDurationMinutes();
            item.approvalStatus = entry.getApprovalStatus();
            return item;
        }
    }
}
