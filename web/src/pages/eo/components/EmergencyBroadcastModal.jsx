import React, { useState } from 'react';
import { showToast } from '../../../components/common/Toast';
import { eoService } from '../../../services/eoService';

/**
 * EmergencyBroadcastModal
 * Modal kích hoạt Cứu Trợ Ca Khẩn Cấp (Emergency Dispatch Pool - Section 11 CANi_Flow_v8.txt)
 * Áp dụng khi Risk Score < 20%, Worker no-show hoặc bỏ dở ca sát giờ (< 2 giờ)
 */
export default function EmergencyBroadcastModal({
  isOpen,
  onClose,
  shift,
  onSuccess,
}) {
  const [reason, setReason] = useState('NO_SHOW');
  const [allowanceBonus, setAllowanceBonus] = useState(35000);
  const [expandedRadiusKm, setExpandedRadiusKm] = useState(30);
  const [customBonus, setCustomBonus] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!isOpen || !shift) return null;

  const currentWage = shift.hourlyWage || 50000;
  const activeBonus = customBonus !== '' ? parseInt(customBonus, 10) || 0 : allowanceBonus;
  const totalHourlyRate = currentWage + activeBonus;
  const missingCount = Math.max(1, (shift.requiredWorkers || 1) - (shift.confirmedWorkers || 0));

  const formatCurrency = (val) => new Intl.NumberFormat('vi-VN').format(val || 0);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      await eoService.triggerEmergencyBroadcast(shift.shiftId, {
        reason,
        allowanceBonus: activeBonus,
        expandedRadiusKm,
        missingCount,
      });

      showToast(
        `Đã phát sóng Emergency Broadcast! Bán kính quét: ${expandedRadiusKm}km, phụ cấp: +${formatCurrency(activeBonus)}đ/h.`,
        'success'
      );
      onSuccess?.(shift.shiftId, {
        allowanceBonus: activeBonus,
        expandedRadiusKm,
        emergencyActive: true,
        activatedAt: new Date().toISOString(),
      });
      onClose();
    } catch (err) {
      showToast(
        'Lỗi kích hoạt Emergency Broadcast: ' + (err.response?.data?.message || err.message),
        'error'
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="eo-modal-overlay">
      <div
        className="eo-modal-box animate-fade-in-up"
        style={{
          maxWidth: 640,
          width: '94%',
          maxHeight: '90vh',
          overflowY: 'auto',
          borderTop: '4px solid #dc2626',
        }}
      >
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.25rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  padding: '3px 9px',
                  borderRadius: 6,
                  fontSize: '0.75rem',
                  fontWeight: 800,
                  backgroundColor: '#fee2e2',
                  color: '#b91c1c',
                  letterSpacing: '0.05em',
                }}
              >
                KHẨN CẤP • SECTION 11
              </span>
              <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 800, color: '#0f172a' }}>
                Kích Hoạt Emergency Broadcast
              </h3>
            </div>
            <p style={{ margin: '0.35rem 0 0', fontSize: '0.875rem', color: '#64748b' }}>
              Phát tín hiệu ưu tiên cao tới toàn bộ Worker trực tuyến trong bán kính mở rộng để lấp ca tức thì.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            style={{
              background: 'transparent',
              border: 'none',
              fontSize: '1.4rem',
              color: '#94a3b8',
              cursor: 'pointer',
              lineHeight: 1,
            }}
          >
            ×
          </button>
        </div>

        {/* Thông tin ca làm việc & Rủi ro */}
        <div
          style={{
            background: '#fff1f2',
            border: '1px solid #fecdd3',
            borderRadius: 10,
            padding: '1rem',
            marginBottom: '1.25rem',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ fontWeight: 700, color: '#9f1239', fontSize: '0.95rem' }}>
              {shift.jobType} — {shift.eventName}
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
              Thiếu {missingCount} nhân sự
            </span>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.5rem', fontSize: '0.825rem', color: '#881337' }}>
            <div>
              <strong>Thời gian ca:</strong> {new Date(shift.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} - {new Date(shift.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
            </div>
            <div>
              <strong>Thù lao gốc:</strong> {formatCurrency(currentWage)} đ/giờ
            </div>
            <div>
              <strong>Rủi ro ca (Risk Score):</strong> <span style={{ fontWeight: 700, color: '#dc2626' }}>Nguy cấp (&lt; 20%)</span>
            </div>
            <div>
              <strong>Địa điểm:</strong> {shift.venueAddress || 'Tại điểm sự kiện'}
            </div>
          </div>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          {/* 1. Lý do kích hoạt */}
          <div>
            <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 700, color: '#1e293b', marginBottom: '0.4rem' }}>
              Tình Huống Kích Hoạt Cứu Trợ
            </label>
            <select
              className="filter-select"
              style={{ width: '100%', boxSizing: 'border-box' }}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            >
              <option value="NO_SHOW">Worker no-show sát giờ ca hoặc huỷ đột xuất (&lt; 2 giờ)</option>
              <option value="ABANDON_MID_SHIFT">Worker tự ý bỏ dở ca giữa chừng (Abandon mid-shift)</option>
              <option value="EXTRA_STAFF_NEEDED">Phát sinh yêu cầu tăng cường nhân sự gấp từ khách hàng</option>
              <option value="CRITICAL_UNFILLED">Sắp tới giờ sự kiện nhưng chưa tuyển đủ chỉ tiêu</option>
            </select>
          </div>

          {/* 2. Cấu hình phụ cấp khẩn cấp (Emergency Allowance) */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
              <label style={{ fontSize: '0.85rem', fontWeight: 700, color: '#1e293b' }}>
                Phụ Cấp Khẩn Cấp Thêm Mỗi Giờ (VNĐ/giờ)
              </label>
              <span style={{ fontSize: '0.75rem', color: '#059669', fontWeight: 600 }}>
                Cộng trực tiếp vào lương giờ
              </span>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '0.5rem', marginBottom: '0.6rem' }}>
              {[0, 20000, 35000, 50000].map((bonus) => {
                const isSelected = customBonus === '' && allowanceBonus === bonus;
                return (
                  <button
                    key={bonus}
                    type="button"
                    onClick={() => {
                      setAllowanceBonus(bonus);
                      setCustomBonus('');
                    }}
                    style={{
                      padding: '0.55rem 0.25rem',
                      borderRadius: 8,
                      border: isSelected ? '2px solid #dc2626' : '1px solid #cbd5e1',
                      background: isSelected ? '#fef2f2' : '#ffffff',
                      color: isSelected ? '#b91c1c' : '#334155',
                      fontWeight: isSelected ? 700 : 500,
                      fontSize: '0.825rem',
                      cursor: 'pointer',
                      textAlign: 'center',
                    }}
                  >
                    {bonus === 0 ? 'Không phụ cấp' : `+${bonus / 1000}k/h`}
                  </button>
                );
              })}
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              <span style={{ fontSize: '0.8rem', color: '#64748b' }}>Hoặc nhập mức khác:</span>
              <input
                type="number"
                step="5000"
                placeholder="Ví dụ: 40000"
                value={customBonus}
                onChange={(e) => setCustomBonus(e.target.value)}
                style={{
                  flex: 1,
                  padding: '0.45rem 0.75rem',
                  borderRadius: 6,
                  border: '1px solid #cbd5e1',
                  fontSize: '0.85rem',
                }}
              />
            </div>

            {/* Preview thù lao sau khi cộng phụ cấp */}
            <div
              style={{
                marginTop: '0.6rem',
                padding: '0.65rem 0.85rem',
                backgroundColor: '#f8fafc',
                borderRadius: 8,
                border: '1px solid #e2e8f0',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}
            >
              <span style={{ fontSize: '0.825rem', color: '#475569' }}>
                Thù lao hiển thị tới Worker:
              </span>
              <span style={{ fontSize: '1rem', fontWeight: 800, color: '#dc2626' }}>
                {formatCurrency(totalHourlyRate)} đ/giờ{' '}
                <span style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 400 }}>
                  ({formatCurrency(currentWage)} + {formatCurrency(activeBonus)})
                </span>
              </span>
            </div>
          </div>

          {/* 3. Mở rộng bán kính Radar */}
          <div>
            <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 700, color: '#1e293b', marginBottom: '0.4rem' }}>
              Bán Kính Quét Radar Khẩn Cấp
            </label>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '0.5rem' }}>
              {[
                { km: 15, label: '15 km (Mặc định)' },
                { km: 30, label: '30 km (Đề xuất)' },
                { km: 50, label: '50 km (Toàn TP)' },
              ].map((opt) => (
                <button
                  key={opt.km}
                  type="button"
                  onClick={() => setExpandedRadiusKm(opt.km)}
                  style={{
                    padding: '0.6rem 0.35rem',
                    borderRadius: 8,
                    border: expandedRadiusKm === opt.km ? '2px solid #2563eb' : '1px solid #cbd5e1',
                    background: expandedRadiusKm === opt.km ? '#eff6ff' : '#ffffff',
                    color: expandedRadiusKm === opt.km ? '#1d4ed8' : '#334155',
                    fontWeight: expandedRadiusKm === opt.km ? 700 : 500,
                    fontSize: '0.8rem',
                    cursor: 'pointer',
                  }}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* Quy chuẩn Section 11 & Cam kết */}
          <div
            style={{
              padding: '0.75rem',
              borderRadius: 8,
              backgroundColor: '#f1f5f9',
              fontSize: '0.775rem',
              color: '#475569',
              lineHeight: 1.5,
            }}
          >
            <strong>Cơ chế phân phối khẩn cấp:</strong> Hệ thống CANi sẽ kích hoạt Priority Push Notification
            tới tất cả Worker trực tuyến có điểm tin cậy phù hợp. Ca sẽ tự động chốt ngay khi có ứng viên đầu tiên xác nhận.
            Thời gian mở cửa sổ cứu trợ khẩn cấp: <strong>15 phút</strong>.
          </div>

          {/* Action buttons */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem' }}>
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              style={{
                padding: '0.6rem 1.25rem',
                borderRadius: 8,
                border: '1px solid #cbd5e1',
                backgroundColor: '#ffffff',
                color: '#475569',
                fontWeight: 600,
                fontSize: '0.85rem',
                cursor: 'pointer',
              }}
            >
              Hủy Bỏ
            </button>

            <button
              type="submit"
              disabled={isSubmitting}
              style={{
                padding: '0.6rem 1.4rem',
                borderRadius: 8,
                border: 'none',
                backgroundColor: '#dc2626',
                color: '#ffffff',
                fontWeight: 700,
                fontSize: '0.85rem',
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.5rem',
                boxShadow: '0 4px 12px rgba(220, 38, 38, 0.3)',
              }}
            >
              {isSubmitting ? 'Đang Phát Sóng...' : 'Phát Sóng Khẩn Cấp (15 Phút)'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
