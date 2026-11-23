import { apiClient } from '../../../services/api';

/**
 * API Service cho Module Kháng Cáo / Giải Trình Tài Khoản Bị Khóa
 */
export const accountAppealApi = {
  // Public: Nộp đơn giải trình kèm chứng từ khắc phục
  submitAppeal: async (payload) => {
    const response = await apiClient.post('/v1/public/account-appeals/submit', payload);
    return response.data?.data;
  },

  // Public: Tra cứu tiến độ đơn giải trình mới nhất theo Email
  getAppealStatus: async (email) => {
    const response = await apiClient.get('/v1/public/account-appeals/status', {
      params: { email: email.trim().toLowerCase() },
    });
    return response.data?.data;
  },

  // Public: Lấy thông tin trạng thái & lý do khóa theo Email
  getLockInfo: async (email) => {
    const response = await apiClient.get('/v1/public/account-appeals/lock-info', {
      params: { email: email.trim().toLowerCase() },
    });
    return response.data?.data;
  },

  // Public: Upload file tài liệu minh chứng
  uploadEvidence: async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await apiClient.post('/v1/public/account-appeals/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data?.data?.url;
  },

  // Admin: Lấy số lượng đơn giải trình chờ duyệt (cho badge thông báo)
  getPendingCount: async () => {
    try {
      const response = await apiClient.get('/v1/admin/account-appeals/pending-count');
      return response.data?.data?.pendingCount ?? 0;
    } catch {
      return 0;
    }
  },

  // Admin: Lấy danh sách đơn giải trình
  getAdminAppeals: async ({ status = '', page = 0, size = 15 } = {}) => {
    const params = { page, size };
    if (status) params.status = status;
    const response = await apiClient.get('/v1/admin/account-appeals', { params });
    return response.data?.data;
  },

  // Admin: Lấy chi tiết một đơn giải trình
  getAppealDetail: async (id) => {
    const response = await apiClient.get(`/v1/admin/account-appeals/${id}`);
    return response.data?.data;
  },

  // Admin: Thẩm định phê duyệt mở khóa hoặc từ chối đơn
  reviewAppeal: async (id, { decision, adminNotes }) => {
    const response = await apiClient.put(`/v1/admin/account-appeals/${id}/review`, {
      decision,
      adminNotes,
    });
    return response.data?.data;
  },
};
