package com.cani.service.admin;

import com.cani.service.*;

import com.cani.dto.admin.config.BulkUpdateSystemConfigRequestDto;
import com.cani.dto.admin.config.BulkUpdateSystemConfigRequestDto.ConfigItem;
import com.cani.dto.admin.config.SystemConfigResponseDto;
import com.cani.dto.admin.config.UpdateSystemConfigRequestDto;
import com.cani.entity.AuditLog;
import com.cani.entity.SystemConfig;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.SystemConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock
    private SystemConfigRepository systemConfigRepository;

    @Mock
    private AuditLogService auditLogService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SystemConfigService systemConfigService;

    private UUID adminUserId;
    private SystemConfig configCommission;
    private SystemConfig configAge;

    @BeforeEach
    void setUp() {
        adminUserId = UUID.randomUUID();

        configCommission = SystemConfig.builder()
                .configId(UUID.randomUUID())
                .key("commission_rate")
                .value("0.00")
                .description("Tỷ lệ hoa hồng nền tảng")
                .updatedAt(Instant.now())
                .updatedBy(null)
                .build();

        configAge = SystemConfig.builder()
                .configId(UUID.randomUUID())
                .key("min_registration_age")
                .value("18")
                .description("Độ tuổi tối thiểu đăng ký làm nhân sự")
                .updatedAt(Instant.now())
                .updatedBy(null)
                .build();
    }

    @Test
    @DisplayName("getAllConfigs - Lấy toàn bộ danh sách cấu hình thành công")
    void testGetAllConfigs_Success() {
        when(systemConfigRepository.findAllByOrderByKeyAsc())
                .thenReturn(List.of(configCommission, configAge));

        List<SystemConfigResponseDto> result = systemConfigService.getAllConfigs();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getKey()).isEqualTo("commission_rate");
        assertThat(result.get(0).getValue()).isEqualTo("0.00");
        assertThat(result.get(1).getKey()).isEqualTo("min_registration_age");
        assertThat(result.get(1).getValue()).isEqualTo("18");

        verify(systemConfigRepository).findAllByOrderByKeyAsc();
    }

    @Test
    @DisplayName("getConfigByKey - Lấy chi tiết cấu hình theo key thành công")
    void testGetConfigByKey_Success() {
        when(systemConfigRepository.findByKey("commission_rate"))
                .thenReturn(Optional.of(configCommission));

        SystemConfigResponseDto result = systemConfigService.getConfigByKey("commission_rate");

        assertThat(result).isNotNull();
        assertThat(result.getKey()).isEqualTo("commission_rate");
        assertThat(result.getValue()).isEqualTo("0.00");
        assertThat(result.getDescription()).isEqualTo("Tỷ lệ hoa hồng nền tảng");
    }

    @Test
    @DisplayName("getConfigByKey - Ném ResourceNotFoundException khi key không tồn tại")
    void testGetConfigByKey_NotFound_ThrowsException() {
        when(systemConfigRepository.findByKey("unknown_key"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemConfigService.getConfigByKey("unknown_key"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cấu hình hệ thống không tồn tại với key: 'unknown_key'");
    }

    @Test
    @DisplayName("updateConfig - Cập nhật giá trị đơn lẻ thành công và ghi nhận AuditLog")
    void testUpdateConfig_Success() {
        when(systemConfigRepository.findByKey("commission_rate"))
                .thenReturn(Optional.of(configCommission));
        when(systemConfigRepository.save(any(SystemConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSystemConfigRequestDto request = UpdateSystemConfigRequestDto.builder()
                .value("0.05")
                .description("Cập nhật tỷ lệ hoa hồng lên 5%")
                .build();

        SystemConfigResponseDto response = systemConfigService.updateConfig("commission_rate", request, adminUserId);

        assertThat(response).isNotNull();
        assertThat(response.getKey()).isEqualTo("commission_rate");
        assertThat(response.getValue()).isEqualTo("0.05");
        assertThat(response.getDescription()).isEqualTo("Cập nhật tỷ lệ hoa hồng lên 5%");
        assertThat(response.getUpdatedBy()).isEqualTo(adminUserId);

        // Kiểm tra việc ghi nhận AuditLog
        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService, times(1)).logAction(logCaptor.capture());

        AuditLog capturedLog = logCaptor.getValue();
        assertThat(capturedLog.getUserId()).isEqualTo(adminUserId);
        assertThat(capturedLog.getAction()).isEqualTo("UPDATE_SYSTEM_CONFIG");
        assertThat(capturedLog.getEntityType()).isEqualTo("SYSTEM_CONFIG");
        assertThat(capturedLog.getEntityId()).isEqualTo(configCommission.getConfigId());
        assertThat(capturedLog.getOldValues()).contains("\"value\":\"0.00\"");
        assertThat(capturedLog.getNewValues()).contains("\"value\":\"0.05\"");
    }

    @Test
    @DisplayName("updateConfig - Ném ResourceNotFoundException khi key cập nhật không tồn tại")
    void testUpdateConfig_NotFound_ThrowsException() {
        when(systemConfigRepository.findByKey("unknown_key"))
                .thenReturn(Optional.empty());

        UpdateSystemConfigRequestDto request = UpdateSystemConfigRequestDto.builder()
                .value("test")
                .build();

        assertThatThrownBy(() -> systemConfigService.updateConfig("unknown_key", request, adminUserId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(systemConfigRepository, never()).save(any());
        verify(auditLogService, never()).logAction(any());
    }

    @Test
    @DisplayName("bulkUpdateConfigs - Cập nhật hàng loạt dạng Map thành công và ghi nhận AuditLog cho từng item")
    void testBulkUpdateConfigs_WithMap_Success() {
        when(systemConfigRepository.findByKey("commission_rate"))
                .thenReturn(Optional.of(configCommission));
        when(systemConfigRepository.findByKey("min_registration_age"))
                .thenReturn(Optional.of(configAge));
        when(systemConfigRepository.save(any(SystemConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, String> configs = new LinkedHashMap<>();
        configs.put("commission_rate", "0.08");
        configs.put("min_registration_age", "20");

        BulkUpdateSystemConfigRequestDto request = BulkUpdateSystemConfigRequestDto.builder()
                .configs(configs)
                .build();

        List<SystemConfigResponseDto> result = systemConfigService.bulkUpdateConfigs(request, adminUserId);

        assertThat(result).hasSize(2);
        assertThat(configCommission.getValue()).isEqualTo("0.08");
        assertThat(configAge.getValue()).isEqualTo("20");
        assertThat(configCommission.getUpdatedBy()).isEqualTo(adminUserId);
        assertThat(configAge.getUpdatedBy()).isEqualTo(adminUserId);

        // Ghi nhận 2 audit logs
        verify(auditLogService, times(2)).logAction(any(AuditLog.class));
    }

    @Test
    @DisplayName("bulkUpdateConfigs - Cập nhật hàng loạt dạng List<ConfigItem> thành công kèm mô tả mới")
    void testBulkUpdateConfigs_WithItemsList_Success() {
        when(systemConfigRepository.findByKey("commission_rate"))
                .thenReturn(Optional.of(configCommission));
        when(systemConfigRepository.save(any(SystemConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<ConfigItem> items = List.of(
                ConfigItem.builder()
                        .key("commission_rate")
                        .value("0.10")
                        .description("Hoa hồng 10% mùa cao điểm")
                        .build()
        );

        BulkUpdateSystemConfigRequestDto request = BulkUpdateSystemConfigRequestDto.builder()
                .items(items)
                .build();

        List<SystemConfigResponseDto> result = systemConfigService.bulkUpdateConfigs(request, adminUserId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getValue()).isEqualTo("0.10");
        assertThat(result.get(0).getDescription()).isEqualTo("Hoa hồng 10% mùa cao điểm");

        verify(auditLogService, times(1)).logAction(any(AuditLog.class));
    }

    @Test
    @DisplayName("bulkUpdateConfigs - Ném BadRequestException khi payload rỗng")
    void testBulkUpdateConfigs_EmptyRequest_ThrowsBadRequest() {
        BulkUpdateSystemConfigRequestDto emptyRequest = BulkUpdateSystemConfigRequestDto.builder()
                .configs(Collections.emptyMap())
                .items(Collections.emptyList())
                .build();

        assertThatThrownBy(() -> systemConfigService.bulkUpdateConfigs(emptyRequest, adminUserId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Danh sách cấu hình cập nhật không được để trống");

        assertThatThrownBy(() -> systemConfigService.bulkUpdateConfigs(null, adminUserId))
                .isInstanceOf(BadRequestException.class);

        verify(systemConfigRepository, never()).save(any());
        verify(auditLogService, never()).logAction(any());
    }

    @Test
    @DisplayName("bulkUpdateConfigs - Ném ResourceNotFoundException nếu có ít nhất 1 key không tồn tại và không lưu gì")
    void testBulkUpdateConfigs_KeyNotFound_ThrowsException() {
        when(systemConfigRepository.findByKey("commission_rate"))
                .thenReturn(Optional.of(configCommission));
        when(systemConfigRepository.findByKey("invalid_key"))
                .thenReturn(Optional.empty());

        Map<String, String> configs = new LinkedHashMap<>();
        configs.put("commission_rate", "0.05");
        configs.put("invalid_key", "value");

        BulkUpdateSystemConfigRequestDto request = BulkUpdateSystemConfigRequestDto.builder()
                .configs(configs)
                .build();

        assertThatThrownBy(() -> systemConfigService.bulkUpdateConfigs(request, adminUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("invalid_key");

        verify(systemConfigRepository, never()).save(any());
        verify(auditLogService, never()).logAction(any());
    }
}

