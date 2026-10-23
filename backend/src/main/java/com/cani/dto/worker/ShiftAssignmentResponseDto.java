package com.cani.dto.worker;

import com.cani.entity.enums.AssignmentSource;
import com.cani.entity.enums.AssignmentStatus;
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
public class ShiftAssignmentResponseDto {
    private UUID assignmentId;
    private UUID shiftId;
    private UUID eventId;
    private String eventName;
    private String venueAddress;
    private String jobType;
    private Instant startTime;
    private Instant endTime;
    private BigDecimal hourlyWage;
    private BigDecimal totalPay;
    private AssignmentStatus status;
    private AssignmentSource source;
    private Boolean eoConfirmed;
    private Double venueLat;
    private Double venueLng;
    private String attendanceStatus;
    private Instant checkinTime;
    private Instant checkoutTime;
    private BigDecimal hoursWorked;
    private Instant createdAt;
}
