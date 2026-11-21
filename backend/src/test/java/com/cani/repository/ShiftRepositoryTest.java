package com.cani.repository;

import com.cani.entity.*;
import com.cani.entity.enums.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("h2")
@Transactional
public class ShiftRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private DispatchProposalRepository dispatchProposalRepository;

    private Shift seedShift(int requiredWorkers, int confirmedWorkers) {
        User eoUser = User.builder()
                .userId(UUID.randomUUID())
                .email("eo_" + UUID.randomUUID() + "@cani.vn")
                .fullName("EO Test")
                .role(UserRole.EVENT_ORGANIZER)
                .status(UserStatus.ACTIVE)
                .build();
        em.persist(eoUser);

        EventOrganizer eo = EventOrganizer.builder()
                .user(eoUser)
                .companyName("Test EO")
                .verificationStatus(VerificationStatus.APPROVED)
                .ratingAvg(BigDecimal.valueOf(4.5))
                .build();
        em.persist(eo);

        Event event = Event.builder()
                .eventOrganizer(eo)
                .name("Test Event")
                .venueAddress("TP.HCM")
                .venueLat(10.7769)
                .venueLng(106.7009)
                .startTime(Instant.now().minus(java.time.Duration.ofDays(1)))
                .endTime(Instant.now())
                .build();
        em.persist(event);

        Instant now = Instant.now();
        Shift shift = Shift.builder()
                .event(event)
                .jobType("Setup")
                .startTime(now.plus(java.time.Duration.ofDays(1)))
                .endTime(now.plus(java.time.Duration.ofDays(1)).plus(java.time.Duration.ofHours(4)))
                .hourlyWage(BigDecimal.valueOf(50000))
                .requiredWorkers(requiredWorkers)
                .confirmedWorkers(confirmedWorkers)
                .remainingWorkers(requiredWorkers - confirmedWorkers)
                .status(ShiftStatus.ACTIVE)
                .build();
        em.persist(shift);
        em.flush();
        return shift;
    }

    private Worker seedWorker() {
        User workerUser = User.builder()
                .userId(UUID.randomUUID())
                .email("worker_" + UUID.randomUUID() + "@cani.vn")
                .fullName("Worker Test")
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .build();
        em.persist(workerUser);

        Worker worker = Worker.builder()
                .user(workerUser)
                .fullName("Worker Test")
                .reliabilityScore(90)
                .ratingAvg(BigDecimal.valueOf(4.2))
                .kycStatus(KycStatus.APPROVED)
                .totalShiftsCompleted(0)
                .build();
        em.persist(worker);
        em.flush();
        return worker;
    }

    @Test
    @DisplayName("N-increment succeeds within capacity: confirmed+remaining update, rows=1")
    void incrementAtomicBy_success() {
        Shift shift = seedShift(6, 4);

        int rows = shiftRepository.incrementConfirmedWorkersAtomicBy(shift.getShiftId(), 2);

        em.clear();
        Shift reloaded = em.getReference(Shift.class, shift.getShiftId());
        assertThat(rows).isEqualTo(1);
        assertThat(reloaded.getConfirmedWorkers()).isEqualTo(6);
        assertThat(reloaded.getRemainingWorkers()).isEqualTo(0);
    }

    @Test
    @DisplayName("N-increment rejected when it would exceed required workers: rows=0, no change")
    void incrementAtomicBy_overCapacity() {
        Shift shift = seedShift(6, 5);

        int rows = shiftRepository.incrementConfirmedWorkersAtomicBy(shift.getShiftId(), 3);

        em.clear();
        Shift reloaded = em.getReference(Shift.class, shift.getShiftId());
        assertThat(rows).isEqualTo(0);
        assertThat(reloaded.getConfirmedWorkers()).isEqualTo(5);
        assertThat(reloaded.getRemainingWorkers()).isEqualTo(1);
    }

    @Test
    @DisplayName("GREATEST floors remaining_workers at 0")
    void incrementAtomicBy_remainingFlooredAtZero() {
        Shift shift = seedShift(6, 4);
        shift.setRemainingWorkers(1);
        em.flush();

        int rows = shiftRepository.incrementConfirmedWorkersAtomicBy(shift.getShiftId(), 2);

        em.clear();
        Shift reloaded = em.getReference(Shift.class, shift.getShiftId());
        assertThat(rows).isEqualTo(1);
        assertThat(reloaded.getConfirmedWorkers()).isEqualTo(6);
        assertThat(reloaded.getRemainingWorkers()).isZero();
    }

    @Test
    @DisplayName("Group-based proposal queries filter by shiftId+groupId and groupId+status")
    void groupBasedQueries() {
        Shift shift = seedShift(6, 0);
        Worker worker = seedWorker();
        UUID groupId = UUID.randomUUID();

        DispatchProposal p1 = DispatchProposal.builder()
                .shift(shift)
                .worker(worker)
                .groupId(groupId)
                .status(ProposalStatus.PENDING)
                .source(AssignmentSource.GROUP)
                .build();
        dispatchProposalRepository.save(p1);

        DispatchProposal p2 = DispatchProposal.builder()
                .shift(shift)
                .worker(worker)
                .groupId(groupId)
                .status(ProposalStatus.ACCEPTED)
                .source(AssignmentSource.GROUP)
                .build();
        dispatchProposalRepository.save(p2);

        UUID otherGroupId = UUID.randomUUID();
        DispatchProposal p3 = DispatchProposal.builder()
                .shift(shift)
                .worker(worker)
                .groupId(otherGroupId)
                .status(ProposalStatus.PENDING)
                .source(AssignmentSource.GROUP)
                .build();
        dispatchProposalRepository.save(p3);

        List<DispatchProposal> byShiftGroupStatuses = dispatchProposalRepository
                .findByShift_ShiftIdAndGroupIdAndStatusIn(shift.getShiftId(), groupId,
                        List.of(ProposalStatus.PENDING, ProposalStatus.ACCEPTED));
        assertThat(byShiftGroupStatuses).hasSize(2);

        List<DispatchProposal> pendingInGroup = dispatchProposalRepository
                .findByGroupIdAndStatus(groupId, ProposalStatus.PENDING);
        assertThat(pendingInGroup).hasSize(1);
        assertThat(pendingInGroup.get(0).getProposalId()).isEqualTo(p1.getProposalId());
    }
}
