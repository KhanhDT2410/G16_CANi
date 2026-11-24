package com.cani.client.skill;

import com.cani.service.skill.eval.DocumentExtractionResult;

/**
 * Cổng tích hợp provider AI đọc tài liệu CV/chứng chỉ thực tế (OCR / Vision đa phương thức).
 * Tuyệt đối không chứa logic cấp SP hay xác minh nguồn cấp chứng chỉ.
 */
public interface SkillAiDocumentProvider {

    /**
     * @return true chỉ khi provider đã được bật, cấu hình hợp lệ (không phải Gemini),
     * có đủ credentials và kết nối thực tế đã được xác nhận.
     */
    boolean isReady();

    /**
     * @return true nếu cấu hình đầu vào (provider, endpoint, apiKey, model) đầy đủ và không phải Gemini,
     * nhưng chưa đồng nghĩa với việc đã xác nhận kết nối thành công.
     */
    boolean hasValidConfiguration();

    /**
     * Chủ động kiểm tra kết nối tới provider thực tế (ví dụ HTTP OPTIONS/models/health probe).
     */
    boolean verifyConnection();

    String getProviderName();

    String getModelName();

    String getUnconfiguredReason();

    /**
     * Gọi provider AI thực tế để trích xuất dữ liệu có cấu trúc từ ảnh hoặc PDF.
     */
    DocumentExtractionResult extractDocument(
            byte[] fileBytes,
            String filename,
            String mimeType,
            String prompt,
            String promptVersion
    );
}
