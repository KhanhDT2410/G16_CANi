import React from 'react';

export const CustomerCarePage: React.FC = () => {
  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', fontFamily: 'inherit' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <div>
          <h2 style={{ fontSize: '1.5rem', fontWeight: 'bold', color: '#111827', margin: 0 }}>
            🎧 Customer Care & Dispute Center
          </h2>
          <p style={{ color: '#6b7280', margin: '0.25rem 0 0 0', fontSize: '0.9rem' }}>
            Tiếp nhận khiếu nại ca làm việc, hỗ trợ Worker & Event Organizer, và giải quyết tranh chấp hợp đồng.
          </p>
        </div>
        <span style={{
          backgroundColor: '#eff6ff',
          color: '#1d4ed8',
          padding: '0.4rem 0.8rem',
          borderRadius: '9999px',
          fontSize: '0.8rem',
          fontWeight: '600'
        }}>
          Role: CUSTOMER_CARE
        </span>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '1rem', marginBottom: '2rem' }}>
        <div style={{ padding: '1.25rem', backgroundColor: '#fff', borderRadius: '0.75rem', border: '1px solid #e5e7eb' }}>
          <div style={{ color: '#6b7280', fontSize: '0.85rem' }}>Khiếu nại ca mới (SUBMITTED)</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 'bold', color: '#dc2626', marginTop: '0.5rem' }}>7</div>
          <div style={{ color: '#6b7280', fontSize: '0.8rem', marginTop: '0.25rem' }}>Cần xử lý trong 2h</div>
        </div>

        <div style={{ padding: '1.25rem', backgroundColor: '#fff', borderRadius: '0.75rem', border: '1px solid #e5e7eb' }}>
          <div style={{ color: '#6b7280', fontSize: '0.85rem' }}>Đang điều tra (IN_REVIEW)</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 'bold', color: '#d97706', marginTop: '0.5rem' }}>12</div>
          <div style={{ color: '#6b7280', fontSize: '0.8rem', marginTop: '0.25rem' }}>Liên hệ cả 2 bên</div>
        </div>

        <div style={{ padding: '1.25rem', backgroundColor: '#fff', borderRadius: '0.75rem', border: '1px solid #e5e7eb' }}>
          <div style={{ color: '#6b7280', fontSize: '0.85rem' }}>Đã giải quyết tuần này</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 'bold', color: '#16a34a', marginTop: '0.5rem' }}>45</div>
          <div style={{ color: '#16a34a', fontSize: '0.8rem', marginTop: '0.25rem' }}>Tỷ lệ hài lòng 96%</div>
        </div>
      </div>

      <div style={{ backgroundColor: '#fff', borderRadius: '0.75rem', border: '1px solid #e5e7eb', padding: '1.5rem' }}>
        <h3 style={{ fontSize: '1.1rem', fontWeight: '600', marginBottom: '1rem', color: '#1f2937' }}>
          Danh sách yêu cầu khiếu nại & tranh chấp chờ xử lý
        </h3>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
          <thead>
            <tr style={{ backgroundColor: '#f9fafb', borderBottom: '1px solid #e5e7eb', textAlign: 'left', color: '#4b5563' }}>
              <th style={{ padding: '0.75rem 1rem' }}>Mã khiếu nại</th>
              <th style={{ padding: '0.75rem 1rem' }}>Người gửi</th>
              <th style={{ padding: '0.75rem 1rem' }}>Loại vi phạm</th>
              <th style={{ padding: '0.75rem 1rem' }}>Sự kiện / Ca</th>
              <th style={{ padding: '0.75rem 1rem' }}>Trạng thái</th>
              <th style={{ padding: '0.75rem 1rem' }}>Hành động</th>
            </tr>
          </thead>
          <tbody>
            <tr style={{ borderBottom: '1px solid #f3f4f6' }}>
              <td style={{ padding: '0.75rem 1rem', fontFamily: 'monospace' }}>DISP-9021</td>
              <td style={{ padding: '0.75rem 1rem', fontWeight: '500' }}>Nguyễn Văn Worker</td>
              <td style={{ padding: '0.75rem 1rem' }}><span style={{ color: '#dc2626' }}>PAYMENT (Sai thù lao)</span></td>
              <td style={{ padding: '0.75rem 1rem' }}>Lễ hội âm nhạc Summer Beat</td>
              <td style={{ padding: '0.75rem 1rem', color: '#d97706' }}>● IN_REVIEW</td>
              <td style={{ padding: '0.75rem 1rem' }}>
                <button style={{ padding: '0.3rem 0.6rem', backgroundColor: '#3b82f6', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '0.8rem' }}>
                  Xử lý
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default CustomerCarePage;
