import { NOTIFICATION_TYPE_CONFIG, formatRelativeTime } from '../types/notification.constants';

/**
 * Hiển thị một dòng thông báo đơn lẻ trong danh sách (SRS UC-117)
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const NotificationItem = ({ notification, onSelect }) => {
  const { notificationId, title, body, type, isRead, createdAt } = notification;

  const typeConfig = NOTIFICATION_TYPE_CONFIG[type] || {
    label: 'Thông báo',
    icon: '🔔',
    badgeClass: 'notif-type-system',
  };

  const handleClick = () => {
    if (onSelect) {
      onSelect(notification);
    }
  };

  return (
    <div
      className={`notif-item ${!isRead ? 'unread' : 'read'}`}
      onClick={handleClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          handleClick();
        }
      }}
    >
      <div className={`notif-item-icon-box ${typeConfig.badgeClass}`}>
        <span className="notif-item-icon" role="img" aria-label={typeConfig.label}>
          {typeConfig.icon}
        </span>
      </div>

      <div className="notif-item-content">
        <div className="notif-item-header">
          <span className="notif-item-type-tag">{typeConfig.label}</span>
          <span className="notif-item-time">{formatRelativeTime(createdAt)}</span>
        </div>

        <h4 className="notif-item-title">{title}</h4>
        {body && <p className="notif-item-body">{body}</p>}
      </div>

      {!isRead && <span className="notif-unread-dot" title="Chưa đọc" />}
    </div>
  );
};

export default NotificationItem;

