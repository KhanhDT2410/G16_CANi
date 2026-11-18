package com.cani.service;

import com.cani.dto.response.PageResponse;
import com.cani.dto.wage.WageMatrixRequestDto;
import com.cani.dto.wage.WageMatrixResponseDto;
import com.cani.entity.WageMatrix;
import com.cani.entity.enums.City;
import com.cani.entity.enums.LeadTime;
import com.cani.entity.enums.TimeSlot;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.WageMatrixRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WageMatrixService {

    private final WageMatrixRepository wageMatrixRepository;

    private static final BigDecimal DEFAULT_FALLBACK_WAGE = new BigDecimal("35000.00");

    @Data
    @Builder
    public static class LookupResult {
        private BigDecimal wagePerHour;
        private UUID matchedRowId;
        private int fallbackLevel;
    }

    /**
     * Lấy danh sách ma trận lương có phân trang và lọc
     */
    @Transactional(readOnly = true)
    public PageResponse<WageMatrixResponseDto> filterWageMatrix(
            City city, String jobType, Boolean isActive, Pageable pageable
    ) {
        Specification<WageMatrix> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (city != null) {
                predicates.add(cb.equal(root.get("city"), city));
            }
            if (jobType != null && !jobType.trim().isEmpty()) {
                String pattern = "%" + jobType.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("jobType")), pattern));
            }
            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<WageMatrix> page = wageMatrixRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(this::mapToDto));
    }

    /**
     * Xem chi tiết một bản ghi ma trận lương
     */
    @Transactional(readOnly = true)
    public WageMatrixResponseDto getWageMatrixById(UUID matrixId) {
        WageMatrix wm = wageMatrixRepository.findById(matrixId)
                .orElseThrow(() -> new ResourceNotFoundException("WageMatrix", "id", matrixId));
        return mapToDto(wm);
    }

    /**
     * Tạo mới cấu hình ma trận lương (Admin)
     */
    @Transactional
    public WageMatrixResponseDto createWageMatrix(WageMatrixRequestDto request) {
        String trimmedJobType = request.getJobType().trim();
        Optional<WageMatrix> existing = wageMatrixRepository.findByCityAndJobTypeIgnoreCaseAndTimeSlotAndLeadTime(
                request.getCity(), trimmedJobType, request.getTimeSlot(), request.getLeadTime()
        );
        if (existing.isPresent()) {
            throw new BadRequestException("Cấu hình lương cho tổ hợp này đã tồn tại trong hệ thống");
        }

        WageMatrix wm = WageMatrix.builder()
                .city(request.getCity())
                .jobType(trimmedJobType)
                .timeSlot(request.getTimeSlot())
                .leadTime(request.getLeadTime())
                .wagePerHour(request.getWagePerHour())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        WageMatrix saved = wageMatrixRepository.save(wm);
        log.info("Đã tạo mới cấu hình ma trận lương: id={}", saved.getMatrixId());
        return mapToDto(saved);
    }

    /**
     * Cập nhật cấu hình ma trận lương (Admin)
     */
    @Transactional
    public WageMatrixResponseDto updateWageMatrix(UUID matrixId, WageMatrixRequestDto request) {
        WageMatrix wm = wageMatrixRepository.findById(matrixId)
                .orElseThrow(() -> new ResourceNotFoundException("WageMatrix", "id", matrixId));

        String trimmedJobType = request.getJobType().trim();
        wm.setCity(request.getCity());
        wm.setJobType(trimmedJobType);
        wm.setTimeSlot(request.getTimeSlot());
        wm.setLeadTime(request.getLeadTime());
        wm.setWagePerHour(request.getWagePerHour());
        if (request.getIsActive() != null) {
            wm.setIsActive(request.getIsActive());
        }

        WageMatrix saved = wageMatrixRepository.save(wm);
        log.info("Đã cập nhật cấu hình ma trận lương: id={}", saved.getMatrixId());
        return mapToDto(saved);
    }

    /**
     * Bật / tắt trạng thái hoạt động của cấu hình lương
     */
    @Transactional
    public WageMatrixResponseDto toggleActive(UUID matrixId) {
        WageMatrix wm = wageMatrixRepository.findById(matrixId)
                .orElseThrow(() -> new ResourceNotFoundException("WageMatrix", "id", matrixId));
        wm.setIsActive(!Boolean.TRUE.equals(wm.getIsActive()));
        WageMatrix saved = wageMatrixRepository.save(wm);
        return mapToDto(saved);
    }

    /**
     * Xóa cấu hình ma trận lương
     */
    @Transactional
    public void deleteWageMatrix(UUID matrixId) {
        if (!wageMatrixRepository.existsById(matrixId)) {
            throw new ResourceNotFoundException("WageMatrix", "id", matrixId);
        }
        wageMatrixRepository.deleteById(matrixId);
        log.info("Đã xóa cấu hình ma trận lương: id={}", matrixId);
    }

    /**
     * Thuật toán tra cứu mức lương theo cấu trúc Fallback phân tầng
     * Level 0: Khớp chính xác (City + JobType + TimeSlot + LeadTime)
     * Level 1: Khớp City + JobType + TimeSlot (với LeadTime = STANDARD)
     * Level 2: Trung bình các ca của JobType tại City đó
     * Level 3: Trung bình các ca của JobType trên toàn quốc
     * Level 4: Mức sàn mặc định của hệ thống
     */
    @Transactional(readOnly = true)
    public LookupResult lookupWage(City city, String jobType, TimeSlot timeSlot, LeadTime leadTime) {
        String trimmed = jobType != null ? jobType.trim() : "";

        // Level 0: Exact match
        Optional<WageMatrix> exact = wageMatrixRepository.findByCityAndJobTypeIgnoreCaseAndTimeSlotAndLeadTime(
                city, trimmed, timeSlot, leadTime
        );
        if (exact.isPresent() && Boolean.TRUE.equals(exact.get().getIsActive())) {
            return LookupResult.builder()
                    .wagePerHour(exact.get().getWagePerHour())
                    .matchedRowId(exact.get().getMatrixId())
                    .fallbackLevel(0)
                    .build();
        }

        // Level 1: Same city & job & slot, STANDARD lead time
        if (leadTime != LeadTime.STANDARD) {
            Optional<WageMatrix> lvl1 = wageMatrixRepository.findByCityAndJobTypeIgnoreCaseAndTimeSlotAndLeadTime(
                    city, trimmed, timeSlot, LeadTime.STANDARD
            );
            if (lvl1.isPresent() && Boolean.TRUE.equals(lvl1.get().getIsActive())) {
                BigDecimal multiplier = (leadTime == LeadTime.URGENT) ? new BigDecimal("1.20") : new BigDecimal("1.10");
                BigDecimal adjusted = lvl1.get().getWagePerHour().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
                return LookupResult.builder()
                        .wagePerHour(adjusted)
                        .matchedRowId(lvl1.get().getMatrixId())
                        .fallbackLevel(1)
                        .build();
            }
        }

        // Level 2: Average of jobType in that city
        List<WageMatrix> cityMatches = wageMatrixRepository.findByCityAndJobTypeIgnoreCase(city, trimmed);
        List<WageMatrix> activeCityMatches = cityMatches.stream().filter(m -> Boolean.TRUE.equals(m.getIsActive())).toList();
        if (!activeCityMatches.isEmpty()) {
            BigDecimal sum = activeCityMatches.stream().map(WageMatrix::getWagePerHour).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(BigDecimal.valueOf(activeCityMatches.size()), 2, RoundingMode.HALF_UP);
            return LookupResult.builder()
                    .wagePerHour(avg)
                    .matchedRowId(activeCityMatches.get(0).getMatrixId())
                    .fallbackLevel(2)
                    .build();
        }

        // Level 3: Average of jobType across all cities
        List<WageMatrix> allMatches = wageMatrixRepository.findByJobTypeIgnoreCase(trimmed);
        List<WageMatrix> activeAllMatches = allMatches.stream().filter(m -> Boolean.TRUE.equals(m.getIsActive())).toList();
        if (!activeAllMatches.isEmpty()) {
            BigDecimal sum = activeAllMatches.stream().map(WageMatrix::getWagePerHour).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(BigDecimal.valueOf(activeAllMatches.size()), 2, RoundingMode.HALF_UP);
            return LookupResult.builder()
                    .wagePerHour(avg)
                    .matchedRowId(activeAllMatches.get(0).getMatrixId())
                    .fallbackLevel(3)
                    .build();
        }

        // Level 4: Fallback floor
        return LookupResult.builder()
                .wagePerHour(DEFAULT_FALLBACK_WAGE)
                .matchedRowId(null)
                .fallbackLevel(4)
                .build();
    }

    private WageMatrixResponseDto mapToDto(WageMatrix wm) {
        return WageMatrixResponseDto.builder()
                .matrixId(wm.getMatrixId())
                .city(wm.getCity())
                .jobType(wm.getJobType())
                .timeSlot(wm.getTimeSlot())
                .leadTime(wm.getLeadTime())
                .wagePerHour(wm.getWagePerHour())
                .isActive(wm.getIsActive())
                .createdAt(wm.getCreatedAt())
                .updatedAt(wm.getUpdatedAt())
                .build();
    }
}
