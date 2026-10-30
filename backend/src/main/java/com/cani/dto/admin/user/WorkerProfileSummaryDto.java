package com.cani.dto.admin.user;

import com.cani.entity.enums.KycStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerProfileSummaryDto {

    private UUID workerId;
    private String fullName;
    private String cccdNumber;
    private KycStatus kycStatus;
    private Integer reliabilityScore;
    private BigDecimal ratingAvg;
    private Integer ratingCount;
    private Integer totalShiftsCompleted;
    private Integer totalCancellations;
    private Integer totalNoshows;
    private Boolean isOnline;
    private Integer yearsExperience;
}
