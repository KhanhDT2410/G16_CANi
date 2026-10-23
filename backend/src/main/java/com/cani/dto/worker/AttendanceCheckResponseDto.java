package com.cani.dto.worker;

import com.cani.entity.enums.AttendanceStatus;
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
public class AttendanceCheckResponseDto {
    private UUID assignmentId;
    private UUID attendanceId;
    private AttendanceStatus attendanceStatus;
    private String assignmentStatus;
    private Instant checkinTime;
    private Instant checkoutTime;
    private BigDecimal hoursWorked;
    private Boolean isOnTime;
    private String message;
}
