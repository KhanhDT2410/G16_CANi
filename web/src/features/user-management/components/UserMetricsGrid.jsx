/**
 * UserMetricsGrid.jsx
 * 4 thẻ KPI chỉ số người dùng chuẩn DESIGN.md (bo góc 16px, top accent line, gradient)
 */

export const UserMetricsGrid = ({ metrics, loading }) => {
  if (loading || !metrics) {
    return (
      <div className="um-metrics-grid">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="skeleton" style={{ height: 110, borderRadius: 16 }} />
        ))}
      </div>
    );
  }

  return (
    <div className="um-metrics-grid">
      {/* 1. Tổng Người Dùng */}
      <div className="um-metric-card animate-fade-in-up">
        <div className="um-metric-top">
          <span className="um-metric-icon" style={{ background: '#eff6ff', color: '#5B86E5' }}>
            👥
          </span>
          <span className="um-metric-tag positive">
            +{metrics.usersGrowth}%
          </span>
        </div>
        <div className="um-metric-val">
          {metrics.totalUsers?.toLocaleString('vi-VN')}
        </div>
        <div className="um-metric-title">Tổng Tài Khoản Đã Đăng Ký</div>
        <div className="um-metric-sub">Bao gồm Worker, EO, CSKH & Admin</div>
      </div>

      {/* 2. Worker & Tích Xanh */}
      <div className="um-metric-card animate-fade-in-up" style={{ animationDelay: '0.05s' }}>
        <div className="um-metric-top">
          <span className="um-metric-icon" style={{ background: '#ecfdf5', color: '#10b981' }}>
            💼
          </span>
          <span className="um-metric-tag verified">
            ✓ {metrics.workerVerifiedPercent}% eKYC
          </span>
        </div>
        <div className="um-metric-val" style={{ color: '#059669' }}>
          {metrics.activeWorkers?.toLocaleString('vi-VN')}
        </div>
        <div className="um-metric-title">Nhân Sự Sự Kiện (Worker)</div>
        <div className="um-metric-sub">Đã định danh CCCD & sẵn sàng nhận ca</div>
      </div>

      {/* 3. Nhà Tổ Chức (EO) */}
      <div className="um-metric-card animate-fade-in-up" style={{ animationDelay: '0.1s' }}>
        <div className="um-metric-top">
          <span className="um-metric-icon" style={{ background: '#f0f9ff', color: '#0284c7' }}>
            🏢
          </span>
          <span className="um-metric-tag positive">
            +{metrics.eoGrowth}%
          </span>
        </div>
        <div className="um-metric-val" style={{ color: '#0284c7' }}>
          {metrics.activeEOs?.toLocaleString('vi-VN')}
        </div>
        <div className="um-metric-title">Nhà Tổ Chức (Event Organizers)</div>
        <div className="um-metric-sub">Doanh nghiệp & Agency điều phối</div>
      </div>

      {/* 4. Giám Sát & Hạn Chế */}
      <div className="um-metric-card animate-fade-in-up" style={{ animationDelay: '0.15s' }}>
        <div className="um-metric-top">
          <span className="um-metric-icon" style={{ background: '#fef2f2', color: '#ef4444' }}>
            🛡️
          </span>
          <span className="um-metric-tag attention">
            Cần lưu ý
          </span>
        </div>
        <div className="um-metric-val" style={{ color: '#dc2626' }}>
          {metrics.flaggedOrRestricted}
        </div>
        <div className="um-metric-title">Tạm Khóa / AI Flags / Vi Phạm</div>
        <div className="um-metric-sub">Đang bị hạn chế hoặc có rủi ro gian lận</div>
      </div>
    </div>
  );
};

export default UserMetricsGrid;
