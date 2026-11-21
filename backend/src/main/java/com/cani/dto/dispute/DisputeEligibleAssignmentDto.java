package com.cani.dto.dispute;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Thông tin ca phân công tóm tắt để hiển thị trong dropdown chọn ca khi nộp đơn tranh chấp.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeEligibleAssignmentDto {
    private UUID assignmentId;
    private String eventName;
    private String jobType;
    private String counterpartyName;
    private String counterpartyPhone;
    private Instant startTime;
    private Instant endTime;
    private String status;
}
