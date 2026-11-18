import { useState, useEffect, useCallback, useRef } from 'react';
import { useAuth } from '../../auth';
import { supportChatApi } from '../api/supportChatApi';

/**
 * Custom Hook theo dõi thông báo tin nhắn trực tiếp thời gian thực cho Admin & CSKH.
 * Tự động polling định kỳ và lắng nghe sự kiện để cập nhật huy hiệu (badge).
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const useStaffChatAlerts = (pollIntervalMs = 7000) => {
  const { user } = useAuth();
  const isStaff = user?.role === 'ADMIN' || user?.role === 'CUSTOMER_CARE';

  const [summary, setSummary] = useState({
    totalUnreadCount: 0,
    unreadConversationCount: 0,
    recentConversations: [],
  });
  const [loading, setLoading] = useState(false);
  const isMountedRef = useRef(true);

  const fetchSummary = useCallback(async () => {
    if (!isStaff) return;
    try {
      const data = await supportChatApi.getStaffUnreadSummary();
      if (isMountedRef.current && data) {
        setSummary(data);
      }
    } catch {
      // Bỏ qua lỗi mạng nền
    }
  }, [isStaff]);

  useEffect(() => {
    isMountedRef.current = true;
    if (!isStaff) return;

    fetchSummary();

    const intervalId = setInterval(() => {
      // Chỉ fetch khi trang đang hiển thị
      if (typeof document !== 'undefined' && !document.hidden) {
        fetchSummary();
      }
    }, pollIntervalMs);

    const handleChatUpdated = () => {
      fetchSummary();
    };

    const handleVisibilityChange = () => {
      if (!document.hidden) {
        fetchSummary();
      }
    };

    window.addEventListener('cani_staff_chat_updated', handleChatUpdated);
    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      isMountedRef.current = false;
      clearInterval(intervalId);
      window.removeEventListener('cani_staff_chat_updated', handleChatUpdated);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [isStaff, pollIntervalMs, fetchSummary]);

  return {
    summary,
    totalUnread: summary.totalUnreadCount || 0,
    unreadConvs: summary.unreadConversationCount || 0,
    recentConversations: summary.recentConversations || [],
    loading,
    refresh: fetchSummary,
  };
};

export default useStaffChatAlerts;
