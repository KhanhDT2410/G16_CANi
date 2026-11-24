import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { showToast } from '../../../components/common/Toast';
import { eoService } from '../../../services/eoService';
import { getShiftStaffingSummary } from '../utils/eventHelpers';
import EmergencyConsequencesCallout from './EmergencyConsequencesCallout';
import EmergencyRadiusSelector from './EmergencyRadiusSelector';
import EmergencyShiftInfoCard from './EmergencyShiftInfoCard';
import EmergencyActionButtons from './EmergencyActionButtons';

/**
 * EmergencyDispatchConfirmModal (Ticket EO-009 & EO-010)
 * Hộp thoại cảnh báo hệ quả (Warning Modal) và yêu cầu xác nhận trước khi kích hoạt Emergency Dispatch.
 * - Căn giữa tuyệt đối trên màn hình bất kể vị trí cuộn trang.
 * - Bắt buộc có nút xác nhận: "Tôi hiểu và đồng ý kích hoạt" / "Hủy bỏ".
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
}) {
  const summary = getShiftStaffingSummary(shift || {}, event || {});
  const defaultRadius = summary.defaultRadiusKm || 25;
  const [radiusKm, setRadiusKm] = useState(defaultRadius);
  const [isAcknowledged, setIsAcknowledged] = useState(false);
  const [localSubmitting, setLocalSubmitting] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setRadiusKm(defaultRadius);
      setIsAcknowledged(false);
      setLocalSubmitting(false);
    }
  }, [isOpen, defaultRadius]);

  if (!isOpen || !shift) return null;

  const isBusy = externalSubmitting || localSubmitting;
  const wageFormatted = shift.hourlyWage
    ? new Intl.NumberFormat('vi-VN').format(shift.hourlyWage) + ' đ/giờ'
    : 'Theo thỏa thuận';

  const handleFormSubmit = async (e) => {
    e.preventDefault();
    if (isBusy || !isAcknowledged) return;

    if (onConfirm) {
      onConfirm({ radiusKm });
      return;
    }

    setLocalSubmitting(true);
    try {
      const result = await eoService.enableEmergencyDispatch(shift.shiftId, radiusKm);
      const dispatched = Number(result?.dispatchedCount || 0);
      const appliedRadius = Number(result?.effectiveRadiusKm || radiusKm);

      const msg = dispatched > 0
        ? `Đã kích hoạt điều phối khẩn cấp (${appliedRadius} km): Gửi ${dispatched} đề xuất đến Worker phù hợp.`
        : `Đã mở rộng phạm vi điều phối (${appliedRadius} km) · Chưa tìm được Worker phù hợp.`;
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
        position: 'fixed',
        inset: 0,
        width: '100vw',
        height: '100vh',
        backgroundColor: 'rgba(15, 23, 42, 0.72)',
        backdropFilter: 'blur(8px)',
        WebkitBackdropFilter: 'blur(8px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
        padding: '1.25rem',
        overflowY: 'auto',
        boxSizing: 'border-box',
      }}
    >
      <div
        className="eo-modal-box animate-fade-in-up"
        onClick={(e) => e.stopPropagation()}
        style={{
          position: 'relative',
          margin: 'auto',
          maxWidth: 580,
          width: '100%',
          maxHeight: 'calc(100vh - 2rem)',
          overflowY: 'auto',
          backgroundColor: '#ffffff',
          borderRadius: 16,
          boxShadow: '0 25px 50px -12px rgba(15, 23, 42, 0.35)',
          borderTop: '5px solid #dc2626',
          padding: '1.5rem',
          boxSizing: 'border-box',
        }}
      >
        {/* Header Warning */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', marginBottom: '1rem' }}>
          <div>
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.35rem',
                padding: '3px 9px',
                borderRadius: 6,
                fontSize: '0.74rem',
                fontWeight: 800,
                backgroundColor: '#fef3c7',
                color: '#b45309',
                marginBottom: '0.45rem',
              }}
            >
              {isAutoPrompt ? 'CẢNH BÁO HỆ QUẢ • TỰ ĐỘNG ĐỀ XUẤT' : 'CẢNH BÁO HỆ QUẢ • EMERGENCY DISPATCH'}
            </span>
            <h3 id="emergency-warning-title" style={{ margin: 0, fontSize: '1.2rem', fontWeight: 800, color: '#0f172a' }}>
              Cảnh Báo Hệ Quả Khi Kích Hoạt Emergency Dispatch
            </h3>
            <p style={{ margin: '0.35rem 0 0', fontSize: '0.84rem', color: '#64748b' }}>
              Việc mở rộng tìm kiếm khẩn cấp có thể thay đổi phạm vi địa lý và tiêu chí ứng viên.
            </p>
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

          {/* Warning Callout Box - Chi tiết hệ quả (Ticket EO-010 Tiêu chí 1) */}
          <EmergencyConsequencesCallout radiusKm={radiusKm} wageFormatted={wageFormatted} />

          {/* Checkbox xác nhận & nút bấm chuẩn EO-010: 'Tôi hiểu và đồng ý kích hoạt' / 'Hủy bỏ' */}
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
