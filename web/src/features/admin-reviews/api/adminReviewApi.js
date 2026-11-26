import { apiClient } from '../../../services/api';

/**
 * Admin Review API Service
 */
export const adminReviewApi = {
  /**
   * Lấy danh sách đánh giá có phân trang và bộ lọc
   */
  getReviews: async (params = {}) => {
    const response = await apiClient.get('/v1/admin/reviews', { params });
    return response.data?.data;
  },

  /**
   * Ẩn/gỡ bỏ đánh giá sai lệch kèm lý do
   */
  hideReview: async (id, reason) => {
    const response = await apiClient.post(`/v1/admin/reviews/${id}/hide`, { reason });
    return response.data?.data;
  },

  /**
   * Khôi phục hiển thị đánh giá đã ẩn
   */
  unhideReview: async (id) => {
    const response = await apiClient.post(`/v1/admin/reviews/${id}/unhide`);
    return response.data?.data;
  },

  /**
   * Gắn cờ nghi vấn vi phạm / spam cho đánh giá
   */
  flagReview: async (id, reason) => {
    const response = await apiClient.post(`/v1/admin/reviews/${id}/flag`, { reason });
    return response.data?.data;
  },

  /**
   * Gỡ cờ cảnh báo
   */
  unflagReview: async (id) => {
    const response = await apiClient.post(`/v1/admin/reviews/${id}/unflag`);
    return response.data?.data;
  },
};

