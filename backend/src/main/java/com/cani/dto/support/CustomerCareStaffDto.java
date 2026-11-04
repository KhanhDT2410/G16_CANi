package com.cani.dto.support;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Danh sách nhân sự Customer Care dùng cho chức năng phân công / chuyển phiếu (SCR-25 / SCR-26).
 * Endpoint: GET /api/v1/admin/support/tickets/customer-care-staff
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
     * Số phiếu hỗ trợ đang được giao cho nhân sự này (chưa đóng).
     */
    private long assignedCount;
}

