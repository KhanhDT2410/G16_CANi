import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { showToast } from '../../../components/common/Toast';
import { eoService } from '../../../services/eoService';
import { getShiftStaffingSummary } from '../utils/eventHelpers';

const RADIUS_OPTIONS = [
  { km: 15, label: '15 km (Gần điểm tổ chức)' },
  { km: 25, label: '25 km (Mặc định)' },
  { km: 30, label: '30 km (Mở rộng)' },
  { km: 50, label: '50 km (Toàn khu vực)' },
];

/**
 * Hộp thoại xác nhận kích hoạt điều phối khẩn cấp (chỉ mở khi EO chủ động bấm nút).
 * Hiển thị đúng ca, số người còn thiếu và phạm vi tìm kiếm sẽ áp dụng.
 */
export default function EmergencyDispatchConfirmModal({
  isOpen,
  shift,
  event,
  onClose,
  onSuccess,
  onReloadEvents,
}) {
  const summary = getShiftStaffingSummary(shift || {}, event || {});
  const defaultRadius = summary.defaultRadiusKm || 25;
  const [radiusKm, setRadiusKm] = useState(defaultRadius);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setRadiusKm(defaultRadius);
      setIsSubmitting(false);
    }
  }, [isOpen, defaultRadius]);

  if (!isOpen || !shift) return null;

  const handleConfirm = async (e) => {
    e.preventDefault();
    if (isSubmitting) return;
    setIsSubmitting(true);
    try {
      const result = await eoService.enableEmergencyDispatch(shift.shiftId, radiusKm);
      const dispatched = Number(result?.dispatchedCount || 0);
      const appliedRadius = Number(result?.effectiveRadiusKm || radiusKm);

      if (dispatched > 0) {
        showToast(
          `Đang điều phối khẩn cấp (${appliedRadius} km): đã gửi ${dispatched} đề xuất đến Worker phù hợp.`,
          'success'
        );
      } else {
        showToast(
          `Đã bật điều phối (${appliedRadius} km) · Chưa tìm được Worker phù hợp.`,
          'info'
        );
      }

      onSuccess?.(shift.shiftId, result);
      await onReloadEvents?.();
      onClose?.();
    } catch (err) {
      const msg = err?.response?.data?.message || err?.message || 'Không thể kích hoạt điều phối khẩn cấp.';
      showToast(msg, 'error');
      // Đồng bộ lại trạng thái mới nhất (ví dụ ca vừa đủ người hoặc đã sang IN_PROGRESS)
      await onReloadEvents?.();
      onClose?.();
    } finally {
      setIsSubmitting(false);
    }
  };

  const modalNode = (
    <div
      className="eo-modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="emergency-confirm-title"
      onClick={() => !isSubmitting && onClose?.()}
    >
      <div
        className="eo-modal-box animate-fade-in-up"
        onClick={(e) => e.stopPropagation()}
        style={{
          maxWidth: 540,
          width: '94%',
          borderTop: '4px solid #dc2626',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', marginBottom: '1rem' }}>
          <div>
            <span
              style={{
                display: 'inline-block',
                padding: '2px 8px',
                borderRadius: 6,
                fontSize: '0.72rem',
                fontWeight: 800,
                backgroundColor: '#fee2e2',
                color: '#b91c1c',
                marginBottom: '0.35rem',
              }}
            >
              ĐIỀU PHỐI KHẨN CẤP
            </span>
            <h3 id="emergency-confirm-title" style={{ margin: 0, fontSize: '1.15rem', fontWeight: 800, color: '#0f172a' }}>
              Xác nhận kích hoạt điều phối khẩn cấp
            </h3>
          </div>
          <button
            type="button"
            onClick={() => !isSubmitting && onClose?.()}
            disabled={isSubmitting}
            style={{
              background: 'transparent',
              border: 'none',
              fontSize: '1.35rem',
              color: '#94a3b8',
              cursor: isSubmitting ? 'not-allowed' : 'pointer',
              lineHeight: 1,
            }}
            aria-label="Đóng hộp thoại"
          >
            &times;
          </button>
        </div>

        {/* Thông tin ca & số người còn thiếu */}
        <div
          style={{
            background: '#fff1f2',
            border: '1px solid #fecdd3',
            borderRadius: 10,
            padding: '0.9rem 1rem',
            marginBottom: '1rem',
          }}
        >
          <div style={{ fontWeight: 800, color: '#9f1239', fontSize: '0.95rem', marginBottom: '0.35rem' }}>
            {shift.jobType} {event?.name ? `— ${event.name}` : ''}
          </div>
          <div style={{ fontSize: '0.84rem', color: '#881337', lineHeight: 1.5 }}>
            <div>
              <strong>Giờ bắt đầu:</strong> {summary.startTimeText}
            </div>
            <div>
              <strong>Nhân sự:</strong> Đã chốt <strong>{summary.confirmed}/{summary.required}</strong> ·{' '}
              <strong style={{ color: '#dc2626' }}>Còn thiếu {summary.missing} người</strong>
            </div>
            <div>
              <strong>Phạm vi tìm kiếm sẽ áp dụng:</strong> Bán kính <strong>{radiusKm} km</strong> quanh điểm tổ chức
            </div>
          </div>
        </div>

        <form onSubmit={handleConfirm} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div>
            <label style={{ display: 'block', fontSize: '0.84rem', fontWeight: 700, color: '#1e293b', marginBottom: '0.45rem' }}>
              Phạm vi quét tìm Worker phù hợp
            </label>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.5rem' }}>
              {RADIUS_OPTIONS.map((opt) => {
                const selected = Number(radiusKm) === opt.km;
                return (
                  <button
                    key={opt.km}
                    type="button"
                    disabled={isSubmitting}
                    onClick={() => setRadiusKm(opt.km)}
                    style={{
                      padding: '0.55rem 0.65rem',
                      borderRadius: 8,
                      border: selected ? '2px solid #dc2626' : '1px solid #cbd5e1',
                      background: selected ? '#fef2f2' : '#ffffff',
                      color: selected ? '#b91c1c' : '#334155',
                      fontWeight: selected ? 700 : 500,
                      fontSize: '0.82rem',
                      cursor: isSubmitting ? 'not-allowed' : 'pointer',
                      textAlign: 'left',
                    }}
                  >
                    {opt.label}
                  </button>
                );
              })}
            </div>
          </div>

          <div
            style={{
              padding: '0.7rem 0.85rem',
              borderRadius: 8,
              backgroundColor: '#f8fafc',
              border: '1px solid #e2e8f0',
              fontSize: '0.8rem',
              color: '#475569',
              lineHeight: 1.45,
            }}
          >
            Hệ thống sẽ kiểm tra lại trạng thái ca, gửi đề xuất khẩn cấp tới các Worker trực tuyến phù hợp trong phạm vi{' '}
            <strong>{radiusKm} km</strong> và mở hiển thị ưu tiên trên Radar tìm việc.
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.65rem', marginTop: '0.25rem' }}>
            <button
              type="button"
              onClick={() => !isSubmitting && onClose?.()}
              disabled={isSubmitting}
              style={{
                padding: '0.55rem 1.15rem',
                borderRadius: 8,
                border: '1px solid #cbd5e1',
                backgroundColor: '#ffffff',
                color: '#475569',
                fontWeight: 600,
                fontSize: '0.84rem',
                cursor: isSubmitting ? 'not-allowed' : 'pointer',
              }}
            >
              Để sau
            </button>

            <button
              type="submit"
              disabled={isSubmitting}
              style={{
                padding: '0.55rem 1.25rem',
                borderRadius: 8,
                border: 'none',
                backgroundColor: isSubmitting ? '#f87171' : '#dc2626',
                color: '#ffffff',
                fontWeight: 700,
                fontSize: '0.84rem',
                cursor: isSubmitting ? 'wait' : 'pointer',
                boxShadow: '0 3px 10px rgba(220, 38, 38, 0.25)',
              }}
            >
              {isSubmitting ? 'Đang kích hoạt...' : 'Xác nhận kích hoạt'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );

  return typeof document !== 'undefined' ? createPortal(modalNode, document.body) : modalNode;
}
