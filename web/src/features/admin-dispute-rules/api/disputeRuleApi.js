import { apiClient } from '../../../services/api';

/**
 * API service quản lý bộ quy tắc xử lý khiếu nại (Admin Dispute Rules)
 */
export const disputeRuleApi = {
  getRules: async (params) => {
    const response = await apiClient.get('/v1/admin/dispute-rules', { params });
    return response.data?.data || [];
  },

  createRule: async (payload) => {
    const response = await apiClient.post('/v1/admin/dispute-rules', payload);
    return response.data?.data;
  },

  updateRule: async (id, payload) => {
    const response = await apiClient.put(`/v1/admin/dispute-rules/${id}`, payload);
    return response.data?.data;
  },

  deleteRule: async (id) => {
    const response = await apiClient.delete(`/v1/admin/dispute-rules/${id}`);
    return response.data;
  },
};
