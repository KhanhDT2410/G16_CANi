import StaffMessageItem from './StaffMessageItem';

/**
 * Popover Dropdown hiển thị danh sách tin nhắn trực tiếp mới gửi từ khách hàng (SRS ADM-02).
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const StaffMessageDropdown = ({
  conversations = [],
  totalUnread = 0,
  loading = false,
  onSelectConversation,
  onViewAll,
  onRefresh,
}) => {
  const formatTimeAgo = (isoStr) => {
    if (!isoStr) return '';
    try {
      const diffMins = Math.floor((Date.now() - new Date(isoStr).getTime()) / 60000);
      if (diffMins < 1) return 'Vừa xong';
      if (diffMins < 60) return `${diffMins} phút trước`;
      const diffHours = Math.floor(diffMins / 60);
      if (diffHours < 24) return `${diffHours} giờ trước`;
      return new Date(isoStr).toLocaleDateString('vi-VN', {
        day: '2-digit',
        month: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return '';
    }
  };

  return (
    <div className="notif-dropdown staff-msg-dropdown" role="dialog" aria-label="Tin nhắn trực tiếp khách hàng">
      {/* Header */}
      <div className="notif-dropdown-header">
        <div className="notif-header-title-box">
          <h3 className="notif-header-title">Tin nhắn khách hàng</h3>
          {totalUnread > 0 && (
            <span className="notif-header-badge">{totalUnread} mới</span>
          )}
        </div>

        <button
          type="button"
          className="notif-btn-mark-all"
          onClick={onRefresh}
          title="Làm mới danh sách tin nhắn"
        >
          Làm mới
        </button>
      </div>

      {/* Body List */}
      <div className="notif-dropdown-body staff-msg-dropdown-body">
        {loading && conversations.length === 0 ? (
          <div className="notif-empty-state">
            <span className="notif-spinner" />
            <p className="notif-empty-title">Đang đồng bộ tin nhắn...</p>
          </div>
        ) : conversations.length === 0 ? (
          <div className="notif-empty-state">
            <span className="notif-empty-icon">💬</span>
            <p className="notif-empty-title">Chưa có tin nhắn mới nào</p>
            <span className="notif-empty-desc">
              Khi Nhà tổ chức hoặc Nhân viên gửi tin nhắn trực tiếp, thông báo sẽ hiển thị tại đây.
            </span>
          </div>
        ) : (
          <div className="notif-items-list">
            {conversations.map((conv) => (
              <StaffMessageItem
                key={conv.userId || conv.id}
                conv={conv}
                onSelect={onSelectConversation}
                formatTimeAgo={formatTimeAgo}
              />
            ))}
          </div>
        )}
      </div>

      {/* Footer view all */}
      <div className="staff-msg-dropdown-footer">
        <button
          type="button"
          className="staff-msg-view-all-btn"
          onClick={onViewAll}
        >
          Xem toàn bộ tin nhắn trực tiếp →
        </button>
      </div>
    </div>
  );
};

export default StaffMessageDropdown;
