package com.cani.service;

import com.cani.dto.response.PageResponse;
import com.cani.dto.wage.UpdateWageRegionRequestDto;
import com.cani.dto.wage.WageMatrixRequestDto;
import com.cani.dto.wage.WageMatrixResponseDto;
import com.cani.dto.wage.WageRegionDto;
import com.cani.entity.WageMatrix;
import com.cani.entity.WageRegion;
import com.cani.entity.enums.JobTier;
import com.cani.entity.enums.WageRegionCode;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.WageMatrixRepository;
import com.cani.repository.WageRegionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WageMatrixService {

    private final WageMatrixRepository wageMatrixRepository;
    private final WageRegionRepository wageRegionRepository;

    @Data
    @Builder
    public static class BenchmarkLookupResult {
        private WageRegionCode regionCode;
        private String regionName;
        private BigDecimal legalFloorHourly;
        private BigDecimal marketMultiplier;
        private JobTier jobTier;
        private String tierLabel;
        private BigDecimal minMultiplier;
        private BigDecimal recommendedMultiplier;
        private BigDecimal maxMultiplier;
        private BigDecimal overrideBaseWage;
        private UUID matchedRowId;
        private int fallbackLevel;
    }

    @Transactional(readOnly = true)
    public List<WageRegionDto> getAllRegions() {
        List<WageRegion> stored = wageRegionRepository.findAllByOrderByCodeAsc();
        if (stored.isEmpty()) {
            return Arrays.stream(WageRegionCode.values())
                    .map(code -> WageRegionDto.builder()
                            .code(code)
                            .name(code.getDisplayName())
                            .legalFloorHourly(code.getDefaultStatutoryFloor())
                            .marketMultiplier(BigDecimal.ONE)
                            .isActive(true)
                            .build())
                    .toList();
        }
        return stored.stream().map(this::mapRegionToDto).toList();
    }

    @Transactional
    public WageRegionDto updateRegion(WageRegionCode code, UpdateWageRegionRequestDto request) {
        WageRegion region = wageRegionRepository.findById(code)
                .orElseGet(() -> WageRegion.builder()
                        .code(code)
                        .name(code.getDisplayName())
                        .legalFloorHourly(code.getDefaultStatutoryFloor())
                        .marketMultiplier(BigDecimal.ONE)
                        .isActive(true)
                        .build());

        region.setName(request.getName().trim());
        region.setLegalFloorHourly(request.getLegalFloorHourly());
        region.setMarketMultiplier(request.getMarketMultiplier());
        region.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        if (request.getIsActive() != null) {
            region.setIsActive(request.getIsActive());
        }

        WageRegion saved = wageRegionRepository.save(region);
        log.info("Đã cập nhật cấu hình vùng lương {}: floor={}, marketMultiplier={}",
                code, saved.getLegalFloorHourly(), saved.getMarketMultiplier());
        return mapRegionToDto(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<WageMatrixResponseDto> filterWageMatrix(
            WageRegionCode regionCode, JobTier jobTier, Boolean isActive, Pageable pageable
    ) {
        Specification<WageMatrix> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (regionCode != null) {
                predicates.add(cb.equal(root.get("regionCode"), regionCode));
            }
            if (jobTier != null) {
                predicates.add(cb.equal(root.get("jobTier"), jobTier));
            }
            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Map<WageRegionCode, WageRegion> regionsByCode = wageRegionRepository.findAll().stream()
                .collect(Collectors.toMap(WageRegion::getCode, Function.identity(), (a, b) -> a));

        Page<WageMatrix> page = wageMatrixRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(wm -> mapToDto(wm, regionsByCode.get(wm.getRegionCode()))));
    }

    @Transactional(readOnly = true)
    public WageMatrixResponseDto getWageMatrixById(UUID matrixId) {
        WageMatrix wm = wageMatrixRepository.findById(matrixId)
                .orElseThrow(() -> new ResourceNotFoundException("WageMatrix", "id", matrixId));
        WageRegion region = wageRegionRepository.findById(wm.getRegionCode()).orElse(null);
        return mapToDto(wm, region);
    }

    @Transactional
    public WageMatrixResponseDto createWageMatrix(WageMatrixRequestDto request) {
        Optional<WageMatrix> existing = wageMatrixRepository.findByRegionCodeAndJobTier(
                request.getRegionCode(), request.getJobTier()
        );
        if (existing.isPresent()) {
            throw new BadRequestException("Cấu hình thù lao cho cặp Vùng và Nhóm công việc này đã tồn tại");
        }

        WageRegion region = resolveOrDefaultRegion(request.getRegionCode());
        validateMultipliersAndOverride(request, region);

        WageMatrix wm = WageMatrix.builder()
                .regionCode(request.getRegionCode())
                .jobTier(request.getJobTier())
                .tierLabel(request.getTierLabel().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .minMultiplier(request.getMinMultiplier())
                .recommendedMultiplier(request.getRecommendedMultiplier())
                .maxMultiplier(request.getMaxMultiplier())
                .overrideBaseWage(request.getOverrideBaseWage())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        WageMatrix saved = wageMatrixRepository.save(wm);
        log.info("Đã tạo mới cấu hình Wage Matrix Region x JobTier: id={}, region={}, tier={}",
                saved.getMatrixId(), saved.getRegionCode(), saved.getJobTier());
        return mapToDto(saved, region);
    }

    @Transactional
    public WageMatrixResponseDto updateWageMatrix(UUID matrixId, WageMatrixRequestDto request) {
        WageMatrix wm = wageMatrixRepository.findById(matrixId)
                .orElseThrow(() -> new ResourceNotFoundException("WageMatrix", "id", matrixId));

        if (wm.getRegionCode() != request.getRegionCode() || wm.getJobTier() != request.getJobTier()) {
            Optional<WageMatrix> duplicate = wageMatrixRepository.findByRegionCodeAndJobTier(
                    request.getRegionCode(), request.getJobTier()
            );
            if (duplicate.isPresent() && !duplicate.get().getMatrixId().equals(matrixId)) {
                throw new BadRequestException("Cấu hình thù lao cho cặp Vùng và Nhóm công việc này đã tồn tại");
            }
        }

        WageRegion region = resolveOrDefaultRegion(request.getRegionCode());
        validateMultipliersAndOverride(request, region);

        wm.setRegionCode(request.getRegionCode());
        wm.setJobTier(request.getJobTier());
        wm.setTierLabel(request.getTierLabel().trim());
        wm.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        wm.setMinMultiplier(request.getMinMultiplier());
        wm.setRecommendedMultiplier(request.getRecommendedMultiplier());
        wm.setMaxMultiplier(request.getMaxMultiplier());
        wm.setOverrideBaseWage(request.getOverrideBaseWage());
        if (request.getIsActive() != null) {
            wm.setIsActive(request.getIsActive());
        }

        WageMatrix saved = wageMatrixRepository.save(wm);
        log.info("Đã cập nhật cấu hình Wage Matrix: id={}", saved.getMatrixId());
        return mapToDto(saved, region);
    }

    @Transactional
    public WageMatrixResponseDto toggleActive(UUID matrixId) {
        WageMatrix wm = wageMatrixRepository.findById(matrixId)
                .orElseThrow(() -> new ResourceNotFoundException("WageMatrix", "id", matrixId));
        wm.setIsActive(!Boolean.TRUE.equals(wm.getIsActive()));
        WageMatrix saved = wageMatrixRepository.save(wm);
        WageRegion region = wageRegionRepository.findById(saved.getRegionCode()).orElse(null);
        return mapToDto(saved, region);
    }

    @Transactional
    public void deleteWageMatrix(UUID matrixId) {
        if (!wageMatrixRepository.existsById(matrixId)) {
            throw new ResourceNotFoundException("WageMatrix", "id", matrixId);
        }
        wageMatrixRepository.deleteById(matrixId);
        log.info("Đã xóa cấu hình Wage Matrix: id={}", matrixId);
    }

    /**
     * Tra cứu cấu hình Region x JobTier theo 3 tầng:
     * - Level 0: Khớp chính xác (regionCode, jobTier) đang active trong bảng wage_matrix.
     * - Level 1: Lấy hệ số của jobTier ở một vùng khác đang active kết hợp với sàn của regionCode.
     * - Level 2: Fallback về hệ số chuẩn Bảng A (enum JobTier) và sàn vùng (WageRegionCode).
     */
    @Transactional(readOnly = true)
    public BenchmarkLookupResult lookupBenchmark(WageRegionCode regionCode, JobTier jobTier) {
        WageRegion region = resolveOrDefaultRegion(regionCode);
        BigDecimal floor = region.getLegalFloorHourly();
        BigDecimal marketMult = region.getMarketMultiplier() != null ? region.getMarketMultiplier() : BigDecimal.ONE;

        Optional<WageMatrix> exact = wageMatrixRepository.findByRegionCodeAndJobTier(regionCode, jobTier);
        if (exact.isPresent() && Boolean.TRUE.equals(exact.get().getIsActive())) {
            WageMatrix row = exact.get();
            return BenchmarkLookupResult.builder()
                    .regionCode(regionCode)
                    .regionName(region.getName())
                    .legalFloorHourly(floor)
                    .marketMultiplier(marketMult)
                    .jobTier(jobTier)
                    .tierLabel(row.getTierLabel())
                    .minMultiplier(row.getMinMultiplier())
                    .recommendedMultiplier(row.getRecommendedMultiplier())
                    .maxMultiplier(row.getMaxMultiplier())
                    .overrideBaseWage(row.getOverrideBaseWage())
                    .matchedRowId(row.getMatrixId())
                    .fallbackLevel(0)
                    .build();
        }

        List<WageMatrix> otherRegions = wageMatrixRepository.findByJobTierAndIsActiveTrue(jobTier);
        if (!otherRegions.isEmpty()) {
            WageMatrix row = otherRegions.get(0);
            return BenchmarkLookupResult.builder()
                    .regionCode(regionCode)
                    .regionName(region.getName())
                    .legalFloorHourly(floor)
                    .marketMultiplier(marketMult)
                    .jobTier(jobTier)
                    .tierLabel(row.getTierLabel())
                    .minMultiplier(row.getMinMultiplier())
                    .recommendedMultiplier(row.getRecommendedMultiplier())
                    .maxMultiplier(row.getMaxMultiplier())
                    .overrideBaseWage(null)
                    .matchedRowId(row.getMatrixId())
                    .fallbackLevel(1)
                    .build();
        }

        return BenchmarkLookupResult.builder()
                .regionCode(regionCode)
                .regionName(region.getName())
                .legalFloorHourly(floor)
                .marketMultiplier(marketMult)
                .jobTier(jobTier)
                .tierLabel(jobTier.getLabel())
                .minMultiplier(jobTier.getDefaultMinMultiplier())
                .recommendedMultiplier(jobTier.getDefaultRecommendedMultiplier())
                .maxMultiplier(jobTier.getDefaultMaxMultiplier())
                .overrideBaseWage(null)
                .matchedRowId(null)
                .fallbackLevel(2)
                .build();
    }

    private WageRegion resolveOrDefaultRegion(WageRegionCode code) {
        return wageRegionRepository.findById(code)
                .orElseGet(() -> WageRegion.builder()
                        .code(code)
                        .name(code.getDisplayName())
                        .legalFloorHourly(code.getDefaultStatutoryFloor())
                        .marketMultiplier(BigDecimal.ONE)
                        .isActive(true)
                        .build());
    }

    private void validateMultipliersAndOverride(WageMatrixRequestDto request, WageRegion region) {
        if (request.getMinMultiplier().compareTo(request.getRecommendedMultiplier()) > 0
                || request.getRecommendedMultiplier().compareTo(request.getMaxMultiplier()) > 0) {
            throw new BadRequestException("Hệ số phải thỏa mãn: tối thiểu <= khuyên dùng <= tối đa");
        }
        if (request.getOverrideBaseWage() != null) {
            if (request.getOverrideBaseWage().compareTo(region.getLegalFloorHourly()) < 0) {
                throw new BadRequestException(String.format(
                        "Đơn giá ghim cố định không được thấp hơn sàn pháp lý của %s (%,d đ/giờ)",
                        region.getName(), region.getLegalFloorHourly().longValue()
                ));
            }
        }
    }

    private WageRegionDto mapRegionToDto(WageRegion region) {
        return WageRegionDto.builder()
                .code(region.getCode())
                .name(region.getName())
                .legalFloorHourly(region.getLegalFloorHourly())
                .marketMultiplier(region.getMarketMultiplier())
                .description(region.getDescription())
                .isActive(region.getIsActive())
                .updatedAt(region.getUpdatedAt())
                .build();
    }

    private WageMatrixResponseDto mapToDto(WageMatrix wm, WageRegion region) {
        BigDecimal floor = region != null ? region.getLegalFloorHourly() : wm.getRegionCode().getDefaultStatutoryFloor();
        BigDecimal marketMult = (region != null && region.getMarketMultiplier() != null)
                ? region.getMarketMultiplier()
                : BigDecimal.ONE;
        String regionName = region != null ? region.getName() : wm.getRegionCode().getDisplayName();

        BigDecimal baseFactor = floor.multiply(marketMult);
        BigDecimal computedMin = roundToHundred(baseFactor.multiply(wm.getMinMultiplier()));
        BigDecimal computedSuggested = wm.getOverrideBaseWage() != null
                ? roundToThousand(wm.getOverrideBaseWage())
                : roundToThousand(baseFactor.multiply(wm.getRecommendedMultiplier()));
        BigDecimal computedMax = roundToHundred(baseFactor.multiply(wm.getMaxMultiplier()));

        return WageMatrixResponseDto.builder()
                .matrixId(wm.getMatrixId())
                .regionCode(wm.getRegionCode())
                .regionName(regionName)
                .legalFloorHourly(floor)
                .marketMultiplier(marketMult)
                .jobTier(wm.getJobTier())
                .jobTierLabel(wm.getJobTier().getLabel())
                .tierLabel(wm.getTierLabel())
                .description(wm.getDescription())
                .minMultiplier(wm.getMinMultiplier())
                .recommendedMultiplier(wm.getRecommendedMultiplier())
                .maxMultiplier(wm.getMaxMultiplier())
                .overrideBaseWage(wm.getOverrideBaseWage())
                .computedMinWage(computedMin)
                .computedSuggestedWage(computedSuggested)
                .computedMaxWage(computedMax)
                .isActive(wm.getIsActive())
                .createdAt(wm.getCreatedAt())
                .updatedAt(wm.getUpdatedAt())
                .build();
    }

    private BigDecimal roundToHundred(BigDecimal val) {
        if (val == null) return BigDecimal.ZERO;
        return val.divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    private BigDecimal roundToThousand(BigDecimal val) {
        if (val == null) return BigDecimal.ZERO;
        return val.divide(new BigDecimal("1000"), 0, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("1000"));
    }
}
