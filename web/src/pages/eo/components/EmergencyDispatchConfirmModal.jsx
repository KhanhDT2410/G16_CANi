import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { showToast } from '../../../components/common/Toast';
import { eoService } from '../../../services/eoService';
import { getShiftStaffingSummary } from '../utils/eventHelpers';
import EmergencyConsequencesCallout from './EmergencyConsequencesCallout';
import EmergencyRadiusSelector from './EmergencyRadiusSelector';
import EmergencyShiftInfoCard from './EmergencyShiftInfoCard';
import EmergencyAdvancedOptions from './EmergencyAdvancedOptions';
import EmergencyActionButtons from './EmergencyActionButtons';

/**
 * EmergencyDispatchConfirmModal (Ticket EO-009 & EO-010)
 * Hỗ trợ cả kích hoạt mới lẫn cập nhật cấu hình & quét lại khẩn cấp (redispatch).
 */
export default function EmergencyDispatchConfirmModal({
  isOpen,
  shift,
  event,
  onClose,
  onConfirm,
  onSuccess,
  onReloadEvents,
  isSubmitting: externalSubmitting = false,
  isAutoPrompt = true,
  isRedispatch = false,
}) {
  const summary = getShiftStaffingSummary(shift || {}, event || {});
  const defaultRadius = isRedispatch
    ? summary.emergencyRadiusKm || summary.defaultRadiusKm || 25
    : summary.defaultRadiusKm || 25;

  const [radiusKm, setRadiusKm] = useState(defaultRadius);
  const [skillRelaxation, setSkillRelaxation] = useState(summary.emergencySkillRelaxation || 'NONE');
  const [emergencyBonus, setEmergencyBonus] = useState(summary.emergencyBonus || 0);
  const [minReliabilityScore, setMinReliabilityScore] = useState(summary.emergencyMinRs ?? '');
  const [isAcknowledged, setIsAcknowledged] = useState(false);
  const [localSubmitting, setLocalSubmitting] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setRadiusKm(defaultRadius);
      setSkillRelaxation(shift?.emergencySkillRelaxation || 'NONE');
      setEmergencyBonus(Number(shift?.emergencyBonus || 0));
      setMinReliabilityScore(shift?.emergencyMinRs ?? '');
      setIsAcknowledged(false);
      setLocalSubmitting(false);
    }
  }, [isOpen, defaultRadius, shift?.shiftId]);

  if (!isOpen || !shift) return null;

  const isBusy = externalSubmitting || localSubmitting;
  const wageFormatted = shift.hourlyWage
    ? new Intl.NumberFormat('vi-VN').format(shift.hourlyWage) + ' đ/giờ'
    : 'Theo thỏa thuận';

  const handleFormSubmit = async (e) => {
    e.preventDefault();
    if (isBusy || !isAcknowledged) return;

    const payload = {
      radiusKm: Number(radiusKm),
      skillRelaxation,
      minReliabilityScore: minReliabilityScore !== '' && minReliabilityScore != null ? Number(minReliabilityScore) : null,
      emergencyBonus: Number(emergencyBonus || 0),
    };

    if (onConfirm) {
      onConfirm(payload);
      return;
    }

    setLocalSubmitting(true);
    try {
      const result = isRedispatch
        ? await eoService.redispatchEmergencyDispatch(shift.shiftId, payload)
        : await eoService.enableEmergencyDispatch(shift.shiftId, payload);
      const dispatched = Number(result?.dispatchedCount || 0);
      const appliedRadius = Number(result?.effectiveRadiusKm || radiusKm);

      const msg = dispatched > 0
        ? `Đã ${isRedispatch ? 'quét lại' : 'kích hoạt'} điều phối khẩn cấp (${appliedRadius} km): Gửi ${dispatched} đề xuất.`
        : `Đã cập nhật phạm vi điều phối (${appliedRadius} km) · Chưa tìm được Worker phù hợp.`;
      showToast(msg, dispatched > 0 ? 'success' : 'info');

      onSuccess?.(shift.shiftId, result);
      await onReloadEvents?.();
      onClose?.();
    } catch (err) {
      showToast(err?.response?.data?.message || err?.message || 'Không thể kích hoạt điều phối khẩn cấp.', 'error');
      await onReloadEvents?.();
      onClose?.();
    } finally {
      setLocalSubmitting(false);
    }
  };

  const modalNode = (
    <div
      className="eo-modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="emergency-warning-title"
      onClick={() => !isBusy && onClose?.()}
      style={{
        position: 'fixed', inset: 0, width: '100vw', height: '100vh',
        backgroundColor: 'rgba(15, 23, 42, 0.72)', backdropFilter: 'blur(8px)', WebkitBackdropFilter: 'blur(8px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9999,
        padding: '1.25rem', overflowY: 'auto', boxSizing: 'border-box',
      }}
    >
      <div
        className="eo-modal-box animate-fade-in-up"
        onClick={(e) => e.stopPropagation()}
        style={{
          position: 'relative', margin: 'auto', maxWidth: 600, width: '100%',
          maxHeight: 'calc(100vh - 2rem)', overflowY: 'auto', backgroundColor: '#ffffff',
          borderRadius: 16, boxShadow: '0 25px 50px -12px rgba(15, 23, 42, 0.35)',
          borderTop: '5px solid #dc2626', padding: '1.5rem', boxSizing: 'border-box',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', marginBottom: '1rem' }}>
          <div>
            <span
              style={{
                display: 'inline-flex', alignItems: 'center', gap: '0.35rem',
                padding: '3px 9px', borderRadius: 6, fontSize: '0.74rem', fontWeight: 800,
                backgroundColor: '#fef3c7', color: '#b45309', marginBottom: '0.45rem',
              }}
            >
              {isRedispatch
                ? 'CẬP NHẬT & QUÉT LẠI • EMERGENCY DISPATCH'
                : isAutoPrompt
                ? 'CẢNH BÁO HỆ QUẢ • TỰ ĐỘNG ĐỀ XUẤT'
                : 'CẢNH BÁO HỆ QUẢ • EMERGENCY DISPATCH'}
            </span>
            <h3 id="emergency-warning-title" style={{ margin: 0, fontSize: '1.18rem', fontWeight: 800, color: '#0f172a' }}>
              {isRedispatch
                ? 'Mở Rộng Phạm Vi & Quét Lại Điều Phối Khẩn Cấp'
                : 'Cảnh Báo Hệ Quả Khi Kích Hoạt Emergency Dispatch'}
            </h3>
          </div>
          <button
            type="button"
            onClick={() => !isBusy && onClose?.()}
            disabled={isBusy}
            style={{ background: 'transparent', border: 'none', fontSize: '1.4rem', color: '#94a3b8', cursor: isBusy ? 'not-allowed' : 'pointer' }}
            aria-label="Đóng hộp thoại"
          >
            ✕
          </button>
        </div>

        <EmergencyShiftInfoCard shift={shift} event={event} summary={summary} wageFormatted={wageFormatted} />

        <form onSubmit={handleFormSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '0.95rem' }}>
          <EmergencyRadiusSelector radiusKm={radiusKm} onChange={setRadiusKm} disabled={isBusy} />

          <EmergencyAdvancedOptions
            skillRelaxation={skillRelaxation}
            onChangeSkillRelaxation={setSkillRelaxation}
            preferOnlyAllowed={summary.emergencyPreferOnlyAllowed}
            emergencyBonus={emergencyBonus}
            onChangeEmergencyBonus={setEmergencyBonus}
            minReliabilityScore={minReliabilityScore}
            onChangeMinReliabilityScore={setMinReliabilityScore}
            baseMinRs={shift?.minReliabilityScore || 60}
            disabled={isBusy}
          />

          <EmergencyConsequencesCallout
            radiusKm={radiusKm}
            wageFormatted={wageFormatted}
            skillRelaxation={skillRelaxation}
            emergencyBonus={emergencyBonus}
          />

          <EmergencyActionButtons
            isAcknowledged={isAcknowledged}
            onToggleAcknowledge={setIsAcknowledged}
            isBusy={isBusy}
            onCancel={() => !isBusy && onClose?.()}
          />
        </form>
      </div>
    </div>
  );

  return typeof document !== 'undefined' ? createPortal(modalNode, document.body) : modalNode;
}
