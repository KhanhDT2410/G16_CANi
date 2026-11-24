package com.cani.dto.admin.disputerule;

import com.cani.entity.enums.DisputeOutcome;
import com.cani.entity.enums.DisputeType;
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
public class DisputeRuleResponseDto {

    private UUID id;
    private String code;
    private String behavior;
    private String violatingParty;
    private String sanctionForm;
    private String specificPenalty;
    private DisputeType ruleType;
    private DisputeOutcome faultOutcome;
    private Integer repeatWindowDays;
    private Integer repeatIndex;
    private String sanctionText;
    private String adminActionText;
    private Boolean enabled;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
