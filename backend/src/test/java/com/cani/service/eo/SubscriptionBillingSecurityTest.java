package com.cani.service.eo;

import com.cani.config.PayOSConfig;
import com.cani.dto.subscription.PayOSPaymentResponseDto;
import com.cani.dto.subscription.PurchaseSubscriptionRequestDto;
import com.cani.dto.subscription.SubscriptionResponseDto;
import com.cani.entity.EventOrganizer;
import com.cani.entity.PayOSOrder;
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
    private com.cani.repository.PlanRepository planRepository;

    @org.mockito.Spy
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Mock
    private EventOrganizerRepository eventOrganizerRepository;

    @Mock
    private PayOSService payOSService;

    @Mock
    private PayOSConfig payOSConfig;

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
        ReflectionTestUtils.setField(subscriptionService, "payOSOrderRepository", payOSOrderRepository);
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

    @Test
    @DisplayName("PAY-001.1: Webhook PayOS có chữ ký hợp lệ nhưng status=CANCELLED hoặc code!=00 -> KHÔNG kích hoạt gói")
    void processPayOSWebhook_CancelledOrNon00Code_DoesNotActivateSubscription() {
        long orderCode = 202600001L;
        PayOSOrder dbOrder = PayOSOrder.builder()
                .orderId(UUID.randomUUID())
                .orderCode(orderCode)
                .eoId(sampleEoId)
                .planType(PlanType.ENTERPRISE)
                .amount(BigDecimal.valueOf(249000))
                .status("PENDING")
                .build();
        lenient().when(payOSOrderRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(dbOrder));

        Map<String, Object> cancelledData = new HashMap<>();
        cancelledData.put("orderCode", orderCode);
        cancelledData.put("code", "00");
        cancelledData.put("status", "CANCELLED");
        cancelledData.put("amount", 249000);
        Map<String, Object> cancelledPayload = Map.of("data", cancelledData, "signature", "sig-ok");

        when(payOSConfig.verifyWebhookSignature(eq(cancelledData), eq("sig-ok"))).thenReturn(true);

        SubscriptionResponseDto res1 = subscriptionService.processPayOSWebhook(cancelledPayload);
        assertThat(res1.getStatus()).isNotEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(dbOrder.getStatus()).isEqualTo("PENDING");

        Map<String, Object> failedData = new HashMap<>();
        failedData.put("orderCode", orderCode);
        failedData.put("code", "01");
        failedData.put("amount", 249000);
        Map<String, Object> failedPayload = Map.of("data", failedData, "signature", "sig-fail");

        when(payOSConfig.verifyWebhookSignature(eq(failedData), eq("sig-fail"))).thenReturn(true);

        SubscriptionResponseDto res2 = subscriptionService.processPayOSWebhook(failedPayload);
        assertThat(res2.getStatus()).isNotEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(dbOrder.getStatus()).isEqualTo("PENDING");
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    @DisplayName("PAY-001.2: Webhook PayOS gửi amount < plan.price (hoặc thiếu amount) -> Từ chối BadRequestException và giữ nguyên PENDING")
    void processPayOSWebhook_UnderpaidAmount_RejectsAndActivatesNothing() {
        long orderCode = 202600002L;
        PayOSOrder dbOrder = PayOSOrder.builder()
                .orderId(UUID.randomUUID())
                .orderCode(orderCode)
                .eoId(sampleEoId)
                .planType(PlanType.ENTERPRISE)
                .amount(BigDecimal.valueOf(249000))
                .status("PENDING")
                .build();
        when(payOSOrderRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(dbOrder));

        Map<String, Object> underpaidData = new HashMap<>();
        underpaidData.put("orderCode", orderCode);
        underpaidData.put("code", "00");
        underpaidData.put("status", "PAID");
        underpaidData.put("amount", 1000); // Giả mạo trả 1.000đ cho gói 249.000đ
        Map<String, Object> payload = Map.of("data", underpaidData, "signature", "sig-underpaid");

        when(payOSConfig.verifyWebhookSignature(eq(underpaidData), eq("sig-underpaid"))).thenReturn(true);

        assertThatThrownBy(() -> subscriptionService.processPayOSWebhook(payload))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Số tiền thanh toán PayOS không khớp");

        assertThat(dbOrder.getStatus()).isEqualTo("PENDING");
        verify(payOSOrderRepository, never()).save(any());
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    @DisplayName("PAY-001.3: Webhook PayOS lần 1 khóa FOR UPDATE cập nhật DB sang PAID dù pendingOrders còn cache; retry lần 2, 3 là idempotent (kích hoạt đúng 1 lần)")
    void processPayOSWebhook_UpdatesDbPaidOnFirstCallAndIsIdempotentOnRetries() {
        long orderCode = 202600003L;
        seedPendingOrder(orderCode, sampleEoId, PlanType.ENTERPRISE);

        PayOSOrder dbOrder = PayOSOrder.builder()
                .orderId(UUID.randomUUID())
                .orderCode(orderCode)
                .eoId(sampleEoId)
                .planType(PlanType.ENTERPRISE)
                .amount(BigDecimal.valueOf(249000))
                .status("PENDING")
                .build();

        when(payOSOrderRepository.findByOrderCodeForUpdate(orderCode)).thenReturn(Optional.of(dbOrder));
        when(payOSOrderRepository.save(any(PayOSOrder.class))).thenAnswer(i -> i.getArgument(0));
        when(eventOrganizerRepository.findById(sampleEoId)).thenReturn(Optional.of(sampleEo));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> {
            Subscription s = i.getArgument(0);
            s.setSubscriptionId(UUID.randomUUID());
            return s;
        });
        when(eventOrganizerRepository.save(any(EventOrganizer.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> data = new HashMap<>();
        data.put("orderCode", orderCode);
        data.put("code", "00");
        data.put("status", "PAID");
        data.put("amount", 249000);
        Map<String, Object> payload = Map.of("data", data, "signature", "sig-valid");
        when(payOSConfig.verifyWebhookSignature(eq(data), eq("sig-valid"))).thenReturn(true);

        // Lần 1: Kích hoạt gói, đồng thời chuyển PayOSOrder.status sang PAID
        SubscriptionResponseDto first = subscriptionService.processPayOSWebhook(payload);
        assertThat(first.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(first.getPlanType()).isEqualTo(PlanType.ENTERPRISE);
        assertThat(dbOrder.getStatus()).isEqualTo("PAID");

        // Retry lần 2 & 3 từ PayOS: findByOrderCodeForUpdate thấy status == PAID -> trả idempotent, KHÔNG gọi lại activateSubscription
        SubscriptionResponseDto retry2 = subscriptionService.processPayOSWebhook(payload);
        SubscriptionResponseDto retry3 = subscriptionService.processPayOSWebhook(payload);
        assertThat(retry2.getPlanName()).isEqualTo("PayOS Webhook Accepted");
        assertThat(retry3.getPlanName()).isEqualTo("PayOS Webhook Accepted");

        verify(payOSOrderRepository, times(3)).findByOrderCodeForUpdate(orderCode);
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        verify(notificationService, times(1)).createAndSend(eq(sampleUserId), anyString(), anyString(), any(), anyString(), any(), anyMap());
    }

    @Test
    @DisplayName("PAY-001.4: createPayOSPaymentSession sinh orderCode không trùng lặp khi gọi liên tiếp (cả qua Sequence và Atomic fallback)")
    void createPayOSPaymentSession_GeneratesUniqueOrderCodesConcurrently() {
        when(eventOrganizerRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(sampleEo));
        when(payOSService.createPaymentRequest(anyLong(), anyInt(), anyString(), any(), any()))
                .thenAnswer(inv -> PayOSPaymentResponseDto.builder()
                        .orderCode(inv.getArgument(0))
                        .amount(inv.getArgument(1))
                        .status("PENDING")
                        .build());

        PurchaseSubscriptionRequestDto request = new PurchaseSubscriptionRequestDto();
        request.setPlanType(PlanType.MATCHING_PLUS);

        // 1. Khi Sequence trả null (ví dụ chưa có sequence), fallback Atomic counter + random suffix vẫn không bao giờ trùng
        Set<Long> fallbackCodes = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            PayOSPaymentResponseDto resp = subscriptionService.createPayOSPaymentSession(sampleUserId, request);
            assertThat(resp.getOrderCode()).isPositive();
            fallbackCodes.add(resp.getOrderCode());
        }
        assertThat(fallbackCodes).hasSize(50);

        // 2. Khi PostgreSQL Sequence hoạt động, sử dụng trực tiếp giá trị từ Sequence
        when(payOSOrderRepository.nextOrderCode()).thenReturn(1000000001L, 1000000002L);
        PayOSPaymentResponseDto seqResp1 = subscriptionService.createPayOSPaymentSession(sampleUserId, request);
        PayOSPaymentResponseDto seqResp2 = subscriptionService.createPayOSPaymentSession(sampleUserId, request);
        assertThat(seqResp1.getOrderCode()).isEqualTo(1000000001L);
        assertThat(seqResp2.getOrderCode()).isEqualTo(1000000002L);
    }
}
