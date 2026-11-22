package com.cani.controller;

import com.cani.dto.admin.shift.AdminShiftActionRequestDto;
import com.cani.dto.admin.shift.AdminShiftDetailDto;
import com.cani.dto.admin.shift.AdminShiftSummaryDto;
import com.cani.entity.enums.ShiftStatus;
import com.cani.repository.UserRepository;
import com.cani.service.AdminShiftService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminShiftController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminShiftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminShiftService adminShiftService;

    @MockitoBean
    private UserRepository userRepository;

    private static final String ADMIN_UUID = "550e8400-e29b-41d4-a716-446655440000";

    @Test
    @DisplayName("GET /api/v1/admin/shifts - Lấy danh sách ca thành công")
    @WithMockUser(username = ADMIN_UUID, roles = "ADMIN")
    void testGetShifts_Success() throws Exception {
        AdminShiftSummaryDto summaryDto = AdminShiftSummaryDto.builder()
                .shiftId(UUID.randomUUID())
                .shiftCode("SH-001")
                .jobType("Bảo vệ")
                .status(ShiftStatus.ACTIVE)
                .build();

        when(adminShiftService.getShifts(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summaryDto)));

        mockMvc.perform(get("/api/v1/admin/shifts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].shiftCode").value("SH-001"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/shifts/{id} - Lấy chi tiết ca thành công")
    @WithMockUser(username = ADMIN_UUID, roles = "ADMIN")
    void testGetShiftDetail_Success() throws Exception {
        UUID shiftId = UUID.randomUUID();
        AdminShiftDetailDto detailDto = AdminShiftDetailDto.builder()
                .shiftId(shiftId)
                .shiftCode("SH-001")
                .jobType("Bảo vệ")
                .status(ShiftStatus.ACTIVE)
                .build();

        when(adminShiftService.getShiftDetail(shiftId)).thenReturn(detailDto);

        mockMvc.perform(get("/api/v1/admin/shifts/{id}", shiftId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shiftCode").value("SH-001"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/shifts/{id}/cancel - Hủy ca cưỡng chế thành công")
    @WithMockUser(username = ADMIN_UUID, roles = "ADMIN")
    void testCancelShift_Success() throws Exception {
        UUID shiftId = UUID.randomUUID();
        AdminShiftActionRequestDto request = new AdminShiftActionRequestDto("Vi phạm nghiêm trọng an toàn lao động");
        AdminShiftDetailDto detailDto = AdminShiftDetailDto.builder()
                .shiftId(shiftId)
                .status(ShiftStatus.CANCELLED)
                .cancellationReason(request.getReason())
                .build();

        when(adminShiftService.cancelShift(any(), eq(shiftId), eq(request.getReason()))).thenReturn(detailDto);

        mockMvc.perform(post("/api/v1/admin/shifts/{id}/cancel", shiftId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/shifts/{id}/suspend - Tạm đình chỉ ca thành công")
    @WithMockUser(username = ADMIN_UUID, roles = "ADMIN")
    void testSuspendShift_Success() throws Exception {
        UUID shiftId = UUID.randomUUID();
        AdminShiftActionRequestDto request = new AdminShiftActionRequestDto("Đang điều tra phản ánh từ người lao động");
        AdminShiftDetailDto detailDto = AdminShiftDetailDto.builder()
                .shiftId(shiftId)
                .status(ShiftStatus.SUSPENDED)
                .adminNote(request.getReason())
                .build();

        when(adminShiftService.suspendShift(any(), eq(shiftId), eq(request.getReason()))).thenReturn(detailDto);

        mockMvc.perform(post("/api/v1/admin/shifts/{id}/suspend", shiftId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/shifts/{id}/warn - Gửi cảnh báo thành công")
    @WithMockUser(username = ADMIN_UUID, roles = "ADMIN")
    void testWarnEo_Success() throws Exception {
        UUID shiftId = UUID.randomUUID();
        AdminShiftActionRequestDto request = new AdminShiftActionRequestDto("Vui lòng bổ sung bảo hộ lao động đúng cam kết");
        AdminShiftDetailDto detailDto = AdminShiftDetailDto.builder()
                .shiftId(shiftId)
                .adminNote(request.getReason())
                .build();

        when(adminShiftService.warnEo(any(), eq(shiftId), eq(request.getReason()))).thenReturn(detailDto);

        mockMvc.perform(post("/api/v1/admin/shifts/{id}/warn", shiftId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/admin/shifts/{id}/cancel - Lỗi khi lý do quá ngắn (< 5 ký tự)")
    @WithMockUser(username = ADMIN_UUID, roles = "ADMIN")
    void testCancelShift_ValidationError() throws Exception {
        UUID shiftId = UUID.randomUUID();
        AdminShiftActionRequestDto request = new AdminShiftActionRequestDto("abc");

        mockMvc.perform(post("/api/v1/admin/shifts/{id}/cancel", shiftId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
