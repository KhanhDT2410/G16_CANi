package com.cani.dto.admin.sla;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaScanReportDto {
    private Instant scannedAt;
    private int ticketBreachesFound;
    private int ticketAlertsSent;
    private int disputeCcBreachesFound;
    private int disputeCcAlertsSent;
    private int disputeAdminBreachesFound;
    private int disputeAdminAlertsSent;
    private int totalAlertsSent;
}

