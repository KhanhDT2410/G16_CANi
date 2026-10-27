import { useState } from 'react';
import { PACKAGE_TYPES } from '../types/revenueDashboard.constants';

/**
 * Biểu đồ phân tích doanh thu so sánh chu kỳ (Period-over-Period) & Cơ cấu gói
 * Vẽ SVG mượt mà không phụ thuộc thư viện bên ngoài nặng nề.
 */
const RevenueChartSection = ({ chartData = [], metrics }) => {
  const [hoveredPoint, setHoveredPoint] = useState(null);

  if (!chartData || chartData.length === 0) return null;

  // Tính toán kích thước SVG
  const width = 640;
  const height = 240;
  const padding = { top: 20, right: 30, bottom: 40, left: 60 };

  const maxVal = Math.max(
    ...chartData.map((d) => Math.max(d.current, d.previous || 0)),
    20000000
  );

  const getX = (index) => {
    return (
      padding.left +
      (index / (chartData.length - 1)) * (width - padding.left - padding.right)
    );
  };

  const getY = (val) => {
    return (
      height -
      padding.bottom -
      (val / maxVal) * (height - padding.top - padding.bottom)
    );
  };

  // Tạo đường dẫn SVG Path cho chu kỳ hiện tại và chu kỳ trước
  const currentPath = chartData
    .map((d, i) => `${i === 0 ? 'M' : 'L'} ${getX(i)} ${getY(d.current)}`)
    .join(' ');

  const previousPath = chartData
    .map((d, i) => `${i === 0 ? 'M' : 'L'} ${getX(i)} ${getY(d.previous || 0)}`)
    .join(' ');

  // Area fill cho chu kỳ hiện tại
  const areaPath = `${currentPath} L ${getX(chartData.length - 1)} ${height - padding.bottom} L ${getX(0)} ${height - padding.bottom} Z`;

  return (
    <div className="rev-chart-grid">
      {/* 1. Biểu đồ Period-over-Period Trend Line */}
      <div className="rev-chart-card">
        <div className="rev-chart-header">
          <div>
            <h3 className="rev-chart-title">Biểu Đồ Doanh Thu So Với Cùng Kỳ</h3>
            <p className="rev-chart-desc">
              Theo dõi biến động dòng tiền mua gói dịch vụ VNPay từng ngày trong tháng
            </p>
          </div>
          <div className="rev-chart-legend">
            <span className="legend-item current">
              <span className="legend-dot current" /> Kỳ này
            </span>
            <span className="legend-item previous">
              <span className="legend-dot previous" /> Cùng kỳ tháng trước
            </span>
          </div>
        </div>

        <div className="rev-svg-wrapper">
          <svg
            viewBox={`0 0 ${width} ${height}`}
            className="rev-svg-chart"
            preserveAspectRatio="none"
          >
            <defs>
              <linearGradient id="currentGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#36D1DC" stopOpacity="0.32" />
                <stop offset="100%" stopColor="#5B86E5" stopOpacity="0.0" />
              </linearGradient>
            </defs>

            {/* Trục kẻ ngang Grid lines */}
            {[0, 0.25, 0.5, 0.75, 1].map((pct, idx) => {
              const y = getY(maxVal * pct);
              return (
                <g key={idx}>
                  <line
                    x1={padding.left}
                    y1={y}
                    x2={width - padding.right}
                    y2={y}
                    stroke="#e2e8f0"
                    strokeDasharray="4 4"
                  />
                  <text
                    x={padding.left - 8}
                    y={y + 4}
                    fill="#94a3b8"
                    fontSize="10"
                    textAnchor="end"
                  >
                    {(Math.round((maxVal * pct) / 1000000)).toFixed(0)}M
                  </text>
                </g>
              );
            })}

            {/* Vùng đổ màu Gradient */}
            <path d={areaPath} fill="url(#currentGradient)" />

            {/* Đường kỳ trước (nét đứt) */}
            <path
              d={previousPath}
              fill="none"
              stroke="#cbd5e1"
              strokeWidth="2"
              strokeDasharray="4 4"
            />

            {/* Đường kỳ này (Brand Gradient Stroke) */}
            <path
              d={currentPath}
              fill="none"
              stroke="url(#brandGradientStroke)"
              strokeWidth="3.5"
              strokeLinecap="round"
            />

            <linearGradient id="brandGradientStroke" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%" stopColor="#5B86E5" />
              <stop offset="100%" stopColor="#36D1DC" />
            </linearGradient>

            {/* Điểm nút & Hover */}
            {chartData.map((d, i) => {
              const cx = getX(i);
              const cy = getY(d.current);
              return (
                <g key={i}>
                  <circle
                    cx={cx}
                    cy={cy}
                    r="4.5"
                    fill="#ffffff"
                    stroke="#5B86E5"
                    strokeWidth="2.5"
                    className="rev-chart-point"
                    onMouseEnter={() => setHoveredPoint({ ...d, x: cx, y: cy })}
                    onMouseLeave={() => setHoveredPoint(null)}
                  />
                  {/* Nhãn ngày tháng ở trục X */}
                  <text
                    x={cx}
                    y={height - padding.bottom + 18}
                    fill="#64748b"
                    fontSize="11"
                    textAnchor="middle"
                  >
                    {d.date}
                  </text>
                </g>
              );
            })}
          </svg>

          {/* Tooltip khi hover điểm dữ liệu */}
          {hoveredPoint && (
            <div
              className="rev-chart-tooltip"
              style={{ left: `${(hoveredPoint.x / width) * 100}%`, top: `${hoveredPoint.y - 45}px` }}
            >
              <div className="tooltip-date">Ngày {hoveredPoint.date}/2026</div>
              <div className="tooltip-value">
                <strong>{hoveredPoint.current.toLocaleString('vi-VN')} đ</strong>
              </div>
              <div className="tooltip-sub">
                Cùng kỳ: {(hoveredPoint.previous || 0).toLocaleString('vi-VN')} đ ({hoveredPoint.transactions} giao dịch)
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 2. Cơ cấu phân bổ doanh thu theo gói (Breakdown by Package) */}
      <div className="rev-breakdown-card">
        <h3 className="rev-chart-title">Cơ Cấu Doanh Thu Theo Gói</h3>
        <p className="rev-chart-desc">
          Tỷ trọng đóng góp từ các gói dịch vụ tổ chức sự kiện
        </p>

        <div className="rev-breakdown-list">
          {Object.values(PACKAGE_TYPES).map((pkg) => {
            const isMatching = pkg.id === 'MATCHING_INVITE';
            const isCombo10 = pkg.id === 'ENTERPRISE_10';
            const isCombo30 = pkg.id === 'ENTERPRISE_30';
            const percentage = isMatching ? 54.2 : isCombo10 ? 15.1 : isCombo30 ? 6.7 : 24.0;
            const revenueVal = Math.round(((metrics?.totalRevenue || 286400000) * percentage) / 100);

            return (
              <div key={pkg.id} className="rev-breakdown-item">
                <div className="rev-breakdown-header">
                  <div className="rev-pkg-name">
                    <span className="rev-pkg-dot" style={{ backgroundColor: pkg.color }} />
                    <strong>{pkg.name}</strong>
                  </div>
                  <span className="rev-pkg-val">{revenueVal.toLocaleString('vi-VN')} đ</span>
                </div>
                <div className="rev-breakdown-progress">
                  <div
                    className="rev-breakdown-fill"
                    style={{ width: `${percentage}%`, backgroundColor: pkg.color }}
                  />
                </div>
                <div className="rev-breakdown-sub">
                  <span>{pkg.unitPriceLabel}</span>
                  <span className="rev-pct-tag">{percentage}%</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

export default RevenueChartSection;
