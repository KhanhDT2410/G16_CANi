package com.cani.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình tham số kết nối VNPT eKYC (UC-18 & UC-89).
 */
@Configuration
@ConfigurationProperties(prefix = "cani.ekyc.vnpt")
@Data
public class VnptEkycProperties {

    /**
     * Bật/tắt gọi trực tiếp VNPT eKYC API thật (Mặc định: false để tránh tiêu tốn 100 requests dùng thử).
     */
    private boolean enabled = false;

    /**
     * Domain gốc của VNPT eKYC API
     */
    private String baseUrl = "https://api.idg.vnpt.vn";

    /**
     * Token ID do VNPT cấp (cấu hình qua biến môi trường VNPT_EKYC_TOKEN_ID)
     */
    private String tokenId = "";

    /**
     * Token Key (Public Key) do VNPT cấp (cấu hình qua biến môi trường VNPT_EKYC_TOKEN_KEY)
     */
    private String tokenKey = "";

    /**
     * JWT Access Token (Lưu ý: Hết hạn sau 8 giờ)
     */
    private String accessToken = "";

    /**
     * MAC Address định danh thiết bị gửi lên VNPT (mặc định: TEST1)
     */
    private String macAddress = "TEST1";

    /**
     * Thời gian timeout kết nối (milliseconds)
     */
    private int timeoutMs = 25000;
}
