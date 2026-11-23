import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { showToast } from '../../../components/common/Toast';
import { eoService } from '../../../services/eoService';
import { getShiftStaffingSummary } from '../utils/eventHelpers';

const RADIUS_OPTIONS = [
  { km: 15, label: '15 km', desc: 'Gần điểm tổ chức' },
  { km: 25, label: '25 km', desc: 'Mặc định chuẩn' },
  { km: 30, label: '30 km', desc: 'Mở rộng khu vực' },
  { km: 50, label: '50 km', desc: 'Toàn thành phố' },
];

/**
 * EmergencyDispatchConfirmModal (Ticket EO-009)
 * Hộp thoại tự động đề xuất mở rộng phạm vi tìm kiếm khẩn cấp khi ca làm việc sắp diễn ra mà thiếu nhân sự.
 * Luôn căn giữa tuyệt đối trên màn hình và nút bấm đồng bộ, đẹp mắt.
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
  const [localSubmitting, setLocalSubmitting] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setRadiusKm(defaultRadius);
      setLocalSubmitting(false);
    }
  }, [isOpen, defaultRadius]);

  if (!isOpen || !shift) return null;

  const isBusy = externalSubmitting || localSubmitting;

  const handleFormSubmit = async (e) => {
    e.preventDefault();
    if (isBusy) return;

    if (onConfirm) {
      onConfirm({ radiusKm });
      return;
    }

    setLocalSubmitting(true);
    try {
      const result = await eoService.enableEmergencyDispatch(shift.shiftId, radiusKm);
      const dispatched = Number(result?.dispatchedCount || 0);
      const appliedRadius = Number(result?.effectiveRadiusKm || radiusKm);

      if (dispatched > 0) {
        showToast(
          `Đã kích hoạt điều phối khẩn cấp (${appliedRadius} km): Gửi ${dispatched} đề xuất đến Worker phù hợp theo Preference.`,
          'success'
        );
      } else {
        showToast(
          `Đã mở rộng phạm vi điều phối (${appliedRadius} km) · Chưa tìm được Worker phù hợp.`,
          'info'
        );
      }

      onSuccess?.(shift.shiftId, result);
      await onReloadEvents?.();
      onClose?.();
    } catch (err) {
      const msg = err?.response?.data?.message || err?.message || 'Không thể kích hoạt điều phối khẩn cấp.';
      showToast(msg, 'error');
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
      aria-labelledby="emergency-confirm-title"
      onClick={() => !isBusy && onClose?.()}
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
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
          maxWidth: 560,
          width: '100%',
          maxHeight: 'calc(100vh - 2.5rem)',
          overflowY: 'auto',
          backgroundColor: '#ffffff',
          borderRadius: 16,
          boxShadow: '0 25px 50px -12px rgba(15, 23, 42, 0.35)',
          borderTop: '5px solid #dc2626',
          padding: '1.75rem',
          boxSizing: 'border-box',
        }}
      >
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', marginBottom: '1.15rem' }}>
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
                backgroundColor: '#fee2e2',
                color: '#b91c1c',
                marginBottom: '0.45rem',
                letterSpacing: '0.02em',
              }}
            >
              <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: '#dc2626' }} />
              {isAutoPrompt ? 'TỰ ĐỘNG ĐỀ XUẤT CẬN GIỜ' : 'ĐIỀU PHỐI KHẨN CẤP'}
            </span>
            <h3
              id="emergency-confirm-title"
              style={{
                margin: 0,
                fontSize: '1.22rem',
                fontWeight: 800,
                color: '#0f172a',
                lineHeight: 1.3,
              }}
            >
              Mở Rộng Phạm Vi Tìm Kiếm Khẩn Cấp?
            </h3>
            <p style={{ margin: '0.35rem 0 0', fontSize: '0.85rem', color: '#64748b', lineHeight: 1.45 }}>
              Ca làm việc sắp đến giờ bắt đầu nhưng vẫn chưa đủ nhân sự yêu cầu. Bạn có muốn mở rộng phạm vi tìm kiếm để ghép Worker phù hợp không?
            </p>
          </div>
          <button
            type="button"
            onClick={() => !isBusy && onClose?.()}
            disabled={isBusy}
            style={{
              background: 'transparent',
              border: 'none',
              fontSize: '1.4rem',
              color: '#94a3b8',
              cursor: isBusy ? 'not-allowed' : 'pointer',
              lineHeight: 1,
              padding: '0.2rem',
            }}
            aria-label="Đóng hộp thoại"
          >
            &times;
          </button>
        </div>

        {/* Thông tin ca làm việc & Tình trạng thiếu */}
        <div
          style={{
            background: '#fff1f2',
            border: '1px solid #fecdd3',
            borderRadius: 12,
            padding: '1rem 1.15rem',
            marginBottom: '1.15rem',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.45rem' }}>
            <span style={{ fontWeight: 800, color: '#9f1239', fontSize: '0.98rem' }}>
              {shift.jobType} {event?.name ? `— ${event.name}` : ''}
            </span>
            <span
              style={{
                backgroundColor: '#dc2626',
                color: '#ffffff',
                padding: '2px 8px',
                borderRadius: 99,
                fontSize: '0.75rem',
                fontWeight: 700,
              }}
            >
              Thiếu {summary.missing} người
            </span>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: '0.5rem', fontSize: '0.83rem', color: '#881337', lineHeight: 1.45 }}>
            <div>
              <strong>Giờ bắt đầu:</strong> {summary.startTimeText}
            </div>
            <div>
              <strong>Nhân sự hiện tại:</strong> Đã chốt {summary.confirmed}/{summary.required}
            </div>
          </div>
        </div>

        <form onSubmit={handleFormSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1.15rem' }}>
          {/* Lựa chọn bán kính mở rộng */}
          <div>
            <label style={{ display: 'block', fontSize: '0.86rem', fontWeight: 700, color: '#1e293b', marginBottom: '0.5rem' }}>
              Chọn bán kính mở rộng quét tìm Worker:
            </label>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.65rem' }}>
              {RADIUS_OPTIONS.map((opt) => {
                const selected = Number(radiusKm) === opt.km;
                return (
                  <button
                    key={opt.km}
                    type="button"
                    disabled={isBusy}
                    onClick={() => setRadiusKm(opt.km)}
                    style={{
                      minHeight: '48px',
                      padding: '0.65rem 0.85rem',
                      borderRadius: 10,
                      border: selected ? '2px solid #dc2626' : '1.5px solid #e2e8f0',
                      background: selected ? '#fff1f2' : '#ffffff',
                      color: selected ? '#9f1239' : '#334155',
                      fontWeight: selected ? 700 : 500,
                      cursor: isBusy ? 'not-allowed' : 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      transition: 'all 0.16s ease-in-out',
                      textAlign: 'left',
                    }}
                  >
                    <div>
                      <div style={{ fontSize: '0.86rem', fontWeight: 700 }}>{opt.label}</div>
                      <div style={{ fontSize: '0.74rem', color: selected ? '#be123c' : '#64748b' }}>{opt.desc}</div>
                    </div>
                    {selected && (
                      <span style={{ color: '#dc2626', fontWeight: 900, fontSize: '0.95rem' }}>✓</span>
                    )}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Diễn giải cơ chế Preference */}
          <div
            style={{
              padding: '0.75rem 0.95rem',
              borderRadius: 10,
              backgroundColor: '#f8fafc',
              border: '1px solid #e2e8f0',
              fontSize: '0.81rem',
              color: '#475569',
              lineHeight: 1.5,
            }}
          >
            Hệ thống sẽ lọc Worker trực tuyến trong bán kính <strong>{radiusKm} km</strong> theo đúng tiêu chí{' '}
            <strong>Preference</strong> (mức lương tối thiểu, lịch rảnh, kỹ năng bắt buộc) và tính điểm xếp hạng để gửi đề xuất nhận ca khẩn cấp.
          </div>

          {/* Footer nút hành động đều và đẹp */}
          <div
            style={{
              display: 'flex',
              justifyContent: 'flex-end',
              alignItems: 'center',
              gap: '0.75rem',
              marginTop: '0.35rem',
            }}
          >
            <button
              type="button"
              onClick={() => !isBusy && onClose?.()}
              disabled={isBusy}
              style={{
                minHeight: '42px',
                padding: '0 1.35rem',
                borderRadius: '10px',
                border: '1.5px solid #cbd5e1',
                backgroundColor: '#f8fafc',
                color: '#475569',
                fontWeight: 600,
                fontSize: '0.86rem',
                cursor: isBusy ? 'not-allowed' : 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'all 0.16s ease-in-out',
              }}
            >
              Từ chối, giữ nguyên
            </button>

            <button
              type="submit"
              disabled={isBusy}
              style={{
                minHeight: '42px',
                padding: '0 1.5rem',
                borderRadius: '10px',
                border: 'none',
                background: isBusy
                  ? '#f87171'
                  : 'linear-gradient(135deg, #e11d48 0%, #dc2626 100%)',
                color: '#ffffff',
                fontWeight: 700,
                fontSize: '0.86rem',
                cursor: isBusy ? 'wait' : 'pointer',
                boxShadow: '0 4px 12px rgba(220, 38, 38, 0.28)',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px',
                transition: 'all 0.16s ease-in-out',
              }}
            >
              {isBusy ? 'Đang kích hoạt...' : 'Đồng ý mở rộng tìm kiếm'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );

  return typeof document !== 'undefined' ? createPortal(modalNode, document.body) : modalNode;
}
