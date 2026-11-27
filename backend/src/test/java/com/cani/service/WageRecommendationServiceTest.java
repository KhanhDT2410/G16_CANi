package com.cani.service;

import com.cani.dto.wage.WageRecommendationRequestDto;
import com.cani.dto.wage.WageRecommendationResponseDto;
import com.cani.entity.enums.JobTier;
import com.cani.entity.enums.ShiftStatus;
import com.cani.entity.enums.WageRegionCode;
import com.cani.repository.ShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WageRecommendationServiceTest {

    @Mock
    private WageMatrixService wageMatrixService;

    @Mock
    private ShiftRepository shiftRepository;

    private WageRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new WageRecommendationService(
                wageMatrixService,
                new GeoWageRegionResolver(),
                new JobTierClassifier()
        );
        ReflectionTestUtils.setField(service, "shiftRepository", shiftRepository);
    }

    private Instant vnTime(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .toInstant();
    }

    @Test
    @DisplayName("Normal - Tính đề xuất lương Vùng I theo Bảng A cho ca ban ngày tiêu chuẩn")
    void recommendWage_Region1StandardDaytime_ReturnsBangABenchmark() {
        when(wageMatrixService.lookupBenchmark(WageRegionCode.REGION_1, JobTier.TIER_2_SERVICE))
                .thenReturn(WageMatrixService.BenchmarkLookupResult.builder()
                        .regionCode(WageRegionCode.REGION_1)
                        .regionName("Vùng I")
                        .legalFloorHourly(new BigDecimal("25500.00"))
                        .marketMultiplier(BigDecimal.ONE)
                        .jobTier(JobTier.TIER_2_SERVICE)
                        .tierLabel("Giao tiếp & Dịch vụ")
                        .minMultiplier(new BigDecimal("1.30"))
                        .recommendedMultiplier(new BigDecimal("1.55"))
                        .maxMultiplier(new BigDecimal("1.80"))
                        .fallbackLevel(0)
                        .build());
        when(shiftRepository.findRecentHourlyWagesByJobTypeExcludingStatus(eq("Lễ tân / Check-in"), eq(ShiftStatus.CANCELLED), any()))
                .thenReturn(List.of());

        // 2026-12-08 là thứ Ba (ngày thường), cách hiện tại > 72h (STANDARD)
        WageRecommendationRequestDto req = WageRecommendationRequestDto.builder()
                .venueAddress("Quận 1, TP. Hồ Chí Minh")
                .jobType("Lễ tân / Check-in")
                .startTime(vnTime(2026, 12, 8, 8, 0))
                .endTime(vnTime(2026, 12, 8, 12, 0))
                .build();

        WageRecommendationResponseDto res = service.recommendWage(req);

        assertEquals(WageRegionCode.REGION_1, res.getRegionCode());
        assertEquals(JobTier.TIER_2_SERVICE, res.getJobTier());
        assertEquals(0, new BigDecimal("25500").compareTo(res.getLegalFloorWage()));
        assertEquals(0, new BigDecimal("33000").compareTo(res.getMinWage()));
        assertEquals(0, new BigDecimal("40000").compareTo(res.getSuggestedWage()));
        assertEquals(0, new BigDecimal("46000").compareTo(res.getMaxWage()));
    }

    @Test
    @DisplayName("Boundary - Ca vắt qua khung giờ đêm (17h-23h) phải tính gia quyền M_time và tăng sàn pháp lý đêm")
    void computeWeightedTimeAdjustment_OverlappingNightHours_CalculatesExactWeightAndNightRatio() {
        // 2026-12-08 (Thứ Ba): 17:00-18:00 (1h * 1.00) + 18:00-22:00 (4h * 1.10) + 22:00-23:00 (1h * 1.30) = 6.70 / 6
        WageRecommendationService.TimeAdjustment adj = service.computeWeightedTimeAdjustment(
                vnTime(2026, 12, 8, 17, 0),
                vnTime(2026, 12, 8, 23, 0)
        );

        assertEquals(0, new BigDecimal("1.1167").compareTo(adj.multiplier()));
        assertEquals(0, new BigDecimal("0.1667").compareTo(adj.nightHourRatio()));
    }

    @Test
    @DisplayName("Normal - Dữ liệu lịch sử >= 5 ca dùng Median chống outlier kết hợp Bảng A")
    void recommendWage_WithHistoricalOutlier_BlendsMedianSafely() {
        when(wageMatrixService.lookupBenchmark(WageRegionCode.REGION_2, JobTier.TIER_1_GENERAL))
                .thenReturn(WageMatrixService.BenchmarkLookupResult.builder()
                        .regionCode(WageRegionCode.REGION_2)
                        .regionName("Vùng II")
                        .legalFloorHourly(new BigDecimal("22700.00"))
                        .marketMultiplier(BigDecimal.ONE)
                        .jobTier(JobTier.TIER_1_GENERAL)
                        .tierLabel("Phổ thông")
                        .minMultiplier(new BigDecimal("1.00"))
                        .recommendedMultiplier(new BigDecimal("1.15"))
                        .maxMultiplier(new BigDecimal("1.30"))
                        .fallbackLevel(0)
                        .build());
        // Có 1 outlier 500.000 đ/h nhưng median vẫn là 28.000 đ/h
        when(shiftRepository.findRecentHourlyWagesByJobTypeExcludingStatus(eq("Phát tờ rơi"), eq(ShiftStatus.CANCELLED), any()))
                .thenReturn(List.of(
                        new BigDecimal("26000"),
                        new BigDecimal("27000"),
                        new BigDecimal("28000"),
                        new BigDecimal("29000"),
                        new BigDecimal("500000")
                ));

        WageRecommendationRequestDto req = WageRecommendationRequestDto.builder()
                .venueAddress("Hải Châu, Đà Nẵng")
                .jobType("Phát tờ rơi")
                .startTime(vnTime(2026, 12, 8, 8, 0))
                .endTime(vnTime(2026, 12, 8, 12, 0))
                .build();

        WageRecommendationResponseDto res = service.recommendWage(req);

        assertEquals("CANI_REGION_TIER_HYBRID", res.getBenchmarkSource());
        assertTrue(res.getSuggestedWage().compareTo(new BigDecimal("35000")) < 0);
    }
}
