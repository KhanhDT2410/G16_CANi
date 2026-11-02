package com.cani.entity.enums;

/**
 * Mức độ ưu tiên của ticket hỗ trợ (BR-58 SLA):
 * HIGH: phản hồi trong 4h (BILLING, ACCOUNT)
 * MEDIUM: phản hồi trong 1 ngày (TECHNICAL, GENERAL)
 * LOW: phản hồi trong 3 ngày (OTHER)
 */
public enum TicketPriority {
    HIGH,
    MEDIUM,
    LOW
}

