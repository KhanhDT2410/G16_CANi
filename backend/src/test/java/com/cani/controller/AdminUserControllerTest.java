package com.cani.controller;

import com.cani.dto.admin.user.UpdateUserStatusRequestDto;
import com.cani.dto.admin.user.UserDetailResponseDto;
import com.cani.dto.admin.user.UserFilterRequest;
import com.cani.dto.admin.user.UserSummaryResponseDto;
import com.cani.dto.response.PageResponse;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.repository.UserRepository;
import com.cani.service.AdminUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /api/v1/admin/users - Lấy danh sách thành công")
    @WithMockUser(roles = "ADMIN")
    void testGetUsers_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        UserSummaryResponseDto summaryDto = UserSummaryResponseDto.builder()
                .id(userId)
                .email("test@cani.vn")
                .phone("0987654321")
                .fullName("Test User")
                .role(UserRole.WORKER)
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        PageResponse<UserSummaryResponseDto> pageResponse = PageResponse.<UserSummaryResponseDto>builder()
                .content(List.of(summaryDto))
                .pageNumber(0)
                .pageSize(10)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(adminUserService.getUsers(any(UserFilterRequest.class), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("search", "Test")
                        .param("role", "WORKER")
                        .param("status", "ACTIVE")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].email").value("test@cani.vn"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/admin/users/{id} - Lấy chi tiết user thành công")
    @WithMockUser(roles = "ADMIN")
    void testGetUserById_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        UserDetailResponseDto detailDto = UserDetailResponseDto.builder()
                .id(userId)
                .email("admin@cani.vn")
                .fullName("Admin User")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        when(adminUserService.getUserById(userId)).thenReturn(detailDto);

        mockMvc.perform(get("/api/v1/admin/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("admin@cani.vn"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/users/{id}/status - Cập nhật trạng thái thành công")
    @WithMockUser(roles = "ADMIN")
    void testUpdateUserStatus_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        UpdateUserStatusRequestDto request = UpdateUserStatusRequestDto.builder()
                .status(UserStatus.LOCKED)
                .reason("Vi phạm chính sách nền tảng")
                .build();

        UserDetailResponseDto updatedDto = UserDetailResponseDto.builder()
                .id(userId)
                .email("worker@cani.vn")
                .fullName("Worker User")
                .role(UserRole.WORKER)
                .status(UserStatus.LOCKED)
                .build();

        when(adminUserService.updateUserStatus(eq(userId), any(), any(UpdateUserStatusRequestDto.class)))
                .thenReturn(updatedDto);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", userId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("LOCKED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/users/{id}/status - Dữ liệu không hợp lệ trả về 400 Bad Request")
    @WithMockUser(roles = "ADMIN")
    void testUpdateUserStatus_ValidationError() throws Exception {
        UUID userId = UUID.randomUUID();
        UpdateUserStatusRequestDto invalidRequest = UpdateUserStatusRequestDto.builder()
                .status(null) // Thiếu status
                .reason("")   // Lý do trống
                .build();

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", userId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Dữ liệu đầu vào không hợp lệ"));
    }
}
