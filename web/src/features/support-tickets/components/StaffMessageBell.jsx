import { useState, useRef, useEffect } from 'react';
import { useStaffChatAlerts } from '../hooks/useStaffChatAlerts';
import StaffMessageItem from './StaffMessageItem';
import { MessageIcon } from '../../../layouts/components/HeaderIcons';

/**
 * Nút thông báo tin nhắn trực tiếp trên thanh Header dành cho Admin & CSKH.
 * Tích hợp Popover danh sách hội thoại gần nhất và huy hiệu số tin chưa đọc.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const StaffMessageBell = ({ className = '' }) => {
  const containerRef = useRef(null);
  const [isOpen, setIsOpen] = useState(false);
  const { totalUnread, recentConversations, refresh } = useStaffChatAlerts();

  // Đóng khi click ngoài hoặc ấn Escape
  useEffect(() => {
    const handleOutsideClick = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setIsOpen(false);
      }
    };
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen) setIsOpen(false);
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleOutsideClick);
      document.addEventListener('touchstart', handleOutsideClick);
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      document.removeEventListener('mousedown', handleOutsideClick);
      document.removeEventListener('touchstart', handleOutsideClick);
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  const formatTimeAgo = (isoStr) => {
    if (!isoStr) return '';
    try {
      const diffMins = Math.floor((Date.now() - new Date(isoStr).getTime()) / 60000);
      if (diffMins < 1) return 'Vừa xong';
      if (diffMins < 60) return `${diffMins}p trước`;
      const diffHours = Math.floor(diffMins / 60);
      if (diffHours < 24) return `${diffHours}h trước`;
      return new Date(isoStr).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' });
    } catch {
      return '';
    }
  };

  const handleSelectConversation = (conv) => {
    setIsOpen(false);
    window.dispatchEvent(
      new CustomEvent('cani_open_chat', {
        detail: {
          tab: 'tickets',
          filter: 'CHAT',
          conversation: conv,
          userId: conv.userId,
        },
      })
    );
  };

  const handleOpenAllChat = () => {
    setIsOpen(false);
    window.dispatchEvent(
      new CustomEvent('cani_switch_tab', {
        detail: { tab: 'tickets', filter: 'CHAT' },
      })
    );
  };

  return (
    <div className={`notif-bell-container ${className}`} ref={containerRef}>
      <button
        type="button"
        className={`header-notif-btn ${isOpen ? 'active' : ''}`}
        title="Tin nhắn khách hàng"
        onClick={() => {
          setIsOpen((p) => !p);
          if (!isOpen) refresh();
        }}
        aria-expanded={isOpen}
        aria-haspopup="dialog"
      >
        <MessageIcon />
        {totalUnread > 0 && (
          <span className="notif-badge-pill" style={{ background: '#2563eb' }} title={`${totalUnread} tin nhắn chưa đọc`}>
            {totalUnread > 99 ? '99+' : totalUnread}
          </span>
        )}
      </button>

      {isOpen && (
        <div className="notif-dropdown staff-message-dropdown" role="dialog" aria-label="Tin nhắn trực tiếp">
          <div className="notif-dropdown-header">
            <div className="notif-header-title-box">
              <h3 className="notif-header-title">Tin nhắn khách hàng</h3>
              {totalUnread > 0 && (
                <span className="notif-header-badge" style={{ background: '#eff6ff', color: '#1d4ed8', borderColor: '#bfdbfe' }}>
                  {totalUnread} mới
                </span>
              )}
            </div>
            <button
              type="button"
              className="notif-btn-mark-all"
              onClick={handleOpenAllChat}
              title="Đi tới danh sách tin nhắn"
            >
              Mở danh sách
            </button>
          </div>

          <div className="notif-dropdown-body" style={{ maxHeight: '360px' }}>
            {recentConversations.length === 0 ? (
              <div className="notif-empty-state">
                <span className="notif-empty-icon">💬</span>
                <p className="notif-empty-title">Chưa có tin nhắn nào</p>
                <span className="notif-empty-desc">
                  Khi Nhà tổ chức hoặc Nhân viên nhắn tin, cuộc hội thoại sẽ xuất hiện tại đây.
                </span>
              </div>
            ) : (
              <div className="notif-items-list">
                {recentConversations.map((conv) => (
                  <StaffMessageItem
                    key={conv.userId || conv.id}
                    conv={conv}
                    onSelect={handleSelectConversation}
                    formatTimeAgo={formatTimeAgo}
                  />
                ))}
              </div>
            )}
          </div>

          <div style={{ padding: '0.6rem', borderTop: '1px solid #f1f5f9', textAlign: 'center' }}>
            <button
              type="button"
              onClick={handleOpenAllChat}
              style={{
                background: 'none',
                border: 'none',
                color: '#2563eb',
                fontSize: '0.82rem',
                fontWeight: 600,
                cursor: 'pointer',
                padding: '0.3rem 0.5rem',
              }}
            >
              Xem tất cả cuộc hội thoại →
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default StaffMessageBell;
