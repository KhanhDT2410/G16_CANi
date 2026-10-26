import { apiClient } from '../../../services/api';

/**
 * API functions cho Admin Dashboard (W-38).
 * Tất cả endpoint trả về mock fallback khi backend chưa sẵn sàng.
 */

/** Lấy tổng quan chỉ số nền tảng */
export const fetchDashboardMetrics = () =>
  apiClient.get('/v1/admin/dashboard/metrics');

/** Lấy danh sách sự kiện đang hoạt động */
export const fetchActiveEvents = () =>
  apiClient.get('/v1/admin/dashboard/active-events');

/** Lấy danh sách tranh chấp gần đây */
export const fetchRecentDisputes = () =>
  apiClient.get('/v1/admin/dashboard/recent-disputes');

/** Lấy trạng thái sức khỏe hệ thống */
export const fetchSystemHealth = () =>
  apiClient.get('/v1/admin/dashboard/system-health');

/** Lấy thống kê vận hành CSKH */
export const fetchCskhOperations = () =>
  apiClient.get('/v1/admin/dashboard/cskh-operations');
