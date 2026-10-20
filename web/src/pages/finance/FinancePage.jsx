import React from 'react';

const FinancePage = () => {
  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', fontFamily: 'inherit' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <div>
          <h2 style={{ fontSize: '1.5rem', fontWeight: 'bold', color: '#111827', margin: 0 }}>
            💰 Finance Operations Dashboard (Phòng Tài Chính)
          </h2>
          <p style={{ color: '#6b7280', margin: '0.25rem 0 0 0', fontSize: '0.9rem' }}>
            Theo dõi dòng tiền gói cước Subscription (VNPay), đối soát hóa đơn và giải quyết thanh toán khiếu nại.
          </p>
        </div>
        <span style={{
          backgroundColor: '#ecfdf5',
          color: '#059669',
          padding: '0.4rem 0.8rem',
          borderRadius: '9999px',
          fontSize: '0.8rem',
          fontWeight: '600'
        }}>
          Role: FINANCE_OFFICER
        </span>
      </div>

      {/* Metric Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '1rem', marginBottom: '2rem' }}>
        <div style={{ padding: '1.25rem', backgroundColor: '#fff', borderRadius: '0.75rem', border: '1px solid #e5e7eb', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
          <div style={{ color: '#6b7280', fontSize: '0.85rem', fontWeight: '500' }}>Doanh thu Subscription tháng này</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 'bold', color: '#2563eb', marginTop: '0.5rem' }}>148,500,000 đ</div>
          <div style={{ color: '#16a34a', fontSize: '0.8rem', marginTop: '0.25rem' }}>↑ +18.4% so với tháng trước</div>
        </div>

        <div style={{ padding: '1.25rem', backgroundColor: '#fff', borderRadius: '0.75rem', border: '1px solid #e5e7eb', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
          <div style={{ color: '#6b7280', fontSize: '0.85rem', fontWeight: '500' }}>Giao dịch VNPay thành công</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 'bold', color: '#059669', marginTop: '0.5rem' }}>312</div>
          <div style={{ color: '#6b7280', fontSize: '0.8rem', marginTop: '0.25rem' }}>Tỷ lệ thanh toán thành công 99.4%</div>
        </div>

        <div style={{ padding: '1.25rem', backgroundColor: '#fff', borderRadius: '0.75rem', border: '1px solid #e5e7eb', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
          <div style={{ color: '#6b7280', fontSize: '0.85rem', fontWeight: '500' }}>Khiếu nại thù lao đang chờ xử lý</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 'bold', color: '#d97706', marginTop: '0.5rem' }}>4</div>
          <div style={{ color: '#d97706', fontSize: '0.8rem', marginTop: '0.25rem' }}>Escalated từ CSKH</div>
        </div>

        <div style={{ padding: '1.25rem', backgroundColor: '#fff', borderRadius: '0.75rem', border: '1px solid #e5e7eb', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
          <div style={{ color: '#6b7280', fontSize: '0.85rem', fontWeight: '500' }}>Đối soát ngân hàng T+1</div>
          <div style={{ fontSize: '1.25rem', fontWeight: 'bold', color: '#16a34a', marginTop: '0.75rem' }}>Đã khớp 100%</div>
          <div style={{ color: '#6b7280', fontSize: '0.8rem', marginTop: '0.25rem' }}>Cập nhật lần cuối: 06:00 AM</div>
        </div>
      </div>

      {/* Transaction Table */}
      <div style={{ backgroundColor: '#fff', borderRadius: '0.75rem', border: '1px solid #e5e7eb', padding: '1.5rem', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
        <h3 style={{ fontSize: '1.1rem', fontWeight: '600', marginBottom: '1rem', color: '#1f2937' }}>
          Nhật ký giao dịch gói cước gần nhất (Subscription Payments)
        </h3>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
          <thead>
            <tr style={{ backgroundColor: '#f9fafb', borderBottom: '1px solid #e5e7eb', textAlign: 'left', color: '#4b5563' }}>
              <th style={{ padding: '0.75rem 1rem' }}>Mã GD (VNPay)</th>
              <th style={{ padding: '0.75rem 1rem' }}>Doanh nghiệp (EO)</th>
              <th style={{ padding: '0.75rem 1rem' }}>Gói dịch vụ</th>
              <th style={{ padding: '0.75rem 1rem' }}>Số tiền</th>
              <th style={{ padding: '0.75rem 1rem' }}>Trạng thái</th>
              <th style={{ padding: '0.75rem 1rem' }}>Thời gian</th>
            </tr>
          </thead>
          <tbody>
            <tr style={{ borderBottom: '1px solid #f3f4f6' }}>
              <td style={{ padding: '0.75rem 1rem', fontFamily: 'monospace', color: '#2563eb' }}>VNP-20261019-8812</td>
              <td style={{ padding: '0.75rem 1rem', fontWeight: '500' }}>Công ty CP Sự Kiện RedSun</td>
              <td style={{ padding: '0.75rem 1rem' }}><span style={{ backgroundColor: '#e0e7ff', color: '#3730a3', padding: '0.2rem 0.5rem', borderRadius: '4px', fontSize: '0.8rem' }}>MATCHING_PLUS</span></td>
              <td style={{ padding: '0.75rem 1rem', fontWeight: '600' }}>4,990,000 đ</td>
              <td style={{ padding: '0.75rem 1rem', color: '#16a34a' }}>● Thành công</td>
              <td style={{ padding: '0.75rem 1rem', color: '#6b7280' }}>19/10/2026 10:15</td>
            </tr>
            <tr style={{ borderBottom: '1px solid #f3f4f6' }}>
              <td style={{ padding: '0.75rem 1rem', fontFamily: 'monospace', color: '#2563eb' }}>VNP-20261018-7741</td>
              <td style={{ padding: '0.75rem 1rem', fontWeight: '500' }}>Tập đoàn Tân Thời Đại</td>
              <td style={{ padding: '0.75rem 1rem' }}><span style={{ backgroundColor: '#f3f4f6', color: '#374151', padding: '0.2rem 0.5rem', borderRadius: '4px', fontSize: '0.8rem' }}>INVITE_ONLY</span></td>
              <td style={{ padding: '0.75rem 1rem', fontWeight: '600' }}>1,500,000 đ</td>
              <td style={{ padding: '0.75rem 1rem', color: '#16a34a' }}>● Thành công</td>
              <td style={{ padding: '0.75rem 1rem', color: '#6b7280' }}>18/10/2026 16:42</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default FinancePage;
