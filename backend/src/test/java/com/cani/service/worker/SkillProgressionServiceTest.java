package com.cani.service.worker;

import com.cani.entity.Shift;
import com.cani.entity.Skill;
import com.cani.entity.Worker;
import com.cani.entity.WorkerSkill;
import com.cani.entity.WorkerSkillPoint;
import com.cani.entity.enums.SkillTier;
import com.cani.entity.enums.VerificationTier;
import com.cani.repository.SkillRepository;
import com.cani.repository.WorkerRepository;
import com.cani.repository.WorkerSkillPointRepository;
import com.cani.repository.WorkerSkillRepository;
import com.cani.service.SkillProgressionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SkillProgressionService Unit Tests")
class SkillProgressionServiceTest {

    @Mock
    private WorkerSkillPointRepository workerSkillPointRepository;
    @Mock
    private WorkerSkillRepository workerSkillRepository;
    @Mock
    private SkillRepository skillRepository;
    @Mock
    private WorkerRepository workerRepository;

    @InjectMocks
    private SkillProgressionService skillProgressionService;

    private Worker worker;
    private Skill skill;
    private Shift shift;

    @BeforeEach
    void setUp() {
        UUID workerId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        worker = Worker.builder()
                .workerId(workerId)
                .fullName("Phạm Duy Hưng")
                .skillTier(SkillTier.STARTER)
                .build();
        skill = Skill.builder()
                .skillId(skillId)
                .name("Phục vụ bàn")
                .isActive(true)
                .build();
        shift = Shift.builder()
                .shiftId(UUID.randomUUID())
                .jobType("Phục vụ bàn")
                .requiredSkills(new UUID[]{skillId})
                .build();
    }

    @Test
    @DisplayName("Hoàn thành ca tạo/cập nhật WorkerSkillPoint (+20 SP_experience) và không cộng trùng giữa jobType & requiredSkills")
    void recordCompletedShift_incrementsExperienceOnce() {
        when(skillRepository.findAllById(any())).thenReturn(List.of(skill));
        when(workerSkillPointRepository.findByWorker_WorkerIdAndJobType(worker.getWorkerId(), "Phục vụ bàn"))
                .thenReturn(Optional.empty());
        when(workerSkillPointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        skillProgressionService.recordCompletedShift(worker, shift);

        ArgumentCaptor<WorkerSkillPoint> captor = ArgumentCaptor.forClass(WorkerSkillPoint.class);
        verify(workerSkillPointRepository, times(1)).save(captor.capture());

        WorkerSkillPoint saved = captor.getValue();
        assertEquals("Phục vụ bàn", saved.getJobType());
        assertEquals(1, saved.getTotalShiftsCompleted());
        assertEquals(20, saved.getSpExperience());
        assertEquals(20, saved.getTotalSp());
        assertEquals(SkillTier.STARTER, saved.getTier());
    }

    @Test
    @DisplayName("Đạt 3 ca và đánh giá >= 4.5 sao -> Tự động thăng cấp Tier 3 PLATFORM_PROVEN (+50 SP_verify) & nâng hạng BRONZE")
    void recordShiftRating_promotesToPlatformProvenAndBronzeTier() {
        WorkerSkillPoint existingPoint = WorkerSkillPoint.builder()
                .worker(worker)
                .jobType("Phục vụ bàn")
                .spVerify(0)
                .spExperience(60)
                .spRating(30)
                .totalSp(90)
                .tier(SkillTier.STARTER)
                .totalShiftsCompleted(3)
                .avgRating(new BigDecimal("4.80"))
                .build();

        WorkerSkill existingWs = WorkerSkill.builder()
                .worker(worker)
                .skill(skill)
                .verificationTier(VerificationTier.SELF_DECLARED)
                .spVerifyAwarded(0)
                .build();

        when(skillRepository.findAllById(any())).thenReturn(List.of(skill));
        when(workerSkillPointRepository.findByWorker_WorkerIdAndJobType(worker.getWorkerId(), "Phục vụ bàn"))
                .thenReturn(Optional.of(existingPoint));
        when(workerSkillRepository.findByWorker_WorkerIdAndSkill_SkillId(worker.getWorkerId(), skill.getSkillId()))
                .thenReturn(Optional.of(existingWs));
        when(workerSkillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(workerSkillPointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        skillProgressionService.recordShiftRating(worker, shift, 5);

        assertEquals(VerificationTier.PLATFORM_PROVEN, existingWs.getVerificationTier());
        assertEquals(50, existingWs.getSpVerifyAwarded());
        assertNotNull(existingWs.getVerifiedAt());

        // spVerify (50) + spExperience (60) + spRating (30 + 15 = 45) = 155 -> BRONZE
        assertEquals(50, existingPoint.getSpVerify());
        assertEquals(45, existingPoint.getSpRating());
        assertEquals(155, existingPoint.getTotalSp());
        assertEquals(SkillTier.BRONZE, existingPoint.getTier());
        assertEquals(SkillTier.BRONZE, worker.getSkillTier());
    }

    @Test
    @DisplayName("Practical Validation V2: Không nâng cấp Tier 3 PLATFORM_PROVEN nếu tỷ lệ hủy ca cao (< 85% hoàn thành)")
    void recordShiftRating_highCancellationRate_blocksPlatformProven() {
        WorkerSkillPoint existingPoint = WorkerSkillPoint.builder()
                .worker(worker)
                .jobType("Phục vụ bàn")
                .spVerify(0)
                .spExperience(60)
                .spRating(30)
                .totalSp(90)
                .tier(SkillTier.STARTER)
                .totalShiftsCompleted(3)
                .totalShiftsCancelled(3) // 50% completion rate < 85%
                .avgRating(new BigDecimal("5.00"))
                .build();

        when(skillRepository.findAllById(any())).thenReturn(List.of(skill));
        when(workerSkillPointRepository.findByWorker_WorkerIdAndJobType(worker.getWorkerId(), "Phục vụ bàn"))
                .thenReturn(Optional.of(existingPoint));
        when(workerSkillPointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        skillProgressionService.recordShiftRating(worker, shift, 5);

        verify(workerSkillRepository, never()).save(any(WorkerSkill.class));
        assertEquals(0, existingPoint.getSpVerify());
    }

    @Test
    @DisplayName("Practical Validation V2: Không nâng cấp Tier 3 PLATFORM_PROVEN nếu điểm uy tín thấp hoặc có No-Show")
    void recordShiftRating_lowReliabilityOrNoShow_blocksPlatformProven() {
        worker.setReliabilityScore(55);
        worker.setTotalNoshows(2);

        WorkerSkillPoint existingPoint = WorkerSkillPoint.builder()
                .worker(worker)
                .jobType("Phục vụ bàn")
                .spVerify(0)
                .spExperience(60)
                .spRating(30)
                .totalSp(90)
                .tier(SkillTier.STARTER)
                .totalShiftsCompleted(3)
                .totalShiftsCancelled(0)
                .avgRating(new BigDecimal("5.00"))
                .build();

        when(skillRepository.findAllById(any())).thenReturn(List.of(skill));
        when(workerSkillPointRepository.findByWorker_WorkerIdAndJobType(worker.getWorkerId(), "Phục vụ bàn"))
                .thenReturn(Optional.of(existingPoint));
        when(workerSkillPointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        skillProgressionService.recordShiftRating(worker, shift, 5);

        verify(workerSkillRepository, never()).save(any(WorkerSkill.class));
        assertEquals(0, existingPoint.getSpVerify());
    }
}
