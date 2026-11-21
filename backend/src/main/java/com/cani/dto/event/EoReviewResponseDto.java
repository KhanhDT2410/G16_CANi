package com.cani.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO trả về thông tin đánh giá từ Người lao động (Worker) dành cho Nhà tổ chức sự kiện (EO).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EoReviewResponseDto {
    private UUID reviewId;
    private UUID assignmentId;
    private String eventName;
    private String shiftName;
    private UUID reviewerWorkerId;
    private String reviewerWorkerName;
    private String workerAvatarUrl;
    private Integer rating;
    private Integer criteriaShiftClarity;
    private Integer criteriaTiming;
    private Integer criteriaWorkEnvironment;
    private Integer criteriaSupport;
    private Integer criteriaPaymentTiming;
    private String comment;
    private String[] tags;
    private Instant createdAt;
}
