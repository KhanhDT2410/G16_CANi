import React, { useState } from 'react';
import { showToast } from '../../../components/common/Toast';
import { eoService } from '../../../services/eoService';
import { getShiftStaffingSummary } from '../utils/eventHelpers';
import { openEmergencyPromptForShift } from './EmergencyTriggerButton';

const RELAX_LABELS = {
  LOWER_TIER: 'Hạ 1 bậc xác minh',
  PREFER_ONLY: 'Ưu tiên kỹ năng (phổ thông)',
};

/**
 * ShiftEmergencyBanner (Ticket EO-009)
 * Hiển thị trạng thái Emergency Dispatch + thông số đang chạy + nút Quét lại / Tắt khẩn cấp.
 */
export default function ShiftEmergencyBanner({
  shift,
  event,
  localDispatchResult = null,
  onReloadEvents = null,
}) {
  const [disabling, setDisabling] = useState(false);
  const summary = getShiftStaffingSummary(shift, event);

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

  if (!summary.isEmergency) {
    return null;
  }

  const resolvedHasCandidates =
    localDispatchResult && typeof localDispatchResult.hasCandidates === 'boolean'
      ? localDispatchResult.hasCandidates
      : summary.hasCandidates;

  const emergencyStatusLabel = resolvedHasCandidates
    ? `Đang điều phối khẩn cấp (${summary.emergencyRadiusKm} km)`
    : `Đã bật (${summary.emergencyRadiusKm} km) · Chưa tìm được Worker phù hợp`;

  const handleDisableEmergency = async () => {
    if (disabling || !shift?.shiftId) return;
    setDisabling(true);
    try {
      await eoService.disableEmergencyDispatch(shift.shiftId);
      showToast('Đã tắt chế độ điều phối khẩn cấp cho ca làm việc.', 'info');
      if (onReloadEvents) {
        await onReloadEvents();
      } else if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('cani_eo_reload_events'));
      }
    } catch (err) {
      showToast(err?.response?.data?.message || err?.message || 'Không thể tắt điều phối khẩn cấp.', 'error');
    } finally {
      setDisabling(false);
    }
  };

  return (
    <div
      style={{
        padding: '0.75rem 1rem', borderRadius: 10, backgroundColor: '#fef2f2',
        border: '1px solid #fca5a5', display: 'flex', alignItems: 'center',
        justifyContent: 'space-between', gap: '0.75rem', flexWrap: 'wrap',
      }}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem', flex: '1 1 260px' }}>
        <div style={{ fontSize: '0.85rem', fontWeight: 700, color: '#9f1239' }}>
          {summary.summaryLine}
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.45rem', fontSize: '0.74rem', color: '#7f1d1d' }}>
          {summary.emergencyBonus > 0 && (
            <span style={{ background: '#fee2e2', padding: '1px 7px', borderRadius: 6, fontWeight: 700 }}>
              +{new Intl.NumberFormat('vi-VN').format(summary.emergencyBonus)} đ phụ cấp
            </span>
          )}
          {RELAX_LABELS[summary.emergencySkillRelaxation] && (
            <span style={{ background: '#fff7ed', color: '#9a3412', padding: '1px 7px', borderRadius: 6, fontWeight: 600 }}>
              {RELAX_LABELS[summary.emergencySkillRelaxation]}
            </span>
          )}
          {summary.emergencyMinRs && (
            <span style={{ background: '#f8fafc', color: '#334155', padding: '1px 7px', borderRadius: 6, fontWeight: 600 }}>
              RS tối thiểu: {summary.emergencyMinRs}
            </span>
          )}
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem', flexWrap: 'wrap' }}>
        {!summary.isInviteMode && (
          <>
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.4rem',
                padding: '0.38rem 0.75rem',
                borderRadius: 8,
                fontSize: '0.76rem',
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

            <button
              type="button"
              disabled={!summary.canRedispatchEmergency || disabling}
              title={
                summary.canRedispatchEmergency
                  ? 'Mở rộng bán kính / nới tiêu chí / tăng phụ cấp và quét lại'
                  : `Vui lòng chờ ${summary.emergencyCooldownRemainingSeconds || 0} giây trước khi quét lại`
              }
              onClick={() => openEmergencyPromptForShift(shift.shiftId, event?.eventId || shift.eventId, 'redispatch')}
              style={{
                padding: '0.38rem 0.72rem',
                borderRadius: 8,
                fontSize: '0.76rem',
                fontWeight: 700,
                border: '1px solid #dc2626',
                backgroundColor: summary.canRedispatchEmergency ? '#ffffff' : '#f1f5f9',
                color: summary.canRedispatchEmergency ? '#dc2626' : '#94a3b8',
                cursor: summary.canRedispatchEmergency && !disabling ? 'pointer' : 'not-allowed',
              }}
            >
              {summary.canRedispatchEmergency
                ? 'Mở rộng / Quét lại'
                : `Chờ ${summary.emergencyCooldownRemainingSeconds}s`}
            </button>

            <button
              type="button"
              disabled={disabling}
              onClick={handleDisableEmergency}
              style={{
                padding: '0.38rem 0.72rem',
                borderRadius: 8,
                fontSize: '0.76rem',
                fontWeight: 600,
                border: '1px solid #cbd5e1',
                backgroundColor: '#ffffff',
                color: '#475569',
                cursor: disabling ? 'not-allowed' : 'pointer',
              }}
            >
              {disabling ? 'Đang tắt...' : 'Tắt khẩn cấp'}
            </button>
          </>
        )}
      </div>
    </div>
  );
}
