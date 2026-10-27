import { DATE_RANGES, PACKAGE_TYPES, VNPAY_STATUS } from '../types/revenueDashboard.constants';

/**
 * Thanh điều khiển bộ lọc & Thao tác báo cáo doanh thu (W-34)
 */
const RevenueFilterBar = ({
  selectedPeriod,
  onPeriodChange,
  packageFilter,
  onPackageFilterChange,
  statusFilter,
  onStatusFilterChange,
  searchQuery,
  onSearchChange,
  onExportCsv,
  onRefresh,
  totalResults,
}) => {
  return (
    <div className="rev-filter-bar">
      {/* 1. Hàng chọn chu kỳ thời gian (Pill Segmented Control) */}
      <div className="rev-period-pills">
        {DATE_RANGES.map((item) => (
          <button
            key={item.key}
            type="button"
            className={`rev-period-btn ${selectedPeriod === item.key ? 'active' : ''}`}
            onClick={() => onPeriodChange(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>

      {/* 2. Hàng bộ lọc chi tiết & Tìm kiếm */}
      <div className="rev-controls-row">
        <div className="rev-filter-group">
          {/* Lọc theo gói dịch vụ */}
          <select
            className="rev-select"
            value={packageFilter}
            onChange={(e) => onPackageFilterChange(e.target.value)}
          >
            <option value="ALL">📦 Tất cả gói dịch vụ</option>
            {Object.values(PACKAGE_TYPES).map((pkg) => (
              <option key={pkg.id} value={pkg.id}>
                {pkg.name} ({pkg.unitPriceLabel})
              </option>
            ))}
          </select>

          {/* Lọc theo trạng thái giao dịch */}
          <select
            className="rev-select"
            value={statusFilter}
            onChange={(e) => onStatusFilterChange(e.target.value)}
          >
            <option value="ALL">⚡ Tất cả trạng thái VNPay</option>
            {Object.values(VNPAY_STATUS).map((st) => (
              <option key={st.key} value={st.key}>
                {st.label}
              </option>
            ))}
          </select>

          {/* Ô tìm kiếm */}
          <div className="rev-search-box">
            <svg
              className="rev-search-icon"
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
            >
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
            <input
              type="text"
              className="rev-search-input"
              placeholder="Tìm theo mã đơn, mã VNPay, tên EO..."
              value={searchQuery}
              onChange={(e) => onSearchChange(e.target.value)}
            />
            {searchQuery && (
              <button
                type="button"
                className="rev-search-clear"
                onClick={() => onSearchChange('')}
              >
                &times;
              </button>
            )}
          </div>
        </div>

        {/* Nút tác vụ phải */}
        <div className="rev-actions-group">
          <span className="rev-count-badge">
            {totalResults} giao dịch
          </span>

          <button
            type="button"
            className="rev-btn-secondary"
            title="Tải lại dữ liệu"
            onClick={onRefresh}
          >
            🔄 Làm Mới
          </button>

          <button
            type="button"
            className="rev-btn-primary"
            onClick={onExportCsv}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="7 10 12 15 17 10" />
              <line x1="12" y1="15" x2="12" y2="3" />
            </svg>
            Xuất Báo Cáo CSV
          </button>
        </div>
      </div>
    </div>
  );
};

export default RevenueFilterBar;
