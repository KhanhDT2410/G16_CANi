package com.cani.entity.enums;

/**
 * Chuẩn hóa trạng thái hồ sơ xác minh danh tính điện tử (eKYC).
 * Lưu dưới dạng chuỗi tên enum trong cột VARCHAR(50) để tương thích ngược dữ liệu hiện có.
 */
public enum EkycVerificationStatus {
    /**
     * Hồ sơ mới khởi tạo, chờ bắt đầu xác minh.
     */
    PENDING,

    /**
     * Hồ sơ đang được gửi và xử lý tại cổng VNPT eKYC.
     */
    PROCESSING,

    /**
     * Hồ sơ cần Admin / CSKH kiểm tra và phê duyệt thủ công.
     */
    PENDING_REVIEW,

    /**
     * Xác minh danh tính thành công (tự động hoặc qua Admin phê duyệt).
     */
    APPROVED,

    /**
     * Xác minh thất bại theo kết quả nghiệp vụ (giả mạo, hết hạn, không khớp mặt, trùng CCCD).
     */
    REJECTED,

    /**
     * Lỗi kỹ thuật từ nhà cung cấp / mạng / cấu hình — không đánh đồng gian lận, cho phép thử lại.
     */
    TECHNICAL_FAILURE
}
