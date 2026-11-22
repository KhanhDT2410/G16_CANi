package com.cani.dto.worker;

import com.cani.entity.enums.AssignmentSource;
import com.cani.entity.enums.ProposalStatus;
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
public class JobProposalResponseDto {
    private UUID proposalId;
    private UUID shiftId;
    private UUID eventId;
    private String eventName;
    private UUID eoId;
    private String eoName;
    private Boolean eoVerified;
    private BigDecimal eoRatingAvg;
    private String venueAddress;
    private String jobType;
    private String description;
    private Instant startTime;
    private Instant endTime;
    private Instant arrivalTime;
    private java.util.List<String> requiredSkillNames;
    private BigDecimal hourlyWage;
    private BigDecimal allowance;
    private BigDecimal totalEstimatedPay;
    private BigDecimal matchScore;
    private String scoreBreakdown;
    private ProposalStatus status;
    private Instant expiresAt;
    private AssignmentSource source;
    private Instant createdAt;
    private String imageUrl;
}
