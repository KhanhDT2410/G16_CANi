package com.cani.service.eo;

import com.cani.config.PayOSConfig;
import com.cani.dto.subscription.*;
import com.cani.entity.EventOrganizer;
import com.cani.entity.Subscription;
import com.cani.entity.User;
import com.cani.entity.enums.PlanType;
import com.cani.entity.enums.SubscriptionStatus;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.UserStatus;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.SubscriptionRepository;
import com.cani.service.NotificationService;
import com.cani.service.PayOSService;
import com.cani.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exhaustive Unit Test Suite for SubscriptionService (Event Organizer Billing & Quota Engine).
 * Covers FC-SUB-01, FC-SUB-02, FC-SUB-03 (PayOS VietQR Gateway, BR-08) conforming to IEEE 829.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private EventOrganizerRepository eventOrganizerRepository;

    @Mock
    private PayOSConfig payOSConfig;

    @Mock
    private PayOSService payOSService;

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

    // =========================================================================
    // 1. getAvailablePlans() - FC-SUB-01
    // =========================================================================
    @Nested
    @DisplayName("1. getAvailablePlans() Tests")
    class GetAvailablePlansTests {

        @Test
        @DisplayName("UTC001: Trả về đầy đủ 3 gói cước chuẩn: FREE_TRIAL, MATCHING_PLUS, ENTERPRISE")
        void getAvailablePlans_ReturnsThreePlansWithCorrectAttributes() {
            List<SubscriptionPlanDto> plans = subscriptionService.getAvailablePlans();

            assertThat(plans).hasSize(3);
            assertThat(plans).extracting(SubscriptionPlanDto::getPlanType)
                    .containsExactlyInAnyOrder(PlanType.FREE_TRIAL, PlanType.MATCHING_PLUS, PlanType.ENTERPRISE);

            SubscriptionPlanDto matchingPlus = plans.stream()
                    .filter(p -> p.getPlanType() == PlanType.MATCHING_PLUS)
                    .findFirst().orElseThrow();
            assertThat(matchingPlus.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(99000));
            assertThat(matchingPlus.getMaxEvents()).isEqualTo(5);
            assertThat(matchingPlus.getDurationDays()).isEqualTo(30);
        }
    }

    // =========================================================================
    // 2. createPayOSPaymentSession() - FC-SUB-01
    // =========================================================================
    @Nested
    @DisplayName("2. createPayOSPaymentSession() Tests")
    class PayOSSessionTests {

        @Test
        @DisplayName("UTC002: EO khởi tạo thanh toán VietQR gói trả phí thành công gọi PayOSService")
        void createPayOSPaymentSession_PaidPlan_CallsPayOSService() {
            when(eventOrganizerRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(sampleEo));

            PayOSPaymentResponseDto mockPayOSResponse = PayOSPaymentResponseDto.builder()
                    .orderCode(12345678L)
                    .checkoutUrl("https://pay.payos.vn/web/12345678")
                    .qrCode("vietqr_data_string")
                    .amount(99000)
                    .description("Mua MATCHING_PLUS")
                    .build();

            when(payOSService.createPaymentRequest(anyLong(), anyInt(), anyString(), any(), any()))
                    .thenReturn(mockPayOSResponse);

            PurchaseSubscriptionRequestDto request = new PurchaseSubscriptionRequestDto();
            request.setPlanType(PlanType.MATCHING_PLUS);

            PayOSPaymentResponseDto response = subscriptionService.createPayOSPaymentSession(sampleUserId, request);

            assertThat(response).isNotNull();
            assertThat(response.getOrderCode()).isEqualTo(12345678L);
            assertThat(response.getCheckoutUrl()).contains("pay.payos.vn");
            verify(payOSService, times(1)).createPaymentRequest(anyLong(), eq(99000), anyString(), any(), any());
        }

        @Test
        @DisplayName("UTC003: Người dùng không phải EO bị từ chối khởi tạo thanh toán PayOS (403 FORBIDDEN)")
        void createPayOSPaymentSession_NotEO_ThrowsAppException403() {
            when(eventOrganizerRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.empty());

            PurchaseSubscriptionRequestDto request = new PurchaseSubscriptionRequestDto();
            request.setPlanType(PlanType.MATCHING_PLUS);

            AppException ex = assertThrows(AppException.class, () ->
                    subscriptionService.createPayOSPaymentSession(sampleUserId, request));
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(ex.getMessage()).contains("Chỉ Nhà tổ chức sự kiện (Event Organizer) mới có quyền mua gói cước");
        }

        @Test
        @DisplayName("UTC004: Mua gói FREE_TRIAL qua PayOS kích hoạt ngay lập tức mà không gọi cổng thanh toán")
        void createPayOSPaymentSession_FreeTrialPlan_ActivatesImmediatelyWithoutGateway() {
            when(eventOrganizerRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(sampleEo));
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> {
                Subscription s = i.getArgument(0);
                s.setSubscriptionId(UUID.randomUUID());
                return s;
            });
            when(eventOrganizerRepository.save(any(EventOrganizer.class))).thenAnswer(i -> i.getArgument(0));

            PurchaseSubscriptionRequestDto request = new PurchaseSubscriptionRequestDto();
            request.setPlanType(PlanType.FREE_TRIAL);

            PayOSPaymentResponseDto response = subscriptionService.createPayOSPaymentSession(sampleUserId, request);

            assertThat(response).isNotNull();
            assertThat(response.getCheckoutUrl()).isEmpty();
            assertThat(response.getDescription()).contains("Kích hoạt Gói Trải Nghiệm thành công");
            verify(payOSService, never()).createPaymentRequest(anyLong(), anyInt(), anyString(), any(), any());
            verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        }
    }

    // =========================================================================
    // 3. confirmPayOSPaymentForUser() - FC-SUB-01
    // =========================================================================
    @Nested
    @DisplayName("3. confirmPayOSPaymentForUser() Tests")
    class PayOSConfirmTests {

        @Test
        @DisplayName("UTC005: Xác nhận thanh toán PayOS thành công kích hoạt gói cước và gửi thông báo cho EO")
        void confirmPayOSPaymentForUser_Success_ActivatesSubscriptionAndSendsNotification() {
            long orderCode = 998877L;
            seedPendingOrder(orderCode, sampleEoId, PlanType.MATCHING_PLUS);

            when(eventOrganizerRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(sampleEo));
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> {
                Subscription s = i.getArgument(0);
                s.setSubscriptionId(UUID.randomUUID());
                return s;
            });
            when(eventOrganizerRepository.save(any(EventOrganizer.class))).thenAnswer(i -> i.getArgument(0));

            SubscriptionResponseDto result = subscriptionService.confirmPayOSPaymentForUser(sampleUserId, orderCode);

            assertThat(result).isNotNull();
            assertThat(result.getPlanType()).isEqualTo(PlanType.MATCHING_PLUS);
            assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            verify(subscriptionRepository, times(1)).save(any(Subscription.class));
            verify(eventOrganizerRepository, times(1)).save(any(EventOrganizer.class));
            verify(notificationService, times(1)).createAndSend(eq(sampleUserId), anyString(), anyString(), any(), anyString(), any(), anyMap());
        }

        @Test
        @DisplayName("UTC006: Xác nhận thanh toán PayOS ngăn chặn IDOR khi đơn hàng thuộc EO khác")
        void confirmPayOSPaymentForUser_DifferentEO_ThrowsBadRequestExceptionIdorProtection() {
            long orderCode = 887766L;
            UUID anotherEoId = UUID.fromString("99999999-9999-9999-9999-999999999999");
            seedPendingOrder(orderCode, anotherEoId, PlanType.MATCHING_PLUS);

            when(eventOrganizerRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(sampleEo));

            BadRequestException ex = assertThrows(BadRequestException.class, () ->
                    subscriptionService.confirmPayOSPaymentForUser(sampleUserId, orderCode));
            assertThat(ex.getMessage()).contains("Giao dịch không thuộc về tài khoản Ban tổ chức hiện tại");
            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        @DisplayName("UTC007: Đơn hàng PayOS không tồn tại trong hàng đợi và không có gói active ném BadRequestException")
        void confirmPayOSPaymentForUser_OrderNotFound_ThrowsBadRequestException() {
            long nonExistentOrder = 112233L;
            when(eventOrganizerRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(sampleEo));
            when(subscriptionRepository.findActiveSubscriptions(eq(sampleEoId), eq(SubscriptionStatus.ACTIVE), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            BadRequestException ex = assertThrows(BadRequestException.class, () ->
                    subscriptionService.confirmPayOSPaymentForUser(sampleUserId, nonExistentOrder));
            assertThat(ex.getMessage()).contains("Giao dịch không tồn tại hoặc đã được xử lý trước đó");
        }
    }

    // =========================================================================
    // 4. processPayOSWebhook() - FC-SUB-02
    // =========================================================================
    @Nested
    @DisplayName("4. processPayOSWebhook() Tests")
    class PayOSWebhookTests {

        @Test
        @DisplayName("UTC008: Webhook PayOS có chữ ký số hợp lệ kích hoạt gói cước thành công")
        void processPayOSWebhook_SuccessCode00_ActivatesSubscription() {
            long orderCode = 556677L;
            seedPendingOrder(orderCode, sampleEoId, PlanType.MATCHING_PLUS);

            Map<String, Object> data = new HashMap<>();
            data.put("orderCode", orderCode);
            data.put("code", "00");

            Map<String, Object> payload = new HashMap<>();
            payload.put("data", data);
            payload.put("signature", "valid_payos_hmac_sha256");

            when(payOSConfig.verifyWebhookSignature(eq(data), eq("valid_payos_hmac_sha256"))).thenReturn(true);
            when(eventOrganizerRepository.findById(sampleEoId)).thenReturn(Optional.of(sampleEo));
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> {
                Subscription s = i.getArgument(0);
                s.setSubscriptionId(UUID.randomUUID());
                return s;
            });
            when(eventOrganizerRepository.save(any(EventOrganizer.class))).thenAnswer(i -> i.getArgument(0));

            SubscriptionResponseDto result = subscriptionService.processPayOSWebhook(payload);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        }

        @Test
        @DisplayName("UTC009: Webhook PayOS có chữ ký không hợp lệ bị từ chối với BadRequestException")
        void processPayOSWebhook_InvalidSignature_ThrowsBadRequestException() {
            Map<String, Object> data = Map.of("orderCode", 99999L);
            Map<String, Object> payload = Map.of("data", data, "signature", "forged_signature");

            when(payOSConfig.verifyWebhookSignature(eq(data), eq("forged_signature"))).thenReturn(false);

            BadRequestException ex = assertThrows(BadRequestException.class, () ->
                    subscriptionService.processPayOSWebhook(payload));
            assertThat(ex.getMessage()).contains("Chữ ký bảo mật PayOS không hợp lệ");
            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        @DisplayName("UTC010: Webhook PayOS rỗng hoặc thiếu signature ném BadRequestException")
        void processPayOSWebhook_MissingSignatureOrData_ThrowsBadRequestException() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("data", null);
            payload.put("signature", null);

            BadRequestException ex = assertThrows(BadRequestException.class, () ->
                    subscriptionService.processPayOSWebhook(payload));
            assertThat(ex.getMessage()).contains("Dữ liệu webhook PayOS không hợp lệ");
        }

        @Test
        @DisplayName("UTC011: Webhook PayOS trùng lặp (đơn hàng đã xử lý xong) trả về DTO chấp nhận mà không cộng đúp")
        void processPayOSWebhook_OrderAlreadyProcessed_ReturnsAcceptedDtoWithoutDoubleCredit() {
            long orderCode = 778899L; // not in pendingOrders

            Map<String, Object> data = Map.of("orderCode", orderCode);
            Map<String, Object> payload = Map.of("data", data, "signature", "valid_signature");

            when(payOSConfig.verifyWebhookSignature(eq(data), eq("valid_signature"))).thenReturn(true);

            SubscriptionResponseDto result = subscriptionService.processPayOSWebhook(payload);

            assertThat(result).isNotNull();
            assertThat(result.getPlanName()).isEqualTo("PayOS Webhook Accepted");
            verify(subscriptionRepository, never()).save(any());
        }
    }

    // =========================================================================
    // 5. simulatePurchaseDemo() - FC-SUB-01
    // =========================================================================
    @Nested
    @DisplayName("5. simulatePurchaseDemo() Tests")
    class DemoSimulationTests {

        @Test
        @DisplayName("UTC012: Giả lập Sandbox Demo kích hoạt gói MATCHING_PLUS thành công cho hội đồng")
        void simulatePurchaseDemo_Success_ActivatesDemoSandboxSubscription() {
            when(eventOrganizerRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(sampleEo));
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> {
                Subscription s = i.getArgument(0);
                s.setSubscriptionId(UUID.randomUUID());
                return s;
            });
            when(eventOrganizerRepository.save(any(EventOrganizer.class))).thenAnswer(i -> i.getArgument(0));

            SubscriptionResponseDto result = subscriptionService.simulatePurchaseDemo(sampleUserId, PlanType.MATCHING_PLUS);

            assertThat(result).isNotNull();
            assertThat(result.getPlanType()).isEqualTo(PlanType.MATCHING_PLUS);
            assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(result.getFeatures()).contains("DEMO_SANDBOX");
            verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        }
    }

    // =========================================================================
    // 6. getCurrentSubscription() - FC-SUB-01
    // =========================================================================
    @Nested
    @DisplayName("6. getCurrentSubscription() Tests")
    class CurrentSubscriptionTests {

        @Test
        @DisplayName("UTC013: Lấy gói hiện tại thành công khi EO đang có gói ACTIVE")
        void getCurrentSubscription_ActivePlan_ReturnsMappedDto() {
            when(eventOrganizerRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(sampleEo));

            Subscription activeSub = Subscription.builder()
                    .subscriptionId(UUID.randomUUID())
                    .eventOrganizer(sampleEo)
                    .planName("Gói Matching Chuyên Nghiệp")
                    .planType(PlanType.MATCHING_PLUS)
                    .price(BigDecimal.valueOf(199000))
                    .durationDays(30)
                    .maxEvents(15)
                    .eventsUsed(3)
                    .status(SubscriptionStatus.ACTIVE)
                    .startsAt(Instant.now().minus(Duration.ofDays(5)))
                    .expiresAt(Instant.now().plus(Duration.ofDays(25)))
                    .build();

            when(subscriptionRepository.findActiveSubscriptions(eq(sampleEoId), eq(SubscriptionStatus.ACTIVE), any(Instant.class)))
                    .thenReturn(List.of(activeSub));

            SubscriptionResponseDto response = subscriptionService.getCurrentSubscription(sampleUserId);

            assertThat(response).isNotNull();
            assertThat(response.getPlanType()).isEqualTo(PlanType.MATCHING_PLUS);
            assertThat(response.getRemainingEvents()).isEqualTo(12);
            assertThat(response.isExpired()).isFalse();
        }

        @Test
        @DisplayName("UTC014: EO chưa mua gói trả về DTO mặc định trạng thái EXPIRED với 0 quota")
        void getCurrentSubscription_NoActivePlan_ReturnsExpiredDefaultDto() {
            when(eventOrganizerRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(sampleEo));
            when(subscriptionRepository.findActiveSubscriptions(eq(sampleEoId), eq(SubscriptionStatus.ACTIVE), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            SubscriptionResponseDto response = subscriptionService.getCurrentSubscription(sampleUserId);

            assertThat(response).isNotNull();
            assertThat(response.getPlanType()).isEqualTo(PlanType.INVITE_ONLY);
            assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
            assertThat(response.getRemainingEvents()).isEqualTo(0);
            assertThat(response.isExpired()).isTrue();
        }
    }

    // =========================================================================
    // 7. getSubscriptionHistory() - FC-SUB-01
    // =========================================================================
    @Nested
    @DisplayName("7. getSubscriptionHistory() Tests")
    class SubscriptionHistoryTests {

        @Test
        @DisplayName("UTC015: Lấy lịch sử mua gói của EO trả về danh sách sắp xếp theo thời gian mới nhất")
        void getSubscriptionHistory_ReturnsListOrderedByCreatedAt() {
            when(eventOrganizerRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(sampleEo));

            Subscription sub1 = Subscription.builder()
                    .subscriptionId(UUID.randomUUID())
                    .eventOrganizer(sampleEo)
                    .planType(PlanType.FREE_TRIAL)
                    .status(SubscriptionStatus.EXPIRED)
                    .build();

            Subscription sub2 = Subscription.builder()
                    .subscriptionId(UUID.randomUUID())
                    .eventOrganizer(sampleEo)
                    .planType(PlanType.MATCHING_PLUS)
                    .status(SubscriptionStatus.ACTIVE)
                    .build();

            when(subscriptionRepository.findByEventOrganizer_EoIdOrderByCreatedAtDesc(sampleEoId))
                    .thenReturn(List.of(sub2, sub1));

            List<SubscriptionResponseDto> history = subscriptionService.getSubscriptionHistory(sampleUserId);

            assertThat(history).hasSize(2);
            assertThat(history.get(0).getPlanType()).isEqualTo(PlanType.MATCHING_PLUS);
            assertThat(history.get(1).getPlanType()).isEqualTo(PlanType.FREE_TRIAL);
        }
    }

    // =========================================================================
    // 8. validateAndDeductEventQuota() - FC-SUB-03 (BR-08)
    // =========================================================================
    @Nested
    @DisplayName("8. validateAndDeductEventQuota() Tests (BR-08)")
    class QuotaValidationAndDeductionTests {

        @Test
        @DisplayName("UTC016: EO có hạn mức hợp lệ -> khấu trừ thành công 1 credit sự kiện")
        void validateAndDeductEventQuota_HasQuota_DeductsEventCredit() {
            Subscription activeSub = Subscription.builder()
                    .subscriptionId(UUID.randomUUID())
                    .eventOrganizer(sampleEo)
                    .planType(PlanType.MATCHING_PLUS)
                    .maxEvents(15)
                    .eventsUsed(5)
                    .status(SubscriptionStatus.ACTIVE)
                    .expiresAt(Instant.now().plus(Duration.ofDays(10)))
                    .build();

            when(subscriptionRepository.findActiveSubscriptions(eq(sampleEoId), eq(SubscriptionStatus.ACTIVE), any(Instant.class)))
                    .thenReturn(List.of(activeSub));
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

            subscriptionService.validateAndDeductEventQuota(sampleEo);

            assertThat(activeSub.getEventsUsed()).isEqualTo(6);
            verify(subscriptionRepository, times(1)).save(activeSub);
        }

        @Test
        @DisplayName("UTC017: EO mới chưa có gói -> tự động kích hoạt Gói Trải Nghiệm FREE_TRIAL và khấu trừ")
        void validateAndDeductEventQuota_NoSubscription_AutoProvisionsFreeTrialAndDeducts() {
            Subscription freeSub = Subscription.builder()
                    .subscriptionId(UUID.randomUUID())
                    .eventOrganizer(sampleEo)
                    .planType(PlanType.FREE_TRIAL)
                    .maxEvents(3)
                    .eventsUsed(0)
                    .status(SubscriptionStatus.ACTIVE)
                    .expiresAt(Instant.now().plus(Duration.ofDays(14)))
                    .build();

            when(subscriptionRepository.findActiveSubscriptions(eq(sampleEoId), eq(SubscriptionStatus.ACTIVE), any(Instant.class)))
                    .thenReturn(Collections.emptyList());
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> {
                Subscription s = i.getArgument(0);
                s.setSubscriptionId(freeSub.getSubscriptionId());
                return s;
            });
            when(eventOrganizerRepository.save(any(EventOrganizer.class))).thenAnswer(i -> i.getArgument(0));
            when(subscriptionRepository.findById(any())).thenReturn(Optional.of(freeSub));

            subscriptionService.validateAndDeductEventQuota(sampleEo);

            verify(subscriptionRepository, atLeastOnce()).save(any(Subscription.class));
            verify(eventOrganizerRepository, times(1)).save(any(EventOrganizer.class));
            assertThat(freeSub.getEventsUsed()).isEqualTo(1);
        }

        @Test
        @DisplayName("UTC018: Hết hạn mức sự kiện ở chế độ Strict -> ném BadRequestException")
        void validateAndDeductEventQuota_QuotaExhausted_StrictEnforcement_ThrowsBadRequestException() {
            ReflectionTestUtils.setField(subscriptionService, "strictQuotaEnforcement", true);

            Subscription exhaustedSub = Subscription.builder()
                    .subscriptionId(UUID.randomUUID())
                    .eventOrganizer(sampleEo)
                    .planType(PlanType.MATCHING_PLUS)
                    .maxEvents(10)
                    .eventsUsed(10) // 10/10 used
                    .status(SubscriptionStatus.ACTIVE)
                    .expiresAt(Instant.now().plus(Duration.ofDays(10)))
                    .build();

            when(subscriptionRepository.findActiveSubscriptions(eq(sampleEoId), eq(SubscriptionStatus.ACTIVE), any(Instant.class)))
                    .thenReturn(List.of(exhaustedSub));

            BadRequestException ex = assertThrows(BadRequestException.class, () ->
                    subscriptionService.validateAndDeductEventQuota(sampleEo));
            assertThat(ex.getMessage()).contains("Bạn đã sử dụng hết hạn mức tạo sự kiện của gói");
        }

        @Test
        @DisplayName("UTC019: Hết hạn mức sự kiện ở chế độ Non-Strict (Dev/Demo) -> cho phép qua với cảnh báo")
        void validateAndDeductEventQuota_QuotaExhausted_NonStrict_AllowsThrough() {
            ReflectionTestUtils.setField(subscriptionService, "strictQuotaEnforcement", false);

            Subscription exhaustedSub = Subscription.builder()
                    .subscriptionId(UUID.randomUUID())
                    .eventOrganizer(sampleEo)
                    .planType(PlanType.MATCHING_PLUS)
                    .maxEvents(10)
                    .eventsUsed(10)
                    .status(SubscriptionStatus.ACTIVE)
                    .expiresAt(Instant.now().plus(Duration.ofDays(10)))
                    .build();

            when(subscriptionRepository.findActiveSubscriptions(eq(sampleEoId), eq(SubscriptionStatus.ACTIVE), any(Instant.class)))
                    .thenReturn(List.of(exhaustedSub));
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

            assertDoesNotThrow(() ->
                    subscriptionService.validateAndDeductEventQuota(sampleEo));

            assertThat(exhaustedSub.getEventsUsed()).isEqualTo(11);
            verify(subscriptionRepository, times(1)).save(exhaustedSub);
        }
    }
}
