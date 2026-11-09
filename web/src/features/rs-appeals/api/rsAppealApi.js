import { apiClient } from '../../../services/api';

/**
 * API calls cho UC-94: RS Penalty & Appeal
 */
export const rsAppealApi = {
  /**
   * Hàng đợi đơn kháng cáo điểm uy tín (Admin & Customer Care)
   */
  getAdminAppeals: async ({ status, page = 0, size = 15 } = {}) => {
    const params = { page, size };
    if (status) params.status = status;
    const response = await apiClient.get('/v1/admin/appeals', { params });
    return response.data?.data;
  },

  /**
   * Xem chi tiết một đơn kháng cáo
   */
  getAppealById: async (id) => {
    const response = await apiClient.get(`/v1/admin/appeals/${id}`);
    return response.data?.data;
  },

  /**
   * Phê duyệt hoặc từ chối kháng cáo (Admin & Customer Care)
   */
  reviewAppeal: async (id, { approved, decisionNote }) => {
    const response = await apiClient.post(`/v1/admin/appeals/${id}/review`, {
      approved,
      decisionNote,
    });
    return response.data?.data;
  },

  /**
   * Worker nộp đơn kháng cáo
   */
  submitAppeal: async (payload) => {
    const response = await apiClient.post('/v1/workers/appeals', payload);
    return response.data?.data;
  },

  /**
   * Worker xem các đơn của mình
   */
  getWorkerAppeals: async () => {
    const response = await apiClient.get('/v1/workers/appeals');
    return response.data?.data || [];
  },
};

