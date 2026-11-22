package com.cani.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình kết nối cổng VNPT eKYC thực tế (UC-18 & UC-89).
 *
 * QUAN TRỌNG VỀ BẢO MẬT:
 * - Tuyệt đối KHÔNG hardcode Token ID, Token Key, Access Token hoặc MAC Address trong mã nguồn.
 * - Toàn bộ bí mật phải được truyền qua biến môi trường:
 *   VNPT_EKYC_ENABLED, VNPT_EKYC_TOKEN_ID, VNPT_EKYC_TOKEN_KEY,
 *   VNPT_EKYC_ACCESS_TOKEN, VNPT_EKYC_MAC_ADDRESS.
 *
 * QUAN TRỌNG VỀ HỢP ĐỒNG API VNPT:
 * - Không tự suy đoán thang điểm Face Match (0-1 hay 0-100) hay đặt ngưỡng cứng.
 * - Các tham số faceMatchMinThreshold, faceMatchScoreMax, ocrBackPath, cardLivenessPath,
 *   faceLivenessPath phải được xác nhận theo tài liệu gói API VNPT mà dự án đăng ký.
 */
@Configuration
@ConfigurationProperties(prefix = "cani.ekyc.vnpt")
@Data
public class VnptEkycProperties {

    /**
     * Bật/tắt gọi VNPT eKYC API thực tế (env: VNPT_EKYC_ENABLED). Mặc định false để tránh chạy nhầm khi thiếu cấu hình.
     */
    private boolean enabled = false;

    /**
     * Domain gốc của VNPT eKYC API (env: VNPT_EKYC_BASE_URL).
     */
    private String baseUrl = "https://api.idg.vnpt.vn";

    /**
     * Token ID do VNPT cấp (env: VNPT_EKYC_TOKEN_ID). Bắt buộc cấu hình ngoài mã nguồn.
     */
    private String tokenId = "";

    /**
     * Token Key do VNPT cấp (env: VNPT_EKYC_TOKEN_KEY). Bắt buộc cấu hình ngoài mã nguồn.
     */
    private String tokenKey = "";

    /**
     * JWT Access Token do VNPT cấp (env: VNPT_EKYC_ACCESS_TOKEN). Bắt buộc cấu hình ngoài mã nguồn.
     */
    private String accessToken = "";

    /**
     * MAC Address / mã thiết bị định danh gửi lên VNPT (env: VNPT_EKYC_MAC_ADDRESS).
     */
    private String macAddress = "";

    /**
     * Thời gian timeout kết nối & đọc phản hồi (milliseconds).
     */
    private int timeoutMs = 25000;

    /**
     * Đường dẫn API upload file ảnh lên VNPT Storage.
     */
    private String uploadFilePath = "/file-service/v1/addFile";

    /**
     * Đường dẫn API OCR mặt trước CCCD.
     */
    private String ocrFrontPath = "/ai/v1/ocr/id/front";

    /**
     * Có thực hiện xác minh mặt sau CCCD hay không (mặc định true — không bỏ qua ảnh mặt sau).
     */
    private boolean verifyBackCard = true;

    /**
     * Đường dẫn API OCR mặt sau CCCD (CẦN XÁC NHẬN theo tài liệu gói VNPT dự án đăng ký).
     */
    private String ocrBackPath = "/ai/v1/ocr/id/back";

    /**
     * Đường dẫn API so khớp khuôn mặt chân dung với ảnh thẻ CCCD.
     */
    private String faceComparePath = "/ai/v1/face/compare";

    /**
     * Bật kiểm tra Card Liveness/PAD nếu gói API VNPT đăng ký có hỗ trợ.
     * CẦN XÁC NHẬN endpoint & schema request/response từ tài liệu chính thức VNPT trước khi bật.
     */
    private boolean cardLivenessEnabled = false;
    private String cardLivenessPath = "";

    /**
     * Bật kiểm tra Face Liveness/PAD nếu gói API VNPT đăng ký có hỗ trợ.
     * CẦN XÁC NHẬN endpoint & schema request/response từ tài liệu chính thức VNPT trước khi bật.
     */
    private boolean faceLivenessEnabled = false;
    private String faceLivenessPath = "";

    /**
     * Nếu true, chỉ cho phép AUTO_APPROVED khi gói VNPT có trả kết quả Liveness PASSED.
     */
    private boolean requireLivenessForAutoApprove = false;

    /**
     * Ngưỡng điểm tối thiểu cho Face Match theo đúng thang điểm trong tài liệu hợp đồng VNPT.
     * Để null khi chưa xác nhận tài liệu chính thức (khi đó dựa vào kết quả phân loại MATCH/NOMATCH của VNPT,
     * không tự bịa ngưỡng cứng).
     */
    private Double faceMatchMinThreshold;

    /**
     * Giá trị tối đa hợp lệ của thang điểm Face Match (ví dụ 1.0 hoặc 100.0) theo tài liệu VNPT.
     * Để null nếu chưa xác nhận chính thức (không tự suy đoán 0-1 hay 0-100).
     */
    private Double faceMatchScoreMax;

    public boolean hasRequiredCredentials() {
        return isNotBlank(baseUrl)
                && isNotBlank(tokenId)
                && isNotBlank(tokenKey)
                && isNotBlank(accessToken)
                && isNotBlank(macAddress);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
