package com.cani.client.ekyc;

import com.cani.config.VnptEkycProperties;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Điều phối viên eKYC thực tế (100% Real Verification - Zero Mock).
 * Tuân thủ nguyên tắc định danh thật:
 * 1. Gọi trực tiếp cổng VNPT eKYC API (trích xuất OCR, Face Matching, Anti-fraud).
 * 2. Nếu VNPT gặp sự cố mạng/gián đoạn, sử dụng Gemini Vision AI để đọc OCR trực tiếp trên ảnh CCCD thực tế của người dùng.
 * 3. Tuyệt đối không giả lập dữ liệu: Nếu ảnh không hợp lệ hoặc cả 2 hệ thống không thể nhận diện -> REJECTED.
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class PrimaryEkycProvider implements EkycProvider {

    private final VnptEkycClient vnptClient;
    private final GeminiEkycClient geminiClient;
    private final VnptEkycProperties vnptProperties;

    @Override
    public String getProviderName() {
        return "VNPT_EKYC";
    }

    @Override
    public EkycIdentityResultDto verifyIdentity(
            byte[] frontBytes, String frontMime,
            byte[] backBytes, String backMime,
            byte[] selfieBytes, String selfieMime) {

        // 1. Ưu tiên gọi VNPT eKYC thực tế nếu đã bật và có Access Token
        if (vnptProperties.isEnabled()
                && vnptProperties.getAccessToken() != null
                && !vnptProperties.getAccessToken().isBlank()) {
            log.info("eKYC Router: Đang thực thi xác thực thực tế qua cổng VNPT eKYC API...");
            try {
                EkycIdentityResultDto vnptResult = vnptClient.verifyIdentity(
                        frontBytes, frontMime, backBytes, backMime, selfieBytes, selfieMime);
                boolean isFallback = vnptResult != null
                        && vnptResult.getRawAnalysis() != null
                        && "VNPT_EKYC_FALLBACK".equals(vnptResult.getRawAnalysis().get("provider"));
                if (vnptResult != null && !isFallback && vnptResult.getIdNumber() != null && !vnptResult.getIdNumber().isBlank()) {
                    log.info("VNPT eKYC trích xuất thành công: CCCD={}, Họ tên={}", vnptResult.getIdNumber(), vnptResult.getFullName());
                    return vnptResult;
                }
                log.warn("VNPT eKYC không thể trích xuất hoặc trả về Fallback ({}). Chuyển sang Gemini Vision AI để đọc OCR trực tiếp trên ảnh thẻ CCCD...",
                        vnptResult != null ? vnptResult.getRoutingReason() : "null");
            } catch (Exception e) {
                log.error("Lỗi kết nối VNPT eKYC API: {}. Chuyển sang Gemini Vision AI...", e.getMessage(), e);
            }
        }

        // 2. Sử dụng Gemini Vision AI OCR & Face Matching để đọc trực tiếp trên ảnh thẻ CCCD thật của người dùng
        if (geminiClient != null && geminiClient.isAvailable()) {
            log.info("eKYC Router: Đang điều phối yêu cầu xác thực sang [GEMINI_VISION_EKYC]...");
            try {
                EkycIdentityResultDto geminiResult = geminiClient.verifyIdentity(
                        frontBytes, frontMime, backBytes, backMime, selfieBytes, selfieMime);
                if (geminiResult != null && geminiResult.getIdNumber() != null && !geminiResult.getIdNumber().isBlank()) {
                    log.info("Gemini Vision eKYC trích xuất thành công: CCCD={}, Họ tên={}", geminiResult.getIdNumber(), geminiResult.getFullName());
                    return geminiResult;
                }
            } catch (Exception e) {
                log.error("Lỗi xử lý Gemini Vision AI OCR: {}", e.getMessage(), e);
            }
        }

        // 3. KHÔNG DÙNG MOCK GIẢ LẬP: Từ chối nếu không thể nhận diện được thẻ CCCD thật
        log.warn("eKYC Router: Không thể trích xuất thông tin hợp lệ từ ảnh CCCD. Trả về kết quả REJECTED.");
        return EkycIdentityResultDto.builder()
                .ocrConfidence(0.0)
                .faceMatchScore(0.0)
                .isCutCorner(false)
                .isScreenCaptured(false)
                .isPhotoshoped(false)
                .isExpired(false)
                .routingDecision("REJECTED")
                .routingReason("Xác thực eKYC không thành công: Hệ thống không thể nhận diện được thông tin Căn cước công dân hoặc khuôn mặt từ ảnh tải lên. Vui lòng chụp rõ nét mặt trước, mặt sau thẻ CCCD thật và ảnh selfie chính diện.")
                .rawAnalysis(Map.of("provider", "REAL_VERIFICATION_FAILED", "status", "REJECTED"))
                .build();
    }
}
