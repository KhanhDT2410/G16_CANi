package com.cani.dto.admin.shift;

import com.cani.entity.enums.PaymentStatus;
import com.cani.entity.enums.ShiftStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminShiftDetailDto {
    private UUID shiftId;
    private String shiftCode;
    private UUID eventId;
    private String eventName;
    private String eventDescription;
    private String jobType;
    private String description;
    private LocalDate eventDate;
    private Instant startTime;
    private Instant endTime;
    private String venueAddress;
    private Double locationLat;
    private Double locationLng;

    // Thông tin EO
    private UUID eoUserId;
    private String eoName;
    private String eoPhone;
    private String eoEmail;
    private String companyName;

    // Chi tiết tuyển dụng & Lương
    private Integer requiredWorkers;
    private Integer confirmedWorkers;
    private Integer remainingWorkers;
    private BigDecimal hourlyWage;
    private BigDecimal allowance;
    private BigDecimal totalCost;
    private ShiftStatus status;
    private Integer minReliabilityScore;
    private String dressCode;
    private String checklist;
    private Instant createdAt;
    private PaymentStatus paymentStatus;

    // Thông tin can thiệp quản trị
    private String adminNote;
    private String cancellationReason;
    private UUID cancelledBy;
    private Instant suspendedAt;

    // Danh sách nhân sự tham gia ca
    private List<AdminShiftWorkerDto> workers;
}
