import { useRef, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth';
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
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();
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

    let parsedData = {};
    if (item.data) {
      try {
        parsedData = typeof item.data === 'string' ? JSON.parse(item.data) : item.data;
      } catch (e) {
        console.warn('Lỗi parse notification data:', e);
      }
    }

    const metaShiftId = parsedData?.shiftId || item.metadata?.shiftId;
    const metaEventId = parsedData?.eventId || item.metadata?.eventId;
    const metaAssignmentId = parsedData?.assignmentId || (item.referenceType === 'ASSIGNMENT' ? item.referenceId : null);
    const metaWorkerId = parsedData?.workerId || null;
    let detail = null;

    if (item.referenceType === 'SUPPORT_TICKET') {
      detail = { tab: 'support', ticketId: item.referenceId };
    } else if (item.referenceType === 'DISPUTE') {
      detail = { tab: 'disputes', disputeId: item.referenceId };
    } else if (item.referenceType === 'SHIFT' || item.referenceType === 'ASSIGNMENT' || metaShiftId) {
      detail = {
        tab: 'events',
        shiftId: metaShiftId || (item.referenceType === 'SHIFT' ? item.referenceId : null),
        eventId: metaEventId || null,
        assignmentId: metaAssignmentId || null,
        workerId: metaWorkerId || null,
        notificationType: parsedData?.type || item.type,
        alertType: parsedData?.type || null,
      };
    } else if (item.referenceType === 'PAYMENT') {
      detail = { tab: 'payments' };
    }

    if (!detail) return;

    const dispatchTabSwitch = () => {
      window.dispatchEvent(new CustomEvent('cani_switch_tab', { detail }));
    };

    const role = user?.role;
    if (role === 'ADMIN') {
      if (!location.pathname.startsWith('/admin')) {
        navigate('/admin');
        setTimeout(dispatchTabSwitch, 80);
      } else {
        dispatchTabSwitch();
      }
      return;
    }

    if (role === 'CUSTOMER_CARE') {
      if (!location.pathname.startsWith('/customer-care')) {
        navigate('/customer-care');
        setTimeout(dispatchTabSwitch, 80);
      } else {
        dispatchTabSwitch();
      }
      return;
    }

    if (!location.pathname.startsWith('/eo')) {
      navigate(`/eo?tab=${encodeURIComponent(detail.tab)}`);
      setTimeout(dispatchTabSwitch, 80);
    } else {
      dispatchTabSwitch();
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

