package com.cani.service;

import com.cani.dto.wage.WageRecommendationRequestDto;
import com.cani.dto.wage.WageRecommendationResponseDto;
import com.cani.entity.WageRecommendation;
import com.cani.entity.enums.City;
import com.cani.entity.enums.LeadTime;
import com.cani.entity.enums.ShiftStatus;
import com.cani.entity.enums.TimeSlot;
import com.cani.repository.ShiftRepository;
import com.cani.repository.WageRecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WageRecommendationService {

    private final WageMatrixService wageMatrixService;
    private final WageRecommendationRepository wageRecommendationRepository;

    @Autowired(required = false)
    private ShiftRepository shiftRepository;

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    // Sàn lương tối thiểu giờ theo Nghị định 293/2025/NĐ-CP (hiệu lực từ 01/01/2026)
    private static final BigDecimal REGION_1_MIN_HOURLY = new BigDecimal("25500");
    private static final BigDecimal REGION_2_MIN_HOURLY = new BigDecimal("22700");
    private static final String LEGAL_WAGE_DECREE = "Nghị định 293/2025/NĐ-CP (hiệu lực từ 01/01/2026)";

    // Mức thù lao nền theo giờ (ban ngày ngày thường, tuyển tiêu chuẩn >= 72h) cho danh mục vị trí sự kiện chuẩn CANi
    private static final Map<String, BigDecimal> INDUSTRY_ROLE_BASELINES = Map.ofEntries(
            Map.entry("lễ tân / check-in", new BigDecimal("50000")),
            Map.entry("phục vụ bàn", new BigDecimal("40000")),
            Map.entry("pha chế (bartender/barista)", new BigDecimal("70000")),
            Map.entry("thu ngân", new BigDecimal("45000")),
            Map.entry("điều phối sự kiện", new BigDecimal("80000")),
            Map.entry("kỹ thuật âm thanh - ánh sáng", new BigDecimal("95000")),
            Map.entry("bảo vệ / an ninh", new BigDecimal("45000")),
            Map.entry("hậu cần / setup sự kiện", new BigDecimal("55000")),
            Map.entry("pg / pb", new BigDecimal("85000"))
    );

    private static final BigDecimal GENERAL_EVENT_BASELINE = new BigDecimal("45000");

    /**
     * Gợi ý mức thù lao tối ưu cho ca làm việc kèm dẫn chứng pháp lý & dữ liệu thực tế (WBS 337.0 / UC-14)
     */
    @Transactional
    public WageRecommendationResponseDto recommendWage(WageRecommendationRequestDto request) {
        String normalizedRole = request.getJobType() != null ? request.getJobType().trim() : "";
        String roleKey = normalizedRole.toLowerCase(Locale.ROOT);
        log.info("Tính toán mức thù lao khuyến nghị cho: city={}, jobType={}, startTime={}",
                request.getCity(), normalizedRole, request.getStartTime());

        TimeSlot timeSlot = determineTimeSlot(request.getStartTime());
        LeadTime leadTime = determineLeadTime(request.getStartTime());

        BigDecimal legalFloorWage = resolveLegalFloorWage(request.getCity());
        String legalRegionLabel = resolveLegalRegionLabel(request.getCity());

        // 1. Tra cứu ma trận Admin (UC-34)
        WageMatrixService.LookupResult lookup = wageMatrixService.lookupWage(
                request.getCity(),
                normalizedRole,
                timeSlot,
                leadTime
        );

        // 2. Thống kê dữ liệu thực tế các ca đã tạo trên nền tảng CANi
        List<BigDecimal> historicalWages = fetchHistoricalWages(normalizedRole);
        int sampleCount = historicalWages.size();
        BigDecimal platformAvg = null;
        BigDecimal platformMin = null;
        BigDecimal platformMax = null;
        if (sampleCount > 0) {
            BigDecimal sum = historicalWages.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            platformAvg = sum.divide(BigDecimal.valueOf(sampleCount), 0, RoundingMode.HALF_UP);
            platformMin = historicalWages.stream().min(Comparator.naturalOrder()).orElse(null);
            platformMax = historicalWages.stream().max(Comparator.naturalOrder()).orElse(null);
        }

        // 3. Xác định mức nền, nguồn dẫn chứng & tính toán hệ số minh bạch
        BigDecimal timeSlotMultiplier = resolveTimeSlotMultiplier(timeSlot);
        BigDecimal leadTimeMultiplier = resolveLeadTimeMultiplier(leadTime);

        boolean catalogMatched;
        String benchmarkSource;
        BigDecimal rawSuggested;

        if (lookup.getFallbackLevel() <= 3) {
            // Đã khớp WageMatrix Admin -> Level 0,1 đã bao gồm điều chỉnh thời điểm/độ gấp từ bảng ma trận
            catalogMatched = true;
            benchmarkSource = "CANI_WAGE_MATRIX";
            rawSuggested = lookup.getWagePerHour();
            if (lookup.getFallbackLevel() >= 2) {
                rawSuggested = rawSuggested.multiply(timeSlotMultiplier).multiply(leadTimeMultiplier);
            }
        } else if (sampleCount >= 3 && platformAvg != null) {
            catalogMatched = true;
            benchmarkSource = "CANI_HISTORICAL_DATA";
            rawSuggested = platformAvg.multiply(timeSlotMultiplier).multiply(leadTimeMultiplier);
        } else if (INDUSTRY_ROLE_BASELINES.containsKey(roleKey)) {
            catalogMatched = true;
            benchmarkSource = "CANI_INDUSTRY_CATALOG";
            rawSuggested = INDUSTRY_ROLE_BASELINES.get(roleKey)
                    .multiply(timeSlotMultiplier)
                    .multiply(leadTimeMultiplier);
        } else {
            catalogMatched = false;
            benchmarkSource = "GENERAL_EVENT_FALLBACK";
            BigDecimal base = (sampleCount > 0 && platformAvg != null)
                    ? platformAvg.max(GENERAL_EVENT_BASELINE)
                    : GENERAL_EVENT_BASELINE;
            rawSuggested = base.multiply(timeSlotMultiplier).multiply(leadTimeMultiplier);
        }

        BigDecimal roundedLegalFloor = roundToThousand(legalFloorWage);
        BigDecimal suggestedWage = roundToThousand(rawSuggested).max(roundedLegalFloor);
        BigDecimal minWage = roundToThousand(suggestedWage.multiply(new BigDecimal("0.85"))).max(roundedLegalFloor);
        BigDecimal maxWage = roundToThousand(suggestedWage.multiply(new BigDecimal("1.25"))).max(suggestedWage.add(new BigDecimal("5000")));

        List<String> evidenceFactors = buildEvidenceFactors(
                request.getCity(),
                normalizedRole,
                timeSlot,
                leadTime,
                timeSlotMultiplier,
                leadTimeMultiplier,
                legalFloorWage,
                legalRegionLabel,
                sampleCount,
                platformAvg,
                platformMin,
                platformMax,
                catalogMatched,
                benchmarkSource
        );

        String explanation = generateExplanation(
                request,
                timeSlot,
                leadTime,
                lookup.getFallbackLevel(),
                suggestedWage,
                catalogMatched,
                sampleCount
        );

        String storedJobType = normalizedRole.length() > 100 ? normalizedRole.substring(0, 100) : normalizedRole;
        WageRecommendation recommendation = WageRecommendation.builder()
                .city(request.getCity())
                .jobType(storedJobType)
                .timeSlot(timeSlot)
                .leadTime(leadTime)
                .suggestedWage(suggestedWage)
                .matchedRowId(lookup.getMatchedRowId())
                .fallbackLevel(lookup.getFallbackLevel())
                .shiftId(request.getShiftId())
                .build();

        WageRecommendation saved = wageRecommendationRepository.save(recommendation);
        log.info("Đã lưu khuyến nghị thù lao: id={}, suggestedWage={}, source={}",
                saved.getRecommendationId(), suggestedWage, benchmarkSource);

        return WageRecommendationResponseDto.builder()
                .recommendationId(saved.getRecommendationId())
                .shiftId(request.getShiftId())
                .city(request.getCity())
                .jobType(normalizedRole)
                .timeSlot(timeSlot)
                .leadTime(leadTime)
                .suggestedWage(suggestedWage)
                .minWage(minWage)
                .maxWage(maxWage)
                .explanation(explanation)
                .matchedRowId(lookup.getMatchedRowId())
                .fallbackLevel(lookup.getFallbackLevel())
                .legalFloorWage(legalFloorWage)
                .legalRegionLabel(legalRegionLabel)
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
     * Xác định TimeSlot từ thời gian bắt đầu ca làm
     */
    public TimeSlot determineTimeSlot(Instant startTime) {
        ZonedDateTime zdt = startTime.atZone(VIETNAM_ZONE);
        DayOfWeek dow = zdt.getDayOfWeek();
        int hour = zdt.getHour();

        boolean isWeekend = (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY);

        if (isWeekend) {
            return (hour >= 6 && hour < 18) ? TimeSlot.DAYTIME_WEEKEND : TimeSlot.EVENING_WEEKEND;
        } else {
            if (hour >= 6 && hour < 18) {
                return TimeSlot.DAYTIME_WEEKDAY;
            } else if (hour >= 18 && hour < 22) {
                return TimeSlot.EVENING_WEEKDAY;
            } else {
                return TimeSlot.NIGHT_WEEKDAY;
            }
        }
    }

    /**
     * Xác định LeadTime từ độ trễ giữa thời điểm hiện tại và thời gian bắt đầu ca
     */
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

    private List<BigDecimal> fetchHistoricalWages(String jobType) {
        if (shiftRepository == null || jobType == null || jobType.isBlank()) {
            return List.of();
        }
        try {
            return shiftRepository.findHourlyWagesByJobTypeExcludingStatus(jobType, ShiftStatus.CANCELLED);
        } catch (Exception e) {
            log.debug("Không thể truy vấn thống kê lịch sử ca làm: {}", e.getMessage());
            return List.of();
        }
    }

    private BigDecimal resolveLegalFloorWage(City city) {
        if (city == City.DANANG) {
            return REGION_2_MIN_HOURLY;
        }
        return REGION_1_MIN_HOURLY;
    }

    private String resolveLegalRegionLabel(City city) {
        if (city == City.DANANG) {
            return "Vùng II (Đà Nẵng nội thành)";
        }
        if (city == City.HANOI) {
            return "Vùng I (Hà Nội)";
        }
        return "Vùng I (TP. Hồ Chí Minh)";
    }

    private BigDecimal resolveTimeSlotMultiplier(TimeSlot timeSlot) {
        return switch (timeSlot) {
            case NIGHT_WEEKDAY -> new BigDecimal("1.30"); // Tham chiếu tối thiểu +30% làm đêm (Điều 98 BLLĐ 2019)
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
            City city,
            String jobType,
            TimeSlot timeSlot,
            LeadTime leadTime,
            BigDecimal timeSlotMultiplier,
            BigDecimal leadTimeMultiplier,
            BigDecimal legalFloorWage,
            String legalRegionLabel,
            int sampleCount,
            BigDecimal platformAvg,
            BigDecimal platformMin,
            BigDecimal platformMax,
            boolean catalogMatched,
            String benchmarkSource
    ) {
        List<String> factors = new ArrayList<>();

        factors.add(String.format(
                "Sàn pháp lý %s: %,d đ/giờ theo %s.",
                legalRegionLabel,
                legalFloorWage.longValue(),
                LEGAL_WAGE_DECREE
        ));

        if ("CANI_WAGE_MATRIX".equals(benchmarkSource)) {
            factors.add(String.format(
                    "Đối chiếu Biểu Ma trận Thù lao CANi cho vị trí '%s' tại %s.",
                    jobType, city
            ));
        } else if (sampleCount > 0 && platformAvg != null) {
            factors.add(String.format(
                    "Dữ liệu thực tế trên CANi (%d ca '%s' gần đây): trung bình %,d đ/giờ (biên độ %,d – %,d đ/giờ).",
                    sampleCount,
                    jobType,
                    platformAvg.longValue(),
                    platformMin != null ? platformMin.longValue() : platformAvg.longValue(),
                    platformMax != null ? platformMax.longValue() : platformAvg.longValue()
            ));
        } else if (catalogMatched) {
            factors.add(String.format(
                    "Mức chuẩn ngành sự kiện cho vai trò '%s' (chưa phát sinh đủ %d ca mẫu riêng tại %s nên dùng biểu chuẩn nền tảng).",
                    jobType, 3, city
            ));
        } else {
            factors.add(String.format(
                    "Vị trí tùy chỉnh '%s' chưa có trong danh mục chuẩn và mới ghi nhận %d ca trên CANi — đang áp dụng khung tham chiếu chung ngành Sự kiện.",
                    jobType, sampleCount
            ));
        }

        if (timeSlot == TimeSlot.NIGHT_WEEKDAY) {
            factors.add("Khung giờ ban đêm (từ 22h–06h): cộng tối thiểu +30% theo nguyên tắc làm việc ban đêm (Điều 98 Bộ luật Lao động 2019).");
        } else if (timeSlotMultiplier.compareTo(BigDecimal.ONE) > 0) {
            int pct = timeSlotMultiplier.subtract(BigDecimal.ONE).multiply(new BigDecimal("100")).intValue();
            String slotName = (timeSlot == TimeSlot.DAYTIME_WEEKEND || timeSlot == TimeSlot.EVENING_WEEKEND)
                    ? "Cuối tuần"
                    : "Buổi tối (18h–22h)";
            factors.add(String.format("Khung giờ %s: điều chỉnh +%d%% do nhu cầu nhân sự sự kiện cao điểm.", slotName, pct));
        } else {
            factors.add("Khung giờ ban ngày ngày thường (06h–18h): áp dụng hệ số chuẩn 100% (chưa phát sinh phụ phí giờ).");
        }

        if (leadTimeMultiplier.compareTo(BigDecimal.ONE) > 0) {
            int pct = leadTimeMultiplier.subtract(BigDecimal.ONE).multiply(new BigDecimal("100")).intValue();
            String urgency = leadTime == LeadTime.URGENT ? "Khẩn cấp (< 24h trước giờ ca)" : "Cận ngày (24h–72h trước giờ ca)";
            factors.add(String.format("Độ gấp tuyển dụng [%s]: khuyến nghị cộng +%d%% để tăng tỷ lệ Worker nhận ca kịp tiến độ.", urgency, pct));
        }

        return factors;
    }

    private String generateExplanation(
            WageRecommendationRequestDto request,
            TimeSlot timeSlot,
            LeadTime leadTime,
            int fallbackLevel,
            BigDecimal wage,
            boolean catalogMatched,
            int sampleCount
    ) {
        String sourceDesc = switch (fallbackLevel) {
            case 0, 1 -> "khớp trực tiếp theo Biểu ma trận thù lao CANi";
            case 2, 3 -> "theo mức trung bình của vị trí trong hệ thống CANi";
            default -> sampleCount >= 3
                    ? "tổng hợp từ " + sampleCount + " ca thực tế trên CANi"
                    : (catalogMatched
                    ? "theo khung chuẩn ngành nhân sự sự kiện CANi + sàn pháp lý NĐ 293/2025/NĐ-CP"
                    : "theo khung tham chiếu chung ngành sự kiện + sàn pháp lý NĐ 293/2025/NĐ-CP");
        };

        return String.format(
                "Đề xuất %,d đ/giờ cho '%s' (%s, %s, %s) — tính %s.",
                wage.longValue(),
                request.getJobType(),
                request.getCity(),
                timeSlot,
                leadTime,
                sourceDesc
        );
    }
}
