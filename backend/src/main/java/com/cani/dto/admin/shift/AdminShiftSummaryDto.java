package com.cani.dto.admin.shift;

import com.cani.entity.enums.ShiftStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminShiftSummaryDto {
    private UUID shiftId;
    private String shiftCode;
    private UUID eventId;
    private String eventName;
    private String jobType;
    private LocalDate eventDate;
    private Instant startTime;
    private Instant endTime;
    private String venueAddress;
    
    // Thông tin EO
    private UUID eoUserId;
    private String eoName;
    private String eoPhone;
    private String eoEmail;
    private String companyName;

    // Nhân sự & Chi phí
    private Integer requiredWorkers;
    private Integer confirmedWorkers;
    private Integer remainingWorkers;
    private BigDecimal hourlyWage;
    private ShiftStatus status;
    private Instant createdAt;
}
