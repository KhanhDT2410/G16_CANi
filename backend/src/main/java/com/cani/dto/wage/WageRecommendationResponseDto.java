package com.cani.dto.wage;

import com.cani.entity.enums.City;
import com.cani.entity.enums.JobTier;
import com.cani.entity.enums.LeadTime;
import com.cani.entity.enums.TimeSlot;
import com.cani.entity.enums.WageRegionCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WageRecommendationResponseDto {

    private UUID recommendationId;
    private UUID shiftId;
    private City city;
    private WageRegionCode regionCode;
    private String regionName;
    private BigDecimal marketMultiplier;
    private String jobType;
    private JobTier jobTier;
    private String jobTierLabel;
    private TimeSlot timeSlot;
    private LeadTime leadTime;
    private BigDecimal timeSlotMultiplier;
    private BigDecimal leadTimeMultiplier;
    private BigDecimal nightHourRatio;
    private BigDecimal suggestedWage;
    private BigDecimal minWage;
    private BigDecimal maxWage;
    private String explanation;
    private UUID matchedRowId;
    private Integer fallbackLevel;

    // Dẫn chứng pháp lý, dữ liệu nội bộ CANi & hệ số điều chỉnh minh bạch
    private BigDecimal legalFloorWage;
    private String legalRegionLabel;
    private String legalReference;
    private Integer platformSampleCount;
    private BigDecimal platformAvgWage;
    private BigDecimal platformMinWage;
    private BigDecimal platformMaxWage;
    private Boolean catalogMatched;
    private String benchmarkSource;
    private List<String> evidenceFactors;
}
