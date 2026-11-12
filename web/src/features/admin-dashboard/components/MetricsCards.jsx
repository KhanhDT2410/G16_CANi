/**
 * Thẻ hiển thị các khối chỉ số vận hành & tài chính nền tảng (User, Revenue, Verifications, Operations).
 * Tối ưu hóa màu sắc trang nhã, sắc nét, chuẩn doanh nghiệp (Executive Slate Palette).
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
const formatVnd = (amount) => {
  if (amount === null || amount === undefined) return '0 ₫';
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(amount);
};

export const MetricsCards = ({ metrics, onNavigateTab }) => {
  if (!metrics) return null;
  const { users = {}, verifications = {}, operations = {}, revenue = {} } = metrics;

  return (
    <div className="dashboard-metrics-grid">
      {/* 1. Thống kê Người dùng */}
      <div className="dashboard-metric-section">
        <div className="metric-section-header">
          <div className="metric-section-title"><span>Người Dùng Nền Tảng</span></div>
          <span className="metric-section-badge">Tài khoản</span>
        </div>
        <div className="metric-kpi-main">
          <div>
            <div className="metric-kpi-val">{users.totalUsers?.toLocaleString('vi-VN') || 0}</div>
            <div className="metric-kpi-sub">Tổng tài khoản đã đăng ký</div>
          </div>
          {onNavigateTab && (
            <button type="button" className="metric-navigate-btn" onClick={() => onNavigateTab('users')} title="Đi đến Quản trị người dùng">
              <span>Quản lý ↗</span>
            </button>
          )}
        </div>
        <div className="metric-sub-items">
          <div className="metric-sub-item"><span className="metric-sub-label">Người lao động</span><span className="metric-sub-val">{users.workerCount?.toLocaleString('vi-VN') || 0}</span></div>
          <div className="metric-sub-item"><span className="metric-sub-label">Nhà tổ chức sự kiện</span><span className="metric-sub-val">{users.eventOrganizerCount?.toLocaleString('vi-VN') || 0}</span></div>
          <div className="metric-sub-item"><span className="metric-sub-label">Nhân sự vận hành</span><span className="metric-sub-val">{(users.customerCareCount || 0) + (users.adminCount || 0)}</span></div>
          <div className="metric-sub-item"><span className="metric-sub-label">Tài khoản bị khóa</span><span className="metric-sub-val">{users.lockedUsersCount || 0}</span></div>
        </div>
      </div>

      {/* 2. Thống kê Doanh thu & Tài chính */}
      <div className="dashboard-metric-section">
        <div className="metric-section-header">
          <div className="metric-section-title"><span>Doanh Thu & Dòng Tiền</span></div>
          <span className="metric-section-badge">Tài chính</span>
        </div>
        <div className="metric-kpi-main">
          <div>
            <div className="metric-kpi-val">{formatVnd(revenue.totalPackageRevenue)}</div>
            <div className="metric-kpi-sub">
              Doanh số gói dịch vụ
              {revenue.growthRate != null && (
                <span className={`rev-growth-inline ${revenue.growthRate >= 0 ? 'positive' : 'negative'}`}>
                  ({revenue.growthRate >= 0 ? '+' : ''}{Number(revenue.growthRate).toFixed(1)}%)
                </span>
              )}
            </div>
          </div>
          {onNavigateTab && (
            <button type="button" className="metric-navigate-btn" onClick={() => onNavigateTab('revenue')} title="Đi đến Báo cáo doanh thu & tài chính">
              <span>Xem chi tiết ↗</span>
            </button>
          )}
        </div>
        <div className="metric-sub-items">
          <div className="metric-sub-item"><span className="metric-sub-label">Gói đã thanh toán</span><span className="metric-sub-val">{revenue.totalPackageSales?.toLocaleString('vi-VN') || 0} gói</span></div>
          <div className="metric-sub-item"><span className="metric-sub-label">Khối lượng thù lao</span><span className="metric-sub-val">{formatVnd(revenue.totalPaymentVolume)}</span></div>
          <div className="metric-sub-item" style={{ gridColumn: 'span 2' }}>
            <span className="metric-sub-label">Tổng dòng tiền nền tảng</span>
            <span className="metric-sub-val">{formatVnd(revenue.combinedVolume || ((revenue.totalPackageRevenue || 0) + (revenue.totalPaymentVolume || 0)))}</span>
          </div>
        </div>
      </div>

      {/* 3. Thống kê Thẩm định chờ xử lý */}
      <div className="dashboard-metric-section">
        <div className="metric-section-header">
          <div className="metric-section-title"><span>Hàng Đợi Chờ Thẩm Định</span></div>
          <span className="metric-section-badge">Cần xử lý</span>
        </div>
        <div className="metric-kpi-main">
          <div>
            <div className="metric-kpi-val">{verifications.totalPendingCount?.toLocaleString('vi-VN') || 0}</div>
            <div className="metric-kpi-sub">Tổng hồ sơ đang chờ xét duyệt</div>
          </div>
        </div>
        <div className="metric-sub-items">
          <div className="metric-sub-item"><span className="metric-sub-label">Căn cước người lao động</span><span className="metric-sub-val">{verifications.pendingWorkerEkycCount || 0}</span></div>
          <div className="metric-sub-item"><span className="metric-sub-label">Thông tin doanh nghiệp</span><span className="metric-sub-val">{verifications.pendingEoEkycCount || 0}</span></div>
          <div className="metric-sub-item" style={{ gridColumn: 'span 2' }}>
            <span className="metric-sub-label">Chứng chỉ kỹ năng tay nghề</span>
            <span className="metric-sub-val">{verifications.pendingSkillVerificationsCount || 0}</span>
          </div>
        </div>
      </div>

      {/* 4. Thống kê Vận hành Sự kiện & Ca làm việc */}
      <div className="dashboard-metric-section">
        <div className="metric-section-header">
          <div className="metric-section-title"><span>Sự Kiện & Ca Làm Việc</span></div>
          <span className="metric-section-badge">Vận hành</span>
        </div>
        <div className="metric-kpi-main">
          <div>
            <div className="metric-kpi-val">{operations.activeEventsCount?.toLocaleString('vi-VN') || 0}</div>
            <div className="metric-kpi-sub">Sự kiện đang diễn ra</div>
          </div>
        </div>
        <div className="metric-sub-items">
          <div className="metric-sub-item"><span className="metric-sub-label">Tổng sự kiện</span><span className="metric-sub-val">{operations.totalEventsCount?.toLocaleString('vi-VN') || 0}</span></div>
          <div className="metric-sub-item"><span className="metric-sub-label">Ca đang mở tuyển</span><span className="metric-sub-val">{operations.activeShiftsCount?.toLocaleString('vi-VN') || 0}</span></div>
          <div className="metric-sub-item" style={{ gridColumn: 'span 2' }}>
            <span className="metric-sub-label">Tổng số ca làm việc</span>
            <span className="metric-sub-val">{operations.totalShiftsCount?.toLocaleString('vi-VN') || 0}</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default MetricsCards;
