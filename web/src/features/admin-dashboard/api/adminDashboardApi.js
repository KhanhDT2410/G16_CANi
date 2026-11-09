import { apiClient } from '../../../services/api';

/**
 * API service cho UC-86: Bảng Điều Khiển & Số Liệu Vận Hành Admin
 */
export const adminDashboardApi = {
  /**
   * Lấy tổng hợp các chỉ số vận hành nền tảng (User, Verifications, Operations)
   */
  getMetrics: async () => {
    const response = await apiClient.get('/v1/admin/dashboard/metrics');
    return response.data?.data;
  },

  /**
   * Lấy 10 hoạt động quản trị gần nhất từ bảng audit_logs
   */
  getRecentActivities: async () => {
    const response = await apiClient.get('/v1/admin/dashboard/recent-activities');
    return response.data?.data || [];
  },
};

