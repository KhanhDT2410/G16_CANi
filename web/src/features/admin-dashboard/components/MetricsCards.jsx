import { UsersIcon, ClockAlertIcon, CalendarEventsIcon, ArrowRightIcon } from './Icons';

/**
 * Thẻ hiển thị các khối chỉ số vận hành nền tảng (User, Verifications, Operations)
 */
const MetricsCards = ({ metrics, onNavigateTab }) => {
  if (!metrics) return null;

  const { users = {}, verifications = {}, operations = {} } = metrics;

  return (
    <div className="dashboard-metrics-grid">
      {/* 1. Thống kê Người dùng */}
      <div className="dashboard-metric-section">
        <div className="metric-section-header">
          <div className="metric-section-title">
            <div className="metric-icon-box users">
              <UsersIcon size={18} />
            </div>
            <span>Người Dùng Nền Tảng</span>
          </div>
          <span className="metric-section-badge badge-blue">Tài khoản</span>
        </div>

        <div className="metric-kpi-main">
          <div>
            <div className="metric-kpi-val" style={{ color: '#2563eb' }}>
              {users.totalUsers?.toLocaleString('vi-VN') || 0}
            </div>
            <div className="metric-kpi-sub">Tổng tài khoản đã đăng ký</div>
          </div>
          {onNavigateTab && (
            <button
              type="button"
              className="metric-navigate-btn"
              onClick={() => onNavigateTab('users')}
            >
              <span>Quản lý</span>
              <ArrowRightIcon size={12} />
            </button>
          )}
        </div>

        <div className="metric-sub-items">
          <div className="metric-sub-item">
            <span className="metric-sub-label">Worker</span>
            <span className="metric-sub-val">{users.workerCount?.toLocaleString('vi-VN') || 0}</span>
          </div>
          <div className="metric-sub-item">
            <span className="metric-sub-label">Nhà tổ chức (EO)</span>
            <span className="metric-sub-val">{users.eventOrganizerCount?.toLocaleString('vi-VN') || 0}</span>
          </div>
          <div className="metric-sub-item">
            <span className="metric-sub-label">CSKH / Admin</span>
            <span className="metric-sub-val">
              {(users.customerCareCount || 0) + (users.adminCount || 0)}
            </span>
          </div>
          <div className="metric-sub-item">
            <span className="metric-sub-label" style={{ color: '#dc2626' }}>Đang bị khóa (Locked)</span>
            <span className="metric-sub-val" style={{ color: '#dc2626' }}>
              {users.lockedUsersCount || 0}
            </span>
          </div>
        </div>
      </div>

      {/* 2. Thống kê Thẩm định chờ xử lý */}
      <div className="dashboard-metric-section">
        <div className="metric-section-header">
          <div className="metric-section-title">
            <div className="metric-icon-box verifications">
              <ClockAlertIcon size={18} />
            </div>
            <span>Hàng Đợi Chờ Thẩm Định</span>
          </div>
          <span className="metric-section-badge badge-red">
            Cần xử lý
          </span>
        </div>

        <div className="metric-kpi-main">
          <div>
            <div className="metric-kpi-val" style={{ color: '#dc2626' }}>
              {verifications.totalPendingCount?.toLocaleString('vi-VN') || 0}
            </div>
            <div className="metric-kpi-sub">Tổng hồ sơ đang chờ Admin duyệt</div>
          </div>
        </div>

        <div className="metric-sub-items">
          <div className="metric-sub-item">
            <span className="metric-sub-label">eKYC Worker</span>
            <span className="metric-sub-val" style={{ color: '#dc2626' }}>
              {verifications.pendingWorkerEkycCount || 0}
            </span>
          </div>
          <div className="metric-sub-item">
            <span className="metric-sub-label">eKYC Doanh nghiệp</span>
            <span className="metric-sub-val" style={{ color: '#ea580c' }}>
              {verifications.pendingEoEkycCount || 0}
            </span>
          </div>
          <div className="metric-sub-item" style={{ gridColumn: 'span 2' }}>
            <span className="metric-sub-label">Chứng chỉ kỹ năng Worker (UC-47)</span>
            <span className="metric-sub-val" style={{ color: '#2563eb' }}>
              {verifications.pendingSkillVerificationsCount || 0}
            </span>
          </div>
        </div>
      </div>

      {/* 3. Thống kê Vận hành Sự kiện & Ca làm việc */}
      <div className="dashboard-metric-section">
        <div className="metric-section-header">
          <div className="metric-section-title">
            <div className="metric-icon-box operations">
              <CalendarEventsIcon size={18} />
            </div>
            <span>Sự Kiện & Ca Làm Việc</span>
          </div>
          <span className="metric-section-badge badge-green">
            Vận hành
          </span>
        </div>

        <div className="metric-kpi-main">
          <div>
            <div className="metric-kpi-val" style={{ color: '#059669' }}>
              {operations.activeEventsCount?.toLocaleString('vi-VN') || 0}
            </div>
            <div className="metric-kpi-sub">Sự kiện đang hoạt động (Active)</div>
          </div>
        </div>

        <div className="metric-sub-items">
          <div className="metric-sub-item">
            <span className="metric-sub-label">Tổng sự kiện</span>
            <span className="metric-sub-val">{operations.totalEventsCount?.toLocaleString('vi-VN') || 0}</span>
          </div>
          <div className="metric-sub-item">
            <span className="metric-sub-label">Ca đang mở tuyển</span>
            <span className="metric-sub-val" style={{ color: '#059669' }}>
              {operations.activeShiftsCount?.toLocaleString('vi-VN') || 0}
            </span>
          </div>
          <div className="metric-sub-item" style={{ gridColumn: 'span 2' }}>
            <span className="metric-sub-label">Tổng số ca làm việc (Shifts)</span>
            <span className="metric-sub-val">{operations.totalShiftsCount?.toLocaleString('vi-VN') || 0}</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default MetricsCards;
