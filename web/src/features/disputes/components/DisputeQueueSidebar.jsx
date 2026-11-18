import { DISPUTE_STATUS_CONFIG, DISPUTE_TYPE_LABELS } from '../types/dispute.constants';

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
  myCount,
  riskCount,
  searchTerm,
  setSearchTerm,
}) => {
  return (
    <div className="dispute-queue-sidebar">
      {/* Scope Switcher: Toàn bộ / Của tôi */}
      <div className="sidebar-scope-tabs">
        <button
          type="button"
          className={`scope-tab-btn ${queueScope === 'ALL' ? 'active' : ''}`}
          onClick={() => setQueueScope('ALL')}
        >
          Toàn bộ ({totalCount})
        </button>
        <button
          type="button"
          className={`scope-tab-btn ${queueScope === 'MINE' ? 'active' : ''}`}
          onClick={() => setQueueScope('MINE')}
        >
          Giao tôi ({myCount})
        </button>
      </div>

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
      </div>

      {/* Cards List */}
      <div className="sidebar-items-list">
        {queue.length === 0 ? (
          <div className="sidebar-empty">Không có đơn khiếu nại nào phù hợp</div>
        ) : (
          queue.map((item) => {
            const isSelected = item.id === selectedId;
            const statusCfg = DISPUTE_STATUS_CONFIG[item.status] || {
              label: item.status,
              badgeClass: 'badge-status-open',
            };
            const typeLabel = DISPUTE_TYPE_LABELS[item.type] || item.type;
            const slaText = item.slaDeadline
              ? new Date(item.slaDeadline).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' })
              : 'N/A';

            return (
              <div
                key={item.id}
                className={`queue-item-card ${isSelected ? 'selected' : ''}`}
                onClick={() => onSelect(item.id)}
                role="button"
                tabIndex={0}
              >
                <div className="item-card-top">
                  <span className="item-card-id">{item.displayId || `#${item.id?.substring(0, 8)}`}</span>
                  <span className={`dispute-badge ${statusCfg.badgeClass}`}>{statusCfg.label}</span>
                </div>
                <h4 className="item-card-title">{item.title}</h4>
                <div className="item-card-bottom">
                  <span className="item-card-type">{typeLabel}</span>
                  <span className={`item-card-sla ${item.slaAtRisk ? 'at-risk' : ''}`}>
                    Hạn SLA: {slaText}
                  </span>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};

export default DisputeQueueSidebar;
