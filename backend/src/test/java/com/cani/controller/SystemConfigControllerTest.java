package com.cani.controller;

import com.cani.dto.admin.config.BulkUpdateSystemConfigRequestDto;
import com.cani.dto.admin.config.SystemConfigResponseDto;
import com.cani.dto.admin.config.UpdateSystemConfigRequestDto;
import com.cani.exception.GlobalExceptionHandler;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.UserRepository;
import com.cani.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {SystemConfigController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(SystemConfigControllerTest.MethodSecurityTestConfig.class)
class SystemConfigControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SystemConfigService systemConfigService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /api/v1/admin/configs - Lấy danh sách thành công với ROLE_ADMIN")
    @WithMockUser(roles = "ADMIN")
    void testGetAllConfigs_Admin_Success() throws Exception {
        SystemConfigResponseDto dto = SystemConfigResponseDto.builder()
                .id(UUID.randomUUID())
                .key("commission_rate")
                .value("0.00")
                .description("Tỷ lệ hoa hồng")
                .updatedAt(Instant.now())
                .build();

        when(systemConfigService.getAllConfigs()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/admin/configs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy danh sách cấu hình hệ thống thành công"))
                .andExpect(jsonPath("$.data[0].key").value("commission_rate"))
                .andExpect(jsonPath("$.data[0].value").value("0.00"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/configs/{key} - Lấy chi tiết thành công với ROLE_ADMIN")
    @WithMockUser(roles = "ADMIN")
    void testGetConfigByKey_Admin_Success() throws Exception {
        SystemConfigResponseDto dto = SystemConfigResponseDto.builder()
                .id(UUID.randomUUID())
                .key("support_email")
                .value("hotro@cani.vn")
                .description("Email hỗ trợ")
                .updatedAt(Instant.now())
                .build();

        when(systemConfigService.getConfigByKey("support_email")).thenReturn(dto);

        mockMvc.perform(get("/api/v1/admin/configs/support_email"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.key").value("support_email"))
                .andExpect(jsonPath("$.data.value").value("hotro@cani.vn"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/configs/{key} - Key không tồn tại trả về 404 Not Found")
    @WithMockUser(roles = "ADMIN")
    void testGetConfigByKey_NotFound() throws Exception {
        when(systemConfigService.getConfigByKey("unknown_param"))
                .thenThrow(new ResourceNotFoundException("Cấu hình hệ thống", "key", "unknown_param"));

        mockMvc.perform(get("/api/v1/admin/configs/unknown_param"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("unknown_param")));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/configs/{key} - Cập nhật giá trị thành công với ROLE_ADMIN")
    @WithMockUser(roles = "ADMIN")
    void testUpdateConfig_Admin_Success() throws Exception {
        UpdateSystemConfigRequestDto request = UpdateSystemConfigRequestDto.builder()
                .value("0.05")
                .description("Cập nhật tỷ lệ hoa hồng 5%")
                .build();

        SystemConfigResponseDto updatedDto = SystemConfigResponseDto.builder()
                .id(UUID.randomUUID())
                .key("commission_rate")
                .value("0.05")
                .description("Cập nhật tỷ lệ hoa hồng 5%")
                .updatedAt(Instant.now())
                .build();

        when(systemConfigService.updateConfig(eq("commission_rate"), any(UpdateSystemConfigRequestDto.class), any()))
                .thenReturn(updatedDto);

        mockMvc.perform(put("/api/v1/admin/configs/commission_rate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật cấu hình hệ thống thành công"))
                .andExpect(jsonPath("$.data.value").value("0.05"));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/configs/{key} - Request validation error khi value trống trả về 400 Bad Request")
    @WithMockUser(roles = "ADMIN")
    void testUpdateConfig_ValidationError() throws Exception {
        UpdateSystemConfigRequestDto invalidRequest = UpdateSystemConfigRequestDto.builder()
                .value("") // trống
                .build();

        mockMvc.perform(put("/api/v1/admin/configs/commission_rate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Dữ liệu đầu vào không hợp lệ"));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/configs - Cập nhật hàng loạt thành công với ROLE_ADMIN")
    @WithMockUser(roles = "ADMIN")
    void testBulkUpdateConfigs_Admin_Success() throws Exception {
        BulkUpdateSystemConfigRequestDto request = BulkUpdateSystemConfigRequestDto.builder()
                .configs(Map.of("commission_rate", "0.08", "min_registration_age", "19"))
                .build();

        SystemConfigResponseDto dto1 = SystemConfigResponseDto.builder()
                .id(UUID.randomUUID())
                .key("commission_rate")
                .value("0.08")
                .build();

        SystemConfigResponseDto dto2 = SystemConfigResponseDto.builder()
                .id(UUID.randomUUID())
                .key("min_registration_age")
                .value("19")
                .build();

        when(systemConfigService.bulkUpdateConfigs(any(BulkUpdateSystemConfigRequestDto.class), any()))
                .thenReturn(List.of(dto1, dto2));

        mockMvc.perform(put("/api/v1/admin/configs")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật hàng loạt cấu hình hệ thống thành công"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].value").value("0.08"))
                .andExpect(jsonPath("$.data[1].value").value("19"));
    }

    @Test
    @DisplayName("Phân quyền bảo mật: Người dùng vai trò WORKER bị từ chối truy cập (403 Forbidden)")
    @WithMockUser(roles = "WORKER")
    void testSecurity_WorkerRole_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/configs"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập tài nguyên này"));
    }

    @Test
    @DisplayName("Phân quyền bảo mật: Người dùng vai trò EVENT_ORGANIZER bị từ chối cập nhật (403 Forbidden)")
    @WithMockUser(roles = "EVENT_ORGANIZER")
    void testSecurity_EventOrganizerRole_Forbidden() throws Exception {
        UpdateSystemConfigRequestDto request = UpdateSystemConfigRequestDto.builder()
                .value("0.05")
                .build();

        mockMvc.perform(put("/api/v1/admin/configs/commission_rate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập tài nguyên này"));
    }
}

