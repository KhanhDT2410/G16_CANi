package com.cani.client.skill;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình Gemini Vision AI dùng riêng cho luồng AI đọc & thẩm định chứng chỉ kỹ năng (UC-46).
 * Tách biệt hoàn toàn với cấu hình VNPT eKYC và OpenAI-compatible benchmark extractor.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cani.skill.gemini")
public class SkillGeminiProperties {

    private boolean enabled = true;

    private String apiKey = "";

    private String model = "gemini-flash-lite-latest";

    private String url = "https://generativelanguage.googleapis.com/v1beta/models";

    private int timeoutMs = 25000;
}
