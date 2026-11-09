import { apiClient } from '../../../services/api';

/**
 * API client cho Module No-show (UC-88)
 */
export const noShowApi = {
  /**
   * Lấy danh sách vi phạm No-show kèm bộ lọc và phân trang
   */
  getNoShows: async ({ keyword, workerId, eventId, fromDate, toDate, page = 0, size = 15 } = {}) => {
    const params = { page, size };
    if (keyword) params.keyword = keyword;
    if (workerId) params.workerId = workerId;
    if (eventId) params.eventId = eventId;
    if (fromDate) params.fromDate = fromDate;
    if (toDate) params.toDate = toDate;

    const response = await apiClient.get('/v1/admin/no-shows', { params });
    return response.data?.data;
  },

  /**
   * Lấy số liệu thống kê tổng hợp No-show
   */
  getStats: async () => {
    const response = await apiClient.get('/v1/admin/no-shows/stats');
    return response.data?.data;
  },

  /**
   * Lấy chi tiết một ca No-show
   */
  getDetail: async (id) => {
    const response = await apiClient.get(`/v1/admin/no-shows/${id}`);
    return response.data?.data;
  },

  /**
   * Đánh dấu No-show thủ công cho một ca làm việc
   */
  markNoShow: async (assignmentId, { notes } = {}) => {
    const response = await apiClient.post(`/v1/admin/no-shows/${assignmentId}/mark`, { notes });
    return response.data?.data;
  },

  /**
   * Kích hoạt quét No-show toàn hệ thống tức thì
   */
  triggerScan: async () => {
    const response = await apiClient.post('/v1/admin/no-shows/scan');
    return response.data?.data;
  },
};

