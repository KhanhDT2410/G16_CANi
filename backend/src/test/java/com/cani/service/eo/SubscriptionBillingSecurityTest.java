package com.cani.service.eo;

import com.cani.dto.subscription.SubscriptionResponseDto;
import com.cani.entity.EventOrganizer;
import com.cani.entity.Subscription;
import com.cani.entity.User;
import com.cani.entity.enums.PlanType;
import com.cani.entity.enums.SubscriptionStatus;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.exception.BadRequestException;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.PayOSOrderRepository;
import com.cani.repository.SubscriptionRepository;
import com.cani.service.NotificationService;
import com.cani.service.PayOSService;
import com.cani.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Adversarial Security Test Suite for Subscription Billing & PayOS Gateway (BUG-A Verification).
 * Verifies that unknown orders, cross-tenant orders, and non-PAID statuses cannot activate subscriptions.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionBillingSecurityTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private EventOrganizerRepository eventOrganizerRepository;

    @Mock
    private PayOSService payOSService;

    @Mock
    private PayOSOrderRepository payOSOrderRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private User sampleUser;
    private EventOrganizer sampleEo;
    private UUID sampleUserId;
    private UUID sampleEoId;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        sampleEoId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        sampleUser = User.builder()
                .userId(sampleUserId)
                .email("eo@cani.vn")
                .role(UserRole.EVENT_ORGANIZER)
                .status(UserStatus.ACTIVE)
                .build();

        sampleEo = EventOrganizer.builder()
                .eoId(sampleEoId)
                .user(sampleUser)
                .companyName("Event Pro Vietnam")
                .build();

        ReflectionTestUtils.setField(subscriptionService, "notificationService", notificationService);
        ReflectionTestUtils.setField(subscriptionService, "strictQuotaEnforcement", false);
        ReflectionTestUtils.setField(subscriptionService, "demoEnabled", false);
    }

    private void seedPendingOrder(long orderCode, UUID eoId, PlanType planType) {
        try {
            Class<?> recordClass = Class.forName("com.cani.service.SubscriptionService$PendingOrder");
            Constructor<?> ctor = recordClass.getDeclaredConstructor(UUID.class, PlanType.class, BigDecimal.class, Instant.class);
            ctor.setAccessible(true);
            Object orderInstance = ctor.newInstance(eoId, planType, BigDecimal.valueOf(199000), Instant.now());
            @SuppressWarnings("unchecked")
            Map<String, Object> pendingOrders = (Map<String, Object>) ReflectionTestUtils.getField(subscriptionService, "pendingOrders");
            if (pendingOrders != null) {
                pendingOrders.put("PAYOS_" + orderCode, orderInstance);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to seed pending order for testing", e);
        }
    }

    @Test
    @DisplayName("UTC-SEC-001: confirmPayOSPaymentForUser - Order không tồn tại trong hàng đợi nhưng gateway báo PAID -> Từ chối, không kích hoạt gói (Chặn BUG-A)")
    void confirmPayOSPaymentForUser_OrderNotInQueue_GatewayPaid_RejectsAndActivatesNothing() {
        long unknownOrderCode = 888888888L;
        when(eventOrganizerRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(sampleEo));
        when(payOSService.getPaymentStatus(unknownOrderCode)).thenReturn("PAID");
        when(subscriptionRepository.findActiveSubscriptions(eq(sampleEoId), any(), any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> subscriptionService.confirmPayOSPaymentForUser(sampleUserId, unknownOrderCode))
                .isInstanceOf(BadRequestException.class);

        verify(subscriptionRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("UTC-SEC-002: confirmPayOSPaymentForUser - Order thuộc về EO khác nhưng gateway báo PAID -> Từ chối ngay lập tức")
    void confirmPayOSPaymentForUser_OrderBelongsToOtherEo_GatewayPaid_Rejects() {
        long orderCode = 777777777L;
        UUID otherEoId = UUID.randomUUID();
        seedPendingOrder(orderCode, otherEoId, PlanType.MATCHING_PLUS);

        when(eventOrganizerRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(sampleEo));

        assertThatThrownBy(() -> subscriptionService.confirmPayOSPaymentForUser(sampleUserId, orderCode))
                .isInstanceOf(BadRequestException.class);

        verify(subscriptionRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "CANCELLED", "UNKNOWN"})
    @DisplayName("UTC-SEC-003: confirmPayOSPaymentForUser - Trạng thái Gateway chưa thanh toán (PENDING, CANCELLED, UNKNOWN) -> Không kích hoạt gói")
    void confirmPayOSPaymentForUser_GatewayStatusNotPaid_DoesNotActivate(String gatewayStatus) {
        long orderCode = 666666666L;
        seedPendingOrder(orderCode, sampleEoId, PlanType.MATCHING_PLUS);

        when(eventOrganizerRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(sampleEo));
        when(payOSService.getPaymentStatus(orderCode)).thenReturn(gatewayStatus);

        assertThatThrownBy(() -> subscriptionService.confirmPayOSPaymentForUser(sampleUserId, orderCode))
                .isInstanceOf(BadRequestException.class);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTC-SEC-004: confirmPayOSPaymentForUser - Đúng chủ sở hữu EO và gateway PAID -> Kích hoạt đúng gói cước trong đơn hàng (ENTERPRISE)")
    void confirmPayOSPaymentForUser_OwnerEo_GatewayPaid_ActivatesOrderPlanType() {
        long orderCode = 555555555L;
        seedPendingOrder(orderCode, sampleEoId, PlanType.ENTERPRISE);

        when(eventOrganizerRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(sampleEo));
        when(payOSService.getPaymentStatus(orderCode)).thenReturn("PAID");
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        SubscriptionResponseDto res = subscriptionService.confirmPayOSPaymentForUser(sampleUserId, orderCode);

        assertThat(res).isNotNull();
        assertThat(res.getPlanType()).isEqualTo(PlanType.ENTERPRISE);
        verify(subscriptionRepository).save(argThat(s -> s.getPlanType() == PlanType.ENTERPRISE));
    }

    @Test
    @DisplayName("UTC-SEC-005: confirmPayOSPaymentForUser - Double confirm cùng 1 đơn hàng khi gateway vẫn PAID -> Chỉ kích hoạt lưu DB đúng 1 lần duy nhất")
    void confirmPayOSPaymentForUser_DoubleConfirm_SameOrder_ActivatesOnce() {
        long orderCode = 444444444L;
        seedPendingOrder(orderCode, sampleEoId, PlanType.ENTERPRISE);

        Subscription enterpriseSub = Subscription.builder()
                .subscriptionId(UUID.randomUUID())
                .eventOrganizer(sampleEo)
                .planType(PlanType.ENTERPRISE)
                .status(SubscriptionStatus.ACTIVE)
                .startsAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(86400 * 30))
                .build();

        when(eventOrganizerRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(sampleEo));
        when(payOSService.getPaymentStatus(orderCode)).thenReturn("PAID");
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> {
            Subscription s = i.getArgument(0);
            s.setSubscriptionId(UUID.randomUUID());
            return s;
        });

        // Lần 1: kích hoạt thành công
        SubscriptionResponseDto res1 = subscriptionService.confirmPayOSPaymentForUser(sampleUserId, orderCode);
        assertThat(res1.getPlanType()).isEqualTo(PlanType.ENTERPRISE);

        // Lần 2: order đã bị remove khỏi pendingOrders; active sub đã có trong DB
        when(subscriptionRepository.findActiveSubscriptions(eq(sampleEoId), eq(SubscriptionStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(enterpriseSub));

        SubscriptionResponseDto res2 = subscriptionService.confirmPayOSPaymentForUser(sampleUserId, orderCode);
        assertThat(res2.getPlanType()).isEqualTo(PlanType.ENTERPRISE);

        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }

    @Test
    @DisplayName("UTC-SEC-006: confirmPayOSPaymentForUser - Order lạ, gateway PAID, nhưng EO đang có gói FREE_TRIAL -> Không được tự ý nâng cấp lên MATCHING_PLUS")
    void confirmPayOSPaymentForUser_UnknownOrder_EoAlreadyHasActivePlan_DoesNotGrantUpgrade() {
        long unknownOrderCode = 333333333L;
        Subscription activeFreeTrial = Subscription.builder()
                .subscriptionId(UUID.randomUUID())
                .eventOrganizer(sampleEo)
                .planType(PlanType.FREE_TRIAL)
                .status(SubscriptionStatus.ACTIVE)
                .startsAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(86400 * 30))
                .build();

        when(eventOrganizerRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(sampleEo));
        when(payOSService.getPaymentStatus(unknownOrderCode)).thenReturn("PAID");
        when(subscriptionRepository.findActiveSubscriptions(eq(sampleEoId), eq(SubscriptionStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(activeFreeTrial));

        // Trả về DTO của gói hiện tại (FREE_TRIAL), tuyệt đối KHÔNG cấp mới hay save gói trả phí MATCHING_PLUS
        SubscriptionResponseDto res = subscriptionService.confirmPayOSPaymentForUser(sampleUserId, unknownOrderCode);

        assertThat(res).isNotNull();
        assertThat(res.getPlanType()).isEqualTo(PlanType.FREE_TRIAL);
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }
}
