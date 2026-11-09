package com.cani.entity.enums;

/**
 * Kết quả xử lý một tranh chấp (UC-100 / UC-102).
 * BR-55: quyết định có WORKER_FAULT gắn kèm → tự động −10 điểm RS của Worker.
 */
public enum DisputeOutcome {
    EO_FAULT,
    WORKER_FAULT,
    PARTIAL,
    UNVERIFIED,
    UPHOLD
}
