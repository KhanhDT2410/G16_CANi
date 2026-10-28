package com.cani.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftQrCodeResponseDto {

    private UUID shiftId;
    private UUID eventId;
    private String eventName;
    private String jobType;
    private Instant startTime;
    private Instant endTime;
    private String venueAddress;
    private String checkinQrContent;
    private String checkoutQrContent;
}

