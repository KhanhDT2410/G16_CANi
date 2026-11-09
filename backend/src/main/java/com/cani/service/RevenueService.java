package com.cani.service;

import com.cani.dto.admin.revenue.*;
import com.cani.entity.Event;
import com.cani.entity.Payment;
import com.cani.entity.Subscription;
import com.cani.entity.enums.EventMode;
import com.cani.entity.enums.PaymentStatus;
import com.cani.entity.enums.PlanType;
import com.cani.exception.BadRequestException;
import com.cani.repository.EventRepository;
import com.cani.repository.PaymentRepository;
import com.cani.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service phục vụ UC-108: Bảng theo dõi và phân tích doanh thu (Revenue Dashboard & Metrics).
 * Tính toán doanh thu gói dịch vụ (subscriptions), luồng giao dịch thanh toán (direct payments/VNPay),
 * so sánh cùng kỳ (period-over-period) và cảnh báo các giao dịch rủi ro (DISPUTED/UNVERIFIED).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RevenueService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final EventRepository eventRepository;

    /**
     * Lấy tổng quan doanh thu, so sánh kỳ trước, phân tích theo gói & hình thức sự kiện,
     * tổng hợp VNPay và các giao dịch cần theo dõi.
     */
    public RevenueSummaryResponseDto getRevenueSummary(Instant from, Instant to) {
        Instant effectiveTo = (to != null) ? to : Instant.now();
        Instant effectiveFrom = (from != null) ? from : effectiveTo.minus(Duration.ofDays(30));

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BadRequestException("Thời điểm bắt đầu (from) phải trước thời điểm kết thúc (to)");
        }

        Duration periodDuration = Duration.between(effectiveFrom, effectiveTo);
        Instant prevTo = effectiveFrom;
        Instant prevFrom = effectiveFrom.minus(periodDuration);

        // 1. Dữ liệu kỳ hiện tại
        List<Subscription> currentSubs = subscriptionRepository.findByCreatedAtBetween(effectiveFrom, effectiveTo);
        List<Payment> currentPayments = paymentRepository.findByCreatedAtBetween(effectiveFrom, effectiveTo);

        PeriodMetricsDto currentMetrics = computePeriodMetrics(effectiveFrom, effectiveTo, currentSubs, currentPayments);

        // 2. Dữ liệu kỳ trước (so sánh period-over-period)
        List<Subscription> prevSubs = subscriptionRepository.findByCreatedAtBetween(prevFrom, prevTo);
        List<Payment> prevPayments = paymentRepository.findByCreatedAtBetween(prevFrom, prevTo);

        PeriodMetricsDto prevMetrics = computePeriodMetrics(prevFrom, prevTo, prevSubs, prevPayments);

        // 3. Tỷ lệ tăng trưởng
        RevenueGrowthDto growth = RevenueGrowthDto.builder()
                .packageRevenueGrowthRate(calculateGrowthRate(currentMetrics.getTotalPackageRevenue(), prevMetrics.getTotalPackageRevenue()))
                .packageSalesGrowthRate(calculateGrowthRate(BigDecimal.valueOf(currentMetrics.getTotalPackageSales()), BigDecimal.valueOf(prevMetrics.getTotalPackageSales())))
                .paymentVolumeGrowthRate(calculateGrowthRate(currentMetrics.getTotalPaymentVolume(), prevMetrics.getTotalPaymentVolume()))
                .paymentTransactionsGrowthRate(calculateGrowthRate(BigDecimal.valueOf(currentMetrics.getTotalPaymentTransactions()), BigDecimal.valueOf(prevMetrics.getTotalPaymentTransactions())))
                .combinedVolumeGrowthRate(calculateGrowthRate(currentMetrics.getCombinedVolume(), prevMetrics.getCombinedVolume()))
                .build();

        // 4. Doanh thu theo loại gói dịch vụ (Invite Mode vs Matching + Invite Mode)
        List<PackageTypeRevenueDto> packageBreakdown = computePackageTypeBreakdown(currentSubs, currentMetrics.getTotalPackageRevenue());

        // 5. Phân bổ theo hình thức sự kiện (Event Mode)
        List<EventModeRevenueDto> eventModeBreakdown = computeEventModeBreakdown(effectiveFrom, effectiveTo, currentPayments);

        // 6. Tổng hợp giao dịch VNPay
        VnPaySummaryDto vnpaySummary = computeVnPaySummary(currentPayments);

        // 7. Các giao dịch có cờ cảnh báo (DISPUTED / UNVERIFIED)
        List<RevenueTransactionDto> flaggedTransactions = computeFlaggedTransactions(currentPayments);

        return RevenueSummaryResponseDto.builder()
                .currentPeriod(currentMetrics)
                .previousPeriod(prevMetrics)
                .growth(growth)
                .packageTypeBreakdown(packageBreakdown)
                .eventModeBreakdown(eventModeBreakdown)
                .vnpaySummary(vnpaySummary)
                .flaggedTransactions(flaggedTransactions)
                .build();
    }

    /**
     * Bảng giao dịch chi tiết kèm phân trang và bộ lọc.
     */
    public Page<RevenueTransactionDto> getRevenueTransactions(
            Instant from,
            Instant to,
            String status,
            PlanType packageType,
            Pageable pageable
    ) {
        Instant effectiveTo = (to != null) ? to : Instant.now();
        Instant effectiveFrom = (from != null) ? from : effectiveTo.minus(Duration.ofDays(30));

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BadRequestException("Thời điểm bắt đầu (from) phải trước thời điểm kết thúc (to)");
        }

        List<RevenueTransactionDto> allResults = new ArrayList<>();

        // Nếu lọc theo packageType hoặc không lọc trạng thái payment -> lấy subscriptions
        boolean includeSubscriptions = (status == null || status.isBlank()) || isSubscriptionStatus(status);
        if (includeSubscriptions) {
            List<Subscription> subs = (packageType != null)
                    ? subscriptionRepository.findByPlanTypeAndCreatedAtBetween(packageType, effectiveFrom, effectiveTo)
                    : subscriptionRepository.findByCreatedAtBetween(effectiveFrom, effectiveTo);

            if (status != null && !status.isBlank()) {
                subs = subs.stream()
                        .filter(s -> s.getStatus() != null && s.getStatus().name().equalsIgnoreCase(status))
                        .collect(Collectors.toList());
            }

            subs.forEach(s -> allResults.add(mapSubscriptionToTransactionDto(s)));
        }

        // Nếu không lọc packageType -> lấy payments
        boolean includePayments = (packageType == null);
        if (includePayments) {
            List<Payment> payments;
            PaymentStatus paymentStatus = parsePaymentStatus(status);
            if (paymentStatus != null) {
                payments = paymentRepository.findByStatusInAndCreatedAtBetween(List.of(paymentStatus), effectiveFrom, effectiveTo);
            } else if (status != null && !status.isBlank() && includeSubscriptions) {
                // status chỉ thuộc subscription status -> bỏ qua payments
                payments = Collections.emptyList();
            } else {
                payments = paymentRepository.findByCreatedAtBetween(effectiveFrom, effectiveTo);
            }

            payments.forEach(p -> allResults.add(mapPaymentToTransactionDto(p)));
        }

        // Sắp xếp giảm dần theo thời gian tạo
        allResults.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        // Phân trang
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allResults.size());
        List<RevenueTransactionDto> pagedList = (start <= end && start < allResults.size())
                ? allResults.subList(start, end)
                : Collections.emptyList();

        return new PageImpl<>(pagedList, pageable, allResults.size());
    }

    /**
     * Thống kê xu hướng doanh thu theo chu kỳ (day, week, month).
     */
    public RevenueAnalyticsResponseDto getRevenueAnalytics(String period, Instant from, Instant to) {
        String normalizedPeriod = (period != null && !period.isBlank()) ? period.trim().toLowerCase() : "day";
        if (!List.of("day", "week", "month").contains(normalizedPeriod)) {
            throw new BadRequestException("Chu kỳ phân tích không hợp lệ (hỗ trợ: day, week, month)");
        }

        Instant effectiveTo = (to != null) ? to : Instant.now();
        Instant effectiveFrom = (from != null) ? from : effectiveTo.minus(Duration.ofDays(30));

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BadRequestException("Thời điểm bắt đầu (from) phải trước thời điểm kết thúc (to)");
        }

        List<Subscription> subscriptions = subscriptionRepository.findByCreatedAtBetween(effectiveFrom, effectiveTo);
        List<Payment> payments = paymentRepository.findByCreatedAtBetween(effectiveFrom, effectiveTo);

        List<RevenueAnalyticsPointDto> points = new ArrayList<>();
        ZonedDateTime cursor = effectiveFrom.atZone(VIETNAM_ZONE);
        ZonedDateTime endZoned = effectiveTo.atZone(VIETNAM_ZONE);

        while (cursor.isBefore(endZoned)) {
            ZonedDateTime nextCursor = switch (normalizedPeriod) {
                case "week" -> cursor.plusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case "month" -> cursor.plusMonths(1).with(TemporalAdjusters.firstDayOfMonth());
                default -> cursor.plusDays(1).truncatedTo(ChronoUnit.DAYS);
            };

            if (nextCursor.isAfter(endZoned) || nextCursor.isEqual(cursor)) {
                nextCursor = endZoned;
            }

            Instant bucketStart = cursor.toInstant();
            Instant bucketEnd = nextCursor.toInstant();

            List<Subscription> bucketSubs = subscriptions.stream()
                    .filter(s -> !s.getCreatedAt().isBefore(bucketStart) && s.getCreatedAt().isBefore(bucketEnd))
                    .toList();
            List<Payment> bucketPayments = payments.stream()
                    .filter(p -> !p.getCreatedAt().isBefore(bucketStart) && p.getCreatedAt().isBefore(bucketEnd))
                    .toList();

            BigDecimal packageRev = bucketSubs.stream()
                    .map(Subscription::getPrice)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal paymentVol = bucketPayments.stream()
                    .map(p -> p.getDeclaredAmount() != null ? p.getDeclaredAmount() : p.getAgreedAmount())
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String label = switch (normalizedPeriod) {
                case "week" -> cursor.getYear() + "-W" + cursor.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                case "month" -> String.format("%d-%02d", cursor.getYear(), cursor.getMonthValue());
                default -> String.format("%d-%02d-%02d", cursor.getYear(), cursor.getMonthValue(), cursor.getDayOfMonth());
            };

            points.add(RevenueAnalyticsPointDto.builder()
                    .periodLabel(label)
                    .periodStart(bucketStart)
                    .periodEnd(bucketEnd)
                    .packageRevenue(packageRev)
                    .packageSalesCount(bucketSubs.size())
                    .paymentVolume(paymentVol)
                    .paymentTransactionsCount(bucketPayments.size())
                    .totalCombinedVolume(packageRev.add(paymentVol))
                    .build());

            if (nextCursor.equals(endZoned)) {
                break;
            }
            cursor = nextCursor;
        }

        BigDecimal totalPackageRevenue = points.stream()
                .map(RevenueAnalyticsPointDto::getPackageRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaymentVolume = points.stream()
                .map(RevenueAnalyticsPointDto::getPaymentVolume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return RevenueAnalyticsResponseDto.builder()
                .period(normalizedPeriod)
                .from(effectiveFrom)
                .to(effectiveTo)
                .dataPoints(points)
                .totalPackageRevenue(totalPackageRevenue)
                .totalPaymentVolume(totalPaymentVolume)
                .totalCombinedVolume(totalPackageRevenue.add(totalPaymentVolume))
                .build();
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private PeriodMetricsDto computePeriodMetrics(
            Instant from,
            Instant to,
            List<Subscription> subs,
            List<Payment> payments
    ) {
        BigDecimal packageRev = subs.stream()
                .map(Subscription::getPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal paymentVol = payments.stream()
                .map(p -> p.getDeclaredAmount() != null ? p.getDeclaredAmount() : p.getAgreedAmount())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PeriodMetricsDto.builder()
                .from(from)
                .to(to)
                .totalPackageRevenue(packageRev)
                .totalPackageSales(subs.size())
                .totalPaymentVolume(paymentVol)
                .totalPaymentTransactions(payments.size())
                .combinedVolume(packageRev.add(paymentVol))
                .build();
    }

    private Double calculateGrowthRate(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return (current != null && current.compareTo(BigDecimal.ZERO) > 0) ? 100.0 : 0.0;
        }
        if (current == null) {
            current = BigDecimal.ZERO;
        }
        BigDecimal diff = current.subtract(previous);
        BigDecimal rate = diff.divide(previous, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        return rate.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private List<PackageTypeRevenueDto> computePackageTypeBreakdown(List<Subscription> subs, BigDecimal totalPackageRevenue) {
        List<PackageTypeRevenueDto> result = new ArrayList<>();
        for (PlanType planType : PlanType.values()) {
            List<Subscription> matching = subs.stream()
                    .filter(s -> s.getPlanType() == planType)
                    .toList();

            BigDecimal typeRev = matching.stream()
                    .map(Subscription::getPrice)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Double percentage = (totalPackageRevenue.compareTo(BigDecimal.ZERO) > 0)
                    ? typeRev.divide(totalPackageRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0.0;

            String name = (planType == PlanType.INVITE_ONLY)
                    ? "Gói Giới Hạn Mời (Invite Only)"
                    : "Gói Đối Soát & Ghép Nâng Cao (Matching + Invite)";

            result.add(PackageTypeRevenueDto.builder()
                    .planType(planType)
                    .planTypeName(name)
                    .salesCount(matching.size())
                    .totalRevenue(typeRev)
                    .percentageOfRevenue(percentage)
                    .build());
        }
        return result;
    }

    private List<EventModeRevenueDto> computeEventModeBreakdown(Instant from, Instant to, List<Payment> payments) {
        List<EventModeRevenueDto> result = new ArrayList<>();
        List<Event> events = eventRepository.findByCreatedAtBetween(from, to);

        Map<UUID, EventMode> eventModeMap = new HashMap<>();
        events.forEach(e -> eventModeMap.put(e.getEventId(), e.getMode()));

        for (EventMode mode : EventMode.values()) {
            long eventCount = events.stream().filter(e -> e.getMode() == mode).count();

            List<Payment> modePayments = payments.stream()
                    .filter(p -> p.getEventId() != null && eventModeMap.get(p.getEventId()) == mode)
                    .toList();

            BigDecimal paymentTotal = modePayments.stream()
                    .map(p -> p.getDeclaredAmount() != null ? p.getDeclaredAmount() : p.getAgreedAmount())
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String modeName = (mode == EventMode.MATCHING)
                    ? "Ghép ca làm việc (Matching)"
                    : "Mời đích danh (Invite)";

            result.add(EventModeRevenueDto.builder()
                    .mode(mode)
                    .modeName(modeName)
                    .eventCount(eventCount)
                    .totalPaymentAmount(paymentTotal)
                    .transactionCount(modePayments.size())
                    .build());
        }
        return result;
    }

    private VnPaySummaryDto computeVnPaySummary(List<Payment> payments) {
        List<Payment> vnpayPayments = payments.stream()
                .filter(p -> (p.getPaymentMethod() != null && p.getPaymentMethod().toUpperCase().contains("VNPAY"))
                        || (p.getExternalRef() != null && p.getExternalRef().toUpperCase().contains("VNPAY")))
                .toList();

        BigDecimal totalAmount = vnpayPayments.stream()
                .map(p -> p.getDeclaredAmount() != null ? p.getDeclaredAmount() : p.getAgreedAmount())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long confirmedCount = vnpayPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.CONFIRMED_FULL || p.getStatus() == PaymentStatus.CONFIRMED_PARTIAL)
                .count();

        long pendingCount = vnpayPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.NOT_DECLARED || p.getStatus() == PaymentStatus.DECLARED_PENDING_WORKER)
                .count();

        long flaggedCount = vnpayPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.DISPUTED || p.getStatus() == PaymentStatus.UNVERIFIED)
                .count();

        return VnPaySummaryDto.builder()
                .totalTransactions(vnpayPayments.size())
                .totalAmount(totalAmount)
                .confirmedCount(confirmedCount)
                .pendingCount(pendingCount)
                .flaggedCount(flaggedCount)
                .dataReliabilityNote("Dữ liệu VNPay và cờ cảnh báo được tổng hợp dựa trên payment_method/external_ref và trạng thái đối soát (DISPUTED, UNVERIFIED) do hệ thống hiện chưa có webhook VNPay và bảng giao dịch cổng riêng.")
                .build();
    }

    private List<RevenueTransactionDto> computeFlaggedTransactions(List<Payment> payments) {
        return payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.DISPUTED || p.getStatus() == PaymentStatus.UNVERIFIED)
                .map(this::mapPaymentToTransactionDto)
                .collect(Collectors.toList());
    }

    private RevenueTransactionDto mapSubscriptionToTransactionDto(Subscription s) {
        String orgName = (s.getEventOrganizer() != null && s.getEventOrganizer().getCompanyName() != null)
                ? s.getEventOrganizer().getCompanyName()
                : "Nhà tổ chức sự kiện";

        return RevenueTransactionDto.builder()
                .id(s.getSubscriptionId())
                .transactionType("PACKAGE_SALE")
                .organizerId(s.getEventOrganizer() != null ? s.getEventOrganizer().getEoId() : null)
                .organizerName(orgName)
                .planType(s.getPlanType())
                .planName(s.getPlanName())
                .amount(s.getPrice())
                .status(s.getStatus() != null ? s.getStatus().name() : "UNKNOWN")
                .paymentMethod("VNPAY")
                .isFlagged(s.getStatus() != null && "CANCELLED".equalsIgnoreCase(s.getStatus().name()))
                .flagReason(s.getStatus() != null && "CANCELLED".equalsIgnoreCase(s.getStatus().name()) ? "Gói đăng ký đã bị hủy" : null)
                .createdAt(s.getCreatedAt())
                .build();
    }

    private RevenueTransactionDto mapPaymentToTransactionDto(Payment p) {
        boolean flagged = (p.getStatus() == PaymentStatus.DISPUTED || p.getStatus() == PaymentStatus.UNVERIFIED);
        String flagReason = null;
        if (p.getStatus() == PaymentStatus.DISPUTED) {
            flagReason = "Giao dịch đang có tranh chấp giữa Worker và Nhà tổ chức";
        } else if (p.getStatus() == PaymentStatus.UNVERIFIED) {
            flagReason = "Giao dịch chưa được xác minh đối soát";
        }

        String eoName = (p.getEventOrganizer() != null && p.getEventOrganizer().getCompanyName() != null)
                ? p.getEventOrganizer().getCompanyName()
                : "Nhà tổ chức sự kiện";

        String workerName = (p.getWorker() != null && p.getWorker().getUser() != null)
                ? p.getWorker().getUser().getFullName()
                : "Nhân viên sự kiện";

        BigDecimal amount = (p.getDeclaredAmount() != null) ? p.getDeclaredAmount() : p.getAgreedAmount();

        return RevenueTransactionDto.builder()
                .id(p.getPaymentId())
                .transactionType("DIRECT_PAYMENT")
                .organizerId(p.getEventOrganizer() != null ? p.getEventOrganizer().getEoId() : null)
                .organizerName(eoName)
                .workerId(p.getWorker() != null ? p.getWorker().getWorkerId() : null)
                .workerName(workerName)
                .eventId(p.getEventId())
                .amount(amount)
                .status(p.getStatus() != null ? p.getStatus().name() : "UNKNOWN")
                .paymentMethod(p.getPaymentMethod() != null ? p.getPaymentMethod() : "TIỀN MẶT / CHUYỂN KHOẢN")
                .externalRef(p.getExternalRef())
                .isFlagged(flagged)
                .flagReason(flagReason)
                .createdAt(p.getCreatedAt())
                .build();
    }

    private boolean isSubscriptionStatus(String status) {
        return List.of("ACTIVE", "EXPIRED", "CANCELLED").contains(status.toUpperCase());
    }

    private PaymentStatus parsePaymentStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PaymentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

