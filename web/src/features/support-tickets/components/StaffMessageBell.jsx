import { useState, useRef, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../auth';
import { MessageSquareIcon } from '../../../layouts/components/HeaderIcons';
import useStaffChatAlerts from '../hooks/useStaffChatAlerts';
import StaffMessageDropdown from './StaffMessageDropdown';

/**
 * Nút Icon Tin Nhắn Trực Tiếp trên Header dành cho Admin & CSKH (SRS ADM-02).
 * Hiển thị huy hiệu số tin nhắn chưa đọc và popover xem nhanh tin nhắn khách gửi.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const StaffMessageBell = ({ className = '' }) => {
  const containerRef = useRef(null);
  const [isOpen, setIsOpen] = useState(false);
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const isStaff = user?.role === 'ADMIN' || user?.role === 'CUSTOMER_CARE';
  const { totalUnread, recentConversations, loading, refresh } = useStaffChatAlerts();

  // Đóng dropdown khi click ra ngoài
  useEffect(() => {
    const handleOutside = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener('mousedown', handleOutside);
      document.addEventListener('touchstart', handleOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleOutside);
      document.removeEventListener('touchstart', handleOutside);
    };
  }, [isOpen]);

  // Đóng khi bấm Escape
  useEffect(() => {
    const handleEscape = (e) => {
      if (e.key === 'Escape' && isOpen) setIsOpen(false);
    };
    window.addEventListener('keydown', handleEscape);
    return () => window.removeEventListener('keydown', handleEscape);
  }, [isOpen]);

  if (!isStaff) return null;

  const navigateToChat = (conv = null) => {
    setIsOpen(false);
    window.__cani_pending_chat = conv;
    const targetPath = user?.role === 'ADMIN' ? '/admin' : '/customer-care';

    // Điều hướng nếu chưa ở đúng trang
    if (location.pathname !== targetPath) {
      navigate(targetPath, { state: { openChatConv: conv } });
    }

    // Phát sự kiện toàn cục để AdminPage / CustomerCarePage mở tab & pop-up chat ngay lập tức
    setTimeout(() => {
      window.dispatchEvent(
        new CustomEvent('cani_open_staff_chat', { detail: conv })
      );
    }, 50);
  };

  const handleToggle = () => {
    if (!isOpen) {
      refresh();
    }
    setIsOpen((prev) => !prev);
  };

  return (
    <div className={`notif-bell-container staff-chat-bell-container ${className}`} ref={containerRef}>
      <button
        type="button"
        className={`header-notif-btn ${isOpen ? 'active' : ''}`}
        title="Tin nhắn trực tiếp từ khách hàng"
        onClick={handleToggle}
        aria-expanded={isOpen}
        aria-haspopup="dialog"
      >
        <MessageSquareIcon />
        {totalUnread > 0 && (
          <span
            className="notif-badge-pill staff-msg-badge"
            title={`${totalUnread} tin nhắn khách hàng chưa đọc`}
          >
            {totalUnread > 99 ? '99+' : totalUnread}
          </span>
        )}
      </button>

      {isOpen && (
        <StaffMessageDropdown
          conversations={recentConversations}
          totalUnread={totalUnread}
          loading={loading}
          onSelectConversation={(conv) => navigateToChat(conv)}
          onViewAll={() => navigateToChat(null)}
          onRefresh={refresh}
        />
      )}
    </div>
  );
};

export default StaffMessageBell;
