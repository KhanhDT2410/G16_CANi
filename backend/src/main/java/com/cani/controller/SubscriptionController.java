package com.cani.controller;

import com.cani.dto.response.ApiResponse;
import com.cani.dto.subscription.PayOSPaymentResponseDto;
import com.cani.dto.subscription.PurchaseSubscriptionRequestDto;
import com.cani.dto.subscription.PurchaseSubscriptionResponseDto;
import com.cani.dto.subscription.SubscriptionPlanDto;
import com.cani.dto.subscription.SubscriptionResponseDto;
import com.cani.entity.enums.PlanType;
import com.cani.security.SecurityUtils;
import com.cani.service.SubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller phục vụ Quản lý & Mua gói cước dịch vụ EO (UC-10, UC-11, UC-12, W-06)
 */
@RestController
@RequestMapping("/api/v1/eo/subscriptions")
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * UC-10: Xem danh mục gói cước có sẵn (Invite Mode 50k & Matching Mode 80k)
     */
    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<SubscriptionPlanDto>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.getAvailablePlans(), "Lấy danh mục gói cước thành công"));
    }

    /**
     * UC-10: Lấy gói cước hiện tại & hạn mức Quota của EO
     */
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<SubscriptionResponseDto>> getCurrentSubscription(
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.getCurrentSubscription(userId), "Lấy thông tin gói cước hiện tại thành công"));
    }

    /**
     * UC-12: Xem lịch sử mua gói cước của EO
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<SubscriptionResponseDto>>> getHistory(
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.getSubscriptionHistory(userId), "Lấy lịch sử mua gói cước thành công"));
    }

    /**
     * UC-11: Khởi tạo phiên thanh toán VNPay
     */
    @PostMapping("/purchase")
    public ResponseEntity<ApiResponse<PurchaseSubscriptionResponseDto>> purchaseSubscription(
            @Valid @RequestBody PurchaseSubscriptionRequestDto request,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        PurchaseSubscriptionResponseDto response = subscriptionService.createVNPayPaymentSession(userId, request, servletRequest);
        return ResponseEntity.ok(ApiResponse.ok(response, "Khởi tạo giao dịch VNPay thành công. Chuyển hướng để thanh toán."));
    }

    /**
     * UC-11: Nhận kết quả callback từ VNPay
     */
    @GetMapping("/vnpay-callback")
    public ResponseEntity<ApiResponse<SubscriptionResponseDto>> vnpayCallback(
            @RequestParam Map<String, String> queryParams
    ) {
        SubscriptionResponseDto result = subscriptionService.processVNPayCallback(queryParams);
        return ResponseEntity.ok(ApiResponse.ok(result, "Xác thực thanh toán VNPay thành công! Gói dịch vụ đã được kích hoạt."));
    }

    /**
     * Khởi tạo phiên thanh toán VietQR qua PayOS Gateway
     */
    @PostMapping("/purchase-payos")
    public ResponseEntity<ApiResponse<PayOSPaymentResponseDto>> purchaseSubscriptionPayOS(
            @Valid @RequestBody PurchaseSubscriptionRequestDto request,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        PayOSPaymentResponseDto response = subscriptionService.createPayOSPaymentSession(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Khởi tạo mã VietQR thanh toán PayOS thành công"));
    }

    /**
     * Xác nhận thanh toán PayOS sau khi chuyển khoản
     */
    @PostMapping("/payos-confirm/{orderCode}")
    public ResponseEntity<ApiResponse<SubscriptionResponseDto>> confirmPayOS(
            @PathVariable("orderCode") long orderCode,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        SubscriptionResponseDto response = subscriptionService.confirmPayOSPaymentForUser(userId, orderCode);
        return ResponseEntity.ok(ApiResponse.ok(response, "Xác nhận thanh toán VietQR thành công! Gói cước đã được kích hoạt."));
    }

    /**
     * Webhook nhận kết quả tự động từ PayOS
     */
    @PostMapping("/payos-webhook")
    public ResponseEntity<ApiResponse<SubscriptionResponseDto>> payosWebhook(
            @RequestBody Map<String, Object> webhookPayload
    ) {
        SubscriptionResponseDto response = subscriptionService.processPayOSWebhook(webhookPayload);
        return ResponseEntity.ok(ApiResponse.ok(response, "Xử lý Webhook PayOS thành công"));
    }

    /**
     * UC-11 (Demo Sandbox): Giả lập thanh toán thành công cho Demo hội đồng
     */
    @PostMapping("/demo-simulate")
    public ResponseEntity<ApiResponse<SubscriptionResponseDto>> simulateDemo(
            @RequestParam(value = "planType", defaultValue = "MATCHING_PLUS") PlanType planType,
            Authentication authentication
    ) {
        UUID userId = SecurityUtils.getCurrentUserId(authentication);
        SubscriptionResponseDto result = subscriptionService.simulatePurchaseDemo(userId, planType);
        return ResponseEntity.ok(ApiResponse.ok(result, "Kích hoạt Gói dịch vụ (Mô phỏng Sandbox) thành công!"));
    }
}
