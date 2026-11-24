package com.cani.dto.dispute;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EoStrikeSummaryDto {

    private UUID eoId;
    private UUID eoUserId;
    private Long strikeCount90d;
    private List<StrikeItemDto> strikes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StrikeItemDto {
        private UUID strikeId;
        private UUID disputeId;
        private String reason;
        private Instant createdAt;
    }
}
