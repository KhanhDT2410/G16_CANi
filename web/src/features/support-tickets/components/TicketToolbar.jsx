export const TicketToolbar = ({ activeFilter, onFilterChange, searchTerm, onSearchChange, stats }) => {
  return (
    <div className="tickets-toolbar">
      <div className="tickets-tabs">
        <button
          type="button"
          className={`tickets-tab-btn ${activeFilter === 'ALL' ? 'active' : ''}`}
          onClick={() => onFilterChange('ALL')}
        >
          Tất Cả ({stats.total})
        </button>
        <button
          type="button"
          className={`tickets-tab-btn ${activeFilter === 'MINE' ? 'active' : ''}`}
          onClick={() => onFilterChange('MINE')}
        >
          Được Giao Cho Tôi ({stats.myCount || 0})
        </button>
        <button
          type="button"
          className={`tickets-tab-btn ${activeFilter === 'OPEN' ? 'active' : ''}`}
          onClick={() => onFilterChange('OPEN')}
        >
          Chờ Tiếp Nhận ({stats.openCount})
        </button>
        <button
          type="button"
          className={`tickets-tab-btn ${activeFilter === 'IN_PROGRESS' ? 'active' : ''}`}
          onClick={() => onFilterChange('IN_PROGRESS')}
        >
          Đang Xử Lý ({stats.inProgressCount})
        </button>
        <button
          type="button"
          className={`tickets-tab-btn ${activeFilter === 'SLA_RISK' ? 'active' : ''}`}
          onClick={() => onFilterChange('SLA_RISK')}
        >
          SLA Nguy Cơ ({stats.riskCount})
        </button>
      </div>

      <div className="tickets-search-box">
        <input
          type="text"
          className="tickets-search-input"
          placeholder="Tìm theo mã phiếu hoặc tiêu đề..."
          value={searchTerm}
          onChange={(e) => onSearchChange(e.target.value)}
        />
      </div>
    </div>
  );
};

export default TicketToolbar;
