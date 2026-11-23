package com.cani.controller;

import com.cani.dto.admin.review.AdminModerateReviewRequestDto;
import com.cani.dto.admin.review.AdminReviewResponseDto;
import com.cani.entity.User;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.UserRepository;
import com.cani.service.AdminReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminReviewService adminReviewService;

    @MockitoBean
    private UserRepository userRepository;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(createAdminAuthToken());
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private UsernamePasswordAuthenticationToken createAdminAuthToken() {
        User adminUser = User.builder()
                .userId(UUID.randomUUID())
                .email("admin@cani.vn")
                .fullName("System Admin")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        return new UsernamePasswordAuthenticationToken(
                adminUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    @Test
    @DisplayName("GET /api/v1/admin/reviews - Lấy danh sách reviews thành công")
    void testGetReviews_Success() throws Exception {
        UUID reviewId = UUID.randomUUID();
        AdminReviewResponseDto dto = AdminReviewResponseDto.builder()
                .reviewId(reviewId)
                .rating(5)
                .comment("Đánh giá tốt")
                .reviewerName("EO ABC")
                .revieweeName("Worker XYZ")
                .isHidden(false)
                .build();

        when(adminReviewService.getReviews(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/api/v1/admin/reviews")
                        .with(authentication(createAdminAuthToken()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].comment").value("Đánh giá tốt"))
                .andExpect(jsonPath("$.data.content[0].rating").value(5));
    }

    @Test
    @DisplayName("POST /api/v1/admin/reviews/{id}/hide - Ẩn review thành công")
    void testHideReview_Success() throws Exception {
        UUID reviewId = UUID.randomUUID();
        AdminModerateReviewRequestDto request = AdminModerateReviewRequestDto.builder()
                .reason("Spam đánh giá sai lệch")
                .build();

        AdminReviewResponseDto dto = AdminReviewResponseDto.builder()
                .reviewId(reviewId)
                .isHidden(true)
                .hiddenReason("Spam đánh giá sai lệch")
                .build();

        when(adminReviewService.hideReview(any(), eq(reviewId), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/admin/reviews/{id}/hide", reviewId)
                        .with(authentication(createAdminAuthToken()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isHidden").value(true))
                .andExpect(jsonPath("$.data.hiddenReason").value("Spam đánh giá sai lệch"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/reviews/{id}/flag - Gắn cờ review thành công")
    void testFlagReview_Success() throws Exception {
        UUID reviewId = UUID.randomUUID();
        AdminModerateReviewRequestDto request = AdminModerateReviewRequestDto.builder()
                .reason("Nghi vấn bơm điểm")
                .build();

        AdminReviewResponseDto dto = AdminReviewResponseDto.builder()
                .reviewId(reviewId)
                .isFlagged(true)
                .flagReason("Nghi vấn bơm điểm")
                .build();

        when(adminReviewService.flagReview(any(), eq(reviewId), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/admin/reviews/{id}/flag", reviewId)
                        .with(authentication(createAdminAuthToken()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isFlagged").value(true));
    }
}
