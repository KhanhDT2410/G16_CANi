export default function EOMetricsGrid({ metrics }) {
  const { totalEvents, totalRequiredWorkers, totalConfirmedWorkers, totalEstimatedCost, fillRate } = metrics;

  return (
    <section className="eo-metrics-grid">
      <div className="eo-metric-card">
        <div className="metric-header-row">
          <span className="eo-metric-label">Tổng Sự Kiện</span>
          <span className="metric-icon-badge blue">📅</span>
        </div>
        <div className="eo-metric-value">{totalEvents}</div>
        <div className="eo-metric-sub">Đang quản lý</div>
      </div>

      <div className="eo-metric-card">
        <div className="metric-header-row">
          <span className="eo-metric-label">Nhân Sự Cần Tuyển</span>
          <span className="metric-icon-badge cyan">👥</span>
        </div>
        <div className="eo-metric-value">{totalRequiredWorkers}</div>
        <div className="eo-metric-sub">Theo ma trận ca</div>
      </div>

      <div className="eo-metric-card">
        <div className="metric-header-row">
          <span className="eo-metric-label">Nhân Sự Đã Chốt Ca</span>
          <span className="metric-icon-badge green">✅</span>
        </div>
        <div className="metric-value-row">
          <div className="eo-metric-value text-accent-blue">{totalConfirmedWorkers}</div>
          <div className="metric-circular-progress" title={`Tỉ lệ lấp đầy ${fillRate}%`}>
            <svg width="40" height="40" viewBox="0 0 36 36">
              <path
                className="circle-bg"
                d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
              />
              <path
                className="circle-fill"
                strokeDasharray={`${fillRate}, 100`}
                d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
              />
              <text x="18" y="21" className="circle-percent">{fillRate}%</text>
            </svg>
          </div>
        </div>
        <div className="eo-metric-sub">Tỉ lệ lấp đầy: {fillRate}%</div>
      </div>

      <div className="eo-metric-card">
        <div className="metric-header-row">
          <span className="eo-metric-label">Dự Toán Chi Phí Lương</span>
          <span className="metric-icon-badge emerald">💳</span>
        </div>
        <div className="eo-metric-value text-emerald">
          {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(totalEstimatedCost)}
        </div>
        <div className="eo-metric-sub">Thanh toán trực tiếp sau ca</div>
      </div>
    </section>
  );
}
