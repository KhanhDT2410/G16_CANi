import { apiClient } from '../../../services/api';

/**
 * API functions cho System Configuration (W-41 · UC-114/115/116).
 * Hook sẽ tự fallback sang DEFAULT_CONFIG khi backend chưa có endpoint.
 */

/** Lấy cấu hình hệ thống hiện tại (đủ các nhóm: pricing, matching, attendance, rating, rs, sp, flags, cache, maintenance). */
export const fetchSystemConfig = () =>
  apiClient.get('/v1/admin/system/config');

/** Lưu cấu hình hệ thống (validate type + range phía backend, ghi audit log - BR-70). */
export const saveSystemConfig = (payload) =>
  apiClient.put('/v1/admin/system/config', payload);

/** Lấy nhật ký kiểm toán cấu hình (BR-70: bất biến, 60 tháng). */
export const fetchConfigAuditLogs = () =>
  apiClient.get('/v1/admin/system/config/audit-logs');

/** Vô hiệu hóa cache thủ công. */
export const invalidateCache = () =>
  apiClient.post('/v1/admin/system/cache/invalidate');
