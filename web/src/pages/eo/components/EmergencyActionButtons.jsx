import React from 'react';

/**
 * EmergencyActionButtons (Ticket EO-010 Tiêu chí 2)
 * Checkbox nhận thức hệ quả và bộ nút xác nhận rõ ràng: "Tôi hiểu và đồng ý kích hoạt" / "Hủy bỏ".
 */
export default function EmergencyActionButtons({
  isAcknowledged,
  onToggleAcknowledge,
  isBusy,
  onCancel,
}) {
  return (
    <>
      {/* Checkbox xác nhận nhận thức hệ quả */}
      <label
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          gap: '0.65rem',
          fontSize: '0.83rem',
          color: '#1e293b',
          cursor: 'pointer',
          userSelect: 'none',
          padding: '0.6rem 0.8rem',
          background: isAcknowledged ? '#f0fdf4' : '#f8fafc',
          border: `1.5px solid ${isAcknowledged ? '#86efac' : '#e2e8f0'}`,
          borderRadius: 8,
          transition: 'all 0.15s ease-in-out',
        }}
      >
        <input
          type="checkbox"
          checked={isAcknowledged}
          onChange={(e) => onToggleAcknowledge(e.target.checked)}
          disabled={isBusy}
          style={{ marginTop: '0.15rem', width: '16px', height: '16px', accentColor: '#dc2626', cursor: 'pointer' }}
        />
        <span style={{ lineHeight: 1.45 }}>
          Tôi đã đọc, hiểu rõ và <strong>đồng ý với các hệ quả</strong> khi mở rộng bán kính và nới lỏng điều kiện tìm kiếm khẩn cấp cho ca này.
        </span>
      </label>

      {/* Bộ nút xác nhận chuẩn EO-010 Tiêu chí 2: 'Tôi hiểu và đồng ý kích hoạt' / 'Hủy bỏ' */}
      <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: '0.75rem', marginTop: '0.2rem' }}>
        <button
          type="button"
          onClick={onCancel}
          disabled={isBusy}
          style={{
            minHeight: '44px',
            padding: '0 1.4rem',
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
          Hủy bỏ
        </button>

        <button
          type="submit"
          disabled={isBusy || !isAcknowledged}
          title={!isAcknowledged ? 'Vui lòng tích xác nhận đã hiểu hệ quả trước khi kích hoạt' : ''}
          style={{
            minHeight: '44px',
            padding: '0 1.6rem',
            borderRadius: '10px',
            border: 'none',
            background: isBusy || !isAcknowledged
              ? '#cbd5e1'
              : 'linear-gradient(135deg, #e11d48 0%, #dc2626 100%)',
            color: isBusy || !isAcknowledged ? '#64748b' : '#ffffff',
            fontWeight: 700,
            fontSize: '0.86rem',
            cursor: isBusy ? 'wait' : !isAcknowledged ? 'not-allowed' : 'pointer',
            boxShadow: isAcknowledged ? '0 4px 12px rgba(220, 38, 38, 0.28)' : 'none',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '8px',
            transition: 'all 0.16s ease-in-out',
          }}
        >
          {isBusy ? 'Đang kích hoạt...' : 'Tôi hiểu và đồng ý kích hoạt'}
        </button>
      </div>
    </>
  );
}
