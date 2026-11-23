package com.cani.dto.admin.review;

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
public class AdminReviewFilterRequestDto {
    private UUID revieweeId;
    private UUID reviewerId;
    private Integer rating;
    private Boolean isHidden;
    private Boolean isFlagged;
    private Instant fromDate;
    private Instant toDate;
    private String search;
}

