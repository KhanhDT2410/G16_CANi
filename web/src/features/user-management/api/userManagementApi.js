import { apiClient } from '../../../services/api';

/**
 * API functions cho W-39: User Management (UC-112).
 * Phục vụ tìm kiếm, xem hồ sơ, đổi vai trò, đặt lại mật khẩu,
 * tạm khóa (suspend), cấm (ban) và vô hiệu hóa (deactivate).
 * KHÔNG CÓ endpoint xóa cứng (hard delete) nhằm đảm bảo audit trail integrity.
 */

/** Lấy chỉ số tổng quan người dùng */
export const fetchUserMetrics = () =>
  apiClient.get('/v1/admin/users/metrics');

/** Lấy danh sách người dùng kèm bộ lọc */
export const fetchUsers = (params) =>
  apiClient.get('/v1/admin/users', { params });

/** Lấy chi tiết hồ sơ người dùng */
export const fetchUserDetails = (userId) =>
  apiClient.get(`/v1/admin/users/${userId}`);

/** Cập nhật vai trò người dùng (Worker, EO, CSKH, Admin) */
export const updateUserRole = (userId, data) =>
  apiClient.patch(`/v1/admin/users/${userId}/role`, data);

/** Buộc đặt lại mật khẩu (gửi link email hoặc cấp pass tạm) */
export const forceResetPassword = (userId, data) =>
  apiClient.post(`/v1/admin/users/${userId}/force-reset-password`, data);

/** Tạm khóa tài khoản có thời hạn (Suspend) */
export const suspendUser = (userId, data) =>
  apiClient.post(`/v1/admin/users/${userId}/suspend`, data);

/** Cấm tài khoản vĩnh viễn (Ban) */
export const banUser = (userId, data) =>
  apiClient.post(`/v1/admin/users/${userId}/ban`, data);

/** Vô hiệu hóa tài khoản (Deactivate - không xóa vật lý) */
export const deactivateUser = (userId, data) =>
  apiClient.post(`/v1/admin/users/${userId}/deactivate`, data);

/** Mở khóa / Kích hoạt lại tài khoản (Reactivate/Unban) */
export const reactivateUser = (userId, data) =>
  apiClient.post(`/v1/admin/users/${userId}/reactivate`, data);
