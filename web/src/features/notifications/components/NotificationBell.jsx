import { useRef, useEffect } from 'react';
import { useNotifications } from '../hooks/useNotifications';
import NotificationDropdown from './NotificationDropdown';
import { BellIcon } from '../../../layouts/components/HeaderIcons';
import './Notification.css';

/**
 * Nút quả chuông thông báo trên thanh Header (SRS UC-117)
 * Tích hợp popover dropdown và badge số lượng chưa đọc.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const NotificationBell = ({ className = '' }) => {
  const containerRef = useRef(null);
  const {
    notifications,
    rawNotifications,
    unreadCount,
    isLoading,
    error,
    isOpen,
    activeFilter,
    setActiveFilter,
    toggleDropdown,
    closeDropdown,
    markAsRead,
    markAllAsRead,
    refresh,
  } = useNotifications();

  // Đóng dropdown khi bấm ra ngoài container
  useEffect(() => {
    const handleOutsideClick = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        closeDropdown();
      }
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleOutsideClick);
      document.addEventListener('touchstart', handleOutsideClick);
    }

    return () => {
      document.removeEventListener('mousedown', handleOutsideClick);
      document.removeEventListener('touchstart', handleOutsideClick);
    };
  }, [isOpen, closeDropdown]);

  // Đóng khi bấm phím Escape
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen) {
        closeDropdown();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, closeDropdown]);

  // Xử lý khi người dùng chọn một thông báo
  const handleSelectNotification = (item) => {
    if (!item.isRead) {
      markAsRead(item.notificationId);
    }
    closeDropdown();

    // Điều hướng nghiệp vụ tương ứng (Deep Link)
    if (item.referenceType === 'SUPPORT_TICKET') {
      window.dispatchEvent(
        new CustomEvent('cani_switch_tab', { detail: { tab: 'support', ticketId: item.referenceId } })
      );
    } else if (item.referenceType === 'DISPUTE') {
      window.dispatchEvent(
        new CustomEvent('cani_switch_tab', { detail: { tab: 'disputes', disputeId: item.referenceId } })
      );
    } else if (item.referenceType === 'SHIFT' || item.referenceType === 'ASSIGNMENT') {
      window.dispatchEvent(
        new CustomEvent('cani_switch_tab', { detail: { tab: 'events', shiftId: item.referenceId } })
      );
    } else if (item.referenceType === 'PAYMENT') {
      window.dispatchEvent(
        new CustomEvent('cani_switch_tab', { detail: { tab: 'payments' } })
      );
    }
  };

  return (
    <div className={`notif-bell-container ${className}`} ref={containerRef}>
      <button
        type="button"
        className={`header-notif-btn ${isOpen ? 'active' : ''}`}
        title="Thông báo mới"
        onClick={toggleDropdown}
        aria-expanded={isOpen}
        aria-haspopup="dialog"
      >
        <BellIcon />
        {unreadCount > 0 && (
          <span className="notif-badge-pill" title={`${unreadCount} thông báo chưa đọc`}>
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <NotificationDropdown
          notifications={notifications}
          rawNotifications={rawNotifications}
          unreadCount={unreadCount}
          isLoading={isLoading}
          error={error}
          activeFilter={activeFilter}
          setActiveFilter={setActiveFilter}
          onMarkAllAsRead={markAllAsRead}
          onSelectNotification={handleSelectNotification}
          onRefresh={refresh}
        />
      )}
    </div>
  );
};

export default NotificationBell;

