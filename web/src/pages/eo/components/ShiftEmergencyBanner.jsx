import React from 'react';
import { getShiftStaffingSummary } from '../utils/eventHelpers';
import EmergencyTriggerButton from './EmergencyTriggerButton';

/**
 * ShiftEmergencyBanner (Ticket EO-009)
 * Hiển thị cảnh báo thiếu nhân sự cho từng ca làm việc tại chỗ:
 * - Ca đã bật Emergency (do EO hoặc hệ thống tự động khi Worker hủy/No-show) -> nhãn "Đang điều phối khẩn cấp".
 * - Ca cận giờ thiếu người trước giờ bắt đầu (ACTIVE) -> "Cận giờ · Tự động đề xuất".
 * - Ca IN_PROGRESS (đã đến giờ làm) -> giải thích đóng kích hoạt thủ công và cơ chế tự động khi ghi nhận No-show còn đủ thời gian.
 * - Ca INVITE còn thiếu -> nút "Mời thêm Worker".
 * - Khi vừa bù đủ nhân sự trong phiên -> hiện phản hồi xanh "Đã bù đủ nhân sự" và gỡ cảnh báo thiếu.
 */
export default function ShiftEmergencyBanner({
  shift,
  event,
  onOpenInvite,
  localDispatchResult = null,
}) {
  const summary = getShiftStaffingSummary(shift, event);

  // Khi đã đủ nhân sự -> gỡ cảnh báo thiếu; nếu vừa theo dõi emergency trong phiên thì hiện kết quả đã bù đủ
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
        padding: '0.75rem 1rem',
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
              : 'Ca đã đến giờ làm việc (chức năng kích hoạt khẩn cấp thủ công đã đóng). Hệ thống sẽ tự động điều phối khẩn cấp nếu ghi nhận nhân sự vắng mặt (No-show) khi ca còn đủ thời gian làm việc; vui lòng kiểm tra điểm danh hoặc báo cáo vắng mặt nếu cần.'}
          </div>
        )}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
        {/* Chế độ mời trực tiếp (INVITE) */}
        {summary.isInviteMode && onOpenInvite && (
          <button
            type="button"
            onClick={() => onOpenInvite(shift, event)}
            style={{
              minHeight: '34px',
              background: '#7c3aed',
              color: '#ffffff',
              border: 'none',
              borderRadius: 8,
              padding: '0.45rem 0.9rem',
              fontSize: '0.82rem',
              fontWeight: 700,
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              whiteSpace: 'nowrap',
              transition: 'background 0.15s ease',
            }}
          >
            Mời thêm Worker
          </button>
        )}

        {/* Ca đã kích hoạt Emergency Dispatch */}
        {!summary.isInviteMode && summary.isEmergency && (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.4rem',
              padding: '0.4rem 0.85rem',
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

        {/* Ca cận giờ trước lúc bắt đầu chưa bật Emergency Dispatch -> Nút kích hoạt khẩn cấp */}
        {!summary.isInviteMode && !summary.isEmergency && !summary.isInProgress && summary.nearStartUnderstaffed && (
          <EmergencyTriggerButton shift={shift} event={event} compact />
        )}
      </div>
    </div>
  );
}
