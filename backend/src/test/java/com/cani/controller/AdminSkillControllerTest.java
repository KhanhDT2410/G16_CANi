package com.cani.controller;

import com.cani.dto.skill.AdminSkillRequestDto;
import com.cani.dto.skill.AdminSkillResponseDto;
import com.cani.repository.UserRepository;
import com.cani.service.AdminSkillService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminSkillController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminSkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminSkillService adminSkillService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /api/v1/admin/skills - Lấy danh sách kỹ năng thành công")
    @WithMockUser(roles = "ADMIN")
    void testGetSkills_Success() throws Exception {
        UUID skillId = UUID.randomUUID();
        AdminSkillResponseDto dto = AdminSkillResponseDto.builder()
                .skillId(skillId)
                .name("Phục vụ bàn")
                .category("F&B")
                .description("Phục vụ món ăn")
                .isActive(true)
                .createdAt(Instant.now())
                .build();

        when(adminSkillService.getSkills(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/api/v1/admin/skills")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Phục vụ bàn"))
                .andExpect(jsonPath("$.data.content[0].category").value("F&B"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/skills - Tạo mới kỹ năng chuẩn")
    @WithMockUser(roles = "ADMIN")
    void testCreateSkill_Success() throws Exception {
        UUID skillId = UUID.randomUUID();
        AdminSkillRequestDto request = AdminSkillRequestDto.builder()
                .name("Chụp ảnh sự kiện")
                .category("Media")
                .description("Chụp ảnh khoảnh khắc và khách mời")
                .isActive(true)
                .build();

        AdminSkillResponseDto response = AdminSkillResponseDto.builder()
                .skillId(skillId)
                .name("Chụp ảnh sự kiện")
                .category("Media")
                .description("Chụp ảnh khoảnh khắc và khách mời")
                .isActive(true)
                .createdAt(Instant.now())
                .build();

        when(adminSkillService.createSkill(any(AdminSkillRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/skills")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Chụp ảnh sự kiện"))
                .andExpect(jsonPath("$.data.category").value("Media"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/skills/{id}/toggle-status - Thay đổi trạng thái kỹ năng")
    @WithMockUser(roles = "ADMIN")
    void testToggleStatus_Success() throws Exception {
        UUID skillId = UUID.randomUUID();
        AdminSkillResponseDto response = AdminSkillResponseDto.builder()
                .skillId(skillId)
                .name("Phục vụ bàn")
                .category("F&B")
                .isActive(false)
                .build();

        when(adminSkillService.toggleStatus(eq(skillId))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/admin/skills/{id}/toggle-status", skillId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isActive").value(false));
    }
}
