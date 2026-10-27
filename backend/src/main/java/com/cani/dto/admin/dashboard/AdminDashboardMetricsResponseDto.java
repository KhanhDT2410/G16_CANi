package com.cani.dto.admin.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardMetricsResponseDto {

    /**
     * Thống kê tài khoản & người dùng
     */
    private UserMetricsDto users;

    /**
     * Thống kê hồ sơ chờ thẩm định (Worker eKYC, EO eKYC, Skill Certificates)
     */
    private PendingVerificationMetricsDto verifications;

    /**
     * Thống kê sự kiện và ca làm việc (Operations)
     */
    private OperationMetricsDto operations;
}
