package com.cani.service;

import com.cani.config.PayOSConfig;
import com.cani.config.VNPayConfig;
import com.cani.dto.subscription.PayOSPaymentResponseDto;
import com.cani.dto.subscription.PurchaseSubscriptionRequestDto;
import com.cani.dto.subscription.PurchaseSubscriptionResponseDto;
import com.cani.dto.subscription.SubscriptionPlanDto;
import com.cani.dto.subscription.SubscriptionResponseDto;
import com.cani.entity.EventOrganizer;
import com.cani.entity.Subscription;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.PlanType;
import com.cani.entity.enums.SubscriptionStatus;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.SubscriptionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service quản lý Gói dịch vụ & Cổng thanh toán VNPay Sandbox (UC-10, UC-11, UC-12, BR-08)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final EventOrganizerRepository eventOrganizerRepository;
    private final VNPayConfig vnPayConfig;
    private final PayOSConfig payOSConfig;
    private final PayOSService payOSService;

    @Autowired(required = false)
    private NotificationService notificationService;

    @Value("${cani.subscription.strict:false}")
    private boolean strictQuotaEnforcement;

    // Bộ nhớ đệm lưu thông tin giao dịch chờ đối soát VNPay (TxnRef -> OrderData)
    private final Map<String, PendingOrder> pendingOrders = new ConcurrentHashMap<>();

    private record PendingOrder(UUID eoId, PlanType planType, BigDecimal price, Instant createdAt) {}

    // =========================================================================
    // UC-10: XEM DANH MỤC GÓI CƯỚC (VIEW PACKAGE PLANS)
    // =========================================================================
    public List<SubscriptionPlanDto> getAvailablePlans() {
        return List.of(
                SubscriptionPlanDto.builder()
                        .planType(PlanType.FREE_TRIAL)
                        .name("Gói Trải Nghiệm (Khởi Nghiệp)")
                        .price(BigDecimal.ZERO)
                        .durationDays(30)
                        .maxEvents(2)
                        .maxShifts(6)
                        .maxWorkers(20)
                        .description("Tặng 2 sự kiện miễn phí cho Nhà tổ chức trải nghiệm toàn diện hệ thống Matching Radar.")
                        .features(List.of(
                                "Miễn phí 2 Sự kiện đầu tiên",
                                "Tối đa 6 Ca làm việc & 20 Nhân sự",
                                "Kích hoạt Thuật toán Ghép đôi (Matching Radar)",
                                "Điểm danh Check-in / Check-out bằng QR code",
                                "Checklist đầu việc & giám sát trực tiếp"
                        ))
                        .isRecommended(false)
                        .build(),

                SubscriptionPlanDto.builder()
                        .planType(PlanType.MATCHING_PLUS)
                        .name("Gói Tiêu Chuẩn (Standard)")
                        .price(BigDecimal.valueOf(99000))
                        .durationDays(30)
                        .maxEvents(5)
                        .maxShifts(25)
                        .maxWorkers(80)
                        .description("Tối ưu cho đơn vị tổ chức định kỳ: Tuyển dụng chủ động kết hợp Matching Radar.")
                        .features(List.of(
                                "Tạo 5 Sự kiện với tối đa 25 Ca làm việc",
                                "Kích hoạt Thuật toán Ghép đôi (Matching Engine)",
                                "Hiển thị ca làm việc ưu tiên trên Bản đồ Job Radar di động",
                                "Bắn thông báo đẩy (FCM Push) đến ứng viên phù hợp",
                                "Tự động kích hoạt hỗ trợ khi có biến động ca làm"
                        ))
                        .isRecommended(true)
                        .build(),

                SubscriptionPlanDto.builder()
                        .planType(PlanType.ENTERPRISE)
                        .name("Gói Chuyên Nghiệp (Pro / Doanh Nghiệp)")
                        .price(BigDecimal.valueOf(249000))
                        .durationDays(30)
                        .maxEvents(20)
                        .maxShifts(100)
                        .maxWorkers(300)
                        .description("Dành cho quy mô lớn: Ưu tiên tuyển dụng tối đa và mở khóa Gọi Thêm Người Khẩn Cấp.")
                        .features(List.of(
                                "Tạo 20 Sự kiện với 100 Ca làm việc & 300 Nhân sự",
                                "Ưu tiên xếp hạng cao nhất trên Radar tuyển dụng",
                                "Mở khóa tính năng Gọi Nhân Sự Khẩn Cấp (Emergency Dispatch)",
                                "Báo cáo số liệu chuyên sâu & đối soát đa chiều",
                                "Kênh chăm sóc & hỗ trợ kỹ thuật riêng 24/7"
                        ))
                        .isRecommended(false)
                        .build()
        );
    }

    // =========================================================================
    // UC-11: KHỞI TẠO PHIÊN THANH TOÁN VNPAY (PURCHASE PACKAGE VIA VNPAY)
    // =========================================================================
    @Transactional
    public PurchaseSubscriptionResponseDto createVNPayPaymentSession(
            UUID userId, PurchaseSubscriptionRequestDto dto, HttpServletRequest request) {

        EventOrganizer eo = getEoOrThrow(userId);
        SubscriptionPlanDto plan = getPlanDefinition(dto.getPlanType());

        // Sinh mã giao dịch duy nhất
        String vnpTxnRef = "CANI_" + System.currentTimeMillis();
        pendingOrders.put(vnpTxnRef, new PendingOrder(eo.getEoId(), plan.getPlanType(), plan.getPrice(), Instant.now()));

        // Chuẩn bị tham số theo đúng tài liệu VNPay Sandbox
        long amountInVnd = plan.getPrice().longValue();
        long amountForVnPay = amountInVnd * 100L; // VNPay tính theo đơn vị hào (x100)

        String returnUrl = (dto.getReturnUrl() != null && !dto.getReturnUrl().isBlank())
                ? dto.getReturnUrl()
                : vnPayConfig.getReturnUrl();

        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        String vnpCreateDate = formatter.format(cld.getTime());
        cld.add(Calendar.MINUTE, 15);
        String vnpExpireDate = formatter.format(cld.getTime());

        String clientIp = VNPayConfig.getIpAddress(request);

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", vnPayConfig.getTmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(amountForVnPay));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", vnpTxnRef);
        vnpParams.put("vnp_OrderInfo", "Mua " + plan.getName() + " tren san CANi");
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", returnUrl);
        vnpParams.put("vnp_IpAddr", clientIp);
        vnpParams.put("vnp_CreateDate", vnpCreateDate);
        vnpParams.put("vnp_ExpireDate", vnpExpireDate);

        // Tạo query string và mã băm SHA-512
        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnpParams.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                hashData.append(fieldName).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII))
                        .append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String vnpSecureHash = vnPayConfig.hmacSHA512(vnPayConfig.getHashSecret(), hashData.toString());
        query.append("&vnp_SecureHash=").append(vnpSecureHash);
        String paymentUrl = vnPayConfig.getPayUrl() + "?" + query;

        log.info("EO {} khởi tạo phiên thanh toán VNPay: TxnRef={}, Plan={}, Amount={}",
                eo.getEoId(), vnpTxnRef, plan.getPlanType(), plan.getPrice());

        return PurchaseSubscriptionResponseDto.builder()
                .paymentUrl(paymentUrl)
                .txnRef(vnpTxnRef)
                .planType(plan.getPlanType())
                .planName(plan.getName())
                .amount(plan.getPrice())
                .orderInfo(vnpParams.get("vnp_OrderInfo"))
                .isDemo(false)
                .build();
    }

    // =========================================================================
    // UC-11: XỬ LÝ KẾT QUẢ THANH TOÁN VNPAY (CALLBACK / IPN)
    // =========================================================================
    @Transactional
    public SubscriptionResponseDto processVNPayCallback(Map<String, String> queryParams) {
        String vnpSecureHash = queryParams.get("vnp_SecureHash");
        if (vnpSecureHash == null || vnpSecureHash.isBlank()) {
            throw new BadRequestException("Thiếu mã băm bảo mật vnp_SecureHash");
        }

        Map<String, String> fields = new HashMap<>(queryParams);
        fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");

        String calculatedHash = vnPayConfig.hashAllFields(fields);
        if (!calculatedHash.equalsIgnoreCase(vnpSecureHash)) {
            log.error("Sai chữ ký bảo mật VNPay! Calculated: {}, Received: {}", calculatedHash, vnpSecureHash);
            throw new BadRequestException("Chữ ký bảo mật VNPay không hợp lệ (Checksum mismatch)");
        }

        String responseCode = queryParams.get("vnp_ResponseCode");
        String txnRef = queryParams.get("vnp_TxnRef");
        String transactionNo = queryParams.get("vnp_TransactionNo");

        PendingOrder order = pendingOrders.remove(txnRef);
        if (order == null) {
            log.warn("Không tìm thấy đơn hàng chờ cho TxnRef: {}", txnRef);
            throw new ResourceNotFoundException("Giao dịch không tồn tại hoặc đã xử lý trước đó: " + txnRef);
        }

        if (!"00".equals(responseCode)) {
            log.warn("Giao dịch VNPay thất bại hoặc bị hủy: TxnRef={}, ResponseCode={}", txnRef, responseCode);
            throw new BadRequestException("Thanh toán không thành công qua VNPay (Mã lỗi: " + responseCode + ")");
        }

        EventOrganizer eo = eventOrganizerRepository.findById(order.eoId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà tổ chức: " + order.eoId()));

        return activateSubscription(eo, order.planType(), "VNPAY", txnRef, transactionNo);
    }

    // =========================================================================
    // UC-11 (DEMO PHÒNG VỆ): GIẢ LẬP THANH TOÁN THÀNH CÔNG CHO DEMO HỘI ĐỒNG
    // =========================================================================
    @Transactional
    public SubscriptionResponseDto simulatePurchaseDemo(UUID userId, PlanType planType) {
        EventOrganizer eo = getEoOrThrow(userId);
        String mockTxnRef = "DEMO_" + System.currentTimeMillis();
        log.info("Kích hoạt chế độ Sandbox Demo mua gói {} cho EO {}", planType, eo.getEoId());
        return activateSubscription(eo, planType, "DEMO_SANDBOX", mockTxnRef, "MOCK_999999");
    }

    // =========================================================================
    // UC-10 & UC-12: LẤY THÔNG TIN GÓI HIỆN TẠI VÀ LỊCH SỬ MUA GÓI
    // =========================================================================
    @Transactional(readOnly = true)
    public SubscriptionResponseDto getCurrentSubscription(UUID userId) {
        EventOrganizer eo = getEoOrThrow(userId);
        Instant now = Instant.now();

        List<Subscription> activeSubs = subscriptionRepository.findActiveSubscriptions(eo.getEoId(), SubscriptionStatus.ACTIVE, now);
        if (activeSubs.isEmpty()) {
            return SubscriptionResponseDto.builder()
                    .eoId(eo.getEoId())
                    .planName("Chưa kích hoạt gói")
                    .planType(PlanType.INVITE_ONLY)
                    .price(BigDecimal.ZERO)
                    .maxEvents(0)
                    .eventsUsed(0)
                    .remainingEvents(0)
                    .maxShifts(0)
                    .shiftsUsed(0)
                    .remainingShifts(0)
                    .status(SubscriptionStatus.EXPIRED)
                    .isExpired(true)
                    .build();
        }

        return mapToDto(activeSubs.get(0));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponseDto> getSubscriptionHistory(UUID userId) {
        EventOrganizer eo = getEoOrThrow(userId);
        return subscriptionRepository.findByEventOrganizer_EoIdOrderByCreatedAtDesc(eo.getEoId())
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // BR-08: KIỂM TRA & KHẤU TRỪ HẠN MỨC SỰ KIỆN (SUBSCRIPTION QUOTA ENFORCEMENT)
    // =========================================================================
    @Transactional
    public void validateAndDeductEventQuota(EventOrganizer eo) {
        Instant now = Instant.now();
        List<Subscription> activeSubs = subscriptionRepository.findActiveSubscriptions(eo.getEoId(), SubscriptionStatus.ACTIVE, now);

        if (activeSubs.isEmpty()) {
            // Tự động cấp Gói Trải Nghiệm Khởi Nghiệp Miễn Phí (2 sự kiện) nếu đây là EO mới chưa có gói
            log.info("EO {} chưa có gói: Tự động kích hoạt Gói Trải Nghiệm Miễn Phí (FREE_TRIAL) 2 sự kiện", eo.getEoId());
            SubscriptionResponseDto freeSubDto = activateSubscription(eo, PlanType.FREE_TRIAL, "FREE_WELCOME", "FREE_" + System.currentTimeMillis(), "WELCOME_BONUS");
            Subscription freeSub = subscriptionRepository.findById(freeSubDto.getSubscriptionId()).orElse(null);
            if (freeSub != null) {
                activeSubs = List.of(freeSub);
            } else if (strictQuotaEnforcement) {
                throw new BadRequestException("Bạn cần mua Gói dịch vụ để tạo sự kiện.");
            } else {
                return;
            }
        }

        Subscription sub = activeSubs.get(0);
        int max = sub.getMaxEvents() != null ? sub.getMaxEvents() : 0;
        int used = sub.getEventsUsed() != null ? sub.getEventsUsed() : 0;

        if (used >= max) {
            if (strictQuotaEnforcement) {
                throw new BadRequestException("Bạn đã sử dụng hết hạn mức tạo sự kiện của gói (" + used + "/" + max + "). Vui lòng nâng cấp gói tại mục Gói Dịch Vụ để tiếp tục.");
            } else {
                log.info("Chế độ Demo/Dev: EO {} đã hết quota ({}/{}) nhưng strict = false, tiếp tục cho phép.", eo.getEoId(), used, max);
            }
        }

        sub.setEventsUsed(used + 1);
        subscriptionRepository.save(sub);
        log.info("Đã khấu trừ 1 credit Event cho EO {}. Đã dùng: {}/{}", eo.getEoId(), sub.getEventsUsed(), max);
    }

    // =========================================================================
    // PAYOS VIETQR GATEWAY INTEGRATION
    // =========================================================================

    /**
     * Khởi tạo giao dịch thanh toán mua gói cước qua Cổng PayOS VietQR
     */
    @Transactional
    public PayOSPaymentResponseDto createPayOSPaymentSession(UUID userId, PurchaseSubscriptionRequestDto dto) {
        EventOrganizer eo = getEoOrThrow(userId);
        SubscriptionPlanDto plan = getPlanDefinition(dto.getPlanType());

        // Nếu là gói miễn phí trải nghiệm (0đ), kích hoạt trực tiếp không cần qua cổng thanh toán
        if (plan.getPrice().compareTo(BigDecimal.ZERO) == 0) {
            activateSubscription(eo, plan.getPlanType(), "FREE_PROMO", "FREE_" + System.currentTimeMillis(), "WELCOME_FREE");
            return PayOSPaymentResponseDto.builder()
                    .orderCode(System.currentTimeMillis())
                    .amount(0)
                    .description("Kích hoạt Gói Trải Nghiệm thành công")
                    .status("PAID")
                    .checkoutUrl("")
                    .build();
        }

        long orderCode = Math.abs(System.currentTimeMillis() % 900000000L) + 100000000L;
        pendingOrders.put("PAYOS_" + orderCode, new PendingOrder(eo.getEoId(), plan.getPlanType(), plan.getPrice(), Instant.now()));

        String desc = "CANI " + plan.getName();
        if (desc.length() > 25) {
            desc = desc.substring(0, 25);
        }
        String buyerName = eo.getUser() != null ? eo.getUser().getFullName() : "Khach hang CANi";
        String buyerEmail = eo.getUser() != null ? eo.getUser().getEmail() : "user@cani.vn";

        return payOSService.createPaymentRequest(
                orderCode,
                plan.getPrice().intValue(),
                desc,
                buyerName,
                buyerEmail
        );
    }

    /**
     * Xác nhận thanh toán PayOS theo người dùng đăng nhập (Tránh Cross-Tenant Leak & IDOR)
     */
    @Transactional
    public SubscriptionResponseDto confirmPayOSPaymentForUser(UUID userId, long orderCode) {
        EventOrganizer eo = getEoOrThrow(userId);
        PendingOrder order = pendingOrders.remove("PAYOS_" + orderCode);

        if (order != null) {
            if (!order.eoId().equals(eo.getEoId())) {
                log.warn("Cảnh báo bảo mật: User {} cố kích hoạt đơn hàng PayOS {} thuộc về EO {}", userId, orderCode, order.eoId());
                throw new BadRequestException("Giao dịch không thuộc về tài khoản Ban tổ chức hiện tại");
            }
            return activateSubscription(eo, order.planType(), "PAYOS_VIETQR", "PAYOS_" + orderCode, "POS_" + orderCode);
        }

        // Trường hợp reload lại trang hoặc Webhook đã xử lý kích hoạt trước đó cho chính EO này
        Instant now = Instant.now();
        List<Subscription> activeSubs = subscriptionRepository.findActiveSubscriptions(eo.getEoId(), SubscriptionStatus.ACTIVE, now);
        if (!activeSubs.isEmpty()) {
            return mapToDto(activeSubs.get(0));
        }

        throw new BadRequestException("Giao dịch không tồn tại hoặc đã được xử lý trước đó: " + orderCode);
    }

    /**
     * Xác nhận thanh toán thành công qua PayOS theo orderCode (Dành cho Webhook hoặc hệ thống nội bộ)
     */
    @Transactional
    public SubscriptionResponseDto confirmPayOSPayment(long orderCode) {
        PendingOrder order = pendingOrders.remove("PAYOS_" + orderCode);
        if (order == null) {
            log.info("PayOS Webhook/Internal: Giao dịch PAYOS_{} đã được xử lý trước đó hoặc không còn trong hàng đợi.", orderCode);
            return SubscriptionResponseDto.builder()
                    .planName("PayOS Webhook Accepted")
                    .status(SubscriptionStatus.ACTIVE)
                    .build();
        }

        EventOrganizer eo = eventOrganizerRepository.findById(order.eoId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ Ban tổ chức sự kiện: " + order.eoId()));

        return activateSubscription(eo, order.planType(), "PAYOS_VIETQR", "PAYOS_" + orderCode, "POS_" + orderCode);
    }

    /**
     * Nhận và xử lý Webhook tự động từ PayOS
     */
    @Transactional
    public SubscriptionResponseDto processPayOSWebhook(Map<String, Object> webhookPayload) {
        if (webhookPayload == null) {
            throw new BadRequestException("Payload webhook rỗng");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) webhookPayload.get("data");
        String signature = (String) webhookPayload.get("signature");

        if (data == null || signature == null) {
            throw new BadRequestException("Dữ liệu webhook PayOS không hợp lệ");
        }

        boolean verified = payOSConfig.verifyWebhookSignature(data, signature);
        if (!verified) {
            log.warn("Chữ ký bảo mật Webhook PayOS không khớp! Bỏ qua giao dịch.");
            throw new BadRequestException("Chữ ký bảo mật PayOS không hợp lệ");
        }

        Object orderCodeObj = data.get("orderCode");
        long orderCode = orderCodeObj instanceof Number n ? n.longValue() : Long.parseLong(orderCodeObj.toString());

        log.info("Webhook PayOS xác thực thành công cho orderCode: {}", orderCode);
        return confirmPayOSPayment(orderCode);
    }

    // =========================================================================
    // PHƯƠNG THỨC HỖ TRỢ NỘI BỘ
    // =========================================================================
    private SubscriptionResponseDto activateSubscription(
            EventOrganizer eo, PlanType planType, String method, String txnRef, String externalRef) {

        SubscriptionPlanDto plan = getPlanDefinition(planType);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofDays(plan.getDurationDays()));

        String featuresJson = String.format(
                "{\"paymentMethod\":\"%s\",\"txnRef\":\"%s\",\"externalRef\":\"%s\",\"activatedAt\":\"%s\"}",
                method, txnRef, externalRef, now.toString()
        );

        Subscription subscription = Subscription.builder()
                .eventOrganizer(eo)
                .planName(plan.getName())
                .planType(plan.getPlanType())
                .durationDays(plan.getDurationDays())
                .price(plan.getPrice())
                .maxEvents(plan.getMaxEvents())
                .maxShifts(plan.getMaxShifts())
                .maxWorkers(plan.getMaxWorkers())
                .eventsUsed(0)
                .shiftsUsed(0)
                .status(SubscriptionStatus.ACTIVE)
                .startsAt(now)
                .expiresAt(expiresAt)
                .features(featuresJson)
                .build();

        subscription = subscriptionRepository.save(subscription);

        eo.setSubscription(subscription);
        eo.setSubscriptionExpiresAt(expiresAt);
        eventOrganizerRepository.save(eo);

        log.info("Đã kích hoạt Gói dịch vụ thành công cho EO {}: Gói={}, Hạn dùng={}",
                eo.getEoId(), plan.getName(), expiresAt);

        if (notificationService != null && eo.getUser() != null && eo.getUser().getUserId() != null) {
            try {
                notificationService.createAndSend(
                        eo.getUser().getUserId(),
                        "Kích hoạt Gói dịch vụ thành công",
                        "Bạn đã đăng ký thành công " + plan.getName() + ". Hạn mức: " + plan.getMaxEvents() + " sự kiện.",
                        NotificationType.SYSTEM,
                        "SUBSCRIPTION",
                        subscription.getSubscriptionId(),
                        Map.of("subscriptionId", subscription.getSubscriptionId().toString(), "planType", planType.name())
                );
            } catch (Exception ex) {
                log.warn("Không thể gửi thông báo kích hoạt gói cho EO: {}", ex.getMessage());
            }
        }

        return mapToDto(subscription);
    }

    private SubscriptionPlanDto getPlanDefinition(PlanType planType) {
        return getAvailablePlans().stream()
                .filter(p -> p.getPlanType() == planType)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Không tìm thấy cấu hình cho gói cước: " + planType));
    }

    private EventOrganizer getEoOrThrow(UUID userId) {
        return eventOrganizerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new AppException("Chỉ Nhà tổ chức sự kiện (Event Organizer) mới có quyền mua gói cước", HttpStatus.FORBIDDEN));
    }

    private SubscriptionResponseDto mapToDto(Subscription s) {
        int maxE = s.getMaxEvents() != null ? s.getMaxEvents() : 0;
        int usedE = s.getEventsUsed() != null ? s.getEventsUsed() : 0;
        int maxS = s.getMaxShifts() != null ? s.getMaxShifts() : 0;
        int usedS = s.getShiftsUsed() != null ? s.getShiftsUsed() : 0;

        boolean expired = s.getExpiresAt() != null && s.getExpiresAt().isBefore(Instant.now());

        return SubscriptionResponseDto.builder()
                .subscriptionId(s.getSubscriptionId())
                .eoId(s.getEventOrganizer() != null ? s.getEventOrganizer().getEoId() : null)
                .planName(s.getPlanName())
                .planType(s.getPlanType())
                .price(s.getPrice())
                .durationDays(s.getDurationDays())
                .maxEvents(maxE)
                .eventsUsed(usedE)
                .remainingEvents(Math.max(0, maxE - usedE))
                .maxShifts(maxS)
                .shiftsUsed(usedS)
                .remainingShifts(Math.max(0, maxS - usedS))
                .maxWorkers(s.getMaxWorkers())
                .status(expired ? SubscriptionStatus.EXPIRED : s.getStatus())
                .startsAt(s.getStartsAt())
                .expiresAt(s.getExpiresAt())
                .isExpired(expired)
                .features(s.getFeatures())
                .createdAt(s.getCreatedAt())
                .build();
    }
}

