package com.cani.service;

import com.cani.config.PayOSConfig;
import com.cani.dto.subscription.PayOSPaymentResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOSService {

    private final PayOSConfig payOSConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Khởi tạo giao dịch tạo link thanh toán qua PayOS v2 API
     */
    public PayOSPaymentResponseDto createPaymentRequest(
            long orderCode,
            int amount,
            String description,
            String buyerName,
            String buyerEmail
    ) {
        String cleanDesc = description != null ? description.trim() : "CANI DICH VU";
        if (cleanDesc.length() > 25) {
            cleanDesc = cleanDesc.substring(0, 25);
        }

        String cancelUrl = payOSConfig.getCancelUrl();
        String returnUrl = payOSConfig.getReturnUrl();

        String signature = payOSConfig.createRequestSignature(amount, cancelUrl, cleanDesc, orderCode, returnUrl);

        Map<String, Object> body = new HashMap<>();
        body.put("orderCode", orderCode);
        body.put("amount", amount);
        body.put("description", cleanDesc);
        body.put("buyerName", buyerName != null ? buyerName : "Khách hàng CANi");
        body.put("buyerEmail", buyerEmail != null ? buyerEmail : "user@cani.vn");
        body.put("cancelUrl", cancelUrl);
        body.put("returnUrl", returnUrl);
        body.put("signature", signature);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("name", cleanDesc);
        item.put("quantity", 1);
        item.put("price", amount);
        items.add(item);
        body.put("items", items);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", payOSConfig.getClientId());
        headers.set("x-api-key", payOSConfig.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = payOSConfig.getEndpoint() + "/v2/payment-requests";

        try {
            log.info("Gửi yêu cầu tạo liên kết thanh toán PayOS orderCode: {}, amount: {}, desc: {}", orderCode, amount, cleanDesc);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.info("PayOS response: Status={}, Body={}", response.getStatusCode(), response.getBody());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String code = root.path("code").asText("");
                if ("00".equals(code)) {
                    JsonNode data = root.path("data");
                    String rawQr = data.path("qrCode").asText("");
                    String checkoutUrl = data.path("checkoutUrl").asText("");
                    String accNum = data.path("accountNumber").asText(payOSConfig.getDefaultAccountNumber());
                    String accName = data.path("accountName").asText(payOSConfig.getDefaultAccountName());
                    String bin = data.path("bin").asText(payOSConfig.getDefaultBin());

                    String qrCode = rawQr;
                    if (qrCode.isBlank() && !accNum.isBlank()) {
                        qrCode = "https://img.vietqr.io/image/" + bin + "-" + accNum + "-compact2.png?amount="
                                + amount + "&addInfo=" + URLEncoder.encode(cleanDesc, StandardCharsets.UTF_8)
                                + "&accountName=" + URLEncoder.encode(accName, StandardCharsets.UTF_8);
                    }

                    return PayOSPaymentResponseDto.builder()
                            .orderCode(data.path("orderCode").asLong())
                            .amount(data.path("amount").asInt())
                            .description(data.path("description").asText())
                            .accountNumber(accNum)
                            .accountName(accName)
                            .bin(bin)
                            .checkoutUrl(checkoutUrl.isBlank() ? qrCode : checkoutUrl)
                            .qrCode(qrCode)
                            .status(data.path("status").asText("PENDING"))
                            .build();
                } else {
                    log.warn("PayOS trả về mã không thành công: {} - {}", code, root.path("desc").asText());
                }
            }
        } catch (Exception e) {
            log.error("Lỗi gọi PayOS API: {}", e.getMessage(), e);
        }

        // Fallback: Tạo VietQR trực tiếp theo cấu hình hệ thống
        String fallbackAccNum = payOSConfig.getDefaultAccountNumber();
        String fallbackAccName = payOSConfig.getDefaultAccountName();
        String fallbackBin = payOSConfig.getDefaultBin();
        String vietQrImage = "";
        if (!fallbackAccNum.isBlank()) {
            vietQrImage = "https://img.vietqr.io/image/" + fallbackBin + "-" + fallbackAccNum + "-compact2.png?amount="
                    + amount + "&addInfo=" + URLEncoder.encode(cleanDesc, StandardCharsets.UTF_8)
                    + "&accountName=" + URLEncoder.encode(fallbackAccName, StandardCharsets.UTF_8);
        }

        return PayOSPaymentResponseDto.builder()
                .orderCode(orderCode)
                .amount(amount)
                .description(cleanDesc)
                .accountNumber(fallbackAccNum)
                .accountName(fallbackAccName)
                .bin(fallbackBin)
                .checkoutUrl(vietQrImage)
                .qrCode(vietQrImage)
                .status("PENDING")
                .build();
    }

    /**
     * Tra cứu trạng thái giao dịch thanh toán trực tiếp từ PayOS v2 API
     */
    public String getPaymentStatus(long orderCode) {
        Map<String, Object> details = getPaymentOrderDetails(orderCode);
        if (details != null && details.containsKey("status")) {
            return details.get("status").toString();
        }
        return "UNKNOWN";
    }

    /**
     * Tra cứu chi tiết đơn hàng (status, amount, description) từ PayOS v2 API
     */
    public Map<String, Object> getPaymentOrderDetails(long orderCode) {
        String url = payOSConfig.getEndpoint() + "/v2/payment-requests/" + orderCode;
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-client-id", payOSConfig.getClientId());
        headers.set("x-api-key", payOSConfig.getApiKey());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if ("00".equals(root.path("code").asText())) {
                    JsonNode data = root.path("data");
                    Map<String, Object> map = new HashMap<>();
                    map.put("status", data.path("status").asText("PENDING"));
                    map.put("amount", data.path("amount").asInt());
                    map.put("description", data.path("description").asText(""));
                    return map;
                }
            }
        } catch (Exception e) {
            log.warn("Lỗi kiểm tra trạng thái đơn hàng PayOS {}: {}", orderCode, e.getMessage());
        }
        return null;
    }
}

