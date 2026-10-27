import { apiClient } from '../../../services/api';

/**
 * API functions cho W-40: Roles & Permissions (RBAC) Management (UC-113).
 * Mọi thay đổi phân quyền có hiệu lực ngay lập tức và được ghi vết kiểm toán.
 */

/** Lấy danh sách tất cả các vai trò và quyền hạn được gán */
export const fetchRoles = () =>
  apiClient.get('/v1/admin/rbac/roles');

/** Lấy danh mục các phân hệ và quyền hạn chi tiết */
export const fetchPermissions = () =>
  apiClient.get('/v1/admin/rbac/permissions');

/** Lấy chỉ số thống kê phân quyền hệ thống */
export const fetchRbacMetrics = () =>
  apiClient.get('/v1/admin/rbac/metrics');

/** Cập nhật danh sách quyền cho một vai trò */
export const updateRolePermissions = (roleId, permissionCodes, reason = '') =>
  apiClient.put(`/v1/admin/rbac/roles/${roleId}/permissions`, {
    permissionCodes,
    reason,
  });

/** Tạo vai trò tùy chỉnh mới (Custom Role) */
export const createCustomRole = (roleData) =>
  apiClient.post('/v1/admin/rbac/roles', roleData);

/** Xóa một vai trò tùy chỉnh (Không cho phép xóa vai trò hệ thống) */
export const deleteCustomRole = (roleId) =>
  apiClient.delete(`/v1/admin/rbac/roles/${roleId}`);

/** Khôi phục cấu hình phân quyền vai trò về mặc định */
export const resetRoleToDefault = (roleId) =>
  apiClient.post(`/v1/admin/rbac/roles/${roleId}/reset-default`);

/** Lấy nhật ký kiểm toán thay đổi phân quyền (Audit Logs) */
export const fetchRbacAuditLogs = (params) =>
  apiClient.get('/v1/admin/rbac/audit-logs', { params });
