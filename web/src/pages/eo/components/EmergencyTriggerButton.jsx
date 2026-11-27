import React from 'react';
import { getShiftStaffingSummary } from '../utils/eventHelpers';

const DISMISSED_STORAGE_KEY_PREFIX = 'cani_eo_dismissed_emergency_';

export function openEmergencyPromptForShift(shiftId, eventId, mode = 'trigger') {
  if (!shiftId || typeof window === 'undefined') return;
  try {
    sessionStorage.removeItem(DISMISSED_STORAGE_KEY_PREFIX + shiftId);
  } catch {
    // ignore storage errors
  }
  window.dispatchEvent(
    new CustomEvent('cani_open_emergency_prompt', {
      detail: { shiftId, eventId: eventId || null, mode },
    })
  );
}

/**
 * Nút "Kích hoạt khẩn cấp" cho ca làm việc:
 * - Bật (enabled) khi ca thiếu nhân sự ở chế độ MATCHING, trong vòng 24h trước startTime
 *   hoặc đang trong ca (IN_PROGRESS) còn tối thiểu 30 phút làm việc.
 * - Khóa (disabled) khi chưa tới khung kích hoạt (> 24h trước giờ bắt đầu).
 */
export default function EmergencyTriggerButton({ shift, event, compact = false }) {
  const summary = getShiftStaffingSummary(shift, event);

  if (
    !shift?.shiftId ||
    summary.isInviteMode ||
    summary.isDraft ||
    summary.isCancelled ||
    summary.isCompleted ||
    summary.isSuspended ||
    summary.missing <= 0 ||
    summary.isEmergency
  ) {
    return null;
  }

  const canActivateNow = Boolean(
    summary.canTriggerEmergency && (summary.withinManualWindow || summary.isInProgress)
  );
  // Khi ca đã vào giờ (IN_PROGRESS) nhưng không còn đủ thời gian (>= 30p) -> ẩn nút gọn gàng thay vì hiện nút khóa "Gần giờ"
  if (summary.isInProgress && !canActivateNow) {
    return null;
  }

  const disabledReason =
    summary.emergencyBlockReason ||
    'Chỉ kích hoạt được trong vòng 24 giờ trước giờ bắt đầu ca.';

  return (
    <button
      type="button"
      disabled={!canActivateNow}
      title={
        canActivateNow
          ? 'Mở trang xác nhận kích hoạt điều phối khẩn cấp tìm nhân sự thay thế'
          : disabledReason
      }
      onClick={(e) => {
        e.stopPropagation();
        if (!canActivateNow) return;
        openEmergencyPromptForShift(shift.shiftId, event?.eventId || shift.eventId);
      }}
      style={{
        minHeight: compact ? '34px' : '38px',
        background: canActivateNow
          ? 'linear-gradient(135deg, #e11d48 0%, #dc2626 100%)'
          : '#f1f5f9',
        color: canActivateNow ? '#ffffff' : '#94a3b8',
        border: canActivateNow ? 'none' : '1px solid #cbd5e1',
        borderRadius: '8px',
        padding: compact ? '0.42rem 0.85rem' : '0.45rem 0.95rem',
        fontSize: compact ? '0.79rem' : '0.82rem',
        fontWeight: 700,
        cursor: canActivateNow ? 'pointer' : 'not-allowed',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '0.35rem',
        whiteSpace: 'nowrap',
        boxShadow: canActivateNow ? '0 2px 6px rgba(220, 38, 38, 0.25)' : 'none',
        transition: 'all 0.15s ease',
      }}
    >
      <span>{canActivateNow ? 'Kích hoạt khẩn cấp' : 'Kích hoạt khẩn cấp (Gần giờ)'}</span>
    </button>
  );
}
