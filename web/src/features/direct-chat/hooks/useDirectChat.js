import { useState, useEffect, useCallback, useMemo } from 'react';
import { directChatApi } from '../api/directChatApi';

/**
 * Custom hook quản lý danh sách các cuộc hội thoại trao đổi của EO/Worker.
 * Hỗ trợ tự động Polling định kỳ cập nhật tin nhắn mới & badge chưa đọc.
 */
export const useDirectChat = (pollIntervalMs = 4000) => {
  const [conversations, setConversations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeChat, setActiveChat] = useState(null);
  const [filterType, setFilterType] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');

  const fetchConversations = useCallback(async (isSilent = false) => {
    if (!isSilent) setLoading(true);
    try {
      const data = await directChatApi.getMyConversations();
      setConversations(data || []);
    } catch {
      /* Bỏ qua lỗi nền */
    } finally {
      if (!isSilent) setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchConversations(false);
    const interval = setInterval(() => {
      fetchConversations(true);
    }, pollIntervalMs);
    return () => clearInterval(interval);
  }, [fetchConversations, pollIntervalMs]);

  // Lắng nghe sự kiện toàn cục để mở nhanh chat với một người hoặc theo ca
  useEffect(() => {
    const handleOpenChat = async (e) => {
      const detail = e.detail;
      if (!detail) return;
      try {
        let res;
        if (detail.shiftId) {
          res = await directChatApi.openShiftConversation(detail.shiftId);
        } else if (detail.targetUserId) {
          res = await directChatApi.openDirectConversation(detail.targetUserId);
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

  const counts = useMemo(() => ({
    all: conversations.length,
    shift: conversations.filter((c) => c.type === 'SHIFT').length,
    direct: conversations.filter((c) => c.type === 'DIRECT').length,
    unread: conversations.filter((c) => (c.unreadCount || 0) > 0).length,
  }), [conversations]);

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
    refresh: () => fetchConversations(false),
  };
};

export default useDirectChat;
