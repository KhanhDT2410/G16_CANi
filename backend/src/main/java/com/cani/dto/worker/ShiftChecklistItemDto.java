package com.cani.dto.worker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftChecklistItemDto {

    private String id;

    private String title;

    @Builder.Default
    private Boolean isCompleted = false;

    private Instant completedAt;
}
