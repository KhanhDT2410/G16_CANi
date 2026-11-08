import { apiClient } from '../../../services/api';

/**
 * FAQ API Service (SRS FE-12: UC-110 Quản lý Câu hỏi thường gặp & Help Center)
 */
export const faqApi = {
  // === Public Endpoints (Help Center cho mọi người dùng) ===

  /** UC-110b: Danh sách FAQ công khai đã xuất bản */
  getPublicFaqs: async (category) => {
    const params = category ? { category } : {};
    const response = await apiClient.get('/v1/faqs', { params });
    return response.data?.data;
  },

  /** UC-110b: Xem chi tiết FAQ công khai (tự động tăng view_count) */
  getPublicFaqById: async (id) => {
    const response = await apiClient.get(`/v1/faqs/${id}`);
    return response.data?.data;
  },

  // === Admin & Customer Care Endpoints ===

  /** UC-110a: Danh sách quản lý FAQ có lọc và phân trang */
  getFaqs: async (params = {}) => {
    const response = await apiClient.get('/v1/admin/faqs', { params });
    return response.data?.data;
  },

  /** UC-110a: Chi tiết FAQ quản trị */
  getFaqById: async (id) => {
    const response = await apiClient.get(`/v1/admin/faqs/${id}`);
    return response.data?.data;
  },

  /** UC-110a: Tạo câu hỏi thường gặp mới */
  createFaq: async (payload) => {
    const response = await apiClient.post('/v1/admin/faqs', payload);
    return response.data?.data;
  },

  /** UC-110a: Cập nhật nội dung câu hỏi thường gặp */
  updateFaq: async (id, payload) => {
    const response = await apiClient.put(`/v1/admin/faqs/${id}`, payload);
    return response.data?.data;
  },

  /** UC-110a: Xuất bản câu hỏi thường gặp */
  publishFaq: async (id) => {
    const response = await apiClient.post(`/v1/admin/faqs/${id}/publish`);
    return response.data?.data;
  },

  /** UC-110a: Lưu trữ câu hỏi thường gặp */
  archiveFaq: async (id) => {
    const response = await apiClient.post(`/v1/admin/faqs/${id}/archive`);
    return response.data?.data;
  },

  /** UC-110c: Lấy thống kê view count và deflection rate */
  getFaqAnalytics: async () => {
    const response = await apiClient.get('/v1/admin/faqs/analytics');
    return response.data?.data;
  },
};

