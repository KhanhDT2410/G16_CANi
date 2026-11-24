package com.cani.client.skill;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình provider AI đọc tài liệu (OCR / Vision) cho module kỹ năng (UC-43 / UC-46).
 * Tách biệt hoàn toàn với VNPT eKYC và KHÔNG sử dụng Gemini.
 * Chuỗi apiKey không rỗng chưa đủ để xác nhận kết nối; cần provider, endpoint,
 * model hợp lệ và xác thực kết nối thực tế trước khi coi là sẵn sàng.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cani.skill.ai")
public class SkillAiProperties {

    private boolean enabled = false;

    /**
     * Tên provider được phép (ví dụ: openai-compatible, custom-ocr).
     * Giá trị "gemini" bị từ chối rõ ràng theo yêu cầu hệ thống.
     */
    private String provider = "";

    private String endpoint = "";

    private String apiKey = "";

    private String model = "";

    private int timeoutMs = 25000;

    /**
     * Chỉ đánh dấu true khi đã kiểm tra kết nối / gọi thành công tới provider thực tế.
     */
    private boolean connectionVerified = false;
}
