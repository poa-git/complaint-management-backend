package com.system.complaints.dto;

import java.util.ArrayList;
import java.util.List;

public class VisitPlanReviewResponse {
    private String reviewedAt;
    private int total;
    private int sourceTotal;
    private List<VisitPlanComplaintDTO> complaints = new ArrayList<>();

    public String getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(String reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSourceTotal() {
        return sourceTotal;
    }

    public void setSourceTotal(int sourceTotal) {
        this.sourceTotal = sourceTotal;
    }

    public List<VisitPlanComplaintDTO> getComplaints() {
        return complaints;
    }

    public void setComplaints(List<VisitPlanComplaintDTO> complaints) {
        this.complaints = complaints;
    }
}
