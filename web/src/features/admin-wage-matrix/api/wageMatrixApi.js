import { apiClient } from '../../../services/api';

/**
 * API client giao tiếp với AdminWageMatrixController (/api/v1/admin/wage-matrix)
 * Hỗ trợ các chức năng quản trị biểu thù lao theo chuẩn nền tảng (UC-34).
 */
export const wageMatrixApi = {
  /**
   * Lấy danh sách ma trận lương có phân trang và bộ lọc
   */
  getWageMatrixList: async (params = {}) => {
    const response = await apiClient.get('/v1/admin/wage-matrix', { params });
    return response.data?.data;
  },

  /**
   * Tạo mới một mức thù lao chuẩn cho tổ hợp City, JobType, TimeSlot, LeadTime
   */
  createWageMatrix: async (payload) => {
    const response = await apiClient.post('/v1/admin/wage-matrix', payload);
    return response.data?.data;
  },

  /**
   * Cập nhật một bản ghi cấu hình thù lao
   */
  updateWageMatrix: async (matrixId, payload) => {
    const response = await apiClient.put(`/v1/admin/wage-matrix/${matrixId}`, payload);
    return response.data?.data;
  },

  /**
   * Bật/Tắt trạng thái hoạt động của một dòng biểu thù lao
   */
  toggleActiveWageMatrix: async (matrixId) => {
    const response = await apiClient.patch(`/v1/admin/wage-matrix/${matrixId}/toggle-active`);
    return response.data?.data;
  },

  /**
   * Xóa một cấu hình ma trận thù lao
   */
  deleteWageMatrix: async (matrixId) => {
    const response = await apiClient.delete(`/v1/admin/wage-matrix/${matrixId}`);
    return response.data;
  },
};
