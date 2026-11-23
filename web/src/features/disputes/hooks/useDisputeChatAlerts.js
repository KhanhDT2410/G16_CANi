import { useState, useEffect, useCallback } from 'react';
import { disputeApi } from '../api/disputeApi';

/**
 * Hook quản lý cảnh báo tin nhắn tranh chấp mới (Unread Badges) cho Sidebar & Header.
 * Tuân thủ < 200 dòng theo AGENTS.md.
 */
export const useDisputeChatAlerts = (intervalMs = 20000) => {
  const [unreadDisputes, setUnreadDisputes] = useState(0);
  const [unreadMessages, setUnreadMessages] = useState(0);

  const fetchAlerts = useCallback(async () => {
    try {
      const summary = await disputeApi.getDisputeUnreadSummary();
      setUnreadDisputes(summary.totalUnreadDisputes || 0);
      setUnreadMessages(summary.totalUnreadMessages || 0);
    } catch {
      // Bỏ qua lỗi ngầm
    }
  }, []);

  useEffect(() => {
    fetchAlerts();
    const timer = setInterval(fetchAlerts, intervalMs);

    const handleDisputeUpdated = () => fetchAlerts();
    window.addEventListener('cani:dispute_chat_updated', handleDisputeUpdated);

    return () => {
      clearInterval(timer);
      window.removeEventListener('cani:dispute_chat_updated', handleDisputeUpdated);
    };
  }, [fetchAlerts, intervalMs]);

  return {
    unreadDisputes,
    unreadMessages,
    refreshDisputeAlerts: fetchAlerts,
  };
};

export default useDisputeChatAlerts;
