package com.cani.service;

import com.cani.config.PayOSConfig;
import com.cani.dto.subscription.PayOSPaymentResponseDto;
import com.cani.dto.subscription.PurchaseSubscriptionRequestDto;
import com.cani.dto.subscription.SubscriptionPlanDto;
import com.cani.dto.subscription.SubscriptionResponseDto;
import com.cani.entity.EventOrganizer;
import com.cani.entity.PayOSOrder;
import com.cani.entity.Plan;
import com.cani.entity.Subscription;
import com.cani.entity.enums.NotificationType;
import com.cani.entity.enums.PlanType;
import com.cani.entity.enums.SubscriptionStatus;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.PayOSOrderRepository;
import com.cani.repository.PlanRepository;
import com.cani.repository.SubscriptionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service quản lý Gói dịch vụ & Cổng thanh toán VietQR PayOS (UC-10, UC-11, UC-12, BR-08)
 * Đã chuẩn hóa liên kết bảng `plans` (Admin cấu hình) và bảng `subscriptions` (Trạng thái sở hữu của EO).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final EventOrganizerRepository eventOrganizerRepository;
    private final PlanRepository planRepository;
    private final PayOSConfig payOSConfig;
    private final PayOSService payOSService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private NotificationService notificationService;

    @Autowired(required = false)
    private PayOSOrderRepository payOSOrderRepository;

    @Value("${cani.subscription.strict:false}")
    private boolean strictQuotaEnforcement;

    @Value("${cani.subscription.demo-enabled:false}")
    private boolean demoEnabled;

    // Bộ nhớ đệm lưu thông tin giao dịch chờ đối soát PayOS (TxnRef -> OrderData)
    private final Map<String, PendingOrder> pendingOrders = new ConcurrentHashMap<>();

    private record PendingOrder(UUID eoId, PlanType planType, BigDecimal price, Instant createdAt) {}

    // =========================================================================
    // UC-10: XEM DANH MỤC GÓI CƯỚC (VIEW PACKAGE PLANS TỪ DATABASE)
    // =========================================================================
    public List<SubscriptionPlanDto> getAvailablePlans() {
        List<Plan> dbPlans = planRepository != null ? planRepository.findAll() : List.of();
        if (dbPlans == null || dbPlans.isEmpty()) {
            return getDefaultFallbackPlans();
        }

        return dbPlans.stream()
                .sorted(Comparator.comparing(Plan::getPrice))
                .map(this::mapPlanToDto)
                .collect(Collectors.toList());
    }

    private SubscriptionPlanDto mapPlanToDto(Plan plan) {
        List<String> featureList = parseFeatures(plan.getFeatures());
        String description = switch (plan.getPlanType()) {
            case FREE_TRIAL -> "Tặng 2 sự kiện miễn phí cho Nhà tổ chức trải nghiệm toàn diện hệ thống Matching Radar.";
            case MATCHING_PLUS -> "Tối ưu cho đơn vị tổ chức định kỳ: Tuyển dụng chủ động kết hợp Matching Radar.";
            case ENTERPRISE -> "Dành cho quy mô lớn: Ưu tiên tuyển dụng tối đa và mở khóa Gọi Thêm Người Khẩn Cấp.";
            default -> "Gói dịch vụ cho Nhà tổ chức sự kiện CANi.";
        };

        return SubscriptionPlanDto.builder()
                .planType(plan.getPlanType())
                .name(plan.getPlanName())
                .price(plan.getPrice())
                .durationDays(plan.getDurationDays())
                .maxEvents(plan.getMaxEvents())
                .maxShifts(plan.getMaxShifts())
                .maxWorkers(plan.getMaxWorkers())
                .description(description)
                .features(featureList)
                .isRecommended(plan.getPlanType() == PlanType.MATCHING_PLUS)
                .build();
    }

    private List<String> parseFeatures(String featuresJson) {
        if (featuresJson == null || featuresJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(featuresJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.debug("Không thể parse features dưới dạng List<String>, trả về danh sách rỗng: {}", e.getMessage());
            return List.of();
        }
    }

    private List<SubscriptionPlanDto> getDefaultFallbackPlans() {
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
                                "Hiển thị ca làm việc ưu tiên trên Bản đồ Job Radar di động",
                                "Bắn thông báo đẩy (FCM Push) đến ứng viên phù hợp",
                                "Tự động kích hoạt hỗ trợ khi có biến động ca làm"
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
                                "Tạo 5 Sự kiện với tối đa 25 Ca làm việc & 80 Nhân sự",
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
    // UC-11 (DEMO PHÒNG VỆ): GIẢ LẬP THANH TOÁN THÀNH CÔNG CHO DEMO HỘI ĐỒNG
    // =========================================================================
    @Transactional
    public SubscriptionResponseDto simulatePurchaseDemo(UUID userId, PlanType planType) {
        if (!demoEnabled) {
            throw new AppException("Chức năng mô phỏng thanh toán bị vô hiệu hóa trên môi trường này.", HttpStatus.FORBIDDEN);
        }
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
            List<Subscription> pastSubs = subscriptionRepository.findByEventOrganizer_EoIdOrderByCreatedAtDesc(eo.getEoId());
            boolean hadPastSub = !pastSubs.isEmpty();
            Subscription past = hadPastSub ? pastSubs.get(0) : null;
            String planName = past != null ? past.getPlanName() + " (Đã hết hạn)" : "Chưa kích hoạt gói (Mời người miễn phí)";

            return SubscriptionResponseDto.builder()
                    .subscriptionId(past != null ? past.getSubscriptionId() : null)
                    .eoId(eo.getEoId())
                    .planName(planName)
                    .planType(past != null ? past.getPlanType() : PlanType.INVITE_ONLY)
                    .price(BigDecimal.ZERO)
                    .maxEvents(past != null ? past.getMaxEvents() : 0)
                    .eventsUsed(past != null ? past.getEventsUsed() : 0)
                    .remainingEvents(0)
                    .maxShifts(past != null ? past.getMaxShifts() : 0)
                    .shiftsUsed(past != null ? past.getShiftsUsed() : 0)
                    .remainingShifts(0)
                    .maxWorkers(past != null ? past.getMaxWorkers() : 0)
                    .status(SubscriptionStatus.EXPIRED)
                    .isExpired(true)
                    .build();
        }

        if (activeSubs.size() == 1) {
            return mapToDto(activeSubs.get(0));
        }

        // CỘNG DỒN HẠN MỨC & THỜI HẠN (MULTI-SUBSCRIPTION POOL) KHI CÓ NHIỀU HƠN 1 GÓI ĐANG HOẠT ĐỘNG
        Subscription primarySub = activeSubs.get(0);
        int totalMaxEvents = activeSubs.stream()
                .mapToInt(s -> s.getMaxEvents() != null ? s.getMaxEvents() : 0)
                .sum();
        int totalEventsUsed = activeSubs.stream()
                .mapToInt(s -> s.getEventsUsed() != null ? s.getEventsUsed() : 0)
                .sum();
        int totalRemainingEvents = Math.max(0, totalMaxEvents - totalEventsUsed);

        int totalMaxShifts = activeSubs.stream()
                .mapToInt(s -> s.getMaxShifts() != null ? s.getMaxShifts() : 0)
                .sum();
        int totalShiftsUsed = activeSubs.stream()
                .mapToInt(s -> s.getShiftsUsed() != null ? s.getShiftsUsed() : 0)
                .sum();
        int totalRemainingShifts = Math.max(0, totalMaxShifts - totalShiftsUsed);

        int maxWorkers = activeSubs.stream()
                .mapToInt(s -> s.getMaxWorkers() != null ? s.getMaxWorkers() : 0)
                .max()
                .orElse(0);

        Instant latestExpiresAt = activeSubs.stream()
                .map(Subscription::getExpiresAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(primarySub.getExpiresAt());

        SubscriptionResponseDto baseDto = mapToDto(primarySub);
        baseDto.setMaxEvents(totalMaxEvents);
        baseDto.setEventsUsed(totalEventsUsed);
        baseDto.setRemainingEvents(totalRemainingEvents);
        baseDto.setMaxShifts(totalMaxShifts);
        baseDto.setShiftsUsed(totalShiftsUsed);
        baseDto.setRemainingShifts(totalRemainingShifts);
        baseDto.setMaxWorkers(maxWorkers);
        baseDto.setExpiresAt(latestExpiresAt);
        baseDto.setPlanName(primarySub.getPlanName() + " (" + activeSubs.size() + " gói cộng dồn)");
        return baseDto;
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
    // BR-08: KIỂM TRA & KHẤU TRỪ HẠN MỨC SỰ KIỆN (MATCHING ENGINE CHỈ TIÊU QUOTA)
    // =========================================================================
    @Transactional
    public void validateAndDeductEventQuota(EventOrganizer eo) {
        Instant now = Instant.now();
        List<Subscription> activeSubs = subscriptionRepository.findActiveSubscriptions(eo.getEoId(), SubscriptionStatus.ACTIVE, now);

        if (activeSubs.isEmpty()) {
            throw new BadRequestException("Bạn chưa kích hoạt Gói dịch vụ Matching Engine. Vui lòng đăng ký gói dịch vụ tại mục Gói Dịch Vụ để sử dụng thuật toán ghép đôi, hoặc xuất bản sự kiện ở chế độ Mời Trực Tiếp (Miễn phí).");
        }

        // Tìm gói đầu tiên còn quota để khấu trừ (ưu tiên gói có hạn dùng gần nhất trước theo FIFO)
        List<Subscription> sortedSubs = activeSubs.stream()
                .sorted(Comparator.comparing(
                        s -> s.getExpiresAt() != null ? s.getExpiresAt() : Instant.MAX
                ))
                .toList();

        Subscription availableSub = sortedSubs.stream()
                .filter(s -> {
                    int max = s.getMaxEvents() != null ? s.getMaxEvents() : 0;
                    int used = s.getEventsUsed() != null ? s.getEventsUsed() : 0;
                    return used < max;
                })
                .findFirst()
                .orElse(null);

        if (availableSub == null) {
            int totalMax = activeSubs.stream().mapToInt(s -> s.getMaxEvents() != null ? s.getMaxEvents() : 0).sum();
            int totalUsed = activeSubs.stream().mapToInt(s -> s.getEventsUsed() != null ? s.getEventsUsed() : 0).sum();
            if (strictQuotaEnforcement) {
                throw new BadRequestException("Bạn đã sử dụng hết hạn mức tạo sự kiện của gói (" + totalUsed + "/" + totalMax + "). Vui lòng nâng cấp gói tại mục Gói Dịch Vụ hoặc chọn chế độ Mời Trực Tiếp (Miễn phí).");
            } else {
                log.info("Chế độ Demo/Dev: EO {} đã hết quota ({}/{}) nhưng strict = false, tiếp tục cho phép.", eo.getEoId(), totalUsed, totalMax);
                availableSub = sortedSubs.get(0);
            }
        }

        int used = availableSub.getEventsUsed() != null ? availableSub.getEventsUsed() : 0;
        availableSub.setEventsUsed(used + 1);
        subscriptionRepository.save(availableSub);
        log.info("Đã khấu trừ 1 credit Event từ gói {} của EO {}. Quota gói: {}/{}",
                availableSub.getSubscriptionId(), eo.getEoId(), availableSub.getEventsUsed(), availableSub.getMaxEvents());
    }

    // =========================================================================
    // HOÀN TRẢ QUOTA KHI HỦY SỰ KIỆN CHƯA BẮT ĐẦU
    // =========================================================================
    @Transactional
    public void refundEventQuota(EventOrganizer eo) {
        Instant now = Instant.now();
        List<Subscription> activeSubs = subscriptionRepository.findActiveSubscriptions(eo.getEoId(), SubscriptionStatus.ACTIVE, now);

        Subscription subToRefund = null;
        if (!activeSubs.isEmpty()) {
            // Tìm gói active có eventsUsed > 0 (ưu tiên gói vừa bị khấu trừ)
            subToRefund = activeSubs.stream()
                    .filter(s -> s.getEventsUsed() != null && s.getEventsUsed() > 0)
                    .findFirst()
                    .orElse(activeSubs.get(0));
        } else {
            List<Subscription> pastSubs = subscriptionRepository.findByEventOrganizer_EoIdOrderByCreatedAtDesc(eo.getEoId());
            if (!pastSubs.isEmpty()) {
                subToRefund = pastSubs.get(0);
            }
        }

        if (subToRefund == null) {
            log.info("EO {} không có gói cước nào để hoàn trả quota sự kiện", eo.getEoId());
            return;
        }

        int used = subToRefund.getEventsUsed() != null ? subToRefund.getEventsUsed() : 0;
        if (used > 0) {
            subToRefund.setEventsUsed(used - 1);
            subscriptionRepository.save(subToRefund);
            log.info("Đã hoàn trả 1 credit Event cho gói {} của EO {}. Quota đã dùng giảm xuống: {}/{}",
                    subToRefund.getSubscriptionId(), eo.getEoId(), subToRefund.getEventsUsed(), subToRefund.getMaxEvents());
        }
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

        // Nếu là gói miễn phí trải nghiệm (0đ), kiểm tra điều kiện chống farm và chống ghi đè gói trả phí
        if (plan.getPrice().compareTo(BigDecimal.ZERO) == 0 || plan.getPlanType() == PlanType.FREE_TRIAL) {
            Instant now = Instant.now();
            List<Subscription> activeSubs = subscriptionRepository.findActiveSubscriptions(eo.getEoId(), SubscriptionStatus.ACTIVE, now);
            boolean hasActivePaidPlan = activeSubs.stream()
                    .anyMatch(s -> s.getPlanType() != PlanType.FREE_TRIAL && s.getPlanType() != PlanType.INVITE_ONLY);
            if (hasActivePaidPlan) {
                throw new BadRequestException("Tài khoản đang có gói dịch vụ trả phí hoạt động, không thể chuyển sang gói dùng thử.");
            }

            boolean hasUsedTrial = subscriptionRepository.findByEventOrganizer_EoIdOrderByCreatedAtDesc(eo.getEoId())
                    .stream()
                    .anyMatch(s -> s.getPlanType() == PlanType.FREE_TRIAL);
            if (hasUsedTrial) {
                throw new BadRequestException("Mỗi Nhà tổ chức sự kiện chỉ được sử dụng Gói Trải Nghiệm (Free Trial) 1 lần duy nhất.");
            }

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

        // Lưu đơn hàng bền vững vào DB trước khi gọi PayOS Gateway (Chặn mất mát order khi restart/multi-instance)
        if (payOSOrderRepository != null) {
            UUID planId = null;
            if (planRepository != null) {
                planId = planRepository.findFirstByPlanType(plan.getPlanType())
                        .map(Plan::getPlanId)
                        .orElse(null);
            }
            PayOSOrder payOSOrder = PayOSOrder.builder()
                    .orderCode(orderCode)
                    .eoId(eo.getEoId())
                    .planId(planId)
                    .planType(plan.getPlanType())
                    .amount(plan.getPrice())
                    .status("PENDING")
                    .build();
            payOSOrderRepository.save(payOSOrder);
        }

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

        // 1. Phục hồi thông tin đơn hàng từ cache in-memory hoặc từ repository bền vững payos_orders
        PendingOrder order = pendingOrders.get("PAYOS_" + orderCode);
        UUID orderEoId = order != null ? order.eoId() : null;
        PlanType orderPlanType = order != null ? order.planType() : null;

        if (order == null && payOSOrderRepository != null) {
            Optional<PayOSOrder> dbOrderOpt = payOSOrderRepository.findByOrderCode(orderCode);
            if (dbOrderOpt.isPresent()) {
                PayOSOrder dbOrder = dbOrderOpt.get();
                orderEoId = dbOrder.getEoId();
                orderPlanType = dbOrder.getPlanType();
            }
        }

        // 2. Chống IDOR / Cross-Tenant: kiểm tra quyền sở hữu đơn hàng
        if (orderEoId != null && !orderEoId.equals(eo.getEoId())) {
            log.warn("Cảnh báo bảo mật: User {} cố kích hoạt đơn hàng PayOS {} thuộc về EO {}", userId, orderCode, orderEoId);
            throw new BadRequestException("Giao dịch không thuộc về tài khoản Ban tổ chức hiện tại");
        }

        // 3. Kiểm tra trạng thái thực tế từ PayOS Gateway
        Map<String, Object> payOsOrderDetails = payOSService.getPaymentOrderDetails(orderCode);
        String payOsStatus = payOsOrderDetails != null && payOsOrderDetails.containsKey("status")
                ? payOsOrderDetails.get("status").toString()
                : payOSService.getPaymentStatus(orderCode);
        log.info("Xác thực PayOS orderCode {} với PayOS Gateway: status={}", orderCode, payOsStatus);

        boolean isRealPaid = "PAID".equalsIgnoreCase(payOsStatus);

        // Phục hồi planType từ Gateway nếu bị mất bộ nhớ đệm
        if (orderPlanType == null && payOsOrderDetails != null && isRealPaid) {
            Integer amount = (Integer) payOsOrderDetails.get("amount");
            if (amount != null) {
                orderPlanType = planRepository.findAll().stream()
                        .filter(p -> p.getPrice() != null && p.getPrice().intValue() == amount)
                        .map(Plan::getPlanType)
                        .findFirst()
                        .orElse(PlanType.MATCHING_PLUS);
                log.info("Khôi phục thành công gói dịch vụ {} cho orderCode {} từ PayOS Gateway theo số tiền {}", orderPlanType, orderCode, amount);
            }
        }

        // 4. Khi Gateway báo PAID (hoặc demoEnabled) và order hợp lệ của chính EO này -> Kích hoạt đúng gói cước trong order
        if (orderPlanType != null && (isRealPaid || demoEnabled)) {
            pendingOrders.remove("PAYOS_" + orderCode);
            BigDecimal orderAmount = order != null ? order.price() : null;
            if (payOSOrderRepository != null) {
                Optional<PayOSOrder> poOpt = payOSOrderRepository.findByOrderCode(orderCode);
                if (poOpt.isPresent()) {
                    PayOSOrder po = poOpt.get();
                    po.setStatus("PAID");
                    payOSOrderRepository.save(po);
                    if (orderAmount == null) {
                        orderAmount = po.getAmount();
                    }
                }
            }
            String gatewayRef = isRealPaid ? "PAYOS_VIETQR" : "PAYOS_VIETQR_DEMO";
            return activateSubscription(eo, orderPlanType, gatewayRef, "PAYOS_" + orderCode, "POS_" + orderCode, orderAmount);
        }

        // 5. Nếu có order nhưng chưa thanh toán trên Gateway -> Báo lỗi
        if (orderPlanType != null) {
            throw new BadRequestException("Giao dịch chưa hoàn tất thanh toán trên PayOS (Trạng thái: " + payOsStatus + "). Vui lòng hoàn tất chuyển khoản.");
        }

        // 6. Trường hợp reload lại trang hoặc Webhook đã xử lý kích hoạt trước đó cho chính EO này
        Instant now = Instant.now();
        List<Subscription> activeSubs = subscriptionRepository.findActiveSubscriptions(eo.getEoId(), SubscriptionStatus.ACTIVE, now);
        if (!activeSubs.isEmpty()) {
            return mapToDto(activeSubs.get(0));
        }

        // 7. Order lạ hoàn toàn không có trong hệ thống và không có gói active nào -> Từ chối
        throw new BadRequestException("Giao dịch không tồn tại hoặc đã được xử lý trước đó: " + orderCode);
    }

    /**
     * Xác nhận thanh toán thành công qua PayOS theo orderCode (Dành cho Webhook hoặc hệ thống nội bộ)
     */
    @Transactional
    public SubscriptionResponseDto confirmPayOSPayment(long orderCode) {
        PendingOrder order = pendingOrders.remove("PAYOS_" + orderCode);
        UUID eoId = order != null ? order.eoId() : null;
        PlanType planType = order != null ? order.planType() : null;

        if (order == null && payOSOrderRepository != null) {
            Optional<PayOSOrder> dbOrderOpt = payOSOrderRepository.findByOrderCode(orderCode);
            if (dbOrderOpt.isPresent()) {
                PayOSOrder dbOrder = dbOrderOpt.get();
                eoId = dbOrder.getEoId();
                planType = dbOrder.getPlanType();
                dbOrder.setStatus("PAID");
                payOSOrderRepository.save(dbOrder);
            }
        }

        if (eoId == null || planType == null) {
            log.info("PayOS Webhook/Internal: Giao dịch PAYOS_{} đã được xử lý trước đó hoặc không còn trong hàng đợi.", orderCode);
            return SubscriptionResponseDto.builder()
                    .planName("PayOS Webhook Accepted")
                    .status(SubscriptionStatus.ACTIVE)
                    .build();
        }

        final UUID finalEoId = eoId;
        EventOrganizer eo = eventOrganizerRepository.findById(finalEoId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ Ban tổ chức sự kiện: " + finalEoId));

        BigDecimal orderAmount = (order != null) ? order.price() : null;
        return activateSubscription(eo, planType, "PAYOS_VIETQR", "PAYOS_" + orderCode, "POS_" + orderCode, orderAmount);
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

        boolean isValid = payOSConfig.verifyWebhookSignature(data, signature);
        if (!isValid) {
            log.warn("Chữ ký Webhook PayOS không hợp lệ!");
            throw new BadRequestException("Chữ ký bảo mật PayOS không hợp lệ");
        }

        Object codeObj = data.get("orderCode");
        if (codeObj == null) {
            throw new BadRequestException("Thiếu orderCode trong data webhook");
        }

        long orderCode = Long.parseLong(codeObj.toString());
        log.info("Webhook PayOS xác thực thành công cho orderCode: {}", orderCode);
        return confirmPayOSPayment(orderCode);
    }

    // =========================================================================
    // KÍCH HOẠT GÓI DỊCH VỤ TRÊN DATABASE (LIÊN KẾT BẢNG PLANS & SUBSCRIPTIONS)
    // =========================================================================
    @Transactional
    public SubscriptionResponseDto activateSubscription(
            EventOrganizer eo,
            PlanType planType,
            String method,
            String txnRef,
            String externalRef
    ) {
        return activateSubscription(eo, planType, method, txnRef, externalRef, null);
    }

    @Transactional
    public SubscriptionResponseDto activateSubscription(
            EventOrganizer eo,
            PlanType planType,
            String method,
            String txnRef,
            String externalRef,
            BigDecimal expectedPrice
    ) {
        // Tìm Plan từ bảng plans trong DB:
        // Lưu ý: Dùng findAllByPlanType để an toàn tuyệt đối trước NonUniqueResultException
        // khi trong bảng plans tồn tại nhiều gói cước cùng loại plan_type (do Admin cấu hình/tạo thêm).
        List<Plan> dbPlans = planRepository.findAllByPlanType(planType);
        Plan plan = dbPlans.stream()
                .filter(p -> p.getPrice() != null && expectedPrice != null && p.getPrice().compareTo(expectedPrice) == 0)
                .findFirst()
                .or(() -> dbPlans.stream().findFirst())
                .orElseGet(() -> {
                    log.warn("Không tìm thấy PlanType {} trong DB plans, khởi tạo tự động từ fallback", planType);
                    SubscriptionPlanDto dto = getPlanDefinition(planType);
                    Plan p = Plan.builder()
                            .planName(dto.getName())
                            .planType(dto.getPlanType())
                            .price(dto.getPrice())
                            .durationDays(dto.getDurationDays())
                            .maxEvents(dto.getMaxEvents())
                            .maxShifts(dto.getMaxShifts())
                            .maxWorkers(dto.getMaxWorkers())
                            .baseMatchingQuota(dto.getMaxEvents())
                            .build();
                    Plan saved = planRepository.save(p);
                    return saved != null ? saved : p;
                });

        Instant now = Instant.now();
        Instant baseTime = now;

        // Nếu EO đang có gói trả phí active còn hạn (expiresAt > now), tự động gia hạn nối tiếp thời gian sử dụng
        if (planType != PlanType.FREE_TRIAL) {
            List<Subscription> existingActive = subscriptionRepository.findActiveSubscriptions(eo.getEoId(), SubscriptionStatus.ACTIVE, now);
            Optional<Instant> latestExpiryOpt = existingActive.stream()
                    .filter(s -> s.getPlanType() != PlanType.FREE_TRIAL)
                    .map(Subscription::getExpiresAt)
                    .filter(Objects::nonNull)
                    .filter(exp -> exp.isAfter(now))
                    .max(Comparator.naturalOrder());
            if (latestExpiryOpt.isPresent()) {
                baseTime = latestExpiryOpt.get();
                log.info("EO {} gia hạn nối tiếp gói cước: mốc bắt đầu tính hạn mới từ {}", eo.getEoId(), baseTime);
            }
        }
        Instant expiresAt = baseTime.plus(Duration.ofDays(plan.getDurationDays()));

        // Nếu EO đang có gói FREE_TRIAL cũ, đánh dấu EXPIRED để ưu tiên gói trả phí mới
        if (planType != PlanType.FREE_TRIAL) {
            List<Subscription> currentTrials = subscriptionRepository.findActiveSubscriptions(eo.getEoId(), SubscriptionStatus.ACTIVE, now);
            for (Subscription trial : currentTrials) {
                if (trial.getPlanType() == PlanType.FREE_TRIAL) {
                    trial.setStatus(SubscriptionStatus.EXPIRED);
                    subscriptionRepository.save(trial);
                }
            }
        }

        String featuresJson = String.format(
                "{\"paymentMethod\":\"%s\",\"txnRef\":\"%s\",\"externalRef\":\"%s\",\"activatedAt\":\"%s\"}",
                method, txnRef, externalRef, now.toString()
        );

        Subscription subscription = Subscription.builder()
                .eventOrganizer(eo)
                .plan(plan)
                .planName(plan.getPlanName())
                .planType(plan.getPlanType())
                .durationDays(plan.getDurationDays())
                .price(plan.getPrice())
                .pricePaid(plan.getPrice())
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
        subscription.setCustomFeatures(featuresJson);

        subscription = subscriptionRepository.save(subscription);

        eo.setSubscriptionExpiresAt(expiresAt);
        eventOrganizerRepository.save(eo);

        log.info("Đã kích hoạt Gói dịch vụ thành công cho EO {}: Gói={}, Hạn dùng={}",
                eo.getEoId(), plan.getPlanName(), expiresAt);

        if (notificationService != null && eo.getUser() != null && eo.getUser().getUserId() != null) {
            try {
                notificationService.createAndSend(
                        eo.getUser().getUserId(),
                        "Kích hoạt Gói dịch vụ thành công",
                        "Bạn đã đăng ký thành công " + plan.getPlanName() + ". Hạn mức: " + plan.getMaxEvents() + " sự kiện.",
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
        Plan plan = s.getPlan();
        String planName = plan != null ? plan.getPlanName() : "Chưa đặt tên gói";
        PlanType planType = plan != null ? plan.getPlanType() : PlanType.INVITE_ONLY;
        BigDecimal price = s.getPricePaid() != null ? s.getPricePaid() : (plan != null ? plan.getPrice() : BigDecimal.ZERO);
        int durationDays = plan != null && plan.getDurationDays() != null ? plan.getDurationDays() : 30;
        int maxE = plan != null && plan.getMaxEvents() != null ? plan.getMaxEvents() : 0;
        int usedE = s.getEventsUsed() != null ? s.getEventsUsed() : 0;
        int maxS = plan != null && plan.getMaxShifts() != null ? plan.getMaxShifts() : 0;
        int usedS = s.getShiftsUsed() != null ? s.getShiftsUsed() : 0;
        int maxW = plan != null && plan.getMaxWorkers() != null ? plan.getMaxWorkers() : 0;

        boolean expired = s.getExpiresAt() != null && s.getExpiresAt().isBefore(Instant.now());

        return SubscriptionResponseDto.builder()
                .subscriptionId(s.getSubscriptionId())
                .eoId(s.getEventOrganizer() != null ? s.getEventOrganizer().getEoId() : null)
                .planName(planName)
                .planType(planType)
                .price(price)
                .durationDays(durationDays)
                .maxEvents(maxE)
                .eventsUsed(usedE)
                .remainingEvents(Math.max(0, maxE - usedE))
                .maxShifts(maxS)
                .shiftsUsed(usedS)
                .remainingShifts(Math.max(0, maxS - usedS))
                .maxWorkers(maxW)
                .status(expired ? SubscriptionStatus.EXPIRED : s.getStatus())
                .startsAt(s.getStartsAt())
                .expiresAt(s.getExpiresAt())
                .isExpired(expired)
                .features(s.getFeatures() != null ? s.getFeatures() : (plan != null ? plan.getFeatures() : null))
                .createdAt(s.getCreatedAt())
                .build();
    }
}
