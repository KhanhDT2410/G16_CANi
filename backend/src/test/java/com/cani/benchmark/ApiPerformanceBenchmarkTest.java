package com.cani.benchmark;

import com.cani.entity.*;
import com.cani.entity.enums.*;
import com.cani.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.datasource.hikari.minimum-idle=1"
})
@ActiveProfiles("h2")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Benchmark: N+1 SQL Count, Hikari Acquire & p95 (30-50 Workers)")
class ApiPerformanceBenchmarkTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private MeterRegistry meterRegistry;

    @Autowired private UserRepository userRepository;
    @Autowired private EventOrganizerRepository eoRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private ShiftRepository shiftRepository;
    @Autowired private WorkerRepository workerRepository;
    @Autowired private ShiftAssignmentRepository assignmentRepository;
    @Autowired private AttendanceLogRepository attendanceLogRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private DispatchProposalRepository proposalRepository;
    @Autowired private SkillRepository skillRepository;
    @Autowired private ShiftSkillRequirementRepository shiftSkillReqRepository;
    @Autowired private WorkerSkillRepository workerSkillRepository;
    @Autowired private WorkerAvailabilityRepository workerAvailabilityRepository;
    @Autowired private WorkerEkycVerificationRepository workerEkycRepository;

    private String eoToken;
    private String adminToken;
    private String workerHistoryToken;
    private String workerNearbyToken;

    private UUID benchmarkEventId;
    private UUID primaryShiftId;

    @BeforeAll
    void seed45WorkerDataset() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();

        User adminUser = userRepository.save(User.builder()
                .userId(UUID.randomUUID())
                .email("bench-admin-" + tag + "@cani.test")
                .fullName("Benchmark Admin")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());
        adminToken = "dev-token-" + adminUser.getUserId();

        User eoUser = userRepository.save(User.builder()
                .userId(UUID.randomUUID())
                .email("bench-eo-" + tag + "@cani.test")
                .fullName("Benchmark EO")
                .role(UserRole.EVENT_ORGANIZER)
                .status(UserStatus.ACTIVE)
                .build());
        eoToken = "dev-token-" + eoUser.getUserId();

        EventOrganizer eo = eoRepository.save(EventOrganizer.builder()
                .user(eoUser)
                .companyName("Benchmark Events Co " + tag)
                .verificationStatus(VerificationStatus.APPROVED)
                .build());

        List<Skill> skills = new ArrayList<>();
        for (int s = 0; s < 3; s++) {
            skills.add(skillRepository.save(Skill.builder()
                    .name("BenchSkill-" + tag + "-" + s)
                    .category("Event")
                    .isActive(true)
                    .build()));
        }

        Event event = eventRepository.save(Event.builder()
                .eventOrganizer(eo)
                .name("Gala 45 Nhân Sự " + tag)
                .venueAddress("Quận 1, TP.HCM")
                .venueLat(10.7769)
                .venueLng(106.7009)
                .eventDate(LocalDate.now().plusDays(1))
                .startTime(now.plus(6, ChronoUnit.HOURS))
                .endTime(now.plus(48, ChronoUnit.HOURS))
                .mode(EventMode.MATCHING)
                .status(EventStatus.ACTIVE)
                .totalShifts(40)
                .totalRequiredWorkers(100)
                .totalConfirmedWorkers(45)
                .build());
        benchmarkEventId = event.getEventId();

        // 40 shifts (Shift #0 is the primary 45-worker shift; Shifts #1..#39 are nearby open shifts)
        List<Shift> shifts = new ArrayList<>();
        UUID[] skillIds = new UUID[]{skills.get(0).getSkillId(), skills.get(1).getSkillId()};
        for (int i = 0; i < 40; i++) {
            Instant sStart = now.plus(6 + i, ChronoUnit.HOURS);
            Instant sEnd = sStart.plus(4, ChronoUnit.HOURS);
            Shift shift = shiftRepository.save(Shift.builder()
                    .event(event)
                    .jobType(i == 0 ? "Phục vụ bàn chính" : "Hỗ trợ sự kiện #" + i)
                    .startTime(sStart)
                    .endTime(sEnd)
                    .requiredWorkers(i == 0 ? 50 : 10)
                    .confirmedWorkers(i == 0 ? 45 : 1)
                    .remainingWorkers(i == 0 ? 5 : 9)
                    .hourlyWage(BigDecimal.valueOf(50000))
                    .status(ShiftStatus.ACTIVE)
                    .locationAddress("Quận 1, TP.HCM")
                    .locationLat(10.7769 + (i * 0.0005))
                    .locationLng(106.7009 + (i * 0.0005))
                    .requiredSkills(skillIds)
                    .performedSkillId(skills.get(0).getSkillId())
                    .minReliabilityScore(50)
                    .maxDistanceKm(15.0)
                    .build());
            shifts.add(shift);

            for (int r = 0; r < 2; r++) {
                shiftSkillReqRepository.save(ShiftSkillRequirement.builder()
                        .shift(shift)
                        .skill(skills.get(r))
                        .requirementType(SkillRequirementType.REQUIRED)
                        .minVerificationTier(VerificationTier.SELF_DECLARED)
                        .requiresValidCertificate(false)
                        .build());
            }
        }
        primaryShiftId = shifts.get(0).getShiftId();

        // 45 workers assigned to primaryShiftId
        List<Worker> workers = new ArrayList<>();
        for (int w = 0; w < 45; w++) {
            User wu = userRepository.save(User.builder()
                    .userId(UUID.randomUUID())
                    .email("bench-wk-" + tag + "-" + w + "@cani.test")
                    .fullName("Worker Bench " + w)
                    .phone("0900" + String.format("%06d", w))
                    .role(UserRole.WORKER)
                    .status(UserStatus.ACTIVE)
                    .build());
            Worker worker = workerRepository.save(Worker.builder()
                    .user(wu)
                    .fullName("Worker Bench " + w)
                    .kycStatus(KycStatus.APPROVED)
                    .reliabilityScore(95)
                    .isOnline(true)
                    .baseLat(10.7769)
                    .baseLng(106.7009)
                    .minWage(BigDecimal.valueOf(30000))
                    .build());
            workers.add(worker);

            workerEkycRepository.save(WorkerEkycVerification.builder()
                    .worker(worker)
                    .fullName(worker.getFullName())
                    .status("APPROVED")
                    .build());

            for (int d = 1; d <= 7; d++) {
                workerAvailabilityRepository.save(WorkerAvailability.builder()
                        .worker(worker)
                        .dayOfWeek(d)
                        .startTime(LocalTime.of(0, 0))
                        .endTime(LocalTime.of(23, 59))
                        .isAvailable(true)
                        .build());
            }
            for (int s = 0; s < 2; s++) {
                workerSkillRepository.save(WorkerSkill.builder()
                        .worker(worker)
                        .skill(skills.get(s))
                        .proficiency(ExperienceLevel.INTERMEDIATE)
                        .verificationTier(VerificationTier.PLATFORM_PROVEN)
                        .build());
            }

            ShiftAssignment sa = assignmentRepository.save(ShiftAssignment.builder()
                    .shift(shifts.get(0))
                    .worker(worker)
                    .status(AssignmentStatus.CHECKED_IN)
                    .source(AssignmentSource.MATCHING)
                    .eoConfirmed(w < 20)
                    .build());

            attendanceLogRepository.save(AttendanceLog.builder()
                    .shiftAssignment(sa)
                    .worker(worker)
                    .checkinTime(now.minus(30, ChronoUnit.MINUTES))
                    .attendanceStatus(AttendanceStatus.ON_TIME)
                    .isOnTime(true)
                    .hoursWorked(BigDecimal.valueOf(4.0))
                    .build());

            if (w < 25) {
                paymentRepository.save(Payment.builder()
                        .eventId(event.getEventId())
                        .shiftId(primaryShiftId)
                        .assignmentId(sa.getAssignmentId())
                        .eventOrganizer(eo)
                        .worker(worker)
                        .agreedAmount(BigDecimal.valueOf(200000))
                        .declaredAmount(BigDecimal.valueOf(200000))
                        .status(PaymentStatus.DECLARED_PENDING_WORKER)
                        .paymentMethod("CASH")
                        .declaredAt(now)
                        .build());
            }
        }

        // Worker #0 gets 34 more assignments + 35 proposals to benchmark /workers/assignments and /workers/proposals
        Worker historyWorker = workers.get(0);
        workerHistoryToken = "dev-token-" + historyWorker.getUser().getUserId();
        for (int i = 1; i <= 34; i++) {
            Shift s = shifts.get(i);
            ShiftAssignment sa = assignmentRepository.save(ShiftAssignment.builder()
                    .shift(s)
                    .worker(historyWorker)
                    .status(AssignmentStatus.CONFIRMED)
                    .source(AssignmentSource.MATCHING)
                    .build());
            attendanceLogRepository.save(AttendanceLog.builder()
                    .shiftAssignment(sa)
                    .worker(historyWorker)
                    .attendanceStatus(AttendanceStatus.NOT_CHECKED_IN)
                    .build());
            proposalRepository.save(DispatchProposal.builder()
                    .shift(s)
                    .worker(historyWorker)
                    .status(ProposalStatus.PENDING)
                    .matchScore(BigDecimal.valueOf(92.5))
                    .expiresAt(now.plus(4, ChronoUnit.HOURS))
                    .build());
        }

        // Fresh nearby Worker #46 with 0 assignments so all 40 active shifts run through eligibility evaluation
        User nearbyUser = userRepository.save(User.builder()
                .userId(UUID.randomUUID())
                .email("bench-nearby-" + tag + "@cani.test")
                .fullName("Worker Nearby Bench")
                .phone("0911000046")
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .build());
        workerNearbyToken = "dev-token-" + nearbyUser.getUserId();
        Worker nearbyWorker = workerRepository.save(Worker.builder()
                .user(nearbyUser)
                .fullName("Worker Nearby Bench")
                .kycStatus(KycStatus.APPROVED)
                .reliabilityScore(98)
                .isOnline(true)
                .baseLat(10.7769)
                .baseLng(106.7009)
                .minWage(BigDecimal.valueOf(25000))
                .build());
        for (int d = 1; d <= 7; d++) {
            workerAvailabilityRepository.save(WorkerAvailability.builder()
                    .worker(nearbyWorker)
                    .dayOfWeek(d)
                    .startTime(LocalTime.of(0, 0))
                    .endTime(LocalTime.of(23, 59))
                    .isAvailable(true)
                    .build());
        }
        for (int s = 0; s < 2; s++) {
            workerSkillRepository.save(WorkerSkill.builder()
                    .worker(nearbyWorker)
                    .skill(skills.get(s))
                    .proficiency(ExperienceLevel.INTERMEDIATE)
                    .verificationTier(VerificationTier.PLATFORM_PROVEN)
                    .build());
        }
    }

    @Test
    void measureEndpointsPerformance_With45WorkersAnd40Shifts() throws Exception {
        SessionFactory sf = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();
        stats.setStatisticsEnabled(true);

        List<BenchmarkRow> rows = List.of(
                benchmarkEndpoint("1. EO Shift Attendance (45 workers)",
                        "/api/v1/eo/events/shifts/" + primaryShiftId + "/attendance", eoToken, stats),
                benchmarkEndpoint("2. EO Event Workers (79 assignments)",
                        "/api/v1/eo/events/" + benchmarkEventId + "/workers", eoToken, stats),
                benchmarkEndpoint("3. Worker Assignments (35 items)",
                        "/api/v1/workers/assignments", workerHistoryToken, stats),
                benchmarkEndpoint("4. Worker Proposals (34 items)",
                        "/api/v1/workers/proposals", workerHistoryToken, stats),
                benchmarkEndpoint("5. Worker Nearby Shifts / Eligibility (40 shifts)",
                        "/api/v1/workers/shifts/nearby?lat=10.7769&lng=106.7009&radiusKm=15", workerNearbyToken, stats),
                benchmarkEndpoint("6. Admin Shifts List (40 shifts in DB)",
                        "/api/v1/admin/shifts?page=0&size=15", adminToken, stats),
                benchmarkEndpoint("7. Admin Shift Detail (45 workers)",
                        "/api/v1/admin/shifts/" + primaryShiftId, adminToken, stats)
        );

        System.out.println("\n================ BENCHMARK REPORT (Hikari max=5, 45 workers / 40 shifts) ================");
        System.out.printf("%-50s | %7s | %8s | %9s | %9s | %11s%n",
                "Endpoint", "SQL/req", "Seq p50", "Seq p95", "Conc p95", "Hikari Acq");
        System.out.println("-----------------------------------------------------------------------------------------------------------------");
        for (BenchmarkRow r : rows) {
            System.out.printf("%-50s | %7d | %6.1fms | %7.1fms | %7.1fms | %6.2fms (%d)%n",
                    r.name, r.sqlPerRequest, r.seqP50Ms, r.seqP95Ms, r.concP95Ms, r.hikariAcquireTotalMs, r.hikariAcquireCount);
            assertTrue(r.sqlPerRequest > 0);
        }
        System.out.println("=================================================================================================================\n");
    }

    private BenchmarkRow benchmarkEndpoint(String name, String url, String token, Statistics stats) throws Exception {
        // Warm up 3x
        for (int i = 0; i < 3; i++) {
            MvcResult res = mockMvc.perform(get(url).header("Authorization", "Bearer " + token)).andReturn();
            assertEquals(200, res.getResponse().getStatus(), "Failed warmup on " + url + ": " + res.getResponse().getContentAsString());
        }

        // Single-request exact SQL count (average of 3 runs)
        stats.clear();
        long totalSql = 0;
        for (int i = 0; i < 3; i++) {
            long before = stats.getPrepareStatementCount();
            MvcResult res = mockMvc.perform(get(url).header("Authorization", "Bearer " + token)).andReturn();
            assertEquals(200, res.getResponse().getStatus());
            long after = stats.getPrepareStatementCount();
            totalSql += (after - before);
        }
        long sqlPerReq = Math.round((double) totalSql / 3.0);

        // Sequential 15 requests -> p50 / p95
        List<Double> seqTimes = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            long t0 = System.nanoTime();
            MvcResult res = mockMvc.perform(get(url).header("Authorization", "Bearer " + token)).andReturn();
            assertEquals(200, res.getResponse().getStatus());
            seqTimes.add((System.nanoTime() - t0) / 1_000_000.0);
        }
        Collections.sort(seqTimes);
        double seqP50 = percentile(seqTimes, 50);
        double seqP95 = percentile(seqTimes, 95);

        // Concurrent 8 threads x 24 requests on Hikari pool max=5
        Timer hikariAcquireTimer = meterRegistry.find("hikaricp.connections.acquire").timer();
        long acqCountBefore = hikariAcquireTimer != null ? hikariAcquireTimer.count() : 0;
        double acqTimeBeforeMs = hikariAcquireTimer != null ? hikariAcquireTimer.totalTime(TimeUnit.MILLISECONDS) : 0.0;

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<Double>> tasks = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            tasks.add(() -> {
                long t0 = System.nanoTime();
                MvcResult res = mockMvc.perform(get(url).header("Authorization", "Bearer " + token)).andReturn();
                assertEquals(200, res.getResponse().getStatus());
                return (System.nanoTime() - t0) / 1_000_000.0;
            });
        }
        List<Future<Double>> futures = pool.invokeAll(tasks);
        List<Double> concTimes = new ArrayList<>();
        for (Future<Double> f : futures) {
            concTimes.add(f.get());
        }
        pool.shutdown();
        Collections.sort(concTimes);
        double concP95 = percentile(concTimes, 95);

        long acqCountAfter = hikariAcquireTimer != null ? hikariAcquireTimer.count() : 0;
        double acqTimeAfterMs = hikariAcquireTimer != null ? hikariAcquireTimer.totalTime(TimeUnit.MILLISECONDS) : 0.0;

        return new BenchmarkRow(
                name,
                sqlPerReq,
                seqP50,
                seqP95,
                concP95,
                Math.max(0.0, acqTimeAfterMs - acqTimeBeforeMs),
                Math.max(0L, acqCountAfter - acqCountBefore)
        );
    }

    private static double percentile(List<Double> sorted, int pct) {
        if (sorted.isEmpty()) return 0.0;
        int idx = (int) Math.ceil((pct / 100.0) * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    private record BenchmarkRow(
            String name,
            long sqlPerRequest,
            double seqP50Ms,
            double seqP95Ms,
            double concP95Ms,
            double hikariAcquireTotalMs,
            long hikariAcquireCount
    ) {}
}
