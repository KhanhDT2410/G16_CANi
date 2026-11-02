package com.cani.entity.enums;

/**
 * Trạng thái của ticket hỗ trợ (SRS UC-105 / UC-106 · SCR-25 / SCR-26):
 * OPEN → IN_PROGRESS (khi CC nhận xử lý) → CLOSED (khi đã giải quyết hoặc đóng ticket).
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    CLOSED
}

