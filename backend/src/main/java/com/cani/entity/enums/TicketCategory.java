package com.cani.entity.enums;

/**
 * Phân loại ticket hỗ trợ theo UC-105:
 * ACCOUNT (khóa/xác thực tài khoản) · BILLING (thanh toán/ví) ·
 * TECHNICAL (lỗi hệ thống) · GENERAL (thắc mắc chung) · OTHER (khác).
 */
public enum TicketCategory {
    ACCOUNT,
    BILLING,
    TECHNICAL,
    GENERAL,
    OTHER
}

