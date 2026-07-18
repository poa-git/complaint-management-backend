package com.system.complaints.service;

import com.system.complaints.model.VisitPlanSubmissionSnapshot;
import com.system.complaints.repository.VisitPlanEntryRepository;
import com.system.complaints.repository.VisitPlanRepository;
import com.system.complaints.repository.VisitPlanSubmissionSnapshotItemRepository;
import com.system.complaints.repository.VisitPlanSubmissionSnapshotRepository;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisitPlanReportingServiceTest {
    @Test
    void eligibleReportUsesOnlyLatestSnapshotFromEachDay() {
        VisitPlanRepository planRepository = mock(VisitPlanRepository.class);
        VisitPlanEntryRepository entryRepository = mock(VisitPlanEntryRepository.class);
        VisitPlanSubmissionSnapshotRepository snapshotRepository =
                mock(VisitPlanSubmissionSnapshotRepository.class);
        VisitPlanSubmissionSnapshotItemRepository itemRepository =
                mock(VisitPlanSubmissionSnapshotItemRepository.class);
        VisitPlanReportingService service = new VisitPlanReportingService(
                planRepository,
                entryRepository,
                snapshotRepository,
                itemRepository
        );

        VisitPlanSubmissionSnapshot first = snapshot(1L, "2026-07-17 09:00:00");
        VisitPlanSubmissionSnapshot latest = snapshot(2L, "2026-07-17 10:00:00");
        VisitPlanSubmissionSnapshot nextDay = snapshot(3L, "2026-07-18 08:00:00");
        when(snapshotRepository.findByCapturedAtBetweenOrderByCapturedAtAscPlanIdAscSnapshotVersionAsc(
                Timestamp.valueOf("2026-07-17 00:00:00"),
                Timestamp.valueOf("2026-07-18 23:59:59.999999999")
        )).thenReturn(List.of(first, latest, nextDay));
        when(itemRepository.findBySnapshotIdInOrderBySnapshotIdAscIdAsc(List.of(2L, 3L)))
                .thenReturn(List.of());

        service.preview("ELIGIBLE", LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 18));

        verify(itemRepository).findBySnapshotIdInOrderBySnapshotIdAscIdAsc(List.of(2L, 3L));
    }

    private VisitPlanSubmissionSnapshot snapshot(Long id, String capturedAt) {
        VisitPlanSubmissionSnapshot snapshot = mock(VisitPlanSubmissionSnapshot.class);
        when(snapshot.getId()).thenReturn(id);
        when(snapshot.getCapturedAt()).thenReturn(Timestamp.valueOf(capturedAt));
        return snapshot;
    }
}
