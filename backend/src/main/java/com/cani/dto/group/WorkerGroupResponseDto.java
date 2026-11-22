package com.cani.dto.group;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerGroupResponseDto {

    private UUID groupId;
    private String name;
    private String description;
    private String skillFocus;
    private UUID leaderWorkerId;
    private String leaderName;
    private Integer minSize;
    private Integer maxSize;
    private Integer memberCount;
    private BigDecimal avgRating;
    private Integer reliabilityScore;
    private Boolean isActive;
    private String inviteCode;
    private Instant createdAt;
}
