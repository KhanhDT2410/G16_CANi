package com.cani.dto.team;

import com.cani.entity.enums.SkillTier;
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
public class MyTeamMemberResponseDto {

    private UUID eoWorkerId;
    private UUID workerId;
    private UUID userId;
    private String fullName;
    private String phone;
    private String email;
    private String avatarUrl;
    private SkillTier skillTier;
    private Integer reliabilityScore;
    private BigDecimal ratingAvg;
    private Integer totalShiftsTogether;
    private BigDecimal completionRate;
    private BigDecimal avgRatingGiven;
    private Instant lastWorkedAt;
    private String lastJobType;
    private String internalNote;
    private Boolean isFavorited;
    private Boolean isBlacklisted;
    private Boolean isSavedInMyTeam;
    private Boolean isOnline;
    private Instant addedAt;
}
