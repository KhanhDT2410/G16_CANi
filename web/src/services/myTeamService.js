import { apiClient } from './api';

export const myTeamService = {
  // Lấy danh sách thợ quen
  getMyTeam: async (search = '', favoritedOnly = false) => {
    const params = {};
    if (search && search.trim()) params.search = search.trim();
    if (favoritedOnly) params.favoritedOnly = true;
    const response = await apiClient.get('/v1/eo/my-team', { params });
    return response.data?.data || [];
  },

  // Chi tiết một thợ trong My Team
  getMyTeamMemberDetail: async (workerId) => {
    const response = await apiClient.get(`/v1/eo/my-team/${workerId}`);
    return response.data?.data;
  },

  // Thêm thợ vào My Team (qua workerId hoặc phone)
  addWorkerToMyTeam: async ({ workerId, phone, internalNote = '', isFavorited = false }) => {
    const response = await apiClient.post('/v1/eo/my-team', {
      workerId,
      phone,
      internalNote,
      isFavorited,
    });
    return response.data?.data;
  },

  // Cập nhật ghi chú, cờ yêu thích
  updateMyTeamMember: async (workerId, { internalNote, isFavorited, isBlacklisted }) => {
    const response = await apiClient.put(`/v1/eo/my-team/${workerId}`, {
      internalNote,
      isFavorited,
      isBlacklisted,
    });
    return response.data?.data;
  },

  // Xóa thợ khỏi My Team
  removeWorkerFromMyTeam: async (workerId) => {
    const response = await apiClient.delete(`/v1/eo/my-team/${workerId}`);
    return response.data;
  },
};
