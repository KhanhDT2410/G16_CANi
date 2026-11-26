import { apiClient } from '../../../services/api';

/**
 * Admin Skill API Service
 */
export const adminSkillApi = {
  /**
   * Lấy danh sách kỹ năng có tìm kiếm, lọc danh mục, trạng thái và phân trang
   */
  getSkills: async (params = {}) => {
    const response = await apiClient.get('/v1/admin/skills', { params });
    return response.data?.data;
  },

  /**
   * Lấy danh sách các nhóm danh mục hiện có
   */
  getCategories: async () => {
    const response = await apiClient.get('/v1/admin/skills/categories');
    return response.data?.data;
  },

  /**
   * Tạo kỹ năng mới
   */
  createSkill: async (payload) => {
    const response = await apiClient.post('/v1/admin/skills', payload);
    return response.data?.data;
  },

  /**
   * Cập nhật thông tin kỹ năng
   */
  updateSkill: async (id, payload) => {
    const response = await apiClient.put(`/v1/admin/skills/${id}`, payload);
    return response.data?.data;
  },

  /**
   * Bật / Tắt trạng thái hoạt động của kỹ năng
   */
  toggleStatus: async (id) => {
    const response = await apiClient.patch(`/v1/admin/skills/${id}/toggle-status`);
    return response.data?.data;
  },
};
