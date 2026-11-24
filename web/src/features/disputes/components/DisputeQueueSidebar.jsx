import DisputeQueueCard from './DisputeQueueCard';

/**
 * Cột danh sách hàng đợi tranh chấp cho giao diện hội thoại Split-View (ADM-03).
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const DisputeQueueSidebar = ({
  queue = [],
  selectedId,
  onSelect,
  queueFilter,
  setQueueFilter,
  queueScope,
  setQueueScope,
  totalCount,
  resolvedCount = 0,
  myCount,
  riskCount,
  unreadCount = 0,
  searchTerm,
  setSearchTerm,
  showScopeSwitcher = true,
}) => {
  return (
    <div className="dispute-queue-sidebar">
      {/* Scope Switcher: Đang xử lý / Đã giải quyết / Giao tôi (chỉ cho CSKH & Admin) */}
      {showScopeSwitcher && (
        <div className="sidebar-scope-tabs">
          <button
            type="button"
            className={`scope-tab-btn ${queueScope === 'ALL' ? 'active' : ''}`}
            onClick={() => setQueueScope('ALL')}
          >
            Đang xử lý ({totalCount})
          </button>
          <button
            type="button"
            className={`scope-tab-btn ${queueScope === 'RESOLVED' ? 'active' : ''}`}
            onClick={() => setQueueScope('RESOLVED')}
          >
            Đã giải quyết ({resolvedCount})
          </button>
          <button
            type="button"
            className={`scope-tab-btn ${queueScope === 'MINE' ? 'active' : ''}`}
            onClick={() => setQueueScope('MINE')}
          >
            Giao tôi ({myCount})
          </button>
        </div>
      )}

      {/* Search Input */}
      <div className="sidebar-search-box">
        <input
          type="text"
          placeholder="Tìm theo tiêu đề hoặc mã..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
        {searchTerm && (
          <button type="button" onClick={() => setSearchTerm('')}>✕</button>
        )}
      </div>

      {/* Status Filter Pills */}
      <div className="sidebar-filter-pills">
        <button
          type="button"
          className={`pill-btn ${queueFilter === 'ALL' ? 'active' : ''}`}
          onClick={() => setQueueFilter('ALL')}
        >
          Tất cả
        </button>
        {queueScope === 'RESOLVED' ? (
          <>
            <button
              type="button"
              className={`pill-btn ${queueFilter === 'RESOLVED' ? 'active' : ''}`}
              onClick={() => setQueueFilter('RESOLVED')}
            >
              Hòa giải thành
            </button>
            <button
              type="button"
              className={`pill-btn ${queueFilter === 'FINAL_RESOLVED' ? 'active' : ''}`}
              onClick={() => setQueueFilter('FINAL_RESOLVED')}
            >
              Phán quyết Admin
            </button>
            <button
              type="button"
              className={`pill-btn ${queueFilter === 'UNVERIFIED' ? 'active' : ''}`}
              onClick={() => setQueueFilter('UNVERIFIED')}
            >
              Không xác minh
            </button>
          </>
        ) : (
          <>
            {unreadCount > 0 && (
              <button
                type="button"
                className={`pill-btn unread ${queueFilter === 'UNREAD' ? 'active' : ''}`}
                onClick={() => setQueueFilter('UNREAD')}
              >
                Chưa đọc ({unreadCount})
              </button>
            )}
            <button
              type="button"
              className={`pill-btn ${queueFilter === 'OPEN' ? 'active' : ''}`}
              onClick={() => setQueueFilter('OPEN')}
            >
              Chờ nhận
            </button>
            <button
              type="button"
              className={`pill-btn ${queueFilter === 'INVESTIGATING' ? 'active' : ''}`}
              onClick={() => setQueueFilter('INVESTIGATING')}
            >
              Đang điều tra
            </button>
            <button
              type="button"
              className={`pill-btn ${queueFilter === 'ESCALATED' ? 'active' : ''}`}
              onClick={() => setQueueFilter('ESCALATED')}
            >
              Kháng nghị
            </button>
            {riskCount > 0 && (
              <button
                type="button"
                className={`pill-btn risk ${queueFilter === 'SLA_RISK' ? 'active' : ''}`}
                onClick={() => setQueueFilter('SLA_RISK')}
              >
                Quá hạn ({riskCount})
              </button>
            )}
          </>
        )}
      </div>

      {/* Cards List */}
      <div className="sidebar-items-list">
        {queue.length === 0 ? (
          <div className="sidebar-empty">Không có đơn khiếu nại nào phù hợp</div>
        ) : (
          queue.map((item) => (
            <DisputeQueueCard
              key={item.id}
              item={item}
              isSelected={item.id === selectedId}
              onSelect={onSelect}
            />
          ))
        )}
      </div>
    </div>
  );
};

export default DisputeQueueSidebar;
