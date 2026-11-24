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

  /** Danh sách ca phân công để chọn khi nộp đơn tranh chấp */
  getMyAssignments: async () => {
    const response = await apiClient.get('/v1/disputes/my-assignments');
    return response.data?.data || [];
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

  /** UC-109: Hàng đợi riêng — chỉ đơn đang được giao cho CC hiện tại */
  getMyDisputeQueue: async () => {
    const response = await apiClient.get('/v1/admin/disputes/queue/mine');
    return response.data?.data;
  },

  /** UC-99: Danh sách nhân sự CC để chuyển đơn */
  getCustomerCareStaff: async () => {
    const response = await apiClient.get('/v1/admin/disputes/customer-care-staff');
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

  // === Endpoints cho Kênh Chat Phân Xử Tranh Chấp (Dispute Dual Chat) ===

  /** Lấy dữ liệu hội thoại tranh chấp đa bên theo phân quyền */
  getDisputeChat: async (disputeId) => {
    const response = await apiClient.get(`/v1/disputes/${disputeId}/chat`);
    return response.data?.data;
  },

  /** Gửi tin nhắn trong phiên phân xử tranh chấp */
  sendDisputeChatMessage: async (disputeId, payload) => {
    const response = await apiClient.post(`/v1/disputes/${disputeId}/chat/messages`, payload);
    return response.data?.data;
  },

  /** Đánh dấu đã đọc tin nhắn trong phiên tranh chấp */
  markDisputeChatRead: async (disputeId, party = null) => {
    const url = party
      ? `/v1/disputes/${disputeId}/chat/read?party=${party}`
      : `/v1/disputes/${disputeId}/chat/read`;
    const response = await apiClient.post(url);
    return response.data?.data;
  },

  /** Lấy thống kê tổng số đơn có tin nhắn mới cho Sidebar */
  getDisputeUnreadSummary: async () => {
    const response = await apiClient.get('/v1/disputes/unread-summary');
    return response.data?.data || { totalUnreadDisputes: 0, totalUnreadMessages: 0 };
  },

  /** Lấy lịch sử vi phạm của người dùng để tính tái phạm */
  getViolationHistory: async (userId, days = 60) => {
    const response = await apiClient.get(`/v1/admin/users/${userId}/violation-history`, {
      params: { days },
    });
    return response.data?.data;
  },

  /** Đếm strike của EO trong 90 ngày */
  getEoStrikes: async (disputeId) => {
    const response = await apiClient.get(`/v1/admin/disputes/${disputeId}/eo-strikes`);
    return response.data?.data;
  },

  /** Admin điều chỉnh RS mức tùy ý */
  adjustReliability: async (userId, payload) => {
    const response = await apiClient.post(`/v1/admin/users/${userId}/reliability`, payload);
    return response.data?.data;
  },
};

