import { apiClient } from './api';

export const blockListService = {
  // Xem danh sách người dùng đã chặn
  getBlockedList: async () => {
    const response = await apiClient.get('/v1/blocks');
    return response.data?.data || [];
  },

  // Chặn một người dùng (Event Organizer hoặc Worker)
  blockUser: async (blockedId, reason = '') => {
    const response = await apiClient.post('/v1/blocks', {
      blockedId,
      reason,
    });
    return response.data?.data;
  },

  // Bỏ chặn người dùng
  unblockUser: async (blockedUserId) => {
    const response = await apiClient.delete(`/v1/blocks/${blockedUserId}`);
    return response.data;
  },
};
