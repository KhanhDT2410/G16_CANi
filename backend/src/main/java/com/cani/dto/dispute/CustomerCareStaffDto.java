package com.cani.dto.dispute;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Danh sách nhân sự Customer Care dùng cho chức năng chuyển đơn (UC-99).
 * Endpoint: GET /api/v1/admin/disputes/customer-care-staff
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerCareStaffDto {

    private UUID userId;
    private String fullName;
    private String email;

    /**
     * Số tranh chấp đang được giao cho nhân sự này (chưa xử lý xong).
     */
    private long assignedCount;
}
