import React from 'react';

/**
 * EmergencyShiftInfoCard
 * Khối tóm tắt thông tin ca làm việc và số lượng nhân sự đang thiếu.
 */
export default function EmergencyShiftInfoCard({ shift, event, summary, wageFormatted }) {
  return (
    <div
      style={{
        background: '#fff1f2',
        border: '1px solid #fecdd3',
        borderRadius: 10,
        padding: '0.85rem 1rem',
        marginBottom: '1rem',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.3rem' }}>
        <span style={{ fontWeight: 800, color: '#9f1239', fontSize: '0.95rem' }}>
          {shift?.jobType} {event?.name ? `— ${event.name}` : ''}
        </span>
        <span
          style={{
            backgroundColor: '#dc2626',
            color: '#ffffff',
            padding: '2px 8px',
            borderRadius: 99,
            fontSize: '0.74rem',
            fontWeight: 700,
          }}
        >
          Thiếu {summary?.missing} người
        </span>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.3rem', fontSize: '0.82rem', color: '#881337' }}>
        <div><strong>Bắt đầu:</strong> {summary?.startTimeText}</div>
        <div><strong>Thù lao:</strong> {wageFormatted}</div>
      </div>
    </div>
  );
}
