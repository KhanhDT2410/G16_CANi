import { apiClient } from '../../../services/api';

/**
 * Support Ticket API Service (SRS FE-12: UC-105 / UC-106 · SCR-25 / SCR-26)
 */
export const supportTicketApi = {
  // === Endpoints cho Người dùng (Worker / Event Organizer) ===

  /** UC-105: Nộp ticket hỗ trợ */
  createTicket: async (payload) => {
    const response = await apiClient.post('/v1/support/tickets', payload);
    return response.data?.data;
  },

  /** UC-106: Danh sách ticket của tôi */
  getMyTickets: async () => {
    const response = await apiClient.get('/v1/support/tickets/my');
    return response.data?.data;
  },

  /** UC-106 / SCR-26: Xem chi tiết ticket kèm thread */
  getTicketDetail: async (id) => {
    const response = await apiClient.get(`/v1/support/tickets/${id}`);
    return response.data?.data;
  },

  /** UC-106: Phản hồi ticket từ phía người dùng */
  replyTicketUser: async (id, payload) => {
    const response = await apiClient.post(`/v1/support/tickets/${id}/reply`, payload);
    return response.data?.data;
  },

  /** UC-106: Người dùng đánh dấu đã giải quyết */
  resolveTicket: async (id) => {
    const response = await apiClient.post(`/v1/support/tickets/${id}/resolve`);
    return response.data?.data;
  },

  // === Endpoints cho Customer Care & Admin ===

  /** SCR-25: Lấy hàng đợi ticket cần xử lý (ưu tiên SLA at risk) */
  getQueue: async () => {
    const response = await apiClient.get('/v1/admin/support/tickets/queue');
    return response.data?.data;
  },

  /** SCR-25 / SCR-26: Phân công hoặc nhận ticket */
  assignTicket: async (id, targetCcUserId = null) => {
    const body = targetCcUserId ? { targetCcUserId } : {};
    const response = await apiClient.post(`/v1/admin/support/tickets/${id}/assign`, body);
    return response.data?.data;
  },

  /** SCR-26: CC/Admin phản hồi hoặc thêm ghi chú nội bộ */
  replyTicketAdmin: async (id, payload) => {
    const response = await apiClient.post(`/v1/admin/support/tickets/${id}/reply`, payload);
    return response.data?.data;
  },

  /** SCR-26: CC/Admin đóng ticket kèm lý do */
  closeTicket: async (id, rationale) => {
    const response = await apiClient.post(`/v1/admin/support/tickets/${id}/close`, { rationale });
    return response.data?.data;
  },
};

