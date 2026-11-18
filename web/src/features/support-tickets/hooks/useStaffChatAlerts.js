import { useState, useEffect, useCallback, useRef } from 'react';
import { useAuth } from '../../auth';
import { supportChatApi } from '../api/supportChatApi';
import { showToast } from '../../../components/common/Toast';

// Module-level cache & broadcast giữa Header, Sidebar và Table
let cachedData = {
  totalUnreadCount: 0,
  unreadConversationsCount: 0,
  recentConversations: [],
  conversations: [],
};
let lastFetchTime = 0;
let prevUnreadCount = 0;
let isInitialLoad = true;
const subscribers = new Set();

const notifySubscribers = (data) => {
  cachedData = data;
  subscribers.forEach((cb) => cb(data));
};

// Chuông thông báo Web Audio API (Double-chime)
const playNotificationSound = () => {
  try {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx) return;
    const ctx = new AudioCtx();
    const now = ctx.currentTime;

    const osc1 = ctx.createOscillator();
    const gain1 = ctx.createGain();
    osc1.type = 'sine';
    osc1.frequency.setValueAtTime(659.25, now);
    gain1.gain.setValueAtTime(0.12, now);
    gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.15);
    osc1.connect(gain1);
    gain1.connect(ctx.destination);
    osc1.start(now);
    osc1.stop(now + 0.15);

    const osc2 = ctx.createOscillator();
    const gain2 = ctx.createGain();
    osc2.type = 'sine';
    osc2.frequency.setValueAtTime(880, now + 0.08);
    gain2.gain.setValueAtTime(0.15, now + 0.08);
    gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.32);
    osc2.connect(gain2);
    gain2.connect(ctx.destination);
    osc2.start(now + 0.08);
    osc2.stop(now + 0.32);
  } catch {
    // Bỏ qua nếu trình duyệt chưa có user interaction
  }
};

/**
 * Hook quản lý cảnh báo tin nhắn trực tiếp mới cho Admin & CSKH (SRS ADM-02).
 * Tự động Polling 3s, nổ âm thanh và Toast khi có tin nhắn mới từ EO/Worker.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const useStaffChatAlerts = () => {
  const { user } = useAuth();
  const isStaff = user?.role === 'ADMIN' || user?.role === 'CUSTOMER_CARE';

  const [state, setState] = useState(cachedData);
  const [loading, setLoading] = useState(false);
  const isMountedRef = useRef(true);

  const fetchConversations = useCallback(async (force = false) => {
    if (!isStaff) return;
    const now = Date.now();
    if (!force && now - lastFetchTime < 1500) return;
    lastFetchTime = now;

    try {
      setLoading(true);
      const list = await supportChatApi.getStaffConversations();
      if (Array.isArray(list) && isMountedRef.current) {
        const totalUnread = list.reduce((sum, c) => sum + (c.unreadStaffCount || 0), 0);
        const unreadConvs = list.filter((c) => (c.unreadStaffCount || 0) > 0).length;

        // Ưu tiên danh sách có tin nhắn hoặc có unread
        const withMessages = list.filter((c) => c.lastMessageAt || (c.unreadStaffCount || 0) > 0);
        const recent = withMessages.length > 0 ? withMessages.slice(0, 10) : list.slice(0, 5);

        // NỔ TIN NHẮN: Phát âm thanh và Toast khi số tin chưa đọc tăng lên
        if (!isInitialLoad && totalUnread > prevUnreadCount) {
          const newSender = list.find((c) => (c.unreadStaffCount || 0) > 0) || recent[0];
          playNotificationSound();
          if (newSender) {
            const preview = newSender.lastMessagePreview || 'Đã gửi tin nhắn trực tiếp mới';
            showToast(`💬 ${newSender.userName || 'Khách hàng'}: "${preview}"`, 'info', 5000);
          }
        }
        isInitialLoad = false;
        prevUnreadCount = totalUnread;

        notifySubscribers({
          totalUnreadCount: totalUnread,
          unreadConversationsCount: unreadConvs,
          recentConversations: recent,
          conversations: list,
        });
      }
    } catch {
      // Bỏ qua lỗi mạng nền
    } finally {
      if (isMountedRef.current) setLoading(false);
    }
  }, [isStaff]);

  useEffect(() => {
    isMountedRef.current = true;
    if (!isStaff) return;

    const handleUpdate = (data) => {
      if (isMountedRef.current) setState(data);
    };
    subscribers.add(handleUpdate);

    // Fetch ngay khi mount
    fetchConversations();

    // Polling định kỳ mỗi 3 giây để cập nhật tin nhắn theo thời gian thực
    const interval = setInterval(() => {
      fetchConversations();
    }, 3000);

    const handleFocus = () => fetchConversations(true);
    const handleCustomRefresh = () => fetchConversations(true);
    window.addEventListener('focus', handleFocus);
    window.addEventListener('cani_staff_chat_refresh', handleCustomRefresh);

    return () => {
      isMountedRef.current = false;
      subscribers.delete(handleUpdate);
      clearInterval(interval);
      window.removeEventListener('focus', handleFocus);
      window.removeEventListener('cani_staff_chat_refresh', handleCustomRefresh);
    };
  }, [isStaff, fetchConversations]);

  return {
    conversations: state.conversations,
    recentConversations: state.recentConversations,
    totalUnread: state.totalUnreadCount,
    unreadConversations: state.unreadConversationsCount,
    loading,
    refresh: () => fetchConversations(true),
  };
};

export default useStaffChatAlerts;
