package com.cani.entity.enums;

public enum IssuerVerificationStatus {
    /**
     * Có đường dẫn xác minh (Verification URL / QR) thuộc tên miền chính thức trong whitelist
     * kèm mã chứng chỉ rõ ràng.
     */
    VERIFIED_ONLINE,

    /**
     * Tên tổ chức nằm trong Trusted Issuer Registry của CANi nhưng chưa đối soát trực tuyến.
     */
    KNOWN_ISSUER_UNVERIFIED_ONLINE,

    /**
     * Có tên tổ chức phát hành trên tài liệu nhưng chưa nằm trong danh mục uy tín đã xác minh.
     */
    UNVERIFIED,

    /**
     * Không đọc được hoặc thiếu tên tổ chức cấp chứng chỉ.
     */
    UNKNOWN_ISSUER
}
