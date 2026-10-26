package com.cani.dto.admin.user;

import com.cani.entity.enums.VerificationStatus;
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
public class EoProfileSummaryDto {

    private UUID eoId;
    private String companyName;
    private String taxId;
    private String address;
    private VerificationStatus verificationStatus;
    private Instant verifiedAt;
    private BigDecimal ratingAvg;
    private Integer ratingCount;
    private Integer totalEventsCompleted;
}
