import NotificationItem from './NotificationItem';

/**
 * Dropdown Popover hiển thị danh sách thông báo quả chuông (SRS SCR-10 / UC-117)
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const NotificationDropdown = ({
  notifications = [],
  rawNotifications = [],
  unreadCount = 0,
  isLoading = false,
  error = null,
  activeFilter = 'ALL',
  setActiveFilter,
  onMarkAllAsRead,
  onSelectNotification,
  onRefresh,
}) => {
  return (
    <div className="notif-dropdown" role="dialog" aria-label="Trung tâm thông báo">
      {/* Header */}
      <div className="notif-dropdown-header">
        <div className="notif-header-title-box">
          <h3 className="notif-header-title">Thông báo</h3>
          {unreadCount > 0 && (
            <span className="notif-header-badge">{unreadCount} mới</span>
          )}
        </div>

        {unreadCount > 0 && (
          <button
            type="button"
            className="notif-btn-mark-all"
            onClick={onMarkAllAsRead}
            title="Đánh dấu tất cả thông báo là đã đọc"
          >
            Đã đọc tất cả
          </button>
        )}
      </div>

      {/* Filter Tabs */}
      <div className="notif-filter-tabs">
        <button
          type="button"
          className={`notif-tab-btn ${activeFilter === 'ALL' ? 'active' : ''}`}
          onClick={() => setActiveFilter('ALL')}
        >
          Tất cả ({rawNotifications.length})
        </button>
        <button
          type="button"
          className={`notif-tab-btn ${activeFilter === 'UNREAD' ? 'active' : ''}`}
          onClick={() => setActiveFilter('UNREAD')}
        >
          Chưa đọc ({unreadCount})
        </button>
      </div>

      {/* Content List */}
      <div className="notif-dropdown-body">
        {isLoading && notifications.length === 0 ? (
          <div className="notif-empty-state">
            <span className="notif-spinner" />
            <p className="notif-empty-title">Đang đồng bộ thông báo từ máy chủ...</p>
          </div>
        ) : error ? (
          <div className="notif-empty-state notif-error-state">
            <span className="notif-empty-icon">⚠️</span>
            <p className="notif-empty-title">{error}</p>
            {onRefresh && (
              <button type="button" className="notif-btn-retry" onClick={onRefresh}>
                Thử lại
              </button>
            )}
          </div>
        ) : notifications.length === 0 ? (
          <div className="notif-empty-state">
            <span className="notif-empty-icon">🔔</span>
            <p className="notif-empty-title">
              {activeFilter === 'UNREAD'
                ? 'Không có thông báo chưa đọc nào'
                : 'Chưa có thông báo nào'}
            </p>
            <span className="notif-empty-desc">
              Các cập nhật về ca làm, thanh toán, tranh chấp và hỗ trợ sẽ hiển thị tại đây khi phát sinh.
            </span>
          </div>
        ) : (
          <div className="notif-items-list">
            {notifications.map((item) => (
              <NotificationItem
                key={item.notificationId}
                notification={item}
                onSelect={onSelectNotification}
              />
            ))}
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="notif-dropdown-footer">
        <span className="notif-footer-text">
          Hệ thống thông báo thời gian thực CANi
        </span>
      </div>
    </div>
  );
};

export default NotificationDropdown;

