package com.system.complaints.repository;

import com.system.complaints.model.VisitPlanSubmissionSnapshotItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VisitPlanSubmissionSnapshotItemRepository
        extends JpaRepository<VisitPlanSubmissionSnapshotItem, Long> {
    List<VisitPlanSubmissionSnapshotItem> findBySnapshotIdInOrderBySnapshotIdAscIdAsc(List<Long> snapshotIds);
}
