package com.cani.client.skill;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình kết nối Vision AI phục vụ thẩm định chứng chỉ, bằng cấp nghề nghiệp (Skill Verification).
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
     * Tên mô hình Vision AI
     */
    private String model = "gemini-3.5-flash-lite";

    /**
     * Endpoint API
     */
    private String url = "https://generativelanguage.googleapis.com/v1beta/models";

    /**
     * Thời gian timeout tính bằng mili-giây
     */
    private int timeoutMs = 20000;
}
