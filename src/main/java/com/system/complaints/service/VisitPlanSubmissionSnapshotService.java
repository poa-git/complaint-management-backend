package com.system.complaints.service;

import com.system.complaints.dto.VisitPlanComplaintDTO;
import com.system.complaints.model.VisitPlan;
import com.system.complaints.model.VisitPlanSubmissionSnapshot;
import com.system.complaints.model.VisitPlanSubmissionSnapshotItem;
import com.system.complaints.repository.VisitPlanSubmissionSnapshotItemRepository;
import com.system.complaints.repository.VisitPlanSubmissionSnapshotRepository;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
public class VisitPlanSubmissionSnapshotService {
    private final VisitPlanSubmissionSnapshotRepository snapshotRepository;
    private final VisitPlanSubmissionSnapshotItemRepository itemRepository;

    public VisitPlanSubmissionSnapshotService(
            VisitPlanSubmissionSnapshotRepository snapshotRepository,
            VisitPlanSubmissionSnapshotItemRepository itemRepository
    ) {
        this.snapshotRepository = snapshotRepository;
        this.itemRepository = itemRepository;
    }

    public VisitPlanSubmissionSnapshot capture(
            VisitPlan plan,
            String submittedBy,
            Timestamp capturedAt,
            List<VisitPlanComplaintDTO> complaints
    ) {
        int nextVersion = snapshotRepository.findTopByPlanIdOrderBySnapshotVersionDesc(plan.getId())
                .map(previous -> previous.getSnapshotVersion() + 1)
                .orElse(1);

        VisitPlanSubmissionSnapshot snapshot = new VisitPlanSubmissionSnapshot();
        snapshot.setPlanId(plan.getId());
        snapshot.setSnapshotVersion(nextVersion);
        snapshot.setScheduleDate(plan.getScheduleDate());
        snapshot.setSubmittedBy(submittedBy);
        snapshot.setCapturedAt(capturedAt);
        snapshot.setEligibleCount(complaints.size());
        snapshot = snapshotRepository.save(snapshot);

        Long snapshotId = snapshot.getId();
        List<VisitPlanSubmissionSnapshotItem> items = complaints.stream().map(source -> {
            VisitPlanSubmissionSnapshotItem item = new VisitPlanSubmissionSnapshotItem();
            item.setSnapshotId(snapshotId);
            item.setComplaintId(source.getComplaintId());
            item.setComplaintDate(source.getDate());
            item.setBankName(source.getBankName());
            item.setBranchCode(source.getBranchCode());
            item.setBranchName(source.getBranchName());
            item.setCity(source.getCity());
            item.setComplaintStatus(source.getComplaintStatus());
            item.setCourierStatus(source.getCourierStatus());
            item.setVisitorName(source.getVisitorName());
            item.setComplaintAge(source.getComplaintAge());
            item.setHardwareDeliveryAge(source.getHardwareDeliveryAge());
            item.setPriorityLabel(source.getPriorityLabel());
            item.setPriorityDetail(source.getPriorityDetail());
            return item;
        }).toList();
        itemRepository.saveAll(items);
        return snapshot;
    }
}
