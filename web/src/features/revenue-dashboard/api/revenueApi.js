import { apiClient } from '../../../services/api';

/**
 * API calls cho UC-108: Revenue Dashboard (Bảng theo dõi và phân tích doanh thu)
 */
export const revenueApi = {
  /**
   * Lấy tổng quan doanh thu, so sánh cùng kỳ, phân loại gói & sự kiện, tổng hợp VNPay
   */
  getRevenueSummary: async (from, to) => {
    const params = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await apiClient.get('/v1/admin/revenue/summary', { params });
    return response.data?.data;
  },

  /**
   * Lấy danh sách giao dịch chi tiết kèm phân trang và bộ lọc
   */
  getRevenueTransactions: async ({ from, to, status, packageType, page = 0, size = 15 } = {}) => {
    const params = { page, size };
    if (from) params.from = from;
    if (to) params.to = to;
    if (status) params.status = status;
    if (packageType) params.packageType = packageType;
    const response = await apiClient.get('/v1/admin/revenue/transactions', { params });
    return response.data?.data;
  },

  /**
   * Lấy dữ liệu phân tích xu hướng theo chu kỳ (day, week, month)
   */
  getRevenueAnalytics: async ({ period = 'day', from, to } = {}) => {
    const params = { period };
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await apiClient.get('/v1/admin/revenue/analytics', { params });
    return response.data?.data;
  },
};

