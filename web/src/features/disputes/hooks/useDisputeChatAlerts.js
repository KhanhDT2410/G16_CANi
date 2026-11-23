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
    const isTabVisible = () =>
      typeof document === 'undefined' || document.visibilityState === 'visible';

    if (isTabVisible()) {
      fetchAlerts();
    }

    const timer = setInterval(() => {
      if (isTabVisible()) {
        fetchAlerts();
      }
    }, intervalMs);

    const handleDisputeUpdated = () => {
      if (isTabVisible()) {
        fetchAlerts();
      }
    };

    const handleVisibilityChange = () => {
      if (isTabVisible()) {
        fetchAlerts();
      }
    };

    window.addEventListener('cani:dispute_chat_updated', handleDisputeUpdated);
    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      clearInterval(timer);
      window.removeEventListener('cani:dispute_chat_updated', handleDisputeUpdated);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [fetchAlerts, intervalMs]);

  return {
    unreadDisputes,
    unreadMessages,
    refreshDisputeAlerts: fetchAlerts,
  };
};

export default useDisputeChatAlerts;
