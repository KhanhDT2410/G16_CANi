package com.cani.dto.invite;

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
public class InviteResponseDto {

    private UUID inviteId;
    private String code;
    private String shareLink;
    private String qrContent;
    private UUID eventId;
    private String eventName;
    private UUID shiftId;
    private String jobType;
    private String venueAddress;
    private BigDecimal hourlyWage;
    private Instant startTime;
    private Instant endTime;
    private Integer maxUses;
    private Integer usedCount;
    private Integer remainingSlots;
    private Instant expiresAt;
    private Boolean isActive;
    private Instant createdAt;
}

