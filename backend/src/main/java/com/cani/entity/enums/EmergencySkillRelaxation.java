package com.cani.entity.enums;

/**
 * Mức độ nới lỏng điều kiện kỹ năng có kiểm soát khi kích hoạt Emergency Dispatch.
 * Lưu ý: Không bao giờ nới lỏng chứng chỉ an toàn/nghề bắt buộc (requiresValidCertificate = true)
 * hoặc kỹ năng bắt buộc ở các vị trí chuyên môn/kỹ thuật cao (TIER_5_TECHNICAL, TIER_6_SPECIALIST).
 */
public enum EmergencySkillRelaxation {
    /** Giữ nguyên toàn bộ yêu cầu kỹ năng và mức xác minh của ca. */
    NONE,
    /** Hạ mức xác minh tối thiểu (minVerificationTier) xuống 1 bậc (trừ kỹ năng cần chứng chỉ). */
    LOWER_TIER,
    /** Chuyển kỹ năng REQUIRED thành PREFERRED cho ca phổ thông (TIER_1..TIER_3), trừ chứng chỉ bắt buộc. */
    PREFER_ONLY
}
