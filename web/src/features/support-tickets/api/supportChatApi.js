import { apiClient } from '../../../services/api';

/**
 * Support Live Chat API Service
 */
export const supportChatApi = {
  // === Dành cho Người dùng (Worker / Event Organizer) ===

  /** Lấy phiên chat và lịch sử tin nhắn của tôi */
  getMyConversation: async () => {
    const response = await apiClient.get('/v1/support/chat/my');
    return response.data?.data;
  },

  /** Người dùng gửi tin nhắn */
  sendUserMessage: async (payload) => {
    const response = await apiClient.post('/v1/support/chat/send', payload);
    return response.data?.data;
  },

  // === Dành cho Customer Care & Admin ===

  /** CSKH lấy danh sách hội thoại của người dùng */
  getStaffConversations: async (query = '') => {
    const params = query ? { q: query } : {};
    const response = await apiClient.get('/v1/admin/support/chat/conversations', { params });
    return response.data?.data;
  },

  /** CSKH mở hoặc khởi tạo cuộc trò chuyện với một người dùng (EO/Worker) theo userId */
  initiateStaffConversation: async (userId) => {
    const response = await apiClient.post(`/v1/admin/support/chat/initiate/${userId}`);
    return response.data?.data;
  },

  /** CSKH xem chi tiết hội thoại và đánh dấu đã đọc */
  getStaffConversationDetail: async (id) => {
    const response = await apiClient.get(`/v1/admin/support/chat/conversations/${id}`);
    return response.data?.data;
  },

  /** CSKH phản hồi tin nhắn cho Người dùng */
  sendStaffReply: async (id, payload) => {
    const response = await apiClient.post(`/v1/admin/support/chat/conversations/${id}/reply`, payload);
    return response.data?.data;
  },
};

export default supportChatApi;
