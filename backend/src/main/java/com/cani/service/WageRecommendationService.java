package com.cani.service;

import com.cani.dto.wage.WageRecommendationRequestDto;
import com.cani.dto.wage.WageRecommendationResponseDto;
import com.cani.entity.enums.JobTier;
import com.cani.entity.enums.LeadTime;
import com.cani.entity.enums.ShiftStatus;
import com.cani.entity.enums.TimeSlot;
import com.cani.entity.enums.WageRegionCode;
import com.cani.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WageRecommendationService {

    private final WageMatrixService wageMatrixService;
    private final GeoWageRegionResolver geoWageRegionResolver;
    private final JobTierClassifier jobTierClassifier;

    @Autowired(required = false)
    private ShiftRepository shiftRepository;

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String LEGAL_WAGE_DECREE = "Nghị định 293/2025/NĐ-CP (hiệu lực từ 01/01/2026)";
    private static final int HISTORICAL_WINDOW_DAYS = 90;
    private static final int MIN_HISTORICAL_SAMPLES = 5;

    public record TimeAdjustment(
            TimeSlot dominantSlot,
            BigDecimal multiplier,
            BigDecimal nightHourRatio
    ) {}

    /**
     * Gợi ý mức thù lao tối ưu theo mô hình Region x JobTier (NĐ 293/2025/NĐ-CP)
     * kết hợp tỷ trọng khung giờ thực tế trên [startTime, endTime) và dữ liệu lịch sử 90 ngày.
     */
    @Transactional(readOnly = true)
    public WageRecommendationResponseDto recommendWage(WageRecommendationRequestDto request) {
        String normalizedRole = request.getJobType() != null ? request.getJobType().trim() : "";

        GeoWageRegionResolver.ResolvedRegion resolvedRegion = geoWageRegionResolver.resolve(
                request.getRegionCode(),
                request.getCity(),
                request.getVenueAddress(),
                request.getVenueLat(),
                request.getVenueLng()
        );
        WageRegionCode regionCode = resolvedRegion.regionCode();
        JobTier jobTier = jobTierClassifier.classify(normalizedRole, request.getJobTier());
        boolean catalogMatched = jobTierClassifier.isCanonicalRole(normalizedRole) || request.getJobTier() != null;

        log.info("Tính toán thù lao khuyến nghị: region={}, jobTier={}, jobType={}, start={}, end={}",
                regionCode, jobTier, normalizedRole, request.getStartTime(), request.getEndTime());

        TimeAdjustment timeAdj = computeWeightedTimeAdjustment(request.getStartTime(), request.getEndTime());
        LeadTime leadTime = determineLeadTime(request.getStartTime());
        BigDecimal leadTimeMultiplier = resolveLeadTimeMultiplier(leadTime);

        // 1. Tra cứu chuẩn Region x JobTier trong DB (hoặc fallback theo enum Bảng A)
        WageMatrixService.BenchmarkLookupResult lookup = wageMatrixService.lookupBenchmark(regionCode, jobTier);
        BigDecimal regionFloor = lookup.getLegalFloorHourly();
        BigDecimal marketMult = lookup.getMarketMultiplier() != null ? lookup.getMarketMultiplier() : BigDecimal.ONE;
        BigDecimal baseFactor = regionFloor.multiply(marketMult);

        BigDecimal baseMin = baseFactor.multiply(lookup.getMinMultiplier());
        BigDecimal baseRec = lookup.getOverrideBaseWage() != null
                ? lookup.getOverrideBaseWage()
                : baseFactor.multiply(lookup.getRecommendedMultiplier());
        BigDecimal baseMax = baseFactor.multiply(lookup.getMaxMultiplier());

        // 2. Thống kê dữ liệu lịch sử thực tế 90 ngày gần nhất (dùng Median chống outlier)
        List<BigDecimal> historicalWages = fetchRecentHistoricalWages(normalizedRole);
        int sampleCount = historicalWages.size();
        BigDecimal platformAvg = null;
        BigDecimal platformMin = null;
        BigDecimal platformMax = null;
        BigDecimal platformMedian = null;

        if (sampleCount > 0) {
            List<BigDecimal> sorted = historicalWages.stream().sorted().toList();
            BigDecimal sum = sorted.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            platformAvg = sum.divide(BigDecimal.valueOf(sampleCount), 0, RoundingMode.HALF_UP);
            platformMin = sorted.get(0);
            platformMax = sorted.get(sampleCount - 1);
            platformMedian = computeMedian(sorted);
        }

        String benchmarkSource;
        BigDecimal finalBaseRec = baseRec;

        if (lookup.getOverrideBaseWage() != null) {
            benchmarkSource = "CANI_WAGE_MATRIX_OVERRIDE";
        } else if (sampleCount >= MIN_HISTORICAL_SAMPLES && platformMedian != null) {
            // Pha trọng số 70% Bảng A (Region x JobTier) + 30% Median lịch sử, kẹp trong [baseMin, baseMax]
            BigDecimal blended = baseRec.multiply(new BigDecimal("0.70"))
                    .add(platformMedian.multiply(new BigDecimal("0.30")));
            finalBaseRec = blended.max(baseMin).min(baseMax);
            benchmarkSource = "CANI_REGION_TIER_HYBRID";
        } else {
            benchmarkSource = lookup.getFallbackLevel() <= 1 ? "CANI_WAGE_MATRIX" : "CANI_REGION_TIER_TABLE_A";
        }

        // 3. Nhân hệ số khung giờ gia quyền và độ gấp
        BigDecimal combinedMult = timeAdj.multiplier().multiply(leadTimeMultiplier);
        BigDecimal effectiveLegalFloor = regionFloor
                .multiply(BigDecimal.ONE.add(new BigDecimal("0.30").multiply(timeAdj.nightHourRatio())))
                .setScale(0, RoundingMode.HALF_UP)
                .max(regionFloor);

        BigDecimal suggestedWage = roundToThousand(finalBaseRec.multiply(combinedMult)).max(effectiveLegalFloor);
        BigDecimal minWage = roundToThousand(baseMin.multiply(combinedMult)).max(effectiveLegalFloor);
        if (minWage.compareTo(suggestedWage) > 0) {
            minWage = suggestedWage;
        }
        BigDecimal maxWage = roundToThousand(baseMax.multiply(combinedMult))
                .max(suggestedWage.add(new BigDecimal("5000")));

        List<String> evidenceFactors = buildEvidenceFactors(
                resolvedRegion.regionLabel(),
                regionFloor,
                marketMult,
                jobTier,
                lookup,
                timeAdj,
                leadTime,
                leadTimeMultiplier,
                effectiveLegalFloor,
                sampleCount,
                platformMedian,
                platformMin,
                platformMax,
                catalogMatched
        );

        String explanation = String.format(
                "Đề xuất %,d đ/giờ (khoảng %,d – %,d đ/giờ) cho '%s' thuộc nhóm %s tại %s — tham chiếu Bảng A theo %s.",
                suggestedWage.longValue(),
                minWage.longValue(),
                maxWage.longValue(),
                normalizedRole,
                lookup.getTierLabel(),
                resolvedRegion.regionLabel(),
                LEGAL_WAGE_DECREE
        );

        return WageRecommendationResponseDto.builder()
                .recommendationId(UUID.randomUUID())
                .shiftId(request.getShiftId())
                .city(resolvedRegion.legacyCity())
                .regionCode(regionCode)
                .regionName(lookup.getRegionName())
                .marketMultiplier(marketMult)
                .jobType(normalizedRole)
                .jobTier(jobTier)
                .jobTierLabel(lookup.getTierLabel())
                .timeSlot(timeAdj.dominantSlot())
                .leadTime(leadTime)
                .timeSlotMultiplier(timeAdj.multiplier())
                .leadTimeMultiplier(leadTimeMultiplier)
                .nightHourRatio(timeAdj.nightHourRatio())
                .suggestedWage(suggestedWage)
                .minWage(minWage)
                .maxWage(maxWage)
                .explanation(explanation)
                .matchedRowId(lookup.getMatchedRowId())
                .fallbackLevel(lookup.getFallbackLevel())
                .legalFloorWage(effectiveLegalFloor)
                .legalRegionLabel(resolvedRegion.regionLabel())
                .legalReference(LEGAL_WAGE_DECREE)
                .platformSampleCount(sampleCount)
                .platformAvgWage(platformAvg)
                .platformMinWage(platformMin)
                .platformMaxWage(platformMax)
                .catalogMatched(catalogMatched)
                .benchmarkSource(benchmarkSource)
                .evidenceFactors(evidenceFactors)
                .build();
    }

    /**
     * Tính hệ số khung giờ bình quân gia quyền theo từng phút thực tế trên [startTime, endTime),
     * đảm bảo ca vắt qua nhiều khung giờ (ví dụ 17h–23h) được tính chính xác phụ phí ban đêm (22h–06h).
     */
    public TimeAdjustment computeWeightedTimeAdjustment(Instant startTime, Instant endTime) {
        Instant safeEnd = (endTime != null && endTime.isAfter(startTime))
                ? endTime
                : startTime.plus(4, ChronoUnit.HOURS);

        ZonedDateTime cursor = startTime.atZone(VIETNAM_ZONE);
        ZonedDateTime endZdt = safeEnd.atZone(VIETNAM_ZONE);

        long totalMinutes = 0;
        long nightMinutes = 0;
        BigDecimal weightedSum = BigDecimal.ZERO;
        Map<TimeSlot, Long> minutesBySlot = new EnumMap<>(TimeSlot.class);

        while (cursor.isBefore(endZdt)) {
            ZonedDateTime nextBoundary = nextSlotBoundary(cursor);
            ZonedDateTime segEnd = nextBoundary.isBefore(endZdt) ? nextBoundary : endZdt;
            long segMinutes = Duration.between(cursor, segEnd).toMinutes();
            if (segMinutes <= 0) {
                break;
            }

            TimeSlot slot = classifyPoint(cursor);
            BigDecimal weight = weightForPoint(cursor);
            boolean isNight = cursor.getHour() >= 22 || cursor.getHour() < 6;

            totalMinutes += segMinutes;
            if (isNight) {
                nightMinutes += segMinutes;
            }
            weightedSum = weightedSum.add(weight.multiply(BigDecimal.valueOf(segMinutes)));
            minutesBySlot.merge(slot, segMinutes, Long::sum);

            cursor = segEnd;
        }

        if (totalMinutes <= 0) {
            TimeSlot fallbackSlot = determineTimeSlot(startTime);
            return new TimeAdjustment(fallbackSlot, resolveTimeSlotMultiplier(fallbackSlot), BigDecimal.ZERO);
        }

        TimeSlot dominant = minutesBySlot.entrySet().stream()
                .max(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                .map(Map.Entry::getKey)
                .orElse(TimeSlot.DAYTIME_WEEKDAY);

        BigDecimal avgMultiplier = weightedSum
                .divide(BigDecimal.valueOf(totalMinutes), 4, RoundingMode.HALF_UP);
        BigDecimal nightRatio = BigDecimal.valueOf(nightMinutes)
                .divide(BigDecimal.valueOf(totalMinutes), 4, RoundingMode.HALF_UP);

        return new TimeAdjustment(dominant, avgMultiplier, nightRatio);
    }

    private ZonedDateTime nextSlotBoundary(ZonedDateTime current) {
        int hour = current.getHour();
        if (hour < 6) {
            return current.toLocalDate().atTime(LocalTime.of(6, 0)).atZone(VIETNAM_ZONE);
        }
        if (hour < 18) {
            return current.toLocalDate().atTime(LocalTime.of(18, 0)).atZone(VIETNAM_ZONE);
        }
        if (hour < 22) {
            return current.toLocalDate().atTime(LocalTime.of(22, 0)).atZone(VIETNAM_ZONE);
        }
        return current.toLocalDate().plusDays(1).atStartOfDay(VIETNAM_ZONE);
    }

    private TimeSlot classifyPoint(ZonedDateTime zdt) {
        DayOfWeek dow = zdt.getDayOfWeek();
        int hour = zdt.getHour();
        boolean isWeekend = (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY);
        if (isWeekend) {
            return (hour >= 6 && hour < 18) ? TimeSlot.DAYTIME_WEEKEND : TimeSlot.EVENING_WEEKEND;
        }
        if (hour >= 6 && hour < 18) {
            return TimeSlot.DAYTIME_WEEKDAY;
        }
        if (hour >= 18 && hour < 22) {
            return TimeSlot.EVENING_WEEKDAY;
        }
        return TimeSlot.NIGHT_WEEKDAY;
    }

    private BigDecimal weightForPoint(ZonedDateTime zdt) {
        DayOfWeek dow = zdt.getDayOfWeek();
        int hour = zdt.getHour();
        boolean isWeekend = (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY);
        if (hour >= 22 || hour < 6) {
            return isWeekend ? new BigDecimal("1.35") : new BigDecimal("1.30");
        }
        if (isWeekend) {
            return (hour >= 6 && hour < 18) ? new BigDecimal("1.10") : new BigDecimal("1.15");
        }
        return (hour >= 18) ? new BigDecimal("1.10") : BigDecimal.ONE;
    }

    public TimeSlot determineTimeSlot(Instant startTime) {
        return classifyPoint(startTime.atZone(VIETNAM_ZONE));
    }

    public LeadTime determineLeadTime(Instant startTime) {
        long hoursUntilStart = ChronoUnit.HOURS.between(Instant.now(), startTime);
        if (hoursUntilStart < 24) {
            return LeadTime.URGENT;
        } else if (hoursUntilStart < 72) {
            return LeadTime.SOON;
        } else {
            return LeadTime.STANDARD;
        }
    }

    private List<BigDecimal> fetchRecentHistoricalWages(String jobType) {
        if (shiftRepository == null || jobType == null || jobType.isBlank()) {
            return List.of();
        }
        try {
            Instant since = Instant.now().minus(HISTORICAL_WINDOW_DAYS, ChronoUnit.DAYS);
            return shiftRepository.findRecentHourlyWagesByJobTypeExcludingStatus(
                    jobType, ShiftStatus.CANCELLED, since
            );
        } catch (Exception e) {
            log.debug("Không thể truy vấn thống kê lịch sử ca làm: {}", e.getMessage());
            return List.of();
        }
    }

    private BigDecimal computeMedian(List<BigDecimal> sorted) {
        int n = sorted.size();
        if (n == 0) return null;
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2))
                .divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveTimeSlotMultiplier(TimeSlot timeSlot) {
        return switch (timeSlot) {
            case NIGHT_WEEKDAY -> new BigDecimal("1.30");
            case EVENING_WEEKEND -> new BigDecimal("1.15");
            case EVENING_WEEKDAY, DAYTIME_WEEKEND -> new BigDecimal("1.10");
            case DAYTIME_WEEKDAY -> BigDecimal.ONE;
        };
    }

    private BigDecimal resolveLeadTimeMultiplier(LeadTime leadTime) {
        return switch (leadTime) {
            case URGENT -> new BigDecimal("1.20");
            case SOON -> new BigDecimal("1.10");
            case STANDARD -> BigDecimal.ONE;
        };
    }

    private BigDecimal roundToThousand(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.divide(new BigDecimal("1000"), 0, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("1000"));
    }

    private List<String> buildEvidenceFactors(
            String regionLabel,
            BigDecimal regionFloor,
            BigDecimal marketMult,
            JobTier jobTier,
            WageMatrixService.BenchmarkLookupResult lookup,
            TimeAdjustment timeAdj,
            LeadTime leadTime,
            BigDecimal leadTimeMultiplier,
            BigDecimal effectiveLegalFloor,
            int sampleCount,
            BigDecimal platformMedian,
            BigDecimal platformMin,
            BigDecimal platformMax,
            boolean catalogMatched
    ) {
        List<String> factors = new ArrayList<>();

        factors.add(String.format(
                "Sàn pháp lý %s: %,d đ/giờ theo %s%s.",
                regionLabel,
                regionFloor.longValue(),
                LEGAL_WAGE_DECREE,
                timeAdj.nightHourRatio().compareTo(BigDecimal.ZERO) > 0
                        ? String.format(" (sàn hiệu dụng gồm phụ phí đêm: %,d đ/giờ)", effectiveLegalFloor.longValue())
                        : ""
        ));

        if (lookup.getOverrideBaseWage() != null) {
            factors.add(String.format(
                    "Đơn giá nền được Admin ghim cố định cho nhóm '%s' tại %s: %,d đ/giờ.",
                    lookup.getTierLabel(), lookup.getRegionName(), lookup.getOverrideBaseWage().longValue()
            ));
        } else {
            String multStr = String.format("%.2fx – %.2fx (đề xuất %.2fx)",
                    lookup.getMinMultiplier(), lookup.getMaxMultiplier(), lookup.getRecommendedMultiplier());
            String marketNote = marketMult.compareTo(BigDecimal.ONE) != 0
                    ? String.format(" × hệ số thị trường %.2fx", marketMult)
                    : "";
            factors.add(String.format(
                    "Nhóm công việc [%s]%s: hệ số Bảng A %s%s.",
                    lookup.getTierLabel(),
                    catalogMatched ? "" : " (tự động phân loại từ tên vị trí tùy chỉnh)",
                    multStr,
                    marketNote
            ));
        }

        if (sampleCount > 0 && platformMedian != null) {
            factors.add(String.format(
                    "Dữ liệu thực tế CANi (%d ca gần nhất trong 90 ngày): trung vị %,d đ/giờ (biên độ %,d – %,d đ/giờ).",
                    sampleCount,
                    platformMedian.longValue(),
                    platformMin != null ? platformMin.longValue() : platformMedian.longValue(),
                    platformMax != null ? platformMax.longValue() : platformMedian.longValue()
            ));
        }

        if (timeAdj.nightHourRatio().compareTo(BigDecimal.ZERO) > 0) {
            int nightPct = timeAdj.nightHourRatio().multiply(new BigDecimal("100")).intValue();
            int timePct = timeAdj.multiplier().subtract(BigDecimal.ONE).multiply(new BigDecimal("100")).intValue();
            factors.add(String.format(
                    "Khung giờ có %d%% thời lượng làm đêm (22h–06h, +30%% theo Điều 98 BLLĐ 2019): hệ số khung giờ gia quyền +%d%%.",
                    nightPct, timePct
            ));
        } else if (timeAdj.multiplier().compareTo(BigDecimal.ONE) > 0) {
            int pct = timeAdj.multiplier().subtract(BigDecimal.ONE).multiply(new BigDecimal("100")).intValue();
            factors.add(String.format("Khung giờ cao điểm / cuối tuần (%s): điều chỉnh +%d%%.", timeAdj.dominantSlot(), pct));
        } else {
            factors.add("Khung giờ ban ngày ngày thường (06h–18h): giữ hệ số chuẩn 100%.");
        }

        if (leadTimeMultiplier.compareTo(BigDecimal.ONE) > 0) {
            int pct = leadTimeMultiplier.subtract(BigDecimal.ONE).multiply(new BigDecimal("100")).intValue();
            String urgency = leadTime == LeadTime.URGENT ? "Khẩn cấp (< 24h)" : "Cận ngày (24h–72h)";
            factors.add(String.format("Độ gấp tuyển dụng [%s]: cộng +%d%% để tăng tỷ lệ Worker nhận ca.", urgency, pct));
        }

        return factors;
    }
}
