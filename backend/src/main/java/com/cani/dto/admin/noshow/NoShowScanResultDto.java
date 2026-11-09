package com.cani.dto.admin.noshow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoShowScanResultDto {

    private int totalCandidatesScanned;
    private int totalMarkedNoShow;
    private int totalEmergencyDispatched;
    private int totalAccountsLocked;
    private Instant scannedAt;
    private String message;
}

