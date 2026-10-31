import { apiClient } from '../../../services/api';

/**
 * Dispute & Resolution API Service (SRS FE-11: UC-96 -> UC-104 & UC-109)
 */
export const disputeApi = {
  // === Endpoints cho Bên liên quan (Worker / Event Organizer) ===

  /** UC-96: Nộp tranh chấp */
  createDispute: async (payload) => {
    const response = await apiClient.post('/v1/disputes', payload);
    return response.data?.data;
  },

  /** UC-97: Xem chi tiết tranh chấp */
  getDisputeDetail: async (id) => {
    const response = await apiClient.get(`/v1/disputes/${id}`);
    return response.data?.data;
  },

  /** UC-104: Lịch sử tranh chấp của tôi */
  getMyDisputes: async () => {
    const response = await apiClient.get('/v1/disputes/my');
    return response.data?.data;
  },

  /** UC-98: Nạp bổ sung bằng chứng */
  addEvidence: async (id, payload) => {
    const response = await apiClient.post(`/v1/disputes/${id}/evidence`, payload);
    return response.data?.data;
  },

  /** UC-101: Kháng nghị quyết định CC lên Admin */
  escalateDispute: async (id, payload) => {
    const response = await apiClient.post(`/v1/disputes/${id}/escalate`, payload);
    return response.data?.data;
  },

  // === Endpoints cho Customer Care & Admin ===

  /** UC-109: Hàng đợi tranh chấp chưa xử lý (sắp xếp ưu tiên SLA) */
  getDisputeQueue: async () => {
    const response = await apiClient.get('/v1/admin/disputes/queue');
    return response.data?.data;
  },

  /** UC-99: Nhận đơn hoặc chuyển đơn */
  assignDispute: async (id, targetCcUserId = null) => {
    const body = targetCcUserId ? { targetCcUserId } : null;
    const response = await apiClient.post(`/v1/admin/disputes/${id}/assign`, body);
    return response.data?.data;
  },

  /** UC-100 (CC) & UC-102 (Admin): Ra quyết định giải quyết tranh chấp */
  issueDecision: async (id, payload) => {
    const response = await apiClient.post(`/v1/admin/disputes/${id}/decision`, payload);
    return response.data?.data;
  },
};

