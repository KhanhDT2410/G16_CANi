import React from 'react';
import { getShiftStaffingSummary } from '../utils/eventHelpers';

/**
 * ShiftEmergencyBanner (Ticket EO-009)
 * Hiển thị cảnh báo thiếu nhân sự cho từng ca làm việc tại chỗ:
 * - Loại bỏ nút bật Emergency Dispatch thủ công theo yêu cầu EO-009.
 * - Ca đã bật Emergency -> hiển thị nhãn "Đang điều phối khẩn cấp".
 * - Ca cận giờ thiếu người (ACTIVE hoặc IN_PROGRESS) -> hiển thị thông báo và hệ thống tự động đề xuất.
 * - Ca INVITE còn thiếu -> nút "Mời thêm Worker".
 */
export default function ShiftEmergencyBanner({
  shift,
  event,
  onOpenInvite,
  localDispatchResult = null,
}) {
  const summary = getShiftStaffingSummary(shift, event);

  // Khi đã đủ nhân sự -> gỡ cảnh báo đang hoạt động
  if (summary.missing <= 0 || summary.isDraft || summary.isCancelled || summary.isCompleted) {
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
              : 'Ca đã đến giờ làm việc và vẫn còn thiếu nhân sự. Hệ thống tự động đề xuất mở rộng tìm người bù ca hoặc bạn có thể kiểm tra điểm danh.'}
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

        {/* Ca cận giờ thiếu người nhưng chưa bật Emergency Dispatch (Được quản lý tự động bởi hệ thống qua Popup) */}
        {!summary.isInviteMode && !summary.isEmergency && summary.nearStartUnderstaffed && (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.35rem',
              padding: '0.4rem 0.85rem',
              borderRadius: 8,
              fontSize: '0.78rem',
              fontWeight: 700,
              backgroundColor: '#fff1f2',
              color: '#be123c',
              border: '1px solid #fecdd3',
              whiteSpace: 'nowrap',
            }}
            title="Hệ thống tự động đề xuất mở rộng phạm vi tìm kiếm khi cận giờ bắt đầu"
          >
            <span
              style={{
                width: 7,
                height: 7,
                borderRadius: '50%',
                backgroundColor: '#e11d48',
                display: 'inline-block',
              }}
            />
            Cận giờ · Tự động đề xuất
          </span>
        )}
      </div>
    </div>
  );
}
