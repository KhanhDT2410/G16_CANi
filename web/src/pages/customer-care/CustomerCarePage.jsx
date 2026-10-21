import React from 'react';
import './CustomerCarePage.css';

export const CustomerCarePage = () => {
  return (
    <div className="customer-care-container">
      <div className="cc-header">
        <div>
          <h2 className="cc-title">Customer Care & Dispute Center</h2>
          <p className="cc-subtitle">
            Tiếp nhận khiếu nại ca làm việc, hỗ trợ Worker & Event Organizer, và giải quyết tranh chấp hợp đồng.
          </p>
        </div>
        <span className="cc-role-badge">Role: CUSTOMER_CARE</span>
      </div>

      <div className="cc-stats-grid">
        <div className="cc-stat-card">
          <div className="cc-stat-label">Khiếu nại ca mới (SUBMITTED)</div>
          <div className="cc-stat-value danger">7</div>
          <div className="cc-stat-desc">Cần xử lý trong 2h</div>
        </div>

        <div className="cc-stat-card">
          <div className="cc-stat-label">Đang điều tra (IN_REVIEW)</div>
          <div className="cc-stat-value warning">12</div>
          <div className="cc-stat-desc">Liên hệ cả 2 bên</div>
        </div>

        <div className="cc-stat-card">
          <div className="cc-stat-label">Đã giải quyết tuần này</div>
          <div className="cc-stat-value success">45</div>
          <div className="cc-stat-desc success-text">Tỷ lệ hài lòng 96%</div>
        </div>
      </div>

      <div className="cc-table-section">
        <h3 className="cc-table-title">Danh sách yêu cầu khiếu nại & tranh chấp chờ xử lý</h3>
        <table className="cc-table">
          <thead>
            <tr>
              <th>Mã khiếu nại</th>
              <th>Người gửi</th>
              <th>Loại vi phạm</th>
              <th>Sự kiện / Ca</th>
              <th>Trạng thái</th>
              <th>Hành động</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td className="cc-code-cell">DISP-9021</td>
              <td className="cc-name-cell">Nguyễn Văn Worker</td>
              <td><span className="cc-violation-tag">PAYMENT (Sai thù lao)</span></td>
              <td>Lễ hội Âm nhạc Summer Beat</td>
              <td className="cc-status-in-review">IN_REVIEW</td>
              <td>
                <button type="button" className="cc-btn-action">
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
