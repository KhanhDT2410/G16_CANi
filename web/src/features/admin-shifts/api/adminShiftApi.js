import { apiClient } from '../../../services/api';

/**
 * API calls cho Quản trị Ca làm việc (Admin Shift Management - View only)
 */
export const adminShiftApi = {
  /**
   * Lấy danh sách ca làm việc (tìm kiếm, lọc trạng thái, phân trang)
   */
  getShifts: async (params = {}) => {
    const response = await apiClient.get('/v1/admin/shifts', { params });
    return response.data?.data;
  },

  /**
   * Xem chi tiết ca làm việc kèm danh sách Worker tham gia
   */
  getShiftDetail: async (id) => {
    const response = await apiClient.get(`/v1/admin/shifts/${id}`);
    return response.data?.data;
  },
};
