import React from 'react';

const RADIUS_OPTIONS = [
  { km: 15, label: '15 km', desc: 'Gần điểm tổ chức' },
  { km: 25, label: '25 km', desc: 'Mặc định chuẩn' },
  { km: 30, label: '30 km', desc: 'Mở rộng khu vực' },
  { km: 50, label: '50 km', desc: 'Toàn thành phố' },
];

/**
 * EmergencyRadiusSelector
 * Bộ nút chọn bán kính quét mở rộng, được thiết kế đều và đẹp mắt.
 */
export default function EmergencyRadiusSelector({ radiusKm, onChange, disabled }) {
  return (
    <div>
      <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 700, color: '#1e293b', marginBottom: '0.45rem' }}>
        Phạm vi bán kính quét tìm Worker:
      </label>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.55rem' }}>
        {RADIUS_OPTIONS.map((opt) => {
          const selected = Number(radiusKm) === opt.km;
          return (
            <button
              key={opt.km}
              type="button"
              disabled={disabled}
              onClick={() => onChange(opt.km)}
              style={{
                minHeight: '44px',
                padding: '0.55rem 0.75rem',
                borderRadius: 8,
                border: selected ? '2px solid #dc2626' : '1.5px solid #e2e8f0',
                background: selected ? '#fff1f2' : '#ffffff',
                color: selected ? '#9f1239' : '#334155',
                fontWeight: selected ? 700 : 500,
                cursor: disabled ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                transition: 'all 0.16s ease-in-out',
                textAlign: 'left',
              }}
            >
              <div>
                <div style={{ fontSize: '0.84rem', fontWeight: 700 }}>{opt.label}</div>
                <div style={{ fontSize: '0.72rem', color: selected ? '#be123c' : '#64748b' }}>{opt.desc}</div>
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}
