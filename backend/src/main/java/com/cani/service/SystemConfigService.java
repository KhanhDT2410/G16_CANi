package com.cani.service;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /**
     * Lấy toàn bộ danh sách các tham số cấu hình toàn sàn, sắp xếp theo tên key.
     */
    public List<SystemConfigResponseDto> getAllConfigs() {
        return systemConfigRepository.findAllByOrderByKeyAsc()
                .stream()
                .map(this::toResponseDto)
                .toList();
    }

    /**
     * Lấy thông tin chi tiết của 1 tham số cấu hình theo key.
     */
    public SystemConfigResponseDto getConfigByKey(String key) {
        SystemConfig config = systemConfigRepository.findByKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("Cấu hình hệ thống", "key", key));
        return toResponseDto(config);
    }

    /**
     * Cập nhật giá trị một tham số cấu hình đơn lẻ và ghi nhận Audit Log.
     */
    @Transactional
    public SystemConfigResponseDto updateConfig(String key, UpdateSystemConfigRequestDto request, UUID adminUserId) {
        SystemConfig config = systemConfigRepository.findByKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("Cấu hình hệ thống", "key", key));

        String oldValue = config.getValue();
        String oldDescription = config.getDescription();

        config.setValue(request.getValue());
        if (request.getDescription() != null) {
            config.setDescription(request.getDescription());
        }
        config.setUpdatedBy(adminUserId);

        SystemConfig savedConfig = systemConfigRepository.save(config);

        log.info("Admin {} đã cập nhật cấu hình [key={}] từ '{}' thành '{}'",
                adminUserId, key, oldValue, savedConfig.getValue());

        recordConfigAuditLog(
                adminUserId,
                savedConfig.getConfigId(),
                key,
                oldValue,
                oldDescription,
                savedConfig.getValue(),
                savedConfig.getDescription()
        );

        return toResponseDto(savedConfig);
    }

    /**
     * Cập nhật hàng loạt nhiều tham số cấu hình cùng lúc trong một transaction.
     */
    @Transactional
    public List<SystemConfigResponseDto> bulkUpdateConfigs(BulkUpdateSystemConfigRequestDto request, UUID adminUserId) {
        if (request == null || request.isEmpty()) {
            throw new BadRequestException("Danh sách cấu hình cập nhật không được để trống");
        }

        Map<String, ConfigItem> updates = request.getNormalizedConfigItems();

        // 1. Kiểm tra tính hợp lệ: Toàn bộ key phải tồn tại trong hệ thống trước khi cập nhật
        List<SystemConfig> configsToUpdate = new ArrayList<>();
        for (String key : updates.keySet()) {
            SystemConfig config = systemConfigRepository.findByKey(key)
                    .orElseThrow(() -> new ResourceNotFoundException("Cấu hình hệ thống", "key", key));
            configsToUpdate.add(config);
        }

        // 2. Thực hiện cập nhật và ghi nhận Audit Log cho từng cấu hình
        List<SystemConfigResponseDto> responseDtos = new ArrayList<>();
        for (SystemConfig config : configsToUpdate) {
            ConfigItem item = updates.get(config.getKey());
            String oldValue = config.getValue();
            String oldDescription = config.getDescription();

            config.setValue(item.getValue());
            if (item.getDescription() != null) {
                config.setDescription(item.getDescription());
            }
            config.setUpdatedBy(adminUserId);

            SystemConfig savedConfig = systemConfigRepository.save(config);

            recordConfigAuditLog(
                    adminUserId,
                    savedConfig.getConfigId(),
                    savedConfig.getKey(),
                    oldValue,
                    oldDescription,
                    savedConfig.getValue(),
                    savedConfig.getDescription()
            );

            responseDtos.add(toResponseDto(savedConfig));
        }

        log.info("Admin {} đã cập nhật hàng loạt {} tham số cấu hình hệ thống", adminUserId, responseDtos.size());
        return responseDtos;
    }

    /**
     * Chuyển đổi Entity sang Response DTO.
     */
    public SystemConfigResponseDto toResponseDto(SystemConfig entity) {
        return SystemConfigResponseDto.builder()
                .id(entity.getConfigId())
                .key(entity.getKey())
                .value(entity.getValue())
                .description(entity.getDescription())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }

    /**
     * Ghi nhận Audit Log độc lập mỗi khi có thay đổi tham số cấu hình.
     */
    private void recordConfigAuditLog(UUID adminUserId, UUID configId, String key,
                                      String oldValue, String oldDesc,
                                      String newValue, String newDesc) {
        try {
            Map<String, Object> oldMap = new LinkedHashMap<>();
            oldMap.put("key", key);
            oldMap.put("value", oldValue);
            if (oldDesc != null) {
                oldMap.put("description", oldDesc);
            }

            Map<String, Object> newMap = new LinkedHashMap<>();
            newMap.put("key", key);
            newMap.put("value", newValue);
            if (newDesc != null) {
                newMap.put("description", newDesc);
            }

            AuditLog logEntry = AuditLog.builder()
                    .userId(adminUserId)
                    .action("UPDATE_SYSTEM_CONFIG")
                    .entityType("SYSTEM_CONFIG")
                    .entityId(configId)
                    .oldValues(objectMapper.writeValueAsString(oldMap))
                    .newValues(objectMapper.writeValueAsString(newMap))
                    .build();

            auditLogService.logAction(logEntry);
        } catch (Exception e) {
            log.error("Không thể chuẩn bị AuditLog khi cập nhật cấu hình [key={}]: {}", key, e.getMessage(), e);
        }
    }
}

