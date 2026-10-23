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
     * Token ID do VNPT cấp
     */
    private String tokenId = "5e7a3b1b-db74-1656-e063-63199f0a717f";

    /**
     * Token Key (Public Key) do VNPT cấp
     */
    private String tokenKey = "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAlvv9EToVRDb3ZS58HaSGBNDHN9GLm9lROWE4CxyRMHib2K1XCmSS6suua/4coDMvthnv8zKmUy25BiplQtd6MMCAwEAAQ==";

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
