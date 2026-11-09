/**
 * Constants & Enums cho Module Quản lý Vi phạm No-show (UC-88)
 */

export const NO_SHOW_DETECTED_BY = {
  SYSTEM_CRON: 'SYSTEM_CRON',
  ADMIN: 'ADMIN',
  CUSTOMER_CARE: 'CUSTOMER_CARE',
  EVENT_ORGANIZER: 'EVENT_ORGANIZER',
};

export const NO_SHOW_DETECTED_BY_LABELS = {
  SYSTEM_CRON: 'Hệ thống tự động',
  ADMIN: 'Quản trị viên',
  CUSTOMER_CARE: 'Chăm sóc khách hàng',
  EVENT_ORGANIZER: 'Nhà tổ chức (EO)',
};

export const NO_SHOW_DETECTED_BY_BADGE = {
  SYSTEM_CRON: 'badge-sys',
  ADMIN: 'badge-admin',
  CUSTOMER_CARE: 'badge-cc',
  EVENT_ORGANIZER: 'badge-eo',
};

export const APPEAL_STATUS_LABELS = {
  SUBMITTED: 'Chờ xét duyệt',
  UNDER_REVIEW: 'Đang thẩm tra',
  APPROVED: 'Đã chấp thuận',
  REJECTED: 'Đã từ chối',
};

export const APPEAL_STATUS_BADGE = {
  SUBMITTED: 'badge-warning',
  UNDER_REVIEW: 'badge-info',
  APPROVED: 'badge-success',
  REJECTED: 'badge-danger',
};

