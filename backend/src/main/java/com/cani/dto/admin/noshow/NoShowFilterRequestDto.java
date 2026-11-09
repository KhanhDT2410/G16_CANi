package com.cani.dto.admin.noshow;

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
public class NoShowFilterRequestDto {

    private String keyword;
    private UUID workerId;
    private UUID eventId;
    private Instant fromDate;
    private Instant toDate;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 20;
}

