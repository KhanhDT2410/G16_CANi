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
public class JoinInviteResponseDto {

    private UUID assignmentId;
    private UUID shiftId;
    private String jobType;
    private String eventName;
    private String venueAddress;
    private Instant startTime;
    private Instant endTime;
    private BigDecimal agreedAmount;
    private String status;
    private String message;
}

