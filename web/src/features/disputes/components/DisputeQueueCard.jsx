import { DISPUTE_STATUS_CONFIG, DISPUTE_TYPE_LABELS } from '../types/dispute.constants';

/**
 * Thẻ hiển thị một tranh chấp trong hàng đợi sidebar.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const DisputeQueueCard = ({ item, isSelected, onSelect }) => {
  const hasUnread = (item.unreadCount || 0) > 0 || (item.workerUnreadCount || 0) > 0 || (item.eoUnreadCount || 0) > 0;
  const statusCfg = DISPUTE_STATUS_CONFIG[item.status] || {
    label: item.status,
    badgeClass: 'badge-status-open',
  };
  const typeLabel = DISPUTE_TYPE_LABELS[item.type] || item.type;
  const slaText = item.slaDeadline
    ? new Date(item.slaDeadline).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' })
    : 'N/A';
  const msgTime = item.lastMessageAt
    ? new Date(item.lastMessageAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
    : null;

  return (
    <div
      className={`queue-item-card ${isSelected ? 'selected' : ''} ${hasUnread ? 'has-unread' : ''}`}
      onClick={() => onSelect(item.id)}
      role="button"
      tabIndex={0}
    >
      <div className="item-card-top">
        <div className="item-card-top-left">
          <span className="item-card-id">{item.displayId || `#${item.id?.substring(0, 8)}`}</span>
          {hasUnread && <span className="item-card-dot-badge" title="Có tin nhắn mới chưa đọc" />}
        </div>
        <div className="item-card-top-right">
          {msgTime && <span className="item-card-msg-time">{msgTime}</span>}
          <span className={`dispute-badge ${statusCfg.badgeClass}`}>{statusCfg.label}</span>
        </div>
      </div>

      {hasUnread && (
        <div className="item-card-unread-row">
          {item.workerUnreadCount > 0 && (
            <span className="unread-sender-pill worker" title={`Nhân sự ${item.workerName || ''} đã gửi tin nhắn`}>
              Nhân sự: {item.workerName ? item.workerName : 'Worker'} ({item.workerUnreadCount} tin mới)
            </span>
          )}
          {item.eoUnreadCount > 0 && (
            <span className="unread-sender-pill eo" title={`Nhà tổ chức ${item.eoName || ''} đã gửi tin nhắn`}>
              Nhà tổ chức: {item.eoName ? item.eoName : 'EO'} ({item.eoUnreadCount} tin mới)
            </span>
          )}
          {!item.workerUnreadCount && !item.eoUnreadCount && item.unreadCount > 0 && (
            <span className="unread-sender-pill default">
              {item.lastMessageSenderName ? `${item.lastMessageSenderName}: ` : ''}{item.unreadCount} tin mới
            </span>
          )}
        </div>
      )}

      <h4 className="item-card-title">{item.title}</h4>
      <div className="item-card-bottom">
        <span className="item-card-type">{typeLabel}</span>
        {item.resolvedAt ? (
          <span className="item-card-sla resolved">
            Xong: {new Date(item.resolvedAt).toLocaleDateString('vi-VN')}
          </span>
        ) : (
          <span className={`item-card-sla ${item.slaAtRisk ? 'at-risk' : ''}`}>
            Hạn SLA: {slaText}
          </span>
        )}
      </div>
    </div>
  );
};

export default DisputeQueueCard;
