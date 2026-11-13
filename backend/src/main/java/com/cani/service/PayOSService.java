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
                    String vietQrImg = "https://img.vietqr.io/image/MB-2622014789096-compact2.png?amount="
                            + amount + "&addInfo=" + URLEncoder.encode(cleanDesc, StandardCharsets.UTF_8)
                            + "&accountName=" + URLEncoder.encode("PHAM DUY HUNG", StandardCharsets.UTF_8);

                    return PayOSPaymentResponseDto.builder()
                            .orderCode(data.path("orderCode").asLong())
                            .amount(data.path("amount").asInt())
                            .description(data.path("description").asText())
                            .accountNumber(data.path("accountNumber").asText("2622014789096"))
                            .accountName(data.path("accountName").asText("PHAM DUY HUNG"))
                            .bin(data.path("bin").asText("970422"))
                            .checkoutUrl(checkoutUrl.isBlank() ? vietQrImg : checkoutUrl)
                            .qrCode(rawQr.isBlank() ? vietQrImg : rawQr)
                            .status(data.path("status").asText("PENDING"))
                            .build();
                } else {
                    log.warn("PayOS trả về mã không thành công: {} - {}", code, root.path("desc").asText());
                }
            }
        } catch (Exception e) {
            log.error("Lỗi gọi PayOS API: {}", e.getMessage(), e);
        }

        // Fallback: Tạo VietQR trực tiếp theo chuẩn NAPAS với tài khoản MB của bạn
        log.info("Sử dụng fallback VietQR NAPAS 247 cho MBBank 2622014789096");
        String vietQrImage = "https://img.vietqr.io/image/MB-2622014789096-compact2.png?amount="
                + amount + "&addInfo=" + URLEncoder.encode(cleanDesc, StandardCharsets.UTF_8)
                + "&accountName=" + URLEncoder.encode("PHAM DUY HUNG", StandardCharsets.UTF_8);

        return PayOSPaymentResponseDto.builder()
                .orderCode(orderCode)
                .amount(amount)
                .description(cleanDesc)
                .accountNumber("2622014789096")
                .accountName("PHAM DUY HUNG")
                .bin("970422")
                .checkoutUrl(vietQrImage)
                .qrCode(vietQrImage)
                .status("PENDING")
                .build();
    }

    /**
     * Tra cứu trạng thái giao dịch thanh toán trực tiếp từ PayOS v2 API
     */
    public String getPaymentStatus(long orderCode) {
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
                    return root.path("data").path("status").asText("PENDING");
                }
            }
        } catch (Exception e) {
            log.warn("Lỗi kiểm tra trạng thái đơn hàng PayOS {}: {}", orderCode, e.getMessage());
        }
        return "UNKNOWN";
    }
}

