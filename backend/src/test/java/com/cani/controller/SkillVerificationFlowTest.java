package com.cani.controller;

import com.cani.dto.auth.LoginRequest;
import com.cani.dto.auth.LoginResponse;
import com.cani.dto.auth.UserDto;
import com.cani.dto.skill.*;
import com.cani.entity.Skill;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.enums.RoutingDecision;
import com.cani.entity.enums.SkillTier;
import com.cani.entity.enums.SkillVerificationStatus;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.SkillRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerRepository;
import com.cani.security.RestAccessDeniedHandler;
import com.cani.security.RestAuthenticationEntryPoint;
import com.cani.security.UserAuthorityFilter;
import com.cani.service.AuthService;
import com.cani.service.SkillVerificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.context.annotation.Import;
import com.cani.config.SecurityConfig;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, SkillVerificationController.class})
@Import({SecurityConfig.class, UserAuthorityFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class SkillVerificationFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private SkillVerificationService verificationService;

    @MockitoBean
    private WorkerRepository workerRepository;

    @MockitoBean
    private SkillRepository skillRepository;

    @MockitoBean
    private UserRepository userRepository;

    private User workerUser;
    private User adminUser;
    private Worker workerEntity;
    private Skill mockSkill;
    private UUID workerId;
    private UUID skillId;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        workerId = UUID.randomUUID();
        skillId = UUID.randomUUID();

        workerUser = User.builder()
                .userId(userId)
                .email("worker@cani.vn")
                .fullName("Nguyen Van An")
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();

        adminUser = User.builder()
                .userId(UUID.randomUUID())
                .email("admin@cani.vn")
                .fullName("Admin CANi")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();

        workerEntity = Worker.builder()
                .workerId(workerId)
                .user(workerUser)
                .fullName(workerUser.getFullName())
                .skillTier(SkillTier.STARTER)
                .build();

        mockSkill = Skill.builder()
                .skillId(skillId)
                .name("Pha chế (Bartender/Barista)")
                .category("F&B")
                .isActive(true)
                .build();

        when(workerRepository.findByUser_UserId(workerUser.getUserId()))
                .thenReturn(Optional.of(workerEntity));

        when(verificationService.resolveWorkerId(any(User.class), any()))
                .thenReturn(workerId);
        when(verificationService.resolveWorkerId(eq(null), any()))
                .thenAnswer(inv -> inv.getArgument(1));
        when(verificationService.getTaxonomy())
                .thenReturn(List.of(mockSkill));
    }

    private UsernamePasswordAuthenticationToken createAuthToken(User user) {
        return new UsernamePasswordAuthenticationToken(
                user,
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    @Test
    @DisplayName("Step 1: Worker Login - POST /api/auth/login returns JWT session and worker profile")
    void step1_workerLogin_Success() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .email("worker@cani.vn")
                .password("Password123!")
                .build();

        LoginResponse mockLoginResponse = LoginResponse.builder()
                .token("mock-jwt-token-worker")
                .refreshToken("mock-refresh-token")
                .expiresIn(3600L)
                .user(UserDto.builder()
                        .id(workerUser.getUserId())
                        .email(workerUser.getEmail())
                        .fullName(workerUser.getFullName())
                        .role(workerUser.getRole())
                        .status(workerUser.getStatus())
                        .build())
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(mockLoginResponse);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đăng nhập thành công"))
                .andExpect(jsonPath("$.data.token").value("mock-jwt-token-worker"))
                .andExpect(jsonPath("$.data.user.email").value("worker@cani.vn"))
                .andExpect(jsonPath("$.data.user.role").value("WORKER"));
    }

    @Test
    @DisplayName("Step 2: Get Skill Taxonomy - GET /api/v1/workers/skills/taxonomy is public and returns skills")
    void step2_fetchSkillTaxonomy_Public() throws Exception {
        when(skillRepository.findByIsActiveTrue()).thenReturn(List.of(mockSkill));

        mockMvc.perform(get("/api/v1/workers/skills/taxonomy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Pha chế (Bartender/Barista)"))
                .andExpect(jsonPath("$.data[0].category").value("F&B"));
    }

    @Test
    @DisplayName("Step 2b: Get Tier Config - GET /api/v1/workers/skills/tiers/config is public and returns BR-64 thresholds")
    void step2b_fetchTierConfigs_Public() throws Exception {
        when(verificationService.getTierConfigs()).thenReturn(List.of(
                TierConfigDto.builder().tier(SkillTier.STARTER).minSp(0).nextTierMinSp(100).build(),
                TierConfigDto.builder().tier(SkillTier.BRONZE).minSp(100).nextTierMinSp(200).build()
        ));

        mockMvc.perform(get("/api/v1/workers/skills/tiers/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].tier").value("STARTER"))
                .andExpect(jsonPath("$.data[0].minSp").value(0))
                .andExpect(jsonPath("$.data[1].tier").value("BRONZE"))
                .andExpect(jsonPath("$.data[1].minSp").value(100));
    }

    @Test
    @DisplayName("Step 2c: Worker Declares Skills (Tier 1) - POST /api/v1/workers/skills/declare saves self-declared skills")
    void step2c_declareSkills_Tier1() throws Exception {
        DeclareSkillRequestDto request = DeclareSkillRequestDto.builder()
                .skills(List.of(
                        DeclareSkillRequestDto.SkillItem.builder()
                                .skillId(skillId)
                                .proficiency(com.cani.entity.enums.ExperienceLevel.INTERMEDIATE)
                                .build()
                ))
                .build();

        WorkerSkillResponseDto dto = WorkerSkillResponseDto.builder()
                .id(UUID.randomUUID())
                .skillId(skillId)
                .skillName("Pha chế (Bartender/Barista)")
                .proficiency(com.cani.entity.enums.ExperienceLevel.INTERMEDIATE)
                .verificationTier(com.cani.entity.enums.VerificationTier.SELF_DECLARED)
                .spVerifyAwarded(0)
                .build();

        when(verificationService.declareSkills(eq(workerId), any())).thenReturn(List.of(dto));

        mockMvc.perform(post("/api/v1/workers/skills/declare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf())
                        .with(authentication(createAuthToken(workerUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].skillId").value(skillId.toString()))
                .andExpect(jsonPath("$.data[0].verificationTier").value("SELF_DECLARED"))
                .andExpect(jsonPath("$.data[0].spVerifyAwarded").value(0));
    }

    @Test
    @DisplayName("Step 3: Worker Submits Certificate - POST /api/v1/workers/skills/verify triggers AI verification and awards +25 SP")
    void step3_workerSubmitsCertificate_AutoApproved() throws Exception {
        MockMultipartFile certFile = new MockMultipartFile(
                "file",
                "bartender_cert.png",
                MediaType.IMAGE_PNG_VALUE,
                "fake-certificate-binary-content".getBytes()
        );

        ExtractedSkillDto extractedSkill = ExtractedSkillDto.builder()
                .skillName("Bartender Course")
                .taxonomyRole("Pha chế (Bartender/Barista)")
                .confidenceScore(0.96)
                .evidenceSnippet("Completed 40 hours intensive Bartender Masterclass with Excellence")
                .build();

        SkillVerificationResponseDto responseDto = SkillVerificationResponseDto.builder()
                .id(UUID.randomUUID())
                .workerId(workerId)
                .skillId(skillId)
                .skillName("Pha chế (Bartender/Barista)")
                .jobType("Pha chế (Bartender/Barista)")
                .recipientName("Nguyen Van An")
                .institution("Saigon Bartender Academy")
                .issueDate("15/08/2024")
                .hasSealOrSignature(true)
                .overallConfidence(0.96)
                .routingDecision(RoutingDecision.AUTO_APPROVE)
                .routingReason("Chứng chỉ hợp lệ, đầy đủ con dấu và học phần chuyên môn")
                .status(SkillVerificationStatus.APPROVED)
                .spAwarded(25)
                .currentTotalSp(25)
                .currentTier(SkillTier.STARTER)
                .createdAt(Instant.now())
                .extractedSkills(List.of(extractedSkill))
                .build();

        when(verificationService.submitVerification(eq(workerId), any(), eq(skillId), eq("Gửi chứng chỉ tốt nghiệp")))
                .thenReturn(responseDto);

        mockMvc.perform(multipart("/api/v1/workers/skills/verify")
                        .file(certFile)
                        .param("skillId", skillId.toString())
                        .param("notes", "Gửi chứng chỉ tốt nghiệp")
                        .with(csrf())
                        .with(authentication(createAuthToken(workerUser))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Xác thực AI thành công! Kỹ năng đã được phê duyệt và cộng SP_verify."))
                .andExpect(jsonPath("$.data.workerId").value(workerId.toString()))
                .andExpect(jsonPath("$.data.routingDecision").value("AUTO_APPROVE"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.spAwarded").value(25))
                .andExpect(jsonPath("$.data.currentTotalSp").value(25))
                .andExpect(jsonPath("$.data.extractedSkills[0].taxonomyRole").value("Pha chế (Bartender/Barista)"))
                .andExpect(jsonPath("$.data.hasSealOrSignature").value(true));
    }

    @Test
    @DisplayName("Step 4: Worker Queries History - GET /api/v1/workers/skills/history returns verification list")
    void step4_getWorkerHistory_ReturnsList() throws Exception {
        SkillVerificationResponseDto item = SkillVerificationResponseDto.builder()
                .id(UUID.randomUUID())
                .workerId(workerId)
                .skillName("Pha chế (Bartender/Barista)")
                .jobType("Pha chế (Bartender/Barista)")
                .routingDecision(RoutingDecision.AUTO_APPROVE)
                .status(SkillVerificationStatus.APPROVED)
                .spAwarded(25)
                .overallConfidence(0.96)
                .build();

        when(verificationService.getWorkerVerificationHistory(workerId)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/workers/skills/history")
                        .with(authentication(createAuthToken(workerUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].workerId").value(workerId.toString()))
                .andExpect(jsonPath("$.data[0].spAwarded").value(25))
                .andExpect(jsonPath("$.data[0].status").value("APPROVED"));
    }

    @Test
    @DisplayName("Step 5: Worker Queries Skill Points - GET /api/v1/workers/skills/points returns SP breakdown")
    void step5_getWorkerPoints_ReturnsPoints() throws Exception {
        WorkerSkillPointDto pointDto = WorkerSkillPointDto.builder()
                .id(UUID.randomUUID())
                .jobType("Pha chế (Bartender/Barista)")
                .spVerify(25)
                .spExperience(0)
                .spRating(0)
                .totalSp(25)
                .tier(SkillTier.STARTER)
                .build();

        when(verificationService.getWorkerSkillPoints(workerId)).thenReturn(List.of(pointDto));

        mockMvc.perform(get("/api/v1/workers/skills/points")
                        .with(authentication(createAuthToken(workerUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].jobType").value("Pha chế (Bartender/Barista)"))
                .andExpect(jsonPath("$.data[0].spVerify").value(25))
                .andExpect(jsonPath("$.data[0].totalSp").value(25));
    }

    @Test
    @DisplayName("Step 6: Security Boundary - Worker cannot call adminReview (403 Forbidden)")
    void step6_adminReview_ForbiddenForWorker() throws Exception {
        UUID verificationId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/workers/skills/admin/" + verificationId + "/review")
                        .param("approved", "true")
                        .with(csrf())
                        .with(authentication(createAuthToken(workerUser))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Step 7: Admin Review - Admin user can approve medium-confidence submission")
    void step7_adminReview_SuccessForAdminUser() throws Exception {
        UUID verificationId = UUID.randomUUID();

        SkillVerificationResponseDto approvedResponse = SkillVerificationResponseDto.builder()
                .id(verificationId)
                .workerId(workerId)
                .status(SkillVerificationStatus.APPROVED)
                .spAwarded(25)
                .routingDecision(RoutingDecision.ADMIN_REVIEW)
                .routingReason("Admin đã xác minh con dấu mờ")
                .build();

        when(verificationService.adminReview(eq(verificationId), eq(adminUser.getUserId()), eq(true), eq("Admin đã xác minh con dấu mờ")))
                .thenReturn(approvedResponse);

        mockMvc.perform(post("/api/v1/workers/skills/admin/" + verificationId + "/review")
                        .param("approved", "true")
                        .param("reviewNotes", "Admin đã xác minh con dấu mờ")
                        .with(csrf())
                        .with(authentication(createAuthToken(adminUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Admin đã phê duyệt kỹ năng và cộng +25 SP_verify"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.spAwarded").value(25));
    }
}
