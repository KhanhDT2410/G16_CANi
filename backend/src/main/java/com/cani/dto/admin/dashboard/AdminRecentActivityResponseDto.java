package com.cani.dto.admin.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRecentActivityResponseDto {

    /**
     * ID của bản ghi nhật ký kiểm toán (Audit Log ID)
     */
    private UUID id;

    /**
     * ID của quản trị viên thực hiện hành động
     */
    private UUID userId;

    /**
     * Họ tên của quản trị viên thực hiện hành động
     */
    private String adminName;

    /**
     * Email của quản trị viên thực hiện hành động
     */
    private String adminEmail;

    /**
     * Ảnh đại diện của quản trị viên thực hiện hành động
     */
    private String adminAvatarUrl;

    /**
     * Mã hành động kỹ thuật (VD: UPDATE_USER_STATUS, APPROVE_SKILL_VERIFICATION)
     */
    private String action;

    /**
     * Mô tả hành động thân thiện với người dùng
     */
    private String actionDescription;

    /**
     * Loại đối tượng tác động (VD: USER, SKILL_VERIFICATION, WORKER_EKYC, EO_EKYC)
     */
    private String entityType;

    /**
     * ID của đối tượng bị tác động
     */
    private UUID entityId;

    /**
     * Giá trị cũ dạng JSON
     */
    private String oldValues;

    /**
     * Giá trị mới dạng JSON
     */
    private String newValues;

    /**
     * Địa chỉ IP của quản trị viên khi thực hiện thao tác
     */
    private String ipAddress;

    /**
     * Thời điểm thực hiện hành động
     */
    private Instant createdAt;
}
