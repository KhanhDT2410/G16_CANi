import { apiClient } from '../../../services/api';

/**
 * API calls cho UC-87: Quản trị người dùng (Admin User Management)
 */
export const userManagementApi = {
  /**
   * Lấy danh sách người dùng phân trang và lọc
   */
  getUsers: async (params = {}) => {
    const response = await apiClient.get('/v1/admin/users', { params });
    return response.data?.data;
  },

  /**
   * Xem chi tiết thông tin tài khoản người dùng kèm profile Worker / EO
   */
  getUserById: async (id) => {
    const response = await apiClient.get(`/v1/admin/users/${id}`);
    return response.data?.data;
  },

  /**
   * Cập nhật trạng thái người dùng (Khóa / Mở khóa) kèm lý do
   */
  updateUserStatus: async (id, { status, reason }) => {
    const response = await apiClient.patch(`/v1/admin/users/${id}/status`, {
      status,
      reason,
    });
    return response.data?.data;
  },
};
