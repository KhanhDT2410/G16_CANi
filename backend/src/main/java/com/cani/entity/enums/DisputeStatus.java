package com.cani.entity.enums;

/**
 * Trạng thái vòng đời tranh chấp theo SRS (UC-96 → UC-102):
 * OPEN → INVESTIGATING (CC nhận và điều tra) → RESOLVED / UNVERIFIED (quyết định của CC)
 * → ESCALATED (bên liên quan khiếu nại trong 7 ngày, BR-54) → FINAL_RESOLVED (Admin, BR-68).
 */
public enum DisputeStatus {
    OPEN,
    INVESTIGATING,
    RESOLVED,
    UNVERIFIED,
    ESCALATED,
    FINAL_RESOLVED
}
