package com.cani.service;

import com.cani.dto.wage.WageMatrixRequestDto;
import com.cani.dto.wage.WageMatrixResponseDto;
import com.cani.entity.WageMatrix;
import com.cani.entity.WageRegion;
import com.cani.entity.enums.JobTier;
import com.cani.entity.enums.WageRegionCode;
import com.cani.exception.BadRequestException;
import com.cani.repository.WageMatrixRepository;
import com.cani.repository.WageRegionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WageMatrixServiceTest {

    @Mock
    private WageMatrixRepository wageMatrixRepository;

    @Mock
    private WageRegionRepository wageRegionRepository;

    @InjectMocks
    private WageMatrixService wageMatrixService;

    @Test
    @DisplayName("Normal - Tạo cấu hình Region x JobTier hợp lệ và quy đổi đúng đơn giá giờ")
    void createWageMatrix_Success_ComputesPreviewWages() {
        WageRegion region1 = WageRegion.builder()
                .code(WageRegionCode.REGION_1)
                .name("Vùng I")
                .legalFloorHourly(new BigDecimal("25500.00"))
                .marketMultiplier(BigDecimal.ONE)
                .isActive(true)
                .build();

        WageMatrixRequestDto req = WageMatrixRequestDto.builder()
                .regionCode(WageRegionCode.REGION_1)
                .jobTier(JobTier.TIER_2_SERVICE)
                .tierLabel("Giao tiếp & Dịch vụ")
                .minMultiplier(new BigDecimal("1.30"))
                .recommendedMultiplier(new BigDecimal("1.55"))
                .maxMultiplier(new BigDecimal("1.80"))
                .isActive(true)
                .build();

        when(wageMatrixRepository.findByRegionCodeAndJobTier(WageRegionCode.REGION_1, JobTier.TIER_2_SERVICE))
                .thenReturn(Optional.empty());
        when(wageRegionRepository.findById(WageRegionCode.REGION_1)).thenReturn(Optional.of(region1));
        when(wageMatrixRepository.save(any(WageMatrix.class))).thenAnswer(inv -> {
            WageMatrix wm = inv.getArgument(0);
            wm.setMatrixId(UUID.randomUUID());
            return wm;
        });

        WageMatrixResponseDto dto = wageMatrixService.createWageMatrix(req);

        assertEquals(0, new BigDecimal("33200").compareTo(dto.getComputedMinWage()));
        assertEquals(0, new BigDecimal("40000").compareTo(dto.getComputedSuggestedWage()));
        assertEquals(0, new BigDecimal("45900").compareTo(dto.getComputedMaxWage()));
    }

    @Test
    @DisplayName("Abnormal - Chặn tạo nếu hệ số min > recommended hoặc override thấp hơn sàn vùng")
    void createWageMatrix_ThrowsBadRequest_WhenInvalidMultipliersOrBelowFloor() {
        WageRegion region2 = WageRegion.builder()
                .code(WageRegionCode.REGION_2)
                .name("Vùng II")
                .legalFloorHourly(new BigDecimal("22700.00"))
                .marketMultiplier(BigDecimal.ONE)
                .isActive(true)
                .build();

        when(wageMatrixRepository.findByRegionCodeAndJobTier(WageRegionCode.REGION_2, JobTier.TIER_1_GENERAL))
                .thenReturn(Optional.empty());
        when(wageRegionRepository.findById(WageRegionCode.REGION_2)).thenReturn(Optional.of(region2));

        WageMatrixRequestDto invalidMult = WageMatrixRequestDto.builder()
                .regionCode(WageRegionCode.REGION_2)
                .jobTier(JobTier.TIER_1_GENERAL)
                .tierLabel("Phổ thông")
                .minMultiplier(new BigDecimal("1.50"))
                .recommendedMultiplier(new BigDecimal("1.20"))
                .maxMultiplier(new BigDecimal("1.80"))
                .build();
        assertThrows(BadRequestException.class, () -> wageMatrixService.createWageMatrix(invalidMult));

        WageMatrixRequestDto belowFloor = WageMatrixRequestDto.builder()
                .regionCode(WageRegionCode.REGION_2)
                .jobTier(JobTier.TIER_1_GENERAL)
                .tierLabel("Phổ thông")
                .minMultiplier(new BigDecimal("1.00"))
                .recommendedMultiplier(new BigDecimal("1.15"))
                .maxMultiplier(new BigDecimal("1.30"))
                .overrideBaseWage(new BigDecimal("20000"))
                .build();
        assertThrows(BadRequestException.class, () -> wageMatrixService.createWageMatrix(belowFloor));
    }

    @Test
    @DisplayName("Boundary - lookupBenchmark fallback sang vùng khác (Level 1) hoặc enum Bảng A (Level 2)")
    void lookupBenchmark_FallbacksGracefully() {
        WageRegion region4 = WageRegion.builder()
                .code(WageRegionCode.REGION_4)
                .name("Vùng IV")
                .legalFloorHourly(new BigDecimal("17800.00"))
                .marketMultiplier(BigDecimal.ONE)
                .isActive(true)
                .build();

        when(wageRegionRepository.findById(WageRegionCode.REGION_4)).thenReturn(Optional.of(region4));
        when(wageMatrixRepository.findByRegionCodeAndJobTier(WageRegionCode.REGION_4, JobTier.TIER_5_TECHNICAL))
                .thenReturn(Optional.empty());
        when(wageMatrixRepository.findByJobTierAndIsActiveTrue(JobTier.TIER_5_TECHNICAL))
                .thenReturn(List.of());

        WageMatrixService.BenchmarkLookupResult res =
                wageMatrixService.lookupBenchmark(WageRegionCode.REGION_4, JobTier.TIER_5_TECHNICAL);

        assertEquals(2, res.getFallbackLevel());
        assertEquals(0, new BigDecimal("17800.00").compareTo(res.getLegalFloorHourly()));
        assertEquals(0, new BigDecimal("2.50").compareTo(res.getMinMultiplier()));
        assertEquals(0, new BigDecimal("5.00").compareTo(res.getMaxMultiplier()));
    }
}
