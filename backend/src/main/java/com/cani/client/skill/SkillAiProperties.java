package com.cani.client.skill;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình Gemini Vision AI dùng riêng cho module thẩm định chứng chỉ kỹ năng (UC-46).
 * Tách biệt hoàn toàn với cấu hình eKYC (VNPT eKYC).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cani.skill.ai")
public class SkillAiProperties {

    private boolean enabled = true;

    private String apiKey = "";

    private String model = "gemini-2.5-flash";

    private String url = "https://generativelanguage.googleapis.com/v1beta/models";

    private int timeoutMs = 25000;
}
