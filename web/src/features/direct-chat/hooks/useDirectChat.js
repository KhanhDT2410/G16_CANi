import { useState, useEffect, useCallback, useMemo } from 'react';
import { directChatApi } from '../api/directChatApi';

const parseUnreadConversationsCount = (payload) => {
  if (typeof payload === 'number') return Math.max(0, payload);
  if (payload && typeof payload === 'object') {
    const candidate =
      payload.unreadConversations ??
      payload.unreadConversationsCount ??
      payload.totalUnreadConversations ??
      payload.unreadCount ??
      payload.count ??
      payload.unreadMessages ??
      0;
    return Math.max(0, Number(candidate) || 0);
  }
  return Math.max(0, Number(payload) || 0);
};

const isDocumentVisible = () =>
  typeof document === 'undefined' || document.visibilityState === 'visible';

/**
 * Custom hook quản lý danh sách các cuộc hội thoại trao đổi của EO/Worker.
 * Khi chat đóng: chỉ gọi API đếm chưa đọc (/v1/chat/unread-count), 0 request lấy toàn bộ hội thoại.
 * Khi chat mở: tải & polling danh sách hội thoại (/v1/chat/conversations).
 * Tự động tạm dừng mọi polling khi tab trình duyệt bị ẩn.
 */
export const useDirectChat = (optionsOrInterval = {}) => {
  const normalizedOptions =
    typeof optionsOrInterval === 'number'
      ? { isOpen: true, pollIntervalMs: optionsOrInterval }
      : optionsOrInterval || {};

  const {
    isOpen = false,
    pollIntervalMs = 4000,
    unreadPollIntervalMs = 15000,
  } = normalizedOptions;

  const [conversations, setConversations] = useState([]);
  const [hasLoadedConversations, setHasLoadedConversations] = useState(false);
  const [unreadConversationsCount, setUnreadConversationsCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [activeChat, setActiveChat] = useState(null);
  const [filterType, setFilterType] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');

  const isChatActive = Boolean(isOpen || activeChat);

  const fetchUnreadCount = useCallback(async () => {
    if (!isDocumentVisible()) return;
    try {
      const data = await directChatApi.getUnreadCount();
      setUnreadConversationsCount(parseUnreadConversationsCount(data));
    } catch {
      /* Bỏ qua lỗi nền */
    }
  }, []);

  const fetchConversations = useCallback(async (isSilent = false) => {
    if (!isSilent) setLoading(true);
    try {
      const data = await directChatApi.getMyConversations();
      const list = Array.isArray(data) ? data : [];
      setConversations(list);
      setHasLoadedConversations(true);
      setUnreadConversationsCount(list.filter((c) => (c.unreadCount || 0) > 0).length);
    } catch {
      /* Bỏ qua lỗi nền */
    } finally {
      if (!isSilent) setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (isChatActive) {
      if (isDocumentVisible()) {
        fetchConversations(hasLoadedConversations);
      }
      const interval = setInterval(() => {
        if (isDocumentVisible()) {
          fetchConversations(true);
        }
      }, pollIntervalMs);

      const handleVisibilityChange = () => {
        if (isDocumentVisible()) {
          fetchConversations(true);
        }
      };
      document.addEventListener('visibilitychange', handleVisibilityChange);

      return () => {
        clearInterval(interval);
        document.removeEventListener('visibilitychange', handleVisibilityChange);
      };
    }

    // Khi chat đóng: chỉ lấy unread-count nhẹ, tuyệt đối không gọi getMyConversations()
    fetchUnreadCount();
    const interval = setInterval(() => {
      if (isDocumentVisible()) {
        fetchUnreadCount();
      }
    }, unreadPollIntervalMs);

    const handleVisibilityChange = () => {
      if (isDocumentVisible()) {
        fetchUnreadCount();
      }
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      clearInterval(interval);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [
    isChatActive,
    hasLoadedConversations,
    fetchConversations,
    fetchUnreadCount,
    pollIntervalMs,
    unreadPollIntervalMs,
  ]);

  // Lắng nghe sự kiện toàn cục để mở nhanh chat với một người hoặc theo ca
  useEffect(() => {
    const handleOpenChat = async (e) => {
      const detail = e.detail;
      if (!detail) return;
      try {
        let res;
        if (detail.targetUserId) {
          res = await directChatApi.openDirectConversation(detail.targetUserId);
        } else if (detail.shiftId) {
          res = await directChatApi.openShiftConversation(detail.shiftId);
        }
        if (res?.conversation) {
          setActiveChat(res.conversation);
          fetchConversations(true);
        }
      } catch {
        /* Bỏ qua */
      }
    };

    window.addEventListener('cani_open_direct_chat', handleOpenChat);
    return () => window.removeEventListener('cani_open_direct_chat', handleOpenChat);
  }, [fetchConversations]);

  const counts = useMemo(() => {
    const computedUnread = conversations.filter((c) => (c.unreadCount || 0) > 0).length;
    return {
      all: conversations.length,
      shift: conversations.filter((c) => c.type === 'SHIFT').length,
      direct: conversations.filter((c) => c.type === 'DIRECT').length,
      unread: isChatActive && hasLoadedConversations ? computedUnread : unreadConversationsCount,
    };
  }, [conversations, isChatActive, hasLoadedConversations, unreadConversationsCount]);

  const filteredConversations = useMemo(() => {
    return conversations.filter((c) => {
      if (filterType === 'SHIFT' && c.type !== 'SHIFT') return false;
      if (filterType === 'DIRECT' && c.type !== 'DIRECT') return false;
      if (filterType === 'UNREAD' && (c.unreadCount || 0) <= 0) return false;
      if (!searchTerm.trim()) return true;

      const term = searchTerm.toLowerCase();
      return (
        c.targetUserName?.toLowerCase().includes(term) ||
        c.targetUserPhone?.toLowerCase().includes(term) ||
        c.shiftCode?.toLowerCase().includes(term) ||
        c.shiftName?.toLowerCase().includes(term)
      );
    });
  }, [conversations, filterType, searchTerm]);

  return {
    conversations: filteredConversations,
    rawConversations: conversations,
    loading,
    activeChat,
    setActiveChat,
    filterType,
    setFilterType,
    searchTerm,
    setSearchTerm,
    counts,
    refresh: () => (isChatActive ? fetchConversations(false) : fetchUnreadCount()),
  };
};

export default useDirectChat;
