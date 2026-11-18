package com.cani.dto.ekyc;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EoProfileDto {
    private UUID eoId;
    private UUID userId;
    private String companyName;
    private String taxId;
    private String address;
    private String fullName;
    private String phone;
    private String email;
    private String verificationStatus;
    private Instant verifiedAt;
    private BigDecimal ratingAvg;
    private Integer ratingCount;
    private Integer totalEventsCompleted;
    private String businessLicenseUrl;
    private String businessVerificationStatus;
}

