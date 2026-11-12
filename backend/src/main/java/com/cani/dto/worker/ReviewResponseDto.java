package com.cani.dto.worker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponseDto {

    private UUID reviewId;

    private UUID assignmentId;

    private UUID reviewerId;

    private UUID revieweeId;

    private Integer rating;

    private Integer criteriaWorkEnvironment;

    private Integer criteriaSupport;

    private Integer criteriaPaymentTiming;

    private String comment;

    private String[] tags;

    private Instant createdAt;
}
