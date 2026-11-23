package com.cani.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftAttendanceDto {

    private UUID assignmentId;
    private UUID workerId;
    private UUID userId;
    private String workerName;
    private String workerPhone;
    private String avatarUrl;
    private Integer workerReliabilityScore;
    private BigDecimal workerRatingAvg;
    private Integer workerAge;
    private Boolean meetsMinimumAge;
    private String workerDob;
    private UUID shiftId;
    private String jobType;
    private String shiftName;
    private UUID performedSkillId;
    private String performedSkillName;
    private UUID confirmedPerformedSkillId;
    private String confirmedPerformedSkillName;
    private Boolean spExperienceAwarded;
    private Instant spExperienceAwardedAt;
    private Boolean spRatingAwarded;
    private String inviteOverrideReason;
    private String assignmentStatus;
    private Instant checkinTime;
    private Instant checkoutTime;
    private Boolean isOnTime;
    private BigDecimal hoursWorked;
    private Boolean eoConfirmed;
    private Instant eoConfirmedAt;
    private Boolean hasReviewedWorker;
    private String checklistProgress;
    private String checklistRawProgress;
    private Boolean isChecklistCompleted;
    private String proofImageUrl;
    private String paymentStatus;
    private String paymentProofUrl;
    private String externalRef;
    private Instant declaredAt;
    private BigDecimal agreedWage;
    private BigDecimal declaredAmount;
    private String paymentMethod;
    private String workerConfirmStatus;
    private String workerComment;
    private String workerBio;
    private Integer yearsExperience;

    // Thông tin tài khoản ngân hàng thụ hưởng & mã VietQR phục vụ thanh toán trực tiếp (Non-Escrow)
    private String bankCode;
    private String bankAccountNumber;
    private String bankAccountName;
    private String vietQrUrl;
}
