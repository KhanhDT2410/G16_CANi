import { apiClient } from '../../../services/api';

/**
 * API calls cho UC-114: Cấu hình tham số hệ thống (System Parameters Configuration)
 */
export const systemConfigApi = {
  /**
   * Lấy danh sách toàn bộ các tham số cấu hình hệ thống
   */
  getConfigs: async () => {
    const response = await apiClient.get('/v1/admin/configs');
    return response.data?.data || [];
  },

  /**
   * Lấy chi tiết thông tin một tham số cấu hình theo key
   */
  getConfigByKey: async (key) => {
    const response = await apiClient.get(`/v1/admin/configs/${encodeURIComponent(key)}`);
    return response.data?.data;
  },

  /**
   * Cập nhật giá trị một tham số cấu hình đơn lẻ
   * @param {string} key
   * @param {{ value: string, description?: string }} payload
   */
  updateConfig: async (key, payload) => {
    const response = await apiClient.put(
      `/v1/admin/configs/${encodeURIComponent(key)}`,
      payload
    );
    return response.data?.data;
  },

  /**
   * Cập nhật hàng loạt nhiều tham số cấu hình
   * @param {{ configs?: Record<string, string>, items?: Array<{ key: string, value: string, description?: string }> }} payload
   */
  bulkUpdateConfigs: async (payload) => {
    const response = await apiClient.put('/v1/admin/configs', payload);
    return response.data?.data || [];
  },
};

