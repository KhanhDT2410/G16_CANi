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

  /**
   * Hủy ca làm việc cưỡng chế kèm lý do (Admin Force Cancel)
   */
  cancelShift: async (id, reason) => {
    const response = await apiClient.post(`/v1/admin/shifts/${id}/cancel`, { reason });
    return response.data?.data;
  },

  /**
   * Tạm đình chỉ ca làm việc kèm lý do (Admin Suspend)
   */
  suspendShift: async (id, reason) => {
    const response = await apiClient.post(`/v1/admin/shifts/${id}/suspend`, { reason });
    return response.data?.data;
  },

  /**
   * Gửi cảnh báo/nhắc nhở trực tiếp tới EO của ca (Admin Warn EO)
   */
  warnEo: async (id, reason) => {
    const response = await apiClient.post(`/v1/admin/shifts/${id}/warn`, { reason });
    return response.data?.data;
  },
};
