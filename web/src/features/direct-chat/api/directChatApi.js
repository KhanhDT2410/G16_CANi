import { apiClient } from '../../../services/api';

/**
 * Direct & Shift Chat API Service
 * Trao đổi trực tiếp giữa Event Organizer (EO) và Nhân sự (Worker).
 */
export const directChatApi = {
  /** Lấy danh sách toàn bộ các cuộc trò chuyện của tôi */
  getMyConversations: async () => {
    const res = await apiClient.get('/v1/chat/conversations');
    return res.data?.data || [];
  },

  /** Mở hoặc khởi tạo chat 1-1 với một người dùng (Worker / EO) */
  openDirectConversation: async (targetUserId) => {
    const res = await apiClient.post(`/v1/chat/direct/${targetUserId}`);
    return res.data?.data;
  },

  /** Mở hoặc khởi tạo chat theo Ca làm việc (shiftId) */
  openShiftConversation: async (shiftId) => {
    const res = await apiClient.post(`/v1/chat/shift/${shiftId}`);
    return res.data?.data;
  },

  /** Lấy chi tiết tin nhắn trong một cuộc hội thoại và đánh dấu đã đọc */
  getConversationDetail: async (conversationId) => {
    const res = await apiClient.get(`/v1/chat/conversations/${conversationId}`);
    return res.data?.data;
  },

  /** Gửi tin nhắn vào cuộc hội thoại */
  sendMessage: async (conversationId, payload) => {
    const res = await apiClient.post(`/v1/chat/conversations/${conversationId}/messages`, payload);
    return res.data?.data;
  },
};

export default directChatApi;
