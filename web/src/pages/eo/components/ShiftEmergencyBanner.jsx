import React from 'react';
import { getShiftStaffingSummary } from '../utils/eventHelpers';
import EmergencyTriggerButton from './EmergencyTriggerButton';

/**
 * ShiftEmergencyBanner (Ticket EO-009)
 * Chỉ hiển thị khung cảnh báo điều phối khẩn cấp trong trang chi tiết sau khi ca đã được kích hoạt khẩn cấp
 * (do EO bấm Kích hoạt khẩn cấp hoặc hệ thống tự động khi Worker hủy/No-show), hoặc vừa bù đủ người trong phiên:
 * - Ca đã bật Emergency -> nhãn "Đang điều phối khẩn cấp" / "Đã bật điều phối · Chưa tìm được Worker phù hợp".
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

  // Chỉ hiện khung đỏ điều phối khẩn cấp sau khi đã bật Emergency
  if (!summary.isEmergency) {
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
        backgroundColor: '#fef2f2',
        border: '1px solid #fca5a5',
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
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
        {/* Ca đã kích hoạt Emergency Dispatch */}
        {!summary.isInviteMode && (
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
      </div>
    </div>
  );
}
