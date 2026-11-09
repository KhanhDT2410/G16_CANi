package com.cani.service;

import com.cani.dto.admin.revenue.RevenueAnalyticsResponseDto;
import com.cani.dto.admin.revenue.RevenueSummaryResponseDto;
import com.cani.dto.admin.revenue.RevenueTransactionDto;
import com.cani.entity.Event;
import com.cani.entity.EventOrganizer;
import com.cani.entity.Payment;
import com.cani.entity.Subscription;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.enums.EventMode;
import com.cani.entity.enums.PaymentStatus;
import com.cani.entity.enums.PlanType;
import com.cani.entity.enums.SubscriptionStatus;
import com.cani.exception.BadRequestException;
import com.cani.repository.EventRepository;
import com.cani.repository.PaymentRepository;
import com.cani.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RevenueService — Kiểm thử nghiệp vụ Bảng theo dõi & Phân tích doanh thu (UC-108)")
class RevenueServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private RevenueService revenueService;

    private EventOrganizer mockEo;
    private Worker mockWorker;
    private Instant now;
    private Instant thirtyDaysAgo;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);

        mockEo = EventOrganizer.builder()
                .eoId(UUID.randomUUID())
                .companyName("Công ty Sự kiện Sao Mai")
                .build();

        mockWorker = Worker.builder()
                .workerId(UUID.randomUUID())
                .user(User.builder().userId(UUID.randomUUID()).fullName("Nguyễn Văn A").build())
                .build();
    }

    @Nested
    @DisplayName("1. Tổng quan doanh thu & So sánh theo kỳ (getRevenueSummary)")
    class SummaryTests {

        @Test
        @DisplayName("Tính đúng tổng doanh thu gói, khối lượng giao dịch và tỷ lệ tăng trưởng so với kỳ trước")
        void shouldComputeSummaryAndPeriodOverPeriodGrowth() {
            // Kỳ hiện tại: 2 gói (500k + 1tr) = 1.5tr, 1 payment 200k
            Subscription sub1 = Subscription.builder()
                    .subscriptionId(UUID.randomUUID())
                    .eventOrganizer(mockEo)
                    .planType(PlanType.INVITE_ONLY)
                    .planName("Gói Cơ Bản")
                    .price(BigDecimal.valueOf(500000))
                    .status(SubscriptionStatus.ACTIVE)
                    .createdAt(now.minus(10, ChronoUnit.DAYS))
                    .build();

            Subscription sub2 = Subscription.builder()
                    .subscriptionId(UUID.randomUUID())
                    .eventOrganizer(mockEo)
                    .planType(PlanType.MATCHING_PLUS)
                    .planName("Gói Nâng Cao")
                    .price(BigDecimal.valueOf(1000000))
                    .status(SubscriptionStatus.ACTIVE)
                    .createdAt(now.minus(5, ChronoUnit.DAYS))
                    .build();

            Payment payment1 = Payment.builder()
                    .paymentId(UUID.randomUUID())
                    .eventOrganizer(mockEo)
                    .worker(mockWorker)
                    .agreedAmount(BigDecimal.valueOf(200000))
                    .status(PaymentStatus.CONFIRMED_FULL)
                    .paymentMethod("VNPAY_QR")
                    .createdAt(now.minus(2, ChronoUnit.DAYS))
                    .build();

            // Kỳ trước: 1 gói (1tr) = 1tr, 0 payment
            Subscription prevSub = Subscription.builder()
                    .subscriptionId(UUID.randomUUID())
                    .eventOrganizer(mockEo)
                    .planType(PlanType.MATCHING_PLUS)
                    .price(BigDecimal.valueOf(1000000))
                    .status(SubscriptionStatus.ACTIVE)
                    .createdAt(now.minus(45, ChronoUnit.DAYS))
                    .build();

            when(subscriptionRepository.findByCreatedAtBetween(eq(thirtyDaysAgo), eq(now)))
                    .thenReturn(List.of(sub1, sub2));
            when(paymentRepository.findByCreatedAtBetween(eq(thirtyDaysAgo), eq(now)))
                    .thenReturn(List.of(payment1));

            Instant prevFrom = thirtyDaysAgo.minus(30, ChronoUnit.DAYS);
            when(subscriptionRepository.findByCreatedAtBetween(eq(prevFrom), eq(thirtyDaysAgo)))
                    .thenReturn(List.of(prevSub));
            when(paymentRepository.findByCreatedAtBetween(eq(prevFrom), eq(thirtyDaysAgo)))
                    .thenReturn(List.of());

            when(eventRepository.findByCreatedAtBetween(any(), any()))
                    .thenReturn(List.of());

            RevenueSummaryResponseDto response = revenueService.getRevenueSummary(thirtyDaysAgo, now);

            assertThat(response).isNotNull();
            // Kỳ hiện tại: gói 1.5tr, payment 200k
            assertThat(response.getCurrentPeriod().getTotalPackageRevenue()).isEqualByComparingTo(BigDecimal.valueOf(1500000));
            assertThat(response.getCurrentPeriod().getTotalPackageSales()).isEqualTo(2);
            assertThat(response.getCurrentPeriod().getTotalPaymentVolume()).isEqualByComparingTo(BigDecimal.valueOf(200000));
            assertThat(response.getCurrentPeriod().getTotalPaymentTransactions()).isEqualTo(1);

            // Kỳ trước: gói 1.0tr
            assertThat(response.getPreviousPeriod().getTotalPackageRevenue()).isEqualByComparingTo(BigDecimal.valueOf(1000000));

            // Tăng trưởng doanh thu gói: (1.5tr - 1tr)/1tr = 50.0%
            assertThat(response.getGrowth().getPackageRevenueGrowthRate()).isEqualTo(50.0);

            // Phân bổ loại gói
            assertThat(response.getPackageTypeBreakdown()).hasSize(2);
            assertThat(response.getPackageTypeBreakdown().stream().filter(p -> p.getPlanType() == PlanType.INVITE_ONLY).findFirst().get().getTotalRevenue())
                    .isEqualByComparingTo(BigDecimal.valueOf(500000));
            assertThat(response.getPackageTypeBreakdown().stream().filter(p -> p.getPlanType() == PlanType.MATCHING_PLUS).findFirst().get().getTotalRevenue())
                    .isEqualByComparingTo(BigDecimal.valueOf(1000000));
        }

        @Test
        @DisplayName("Phát hiện và tổng hợp các giao dịch VNPay cùng cờ cảnh báo DISPUTED và UNVERIFIED")
        void shouldIdentifyVnPayAndFlaggedTransactions() {
            Payment pVnPayOk = Payment.builder()
                    .paymentId(UUID.randomUUID())
                    .agreedAmount(BigDecimal.valueOf(300000))
                    .status(PaymentStatus.CONFIRMED_FULL)
                    .paymentMethod("VNPAY")
                    .createdAt(now.minus(1, ChronoUnit.DAYS))
                    .build();

            Payment pDisputed = Payment.builder()
                    .paymentId(UUID.randomUUID())
                    .agreedAmount(BigDecimal.valueOf(400000))
                    .status(PaymentStatus.DISPUTED)
                    .paymentMethod("VNPAY")
                    .createdAt(now.minus(2, ChronoUnit.DAYS))
                    .build();

            Payment pUnverified = Payment.builder()
                    .paymentId(UUID.randomUUID())
                    .agreedAmount(BigDecimal.valueOf(500000))
                    .status(PaymentStatus.UNVERIFIED)
                    .paymentMethod("BANK_TRANSFER")
                    .createdAt(now.minus(3, ChronoUnit.DAYS))
                    .build();

            when(subscriptionRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());
            when(paymentRepository.findByCreatedAtBetween(any(), any()))
                    .thenReturn(List.of(pVnPayOk, pDisputed, pUnverified));
            when(eventRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());

            RevenueSummaryResponseDto response = revenueService.getRevenueSummary(thirtyDaysAgo, now);

            // VNPay summary
            assertThat(response.getVnpaySummary().getTotalTransactions()).isEqualTo(2);
            assertThat(response.getVnpaySummary().getConfirmedCount()).isEqualTo(1);
            assertThat(response.getVnpaySummary().getFlaggedCount()).isEqualTo(1);
            assertThat(response.getVnpaySummary().getDataReliabilityNote()).isNotEmpty();

            // Cờ cảnh báo: 2 giao dịch (pDisputed + pUnverified)
            assertThat(response.getFlaggedTransactions()).hasSize(2);
            assertThat(response.getFlaggedTransactions().stream().allMatch(RevenueTransactionDto::isFlagged)).isTrue();
        }

        @Test
        @DisplayName("Ném BadRequestException khi thời điểm bắt đầu lớn hơn kết thúc")
        void shouldThrowExceptionWhenFromIsAfterTo() {
            Instant from = now;
            Instant to = now.minus(5, ChronoUnit.DAYS);

            assertThatThrownBy(() -> revenueService.getRevenueSummary(from, to))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Thời điểm bắt đầu (from) phải trước thời điểm kết thúc (to)");
        }
    }

    @Nested
    @DisplayName("2. Bảng giao dịch chi tiết (getRevenueTransactions)")
    class TransactionTests {

        @Test
        @DisplayName("Lọc danh sách giao dịch theo loại gói dịch vụ PlanType thành công")
        void shouldFilterTransactionsByPackageType() {
            Subscription sub = Subscription.builder()
                    .subscriptionId(UUID.randomUUID())
                    .eventOrganizer(mockEo)
                    .planType(PlanType.MATCHING_PLUS)
                    .planName("Gói Matching Plus")
                    .price(BigDecimal.valueOf(1500000))
                    .status(SubscriptionStatus.ACTIVE)
                    .createdAt(now.minus(2, ChronoUnit.DAYS))
                    .build();

            when(subscriptionRepository.findByPlanTypeAndCreatedAtBetween(eq(PlanType.MATCHING_PLUS), any(), any()))
                    .thenReturn(List.of(sub));

            Pageable pageable = PageRequest.of(0, 10);
            Page<RevenueTransactionDto> page = revenueService.getRevenueTransactions(
                    thirtyDaysAgo, now, null, PlanType.MATCHING_PLUS, pageable
            );

            assertThat(page.getTotalElements()).isEqualTo(1);
            RevenueTransactionDto item = page.getContent().get(0);
            assertThat(item.getTransactionType()).isEqualTo("PACKAGE_SALE");
            assertThat(item.getPlanType()).isEqualTo(PlanType.MATCHING_PLUS);
            assertThat(item.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500000));
        }

        @Test
        @DisplayName("Lọc danh sách giao dịch theo trạng thái thanh toán PaymentStatus thành công")
        void shouldFilterTransactionsByPaymentStatus() {
            Payment payment = Payment.builder()
                    .paymentId(UUID.randomUUID())
                    .eventOrganizer(mockEo)
                    .worker(mockWorker)
                    .agreedAmount(BigDecimal.valueOf(600000))
                    .status(PaymentStatus.DISPUTED)
                    .createdAt(now.minus(1, ChronoUnit.DAYS))
                    .build();

            when(paymentRepository.findByStatusInAndCreatedAtBetween(eq(List.of(PaymentStatus.DISPUTED)), any(), any()))
                    .thenReturn(List.of(payment));

            Pageable pageable = PageRequest.of(0, 10);
            Page<RevenueTransactionDto> page = revenueService.getRevenueTransactions(
                    thirtyDaysAgo, now, "DISPUTED", null, pageable
            );

            assertThat(page.getTotalElements()).isEqualTo(1);
            RevenueTransactionDto item = page.getContent().get(0);
            assertThat(item.getTransactionType()).isEqualTo("DIRECT_PAYMENT");
            assertThat(item.getStatus()).isEqualTo("DISPUTED");
            assertThat(item.isFlagged()).isTrue();
        }
    }

    @Nested
    @DisplayName("3. Phân tích xu hướng biến động doanh thu (getRevenueAnalytics)")
    class AnalyticsTests {

        @Test
        @DisplayName("Tính toán chuỗi dữ liệu xu hướng theo ngày hợp lệ")
        void shouldComputeDailyAnalyticsTrend() {
            Subscription sub = Subscription.builder()
                    .subscriptionId(UUID.randomUUID())
                    .price(BigDecimal.valueOf(2000000))
                    .createdAt(now.minus(2, ChronoUnit.DAYS))
                    .build();

            when(subscriptionRepository.findByCreatedAtBetween(any(), any()))
                    .thenReturn(List.of(sub));
            when(paymentRepository.findByCreatedAtBetween(any(), any()))
                    .thenReturn(List.of());

            RevenueAnalyticsResponseDto analytics = revenueService.getRevenueAnalytics("day", thirtyDaysAgo, now);

            assertThat(analytics).isNotNull();
            assertThat(analytics.getPeriod()).isEqualTo("day");
            assertThat(analytics.getDataPoints()).isNotEmpty();
            assertThat(analytics.getTotalPackageRevenue()).isEqualByComparingTo(BigDecimal.valueOf(2000000));
        }

        @Test
        @DisplayName("Ném BadRequestException khi chu kỳ period không hợp lệ")
        void shouldThrowExceptionWhenPeriodIsInvalid() {
            assertThatThrownBy(() -> revenueService.getRevenueAnalytics("invalid_period", thirtyDaysAgo, now))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Chu kỳ phân tích không hợp lệ");
        }
    }
}

