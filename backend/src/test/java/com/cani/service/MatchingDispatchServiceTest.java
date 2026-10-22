package com.cani.service;

import com.cani.entity.*;
import com.cani.entity.enums.ProposalStatus;
import com.cani.entity.enums.ShiftStatus;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.DispatchProposalRepository;
import com.cani.repository.ShiftAssignmentRepository;
import com.cani.repository.ShiftRepository;
import com.cani.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingDispatchServiceTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private DispatchProposalRepository dispatchProposalRepository;

    @InjectMocks
    private MatchingDispatchService matchingDispatchService;

    private Shift sampleShift;
    private Worker onlineWorker;
    private Worker offlineWorker;
    private Worker lowRsWorker;

    @BeforeEach
    void setUp() {
        UUID eventId = UUID.randomUUID();
        Event event = Event.builder()
                .eventId(eventId)
                .name("Hội nghị Công nghệ FPT")
                .venueLat(10.7769) // TP.HCM Bến Thành
                .venueLng(106.7009)
                .build();

        sampleShift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .event(event)
                .jobType("Phục vụ")
                .startTime(Instant.now().plusSeconds(3600 * 24))
                .endTime(Instant.now().plusSeconds(3600 * 28))
                .requiredWorkers(2)
                .confirmedWorkers(0)
                .remainingWorkers(2)
                .hourlyWage(BigDecimal.valueOf(35000))
                .status(ShiftStatus.ACTIVE)
                .locationLat(10.7769)
                .locationLng(106.7009)
                .minReliabilityScore(60)
                .build();

        User activeUser1 = User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).status(UserStatus.ACTIVE).build();
        onlineWorker = Worker.builder()
                .workerId(UUID.randomUUID())
                .user(activeUser1)
                .fullName("Nguyễn Văn Worker")
                .isOnline(true)
                .reliabilityScore(85)
                .ratingAvg(BigDecimal.valueOf(4.8))
                .totalShiftsCompleted(5)
                .baseLat(10.7780) // Cách 200m
                .baseLng(106.7010)
                .build();

        User activeUser2 = User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).status(UserStatus.ACTIVE).build();
        offlineWorker = Worker.builder()
                .workerId(UUID.randomUUID())
                .user(activeUser2)
                .fullName("Trần Thị Offline")
                .isOnline(false) // Offline
                .reliabilityScore(90)
                .baseLat(10.7780)
                .baseLng(106.7010)
                .build();

        User activeUser3 = User.builder().userId(UUID.randomUUID()).role(UserRole.WORKER).status(UserStatus.ACTIVE).build();
        lowRsWorker = Worker.builder()
                .workerId(UUID.randomUUID())
                .user(activeUser3)
                .fullName("Lê Văn Uy Tín Thấp")
                .isOnline(true)
                .reliabilityScore(45) // Dưới ngưỡng 60
                .baseLat(10.7780)
                .baseLng(106.7010)
                .build();
    }

    @Test
    @DisplayName("Hard Filter: Chỉ dispatch cho Worker online, uy tín >= 60 và không trùng lịch")
    void shouldDispatchOnlyToQualifiedOnlineWorkers() {
        when(shiftRepository.findById(sampleShift.getShiftId())).thenReturn(Optional.of(sampleShift));
        when(workerRepository.findAll()).thenReturn(List.of(onlineWorker, offlineWorker, lowRsWorker));
        when(shiftAssignmentRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(List.of());
        when(dispatchProposalRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any())).thenReturn(false);
        when(shiftAssignmentRepository.existsByShift_ShiftIdAndWorker_WorkerId(any(), any())).thenReturn(false);

        int count = matchingDispatchService.dispatchForShift(sampleShift.getShiftId());

        assertThat(count).isEqualTo(1);
        verify(dispatchProposalRepository, times(1)).save(any(DispatchProposal.class));
    }

    @Test
    @DisplayName("Haversine Distance: Tính khoảng cách chính xác giữa 2 tọa độ")
    void shouldCalculateAccurateDistance() {
        // Tọa độ Chợ Bến Thành (10.7719, 106.6983) và Nhà Thờ Đức Bà (10.7797, 106.6990) ~ 0.88 km
        double distance = MatchingDispatchService.calculateDistance(10.7719, 106.6983, 10.7797, 106.6990);
        assertThat(distance).isGreaterThan(0.7).isLessThan(1.2);
    }
}
