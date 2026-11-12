package com.cani.client.ekyc;

import com.cani.config.VnptEkycProperties;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Điều phối viên eKYC trung tâm (Orchestrator / Strategy Pattern).
 * Tự động chọn Provider dựa trên cấu hình:
 * - cani.ekyc.provider = vnpt (Mặc định)
 * - cani.ekyc.provider = mock
 *
 * Nếu cấu hình là 'vnpt' nhưng chưa bật (enabled=false) hoặc chưa điền Access Token,
 * hệ thống sẽ tự động chuyển sang Mock Provider để bảo vệ hệ thống không bị lỗi 401/timeout khi dev.
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class PrimaryEkycProvider implements EkycProvider {

    private final VnptEkycClient vnptClient;
    private final GeminiEkycClient geminiClient;
    private final MockEkycProvider mockClient;
    private final VnptEkycProperties vnptProperties;

    @Value("${cani.ekyc.provider:vnpt}")
    private String configuredProvider;

    @Override
    public String getProviderName() {
        return getDelegate().getProviderName();
    }

    @Override
    public EkycIdentityResultDto verifyIdentity(
            byte[] frontBytes, String frontMime,
            byte[] backBytes, String backMime,
            byte[] selfieBytes, String selfieMime) {

        if ("mock".equalsIgnoreCase(configuredProvider)) {
            log.info("eKYC Router: Chế độ cấu hình là [MOCK_EKYC]");
            return mockClient.verifyIdentity(frontBytes, frontMime, backBytes, backMime, selfieBytes, selfieMime);
        }

        // 1. Ưu tiên gọi VNPT eKYC nếu đã bật và có Access Token
        if ("vnpt".equalsIgnoreCase(configuredProvider)
                && vnptProperties.isEnabled()
                && vnptProperties.getAccessToken() != null
                && !vnptProperties.getAccessToken().isBlank()) {
            log.info("eKYC Router: Đang gọi VNPT eKYC API...");
            EkycIdentityResultDto vnptResult = vnptClient.verifyIdentity(
                    frontBytes, frontMime, backBytes, backMime, selfieBytes, selfieMime);
            boolean isFallback = vnptResult != null
                    && vnptResult.getRawAnalysis() != null
                    && "VNPT_EKYC_FALLBACK".equals(vnptResult.getRawAnalysis().get("provider"));
            if (vnptResult != null && !isFallback && vnptResult.getIdNumber() != null && !vnptResult.getIdNumber().isBlank()) {
                return vnptResult;
            }
            log.warn("VNPT eKYC trả về Fallback (Token hết hạn hoặc gián đoạn). Chuyển sang Gemini Vision AI OCR để bóc tách thông tin thực tế trên thẻ CCCD...");
        }

        // 2. Sử dụng Gemini Vision AI OCR & Face Matching để đọc chính xác thông tin thực tế trên thẻ CCCD
        if (geminiClient != null && geminiClient.isAvailable()) {
            log.info("eKYC Router: Đang điều phối yêu cầu xác thực sang [GEMINI_VISION_EKYC]");
            EkycIdentityResultDto geminiResult = geminiClient.verifyIdentity(
                    frontBytes, frontMime, backBytes, backMime, selfieBytes, selfieMime);
            if (geminiResult != null) {
                return geminiResult;
            }
        }

        // 3. Dự phòng cuối cùng
        log.info("eKYC Router: Chuyển về Mock Provider dự phòng.");
        return mockClient.verifyIdentity(frontBytes, frontMime, backBytes, backMime, selfieBytes, selfieMime);
    }

    /**
     * Lựa chọn Delegate Provider phù hợp với cấu hình môi trường
     */
    public EkycProvider getDelegate() {
        if ("mock".equalsIgnoreCase(configuredProvider)) {
            return mockClient;
        }

        if ("vnpt".equalsIgnoreCase(configuredProvider)) {
            if (vnptProperties.isEnabled() && vnptProperties.getAccessToken() != null && !vnptProperties.getAccessToken().isBlank()) {
                return vnptClient;
            }
            if (geminiClient != null && geminiClient.isAvailable()) {
                return geminiClient;
            }
            return mockClient;
        }

        if (geminiClient != null && geminiClient.isAvailable()) {
            return geminiClient;
        }

        return mockClient;
    }
}

