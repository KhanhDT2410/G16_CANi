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
    private UUID eoId;
    private String eoName;
    private Boolean eoVerified;
    private BigDecimal eoRatingAvg;
    private String venueAddress;
    private String jobType;
    private Instant startTime;
    private Instant endTime;
    private Instant arrivalTime;
    private java.util.List<String> requiredSkillNames;
    private UUID performedSkillId;
    private String performedSkillName;
    private UUID confirmedPerformedSkillId;
    private String confirmedPerformedSkillName;
    private Boolean spExperienceAwarded;
    private Instant spExperienceAwardedAt;
    private Boolean spRatingAwarded;
    private String inviteOverrideReason;
    private BigDecimal hourlyWage;
    private BigDecimal emergencyBonus;
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
    private String imageUrl;
    private String proofImageUrl;
    private Instant proofSubmittedAt;
    private String paymentStatus;
    private BigDecimal declaredAmount;
    private String paymentMethod;
    private String workerConfirmStatus;
    private String paymentProofUrl;
    private String externalRef;
    private Instant declaredAt;
    private String workerComment;
    private String bankCode;
    private String bankAccountNumber;
    private String bankAccountName;
    private String vietQrUrl;

    // Bổ sung thông tin đánh giá 2 chiều (EO <-> Worker) sau ca làm
    private Integer eoRating;
    private String eoReviewComment;
    private Instant eoReviewedAt;
    private Boolean hasWorkerReviewed;
}
