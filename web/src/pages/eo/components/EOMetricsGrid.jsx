export default function EOMetricsGrid({ metrics }) {
  const { totalEvents, totalRequiredWorkers, totalConfirmedWorkers, totalEstimatedCost, fillRate } = metrics || {};

  const safeEvents = totalEvents ?? 0;
  const safeRequired = totalRequiredWorkers ?? 0;
  const safeConfirmed = totalConfirmedWorkers ?? 0;
  const safeCost = totalEstimatedCost ?? 0;
  const safeFillRate = fillRate ?? 0;

  return (
    <section className="eo-metrics-grid">
      {/* 1. Tổng sự kiện */}
      <div className="eo-metric-card-modern">
        <div className="metric-card-top">
          <div className="metric-icon-box blue">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
              <line x1="16" y1="2" x2="16" y2="6"></line>
              <line x1="8" y1="2" x2="8" y2="6"></line>
              <line x1="3" y1="10" x2="21" y2="10"></line>
            </svg>
          </div>
          <div className="metric-meta-group">
            <span className="metric-title-text">Tổng sự kiện</span>
            <div className="metric-data-row">
              <span className="metric-big-number">{safeEvents}</span>
              {safeEvents > 0 && (
                <span className="metric-trend-pill green">Đang hoạt động</span>
              )}
            </div>
          </div>
        </div>
        <div className="metric-bottom-sub">Sự kiện trên hệ thống</div>
      </div>

      {/* 2. Nhân sự cần tuyển */}
      <div className="eo-metric-card-modern">
        <div className="metric-card-top">
          <div className="metric-icon-box light-blue">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
              <circle cx="9" cy="7" r="4"></circle>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
            </svg>
          </div>
          <div className="metric-meta-group">
            <span className="metric-title-text">Nhân sự cần tuyển</span>
            <div className="metric-data-row">
              <span className="metric-big-number">{safeRequired}</span>
              {safeRequired > 0 && (
                <span className="metric-trend-pill green">Đang mở tuyển</span>
              )}
            </div>
          </div>
        </div>
        <div className="metric-bottom-sub">Tổng nhu cầu các ca</div>
      </div>

      {/* 3. Nhân sự đã chốt ca */}
      <div className="eo-metric-card-modern">
        <div className="metric-card-top">
          <div className="metric-icon-box emerald">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
              <polyline points="22 4 12 14.01 9 11.01"></polyline>
            </svg>
          </div>
          <div className="metric-meta-group">
            <span className="metric-title-text">Nhân sự đã chốt ca</span>
            <div className="metric-data-row">
              <span className="metric-big-number">{safeConfirmed}</span>
              <span className="metric-trend-pill neutral">{safeFillRate}%</span>
            </div>
          </div>
        </div>
        <div className="metric-bottom-sub">Tỷ lệ lấp đầy: {safeFillRate}%</div>
      </div>

      {/* 4. Dự toán chi phí */}
      <div className="eo-metric-card-modern">
        <div className="metric-card-top">
          <div className="metric-icon-box orange">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="2" y="4" width="20" height="16" rx="2"></rect>
              <line x1="2" y1="10" x2="22" y2="10"></line>
            </svg>
          </div>
          <div className="metric-meta-group">
            <span className="metric-title-text">Dự toán chi phí</span>
            <div className="metric-data-row">
              <span className="metric-big-number text-accent-green">
                {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(safeCost)}
              </span>
            </div>
          </div>
        </div>
        <div className="metric-bottom-sub">Thanh toán trực tiếp sau ca</div>
      </div>
    </section>
  );
}
