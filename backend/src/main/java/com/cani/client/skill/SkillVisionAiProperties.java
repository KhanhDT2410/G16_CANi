package com.cani.client.skill;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình kết nối Vision AI phục vụ thẩm định chứng chỉ, bằng cấp nghề nghiệp (Skill Verification V2).
 */
@Configuration
@ConfigurationProperties(prefix = "cani.ai.gemini")
@Getter
@Setter
public class SkillVisionAiProperties {

    /**
     * API Key gọi dịch vụ Vision AI. Lấy từ biến môi trường GEMINI_API_KEY.
     */
    private String apiKey;

    /**
     * Tên mô hình Vision AI (cấu hình qua GEMINI_MODEL, mặc định gemini-2.5-flash).
     */
    private String model = "gemini-2.5-flash";

    /**
     * Endpoint API
     */
    private String url = "https://generativelanguage.googleapis.com/v1beta/models";

    /**
     * Thời gian timeout tính bằng mili-giây
     */
    private int timeoutMs = 20000;

    /**
     * Chỉ bật trong môi trường sandbox/demo được cấu hình tường minh.
     * Mặc định FALSE (Fail-Closed trên production: tuyệt đối không tự bịa dữ liệu hay AUTO_APPROVE).
     */
    private boolean sandboxFallbackEnabled = false;
}
