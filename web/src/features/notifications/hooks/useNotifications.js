import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { notificationApi } from '../api/notificationApi';
import { showToast } from '../../../components/common/Toast';

/**
 * Hook quản lý nghiệp vụ và trạng thái thông báo In-App (SRS UC-117)
 * Đồng bộ real-time qua polling 15s + visibility/focus và phát `cani_domain_event`
 * để các màn hình đang mở (EO ShiftWorkspace, Events, Payments, Admin) tự cập nhật
 * không cần F5, kể cả khi tổng số chưa đọc (unreadCount) không thay đổi.
 */
export const useNotifications = () => {
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [isOpen, setIsOpen] = useState(false);
  const [activeFilter, setActiveFilter] = useState('ALL'); // 'ALL' | 'UNREAD'

  const seenNotificationIdsRef = useRef(null);
  const isOpenRef = useRef(false);
  const lastFocusDispatchAtRef = useRef(0);

  useEffect(() => {
    isOpenRef.current = isOpen;
  }, [isOpen]);

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

  const isSelfTriggeredConfirmationToast = (item) => {
    let rawData = item?.data;
    if (typeof rawData === 'string') {
      try {
        rawData = JSON.parse(rawData);
      } catch {
        rawData = null;
      }
    }
    const actionType = String(rawData?.type || '').toUpperCase();
    return actionType === 'EMERGENCY_DISPATCH_ACTIVE' || actionType === 'EMERGENCY_DISPATCH_DISABLED';
  };

  const syncDomainFromLatestNotifications = useCallback(
    (arr, { suppressToast = false } = {}) => {
      if (!Array.isArray(arr)) return;
      if (seenNotificationIdsRef.current === null) {
        seenNotificationIdsRef.current = new Set(
          arr.map((n) => n?.notificationId).filter(Boolean)
        );
        return;
      }

      const newlyArrived = arr.filter(
        (n) => n?.notificationId && !seenNotificationIdsRef.current.has(n.notificationId)
      );
      if (newlyArrived.length === 0) return;

      newlyArrived.forEach((n) => {
        seenNotificationIdsRef.current.add(n.notificationId);
        if (!suppressToast && !n.isRead && !isSelfTriggeredConfirmationToast(n)) {
          notifyDesktop(n);
        }
      });

      if (typeof window !== 'undefined') {
        window.dispatchEvent(
          new CustomEvent('cani_domain_event', {
            detail: {
              reason: 'notification',
              items: newlyArrived,
            },
          })
        );
      }
    },
    [notifyDesktop]
  );

  // 1. Tải unread count + top 15 thông báo gần nhất để phát hiện mọi thay đổi kể cả khi unreadCount không đổi
  const fetchUnreadCount = useCallback(async () => {
    try {
      const [countRes, latestRes] = await Promise.allSettled([
        notificationApi.getUnreadCount(),
        notificationApi.getMyNotifications({ limit: isOpenRef.current ? 50 : 15 }),
      ]);

      if (countRes.status === 'fulfilled') {
        setUnreadCount(Number(countRes.value) || 0);
      }
      if (latestRes.status === 'fulfilled') {
        const arr = Array.isArray(latestRes.value) ? latestRes.value : [];
        setNotifications(arr);
        syncDomainFromLatestNotifications(arr);
      }
    } catch (err) {
      console.warn('[useNotifications] Không thể đồng bộ thông báo:', err?.message);
    }
  }, [syncDomainFromLatestNotifications]);

  // 2. Tải danh sách thông báo đầy đủ từ DB (khi mở chuông)
  const fetchNotifications = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await notificationApi.getMyNotifications({ limit: 50 });
      const arr = Array.isArray(data) ? data : [];
      setNotifications(arr);
      const unreadItems = arr.filter((n) => !n.isRead);
      setUnreadCount(unreadItems.length);
      syncDomainFromLatestNotifications(arr, { suppressToast: true });
    } catch (err) {
      console.error('[useNotifications] Lỗi khi tải thông báo:', err);
      setError(err?.response?.data?.message || 'Không thể tải danh sách thông báo');
      setNotifications([]);
    } finally {
      setIsLoading(false);
    }
  }, [syncDomainFromLatestNotifications]);

  // 3. Đánh dấu 1 thông báo là đã đọc
  const markAsRead = useCallback(async (notificationId) => {
    if (!notificationId) return;

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

  // 6. Tự động kiểm tra định kỳ (mỗi 15s khi tab hiển thị) + phản hồi ngay khi quay lại tab/cửa sổ
  useEffect(() => {
    const isTabVisible = () =>
      typeof document === 'undefined' || document.visibilityState === 'visible';

    if (isTabVisible()) {
      fetchUnreadCount();
    }

    const intervalId = setInterval(() => {
      if (isTabVisible()) {
        fetchUnreadCount();
      }
    }, 15000);

    const handleRefresh = () => {
      if (!isTabVisible()) return;
      fetchUnreadCount();
    };

    const handleTabReturn = () => {
      if (!isTabVisible()) return;
      const now = Date.now();
      if (now - lastFocusDispatchAtRef.current < 2000) return;
      lastFocusDispatchAtRef.current = now;
      fetchUnreadCount();
      window.dispatchEvent(
        new CustomEvent('cani_domain_event', {
          detail: { reason: 'tab_focus', items: [] },
        })
      );
    };

    window.addEventListener('cani_notification_refresh', handleRefresh);
    document.addEventListener('visibilitychange', handleTabReturn);
    window.addEventListener('focus', handleTabReturn);

    return () => {
      clearInterval(intervalId);
      window.removeEventListener('cani_notification_refresh', handleRefresh);
      document.removeEventListener('visibilitychange', handleTabReturn);
      window.removeEventListener('focus', handleTabReturn);
    };
  }, [fetchUnreadCount]);

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
