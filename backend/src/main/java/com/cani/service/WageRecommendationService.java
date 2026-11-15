package com.cani.service;

import com.cani.dto.wage.WageRecommendationRequestDto;
import com.cani.dto.wage.WageRecommendationResponseDto;
import com.cani.entity.WageRecommendation;
import com.cani.entity.enums.LeadTime;
import com.cani.entity.enums.TimeSlot;
import com.cani.repository.WageRecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WageRecommendationService {

    private final WageMatrixService wageMatrixService;
    private final WageRecommendationRepository wageRecommendationRepository;

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Gợi ý mức thù lao tối ưu cho ca làm việc (WBS 337.0 / UC-14)
     */
    @Transactional
    public WageRecommendationResponseDto recommendWage(WageRecommendationRequestDto request) {
        log.info("Tính toán mức thù lao khuyến nghị cho: city={}, jobType={}, startTime={}",
                request.getCity(), request.getJobType(), request.getStartTime());

        TimeSlot timeSlot = determineTimeSlot(request.getStartTime());
        LeadTime leadTime = determineLeadTime(request.getStartTime());

        WageMatrixService.LookupResult lookup = wageMatrixService.lookupWage(
                request.getCity(),
                request.getJobType(),
                timeSlot,
                leadTime
        );

        BigDecimal suggestedWage = lookup.getWagePerHour().setScale(-3, RoundingMode.HALF_UP);
        BigDecimal minWage = suggestedWage.multiply(new BigDecimal("0.90")).setScale(-3, RoundingMode.HALF_UP);
        BigDecimal maxWage = suggestedWage.multiply(new BigDecimal("1.20")).setScale(-3, RoundingMode.HALF_UP);

        String explanation = generateExplanation(request, timeSlot, leadTime, lookup.getFallbackLevel(), suggestedWage);

        WageRecommendation recommendation = WageRecommendation.builder()
                .city(request.getCity())
                .jobType(request.getJobType())
                .timeSlot(timeSlot)
                .leadTime(leadTime)
                .suggestedWage(suggestedWage)
                .matchedRowId(lookup.getMatchedRowId())
                .fallbackLevel(lookup.getFallbackLevel())
                .shiftId(request.getShiftId())
                .build();

        WageRecommendation saved = wageRecommendationRepository.save(recommendation);
        log.info("Đã lưu khuyến nghị thù lao: id={}, suggestedWage={}", saved.getRecommendationId(), suggestedWage);

        return WageRecommendationResponseDto.builder()
                .recommendationId(saved.getRecommendationId())
                .shiftId(request.getShiftId())
                .city(request.getCity())
                .jobType(request.getJobType())
                .timeSlot(timeSlot)
                .leadTime(leadTime)
                .suggestedWage(suggestedWage)
                .minWage(minWage)
                .maxWage(maxWage)
                .explanation(explanation)
                .matchedRowId(lookup.getMatchedRowId())
                .fallbackLevel(lookup.getFallbackLevel())
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

    private String generateExplanation(
            WageRecommendationRequestDto request,
            TimeSlot timeSlot,
            LeadTime leadTime,
            int fallbackLevel,
            BigDecimal wage
    ) {
        String levelDesc = switch (fallbackLevel) {
            case 0 -> "khớp chính xác theo biểu ma trận chuẩn";
            case 1 -> "điều chỉnh phụ phí khẩn cấp từ biểu chuẩn";
            case 2 -> "mức trung bình của vị trí công việc tại thành phố";
            case 3 -> "mức trung bình của vị trí công việc trên toàn quốc";
            default -> "mức lương sàn tối thiểu hệ thống CANi";
        };

        return String.format(
                "Mức thù lao khuyến nghị %,d đ/giờ được tính toán dựa trên dữ liệu thị trường tại %s cho vị trí '%s' vào khung giờ %s, độ khẩn cấp %s (%s). Khung đề xuất hợp lý: từ -10%% đến +20%% để đảm bảo tỷ lệ thợ nhận ca cao nhất.",
                wage.longValue(),
                request.getCity(),
                request.getJobType(),
                timeSlot,
                leadTime,
                levelDesc
        );
    }
}
