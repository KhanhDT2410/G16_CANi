import React from 'react';
import { getShiftStaffingSummary } from '../utils/eventHelpers';

/**
 * Hiển thị cảnh báo thiếu nhân sự cho từng ca làm việc tại chỗ kèm thao tác hợp lệ:
 * - Ca ACTIVE (trước giờ bắt đầu) còn thiếu -> nút "Kích hoạt khẩn cấp"
 * - Ca INVITE còn thiếu -> nút "Mời thêm Worker"
 * - Ca đã bật Emergency (do EO hoặc hệ thống tự động khi Worker hủy/No-show) -> nhãn "Đang điều phối khẩn cấp" (ẩn nút tránh kích hoạt trùng)
 * - Ca IN_PROGRESS (đã đến giờ làm) -> ẩn nút kích hoạt thủ công, giải thích cơ chế tự động khi ghi nhận No-show còn đủ thời gian làm việc
 * - Khi vừa bù đủ nhân sự trong phiên -> hiện phản hồi xanh "Đã bù đủ nhân sự" và gỡ cảnh báo thiếu/nút khẩn cấp
 */
export default function ShiftEmergencyBanner({
  shift,
  event,
  onTriggerEmergency,
  onOpenInvite,
  localDispatchResult = null,
}) {
  const summary = getShiftStaffingSummary(shift, event);

  // Khi đã đủ nhân sự -> gỡ cảnh báo thiếu và nút kích hoạt; nếu vừa theo dõi emergency trong phiên thì hiện kết quả đã bù đủ
  if (summary.missing <= 0 || summary.isDraft || summary.isCancelled || summary.isCompleted) {
    if (
      localDispatchResult &&
      summary.missing <= 0 &&
      !summary.isDraft &&
      !summary.isCancelled &&
      !summary.isCompleted
    ) {
      return (
        <div
          style={{
            padding: '0.65rem 0.95rem',
            borderRadius: 10,
            backgroundColor: '#ecfdf5',
            border: '1px solid #a7f3d0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '0.75rem',
            flexWrap: 'wrap',
          }}
        >
          <div style={{ fontSize: '0.84rem', fontWeight: 700, color: '#047857' }}>
            Đã bù đủ nhân sự ({summary.confirmed}/{summary.required}) — Điều phối khẩn cấp đã tự động kết thúc.
          </div>
        </div>
      );
    }
    return null;
  }

  // Chỉ hiện cảnh báo nếu đang trong khung cảnh báo sát giờ hoặc đã bật Emergency
  if (!summary.nearStartUnderstaffed && !summary.isEmergency) {
    return null;
  }

  const resolvedHasCandidates =
    localDispatchResult && typeof localDispatchResult.hasCandidates === 'boolean'
      ? localDispatchResult.hasCandidates
      : summary.hasCandidates;

  const emergencyStatusLabel = resolvedHasCandidates
    ? 'Đang điều phối khẩn cấp'
    : 'Đã bật điều phối · Chưa tìm được Worker phù hợp';

  return (
    <div
      style={{
        padding: '0.75rem 0.95rem',
        borderRadius: 10,
        backgroundColor: summary.isEmergency ? '#fef2f2' : summary.isInProgress ? '#fff7ed' : '#fff1f2',
        border: `1px solid ${summary.isEmergency ? '#fca5a5' : summary.isInProgress ? '#fdba74' : '#fecdd3'}`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '0.75rem',
        flexWrap: 'wrap',
      }}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem', flex: '1 1 260px' }}>
        <div style={{ fontSize: '0.85rem', fontWeight: 700, color: '#9f1239' }}>
          {summary.summaryLine}
        </div>
        {summary.isInProgress && !summary.isEmergency && (
          <div style={{ fontSize: '0.76rem', color: '#9a3412' }}>
            {summary.isInviteMode
              ? 'Ca đã đến giờ làm việc. Bạn có thể mời thêm Worker hoặc kiểm tra điểm danh ca.'
              : 'Ca đã đến giờ làm việc (không thể bật điều phối khẩn cấp thủ công). Hệ thống sẽ tự động điều phối khẩn cấp nếu ghi nhận nhân sự vắng mặt (No-show) khi ca còn đủ thời gian làm việc; vui lòng kiểm tra điểm danh hoặc báo cáo vắng mặt nếu cần.'}
          </div>
        )}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
        {summary.isInviteMode && onOpenInvite && (
          <button
            type="button"
            onClick={() => onOpenInvite(shift, event)}
            style={{
              background: '#7c3aed',
              color: '#ffffff',
              border: 'none',
              borderRadius: 8,
              padding: '0.45rem 0.85rem',
              fontSize: '0.8rem',
              fontWeight: 700,
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            Mời thêm Worker
          </button>
        )}

        {!summary.isInviteMode && summary.isEmergency && (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.35rem',
              padding: '0.4rem 0.8rem',
              borderRadius: 8,
              fontSize: '0.78rem',
              fontWeight: 700,
              backgroundColor: resolvedHasCandidates ? '#fee2e2' : '#fffbeb',
              color: resolvedHasCandidates ? '#b91c1c' : '#b45309',
              border: `1px solid ${resolvedHasCandidates ? '#f87171' : '#fcd34d'}`,
              whiteSpace: 'nowrap',
            }}
          >
            <span
              style={{
                width: 7,
                height: 7,
                borderRadius: '50%',
                backgroundColor: resolvedHasCandidates ? '#dc2626' : '#d97706',
                display: 'inline-block',
              }}
            />
            {emergencyStatusLabel}
          </span>
        )}

        {!summary.isInviteMode && !summary.isEmergency && summary.canTriggerEmergency && onTriggerEmergency && (
          <button
            type="button"
            onClick={() => onTriggerEmergency(shift, event)}
            style={{
              background: 'linear-gradient(135deg, #e11d48 0%, #be123c 100%)',
              color: '#ffffff',
              border: 'none',
              borderRadius: 8,
              padding: '0.45rem 0.9rem',
              fontSize: '0.8rem',
              fontWeight: 700,
              cursor: 'pointer',
              boxShadow: '0 2px 6px rgba(225, 29, 72, 0.25)',
              whiteSpace: 'nowrap',
            }}
          >
            Kích hoạt khẩn cấp
          </button>
        )}
      </div>
    </div>
  );
}
