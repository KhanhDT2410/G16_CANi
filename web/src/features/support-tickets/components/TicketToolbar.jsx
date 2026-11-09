const FILTER_LABELS = {
  ALL: 'Tất Cả',
  MINE: 'Được Giao Cho Tôi',
  OPEN: 'Chờ Tiếp Nhận',
  IN_PROGRESS: 'Đang Xử Lý',
  SLA_RISK: 'Sắp Quá Hạn SLA',
};

/**
 * Thanh công cụ tìm kiếm và thông tin bộ lọc cho Phiếu Hỗ Trợ (ADM-02).
 * Danh mục trạng thái đã được chuyển sang menu xổ xuống ở thanh Sidebar chính.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const TicketToolbar = ({ activeFilter = 'ALL', onFilterChange, searchTerm, onSearchChange, stats }) => {
  const currentCount =
    activeFilter === 'MINE'
      ? stats.myCount || 0
      : activeFilter === 'OPEN'
      ? stats.openCount
      : activeFilter === 'IN_PROGRESS'
      ? stats.inProgressCount
      : activeFilter === 'SLA_RISK'
      ? stats.riskCount
      : stats.total;

  return (
    <div className="tickets-toolbar">
      <div className="tickets-active-filter-pill">
        <span className="pill-prefix">Trạng thái:</span>
        <span className="pill-category-name">{FILTER_LABELS[activeFilter] || 'Tất Cả'}</span>
        <span className="pill-count-badge">{currentCount} phiếu</span>
      </div>

      <div className="tickets-search-box">
        <input
          type="text"
          className="tickets-search-input"
          placeholder="Tìm theo mã phiếu hoặc tiêu đề..."
          value={searchTerm}
          onChange={(e) => onSearchChange(e.target.value)}
        />
        {searchTerm && (
          <button
            type="button"
            className="tickets-search-clear"
            onClick={() => onSearchChange('')}
          >
            ✕
          </button>
        )}
      </div>
    </div>
  );
};

export default TicketToolbar;
