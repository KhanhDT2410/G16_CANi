import { useState, useEffect, useCallback } from 'react';
import { disputeApi } from '../api/disputeApi';

/**
 * Custom hook quản lý trạng thái tải & gửi tin nhắn đối chất thời gian thực cho một tranh chấp.
 */
export const useDisputeChat = (disputeId, isArbitrator) => {
  const [chatData, setChatData] = useState(null);
  const [isSending, setIsSending] = useState(false);

  const fetchChat = useCallback(async (isSilent = false) => {
    if (!disputeId) return;
    try {
      const data = await disputeApi.getDisputeChat(disputeId);
      if (data) {
        setChatData((prev) => {
          if (!prev) return data;
          const oldW = prev.workerConversation?.messages?.length || 0;
          const newW = data.workerConversation?.messages?.length || 0;
          const oldE = prev.eoConversation?.messages?.length || 0;
          const newE = data.eoConversation?.messages?.length || 0;
          if (oldW === newW && oldE === newE && prev.disputeStatus === data.disputeStatus) return prev;
          return data;
        });
      }
    } catch (err) {
      if (!isSilent) console.error('Lỗi tải dữ liệu chat:', err);
    }
  }, [disputeId]);

  useEffect(() => {
    setChatData(null);
    fetchChat(false);
    const interval = setInterval(() => fetchChat(true), 2500);
    return () => clearInterval(interval);
  }, [fetchChat]);

  const sendMessage = async (payload) => {
    if (!disputeId) return;
    const target = payload.targetParty || 'WORKER';
    const tempId = `temp-${Date.now()}`;
    const optimisticMsg = {
      id: tempId,
      conversationId: target === 'WORKER'
        ? chatData?.workerConversation?.conversationId
        : chatData?.eoConversation?.conversationId,
      senderId: 'me',
      senderName: isArbitrator ? 'Quản trị viên' : 'Tôi',
      senderRole: isArbitrator ? 'ADMIN' : 'USER',
      messageText: payload.messageText,
      attachmentUrl: payload.attachmentUrl,
      isMe: true,
      createdAt: new Date().toISOString(),
    };

    setChatData((prev) => {
      if (!prev) return prev;
      const key = target === 'WORKER' ? 'workerConversation' : 'eoConversation';
      const conv = prev[key] || { party: target, messages: [] };
      return { ...prev, [key]: { ...conv, messages: [...(conv.messages || []), optimisticMsg] } };
    });

    setIsSending(true);
    try {
      const sentMsg = await disputeApi.sendDisputeChatMessage(disputeId, payload);
      if (sentMsg) {
        setChatData((prev) => {
          if (!prev) return prev;
          const key = target === 'WORKER' ? 'workerConversation' : 'eoConversation';
          const conv = prev[key];
          if (!conv) return prev;
          return {
            ...prev,
            [key]: {
              ...conv,
              messages: (conv.messages || []).map((m) => (m.id === tempId ? sentMsg : m)),
            },
          };
        });
      }
    } catch (err) {
      console.error('Lỗi gửi tin nhắn phân xử:', err);
    } finally {
      setIsSending(false);
    }
  };

  return { chatData, isSending, sendMessage };
};
