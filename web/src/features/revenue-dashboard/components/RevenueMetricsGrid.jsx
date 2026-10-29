/**
 * 4 Thẻ KPI Chỉ số Doanh thu & Tăng trưởng (W-34)
 * Thiết kế chuẩn DESIGN.md: Bo góc 16px, Top Accent Line Gradient, Typography Be Vietnam Pro.
 */
const RevenueMetricsGrid = ({ metrics, loading }) => {
  if (loading || !metrics) {
    return (
      <div className="rev-metrics-grid">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="rev-metric-card skeleton-card">
            <div className="rev-skeleton-line short" />
            <div className="rev-skeleton-line large" />
            <div className="rev-skeleton-line medium" />
          </div>
        ))}
      </div>
    );
  }

  const {
    totalRevenue,
    growthRate,
    totalTransactions,
    matchingRevenue,
    matchingPercentage,
    inviteOnlyRevenue,
    invitePercentage,
    vnpayHealthScore,
    flaggedTransactionsCount,
  } = metrics;

  return (
    <div className="rev-metrics-grid">
      {/* 1. Tổng doanh thu gói dịch vụ */}
      <div className="rev-metric-card accent-brand">
        <div className="rev-card-header">
          <span className="rev-card-title">Tổng Doanh Thu Bán Gói</span>
          <div className="rev-icon-squircle brand">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
            </svg>
          </div>
        </div>
        <div className="rev-card-value">
          {totalRevenue.toLocaleString('vi-VN')} <span className="rev-currency">VNĐ</span>
        </div>
        <div className="rev-card-footer">
          <span className={`rev-badge-trend ${growthRate >= 0 ? 'up' : 'down'}`}>
            {growthRate >= 0 ? '↑ +' : '↓ '}{Math.abs(growthRate)}%
          </span>
          <span className="rev-subtext">so với kỳ trước ({totalTransactions.toLocaleString()} giao dịch)</span>
        </div>
      </div>

      {/* 2. Doanh thu gói Matching + Invite Mode */}
      <div className="rev-metric-card accent-matching">
        <div className="rev-card-header">
          <span className="rev-card-title">Gói Matching + Invite (80k)</span>
          <div className="rev-icon-squircle matching">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
              <path d="M16 3.13a4 4 0 0 1 0 7.75" />
            </svg>
          </div>
        </div>
        <div className="rev-card-value text-blue">
          {matchingRevenue.toLocaleString('vi-VN')} <span className="rev-currency">VNĐ</span>
        </div>
        <div className="rev-card-footer">
          <div className="rev-progress-bar-container">
            <div className="rev-progress-bar-fill matching" style={{ width: `${matchingPercentage}%` }} />
          </div>
          <span className="rev-subtext font-semibold">{matchingPercentage}% tổng doanh thu</span>
        </div>
      </div>

      {/* 3. Doanh thu gói Invite Mode */}
      <div className="rev-metric-card accent-invite">
        <div className="rev-card-header">
          <span className="rev-card-title">Gói Invite Mode Thuần (50k)</span>
          <div className="rev-icon-squircle invite">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M22 17H2a3 3 0 0 0 3-3V9a7 7 0 0 1 14 0v5a3 3 0 0 0 3 3zm-8.27 4a2 2 0 0 1-3.46 0" />
            </svg>
          </div>
        </div>
        <div className="rev-card-value text-cyan">
          {inviteOnlyRevenue.toLocaleString('vi-VN')} <span className="rev-currency">VNĐ</span>
        </div>
        <div className="rev-card-footer">
          <div className="rev-progress-bar-container">
            <div className="rev-progress-bar-fill invite" style={{ width: `${invitePercentage}%` }} />
          </div>
          <span className="rev-subtext font-semibold">{invitePercentage}% tổng doanh thu</span>
        </div>
      </div>

      {/* 4. Tình trạng giao dịch VNPay */}
      <div className="rev-metric-card accent-vnpay">
        <div className="rev-card-header">
          <span className="rev-card-title">Cổng Thanh Toán VNPay</span>
          <div className="rev-icon-squircle vnpay">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="1" y="4" width="22" height="16" rx="2" ry="2" />
              <line x1="1" y1="10" x2="23" y2="10" />
            </svg>
          </div>
        </div>
        <div className="rev-card-value text-success">
          {vnpayHealthScore}% <span className="rev-unit-label">Thành công</span>
        </div>
        <div className="rev-card-footer">
          {flaggedTransactionsCount > 0 ? (
            <span className="rev-badge-flagged">
              ⚠️ {flaggedTransactionsCount} đơn cần đối soát
            </span>
          ) : (
            <span className="rev-badge-healthy">✓ Toàn bộ đã khớp lệnh IPN</span>
          )}
          <span className="rev-subtext">Không giữ tiền ví / Non-Escrow</span>
        </div>
      </div>
    </div>
  );
};

export default RevenueMetricsGrid;
