export default function EOMetricsGrid({ metrics, onOpenActiveEventDetail }) {
  const { totalEvents, activeEventsCount, totalRequiredWorkers, totalConfirmedWorkers, fillRate } = metrics || {};

  const safeEvents = totalEvents ?? 0;
  const safeActive = activeEventsCount ?? 0;
  const safeRequired = totalRequiredWorkers ?? 0;
  const safeConfirmed = totalConfirmedWorkers ?? 0;
  const safeFillRate = fillRate ?? 0;

  return (
    <section className="eo-metrics-grid">
      {/* 1. Tổng sự kiện */}
      <div className="eo-metric-card-modern">
        <div className="metric-card-top">
          <div className="metric-meta-group">
            <span className="metric-title-text">Tổng sự kiện</span>
            <div className="metric-data-row">
              <span className="metric-big-number">{safeEvents}</span>
              {safeActive > 0 && (
                <span className="metric-trend-pill green">{safeActive} Đang hoạt động</span>
              )}
            </div>
          </div>
        </div>
        {safeActive > 0 && onOpenActiveEventDetail ? (
          <button type="button" className="eo-metric-details" onClick={onOpenActiveEventDetail}>Xem sự kiện đang hoạt động →</button>
        ) : (
          <div className="metric-bottom-sub">Sự kiện trên hệ thống</div>
        )}
      </div>

      {/* 2. Nhân sự cần tuyển */}
      <div className="eo-metric-card-modern">
        <div className="metric-card-top">
          <div className="metric-meta-group">
            <span className="metric-title-text">Nhu cầu nhân sự</span>
            <div className="metric-data-row">
              <span className="metric-big-number">{safeRequired}</span>
            </div>
          </div>
        </div>
        <div className="metric-bottom-sub">Tổng nhu cầu các ca</div>
      </div>

      {/* 3. Nhân sự đã chốt ca */}
      <div className="eo-metric-card-modern">
        <div className="metric-card-top">
          <div className="metric-meta-group">
            <span className="metric-title-text">Nhân sự đã chốt ca</span>
            <div className="metric-data-row">
              <span className="metric-big-number">{safeConfirmed}</span>
            </div>
          </div>
        </div>
        <div className="metric-bottom-sub">Tỷ lệ lấp đầy: {safeFillRate}%</div>
      </div>
    </section>
  );
}
