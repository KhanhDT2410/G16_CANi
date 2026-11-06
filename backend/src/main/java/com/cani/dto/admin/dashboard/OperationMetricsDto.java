package com.cani.dto.admin.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationMetricsDto {

    /**
     * Tổng số sự kiện trên hệ thống
     */
    private long totalEventsCount;

    /**
     * Tổng số sự kiện đang hoạt động (ACTIVE)
     */
    private long activeEventsCount;

    /**
     * Tổng số ca làm việc (SHIFTS) trên toàn hệ thống
     */
    private long totalShiftsCount;

    /**
     * Tổng số ca làm việc đang mở tuyển (ACTIVE)
     */
    private long activeShiftsCount;
}
