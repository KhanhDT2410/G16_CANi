import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { notificationApi } from '../api/notificationApi';
import { showToast } from '../../../components/common/Toast';

/**
 * Hook quản lý nghiệp vụ và trạng thái thông báo In-App (SRS UC-117)
 * Kết hợp Web Browser Notification API để hiện thông báo trên máy ngay khi có cập nhật mới.
 */
export const useNotifications = () => {
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [isOpen, setIsOpen] = useState(false);
  const [activeFilter, setActiveFilter] = useState('ALL'); // 'ALL' | 'UNREAD'
  const seenUnreadIdsRef = useRef(null);

  const notifyDesktop = useCallback((item) => {
    const title = item?.title || 'Thông báo mới từ CANi';
    const body = item?.body || '';
    showToast(`${title}${body ? ': ' + body : ''}`, 'info');

    if (typeof window !== 'undefined' && 'Notification' in window) {
      if (window.Notification.permission === 'granted') {
        try {
          new window.Notification(title, { body });
        } catch {
          // ignore browser notification errors
        }
      }
    }
  }, []);

  // 1. Tải số lượng thông báo chưa đọc & thông báo mới từ server
  const fetchUnreadCount = useCallback(async () => {
    try {
      const count = await notificationApi.getUnreadCount();
      const numericCount = Number(count) || 0;
      setUnreadCount((prev) => {
        if (numericCount > prev || (numericCount > 0 && seenUnreadIdsRef.current === null)) {
          notificationApi.getMyNotifications().then((list) => {
            const arr = Array.isArray(list) ? list : [];
            setNotifications(arr);
            const unreadItems = arr.filter((n) => !n.isRead);
            if (seenUnreadIdsRef.current === null) {
              seenUnreadIdsRef.current = new Set(unreadItems.map((n) => n.notificationId));
            } else {
              const newItems = unreadItems.filter((n) => !seenUnreadIdsRef.current.has(n.notificationId));
              newItems.forEach((n) => {
                seenUnreadIdsRef.current.add(n.notificationId);
                notifyDesktop(n);
              });
            }
          }).catch(() => {});
        }
        return numericCount;
      });
    } catch (err) {
      // Khi chưa đăng nhập hoặc lỗi kết nối, giữ count = 0
      console.warn('[useNotifications] Không thể tải unread count:', err?.message);
    }
  }, [notifyDesktop]);

  // 2. Tải toàn bộ danh sách thông báo từ DB
  const fetchNotifications = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await notificationApi.getMyNotifications();
      const arr = Array.isArray(data) ? data : [];
      setNotifications(arr);
      // Cập nhật lại unread count dựa trên dữ liệu thật
      const unreadItems = arr.filter((n) => !n.isRead);
      setUnreadCount(unreadItems.length);
      if (seenUnreadIdsRef.current === null) {
        seenUnreadIdsRef.current = new Set(unreadItems.map((n) => n.notificationId));
      } else {
        unreadItems.forEach((n) => seenUnreadIdsRef.current.add(n.notificationId));
      }
    } catch (err) {
      console.error('[useNotifications] Lỗi khi tải thông báo:', err);
      setError(err?.response?.data?.message || 'Không thể tải danh sách thông báo');
      setNotifications([]);
    } finally {
      setIsLoading(false);
    }
  }, []);

  // 3. Đánh dấu 1 thông báo là đã đọc
  const markAsRead = useCallback(async (notificationId) => {
    if (!notificationId) return;

    // Cập nhật lạc quan (Optimistic Update) trên giao diện trước
    setNotifications((prev) =>
      prev.map((item) =>
        item.notificationId === notificationId ? { ...item, isRead: true } : item
      )
    );
    setUnreadCount((prev) => Math.max(0, prev - 1));

    try {
      await notificationApi.markAsRead(notificationId);
    } catch (err) {
      console.warn('[useNotifications] Lỗi khi gọi markAsRead:', err);
      // Đồng bộ lại dữ liệu thật nếu API lỗi
      fetchNotifications();
    }
  }, [fetchNotifications]);

  // 4. Đánh dấu toàn bộ thông báo là đã đọc
  const markAllAsRead = useCallback(async () => {
    setNotifications((prev) => prev.map((item) => ({ ...item, isRead: true })));
    setUnreadCount(0);

    try {
      await notificationApi.markAllAsRead();
    } catch (err) {
      console.warn('[useNotifications] Lỗi khi gọi markAllAsRead:', err);
      fetchNotifications();
    }
  }, [fetchNotifications]);

  // 5. Đóng/mở dropdown (xin quyền thông báo trình duyệt nếu chưa từ chối)
  const toggleDropdown = useCallback(() => {
    if (
      typeof window !== 'undefined' &&
      'Notification' in window &&
      window.Notification.permission === 'default'
    ) {
      window.Notification.requestPermission().catch(() => {});
    }
    setIsOpen((prev) => {
      const next = !prev;
      if (next) {
        fetchNotifications();
      }
      return next;
    });
  }, [fetchNotifications]);

  const closeDropdown = useCallback(() => {
    setIsOpen(false);
  }, []);

  // 6. Tự động kiểm tra unread count định kỳ (mỗi 15s) và lắng nghe sự kiện
  useEffect(() => {
    fetchUnreadCount();

    const intervalId = setInterval(() => {
      fetchUnreadCount();
    }, 15000);

    const handleRefresh = () => {
      fetchUnreadCount();
      if (isOpen) {
        fetchNotifications();
      }
    };

    window.addEventListener('cani_notification_refresh', handleRefresh);

    return () => {
      clearInterval(intervalId);
      window.removeEventListener('cani_notification_refresh', handleRefresh);
    };
  }, [fetchUnreadCount, fetchNotifications, isOpen]);

  // Danh sách thông báo sau khi lọc tab
  const filteredNotifications = useMemo(() => {
    if (activeFilter === 'UNREAD') {
      return notifications.filter((n) => !n.isRead);
    }
    return notifications;
  }, [notifications, activeFilter]);

  return {
    notifications: filteredNotifications,
    rawNotifications: notifications,
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
    refresh: fetchNotifications,
  };
};

