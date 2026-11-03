/**
 * Hằng số và cấu hình cho phân hệ Support Tickets (SRS FE-12: UC-105 / UC-106 · SCR-25 / SCR-26)
 */

export const TICKET_STATUS = {
  OPEN: 'OPEN',
  IN_PROGRESS: 'IN_PROGRESS',
  CLOSED: 'CLOSED',
};

export const TICKET_STATUS_CONFIG = {
  OPEN: { label: 'Mới mở', badgeClass: 'ticket-badge-open' },
  IN_PROGRESS: { label: 'Đang xử lý', badgeClass: 'ticket-badge-in-progress' },
  CLOSED: { label: 'Đã đóng', badgeClass: 'ticket-badge-closed' },
};

export const TICKET_CATEGORIES = {
  ACCOUNT: 'ACCOUNT',
  BILLING: 'BILLING',
  TECHNICAL: 'TECHNICAL',
  GENERAL: 'GENERAL',
  OTHER: 'OTHER',
};

export const TICKET_CATEGORY_CONFIG = {
  ACCOUNT: { label: 'Tài khoản & Định danh', code: 'ACCOUNT' },
  BILLING: { label: 'Thanh toán & Ví thù lao', code: 'BILLING' },
  TECHNICAL: { label: 'Sự cố kỹ thuật hệ thống', code: 'TECHNICAL' },
  GENERAL: { label: 'Hỏi đáp & Hướng dẫn', code: 'GENERAL' },
  OTHER: { label: 'Yêu cầu khác', code: 'OTHER' },
};

export const TICKET_PRIORITIES = {
  HIGH: 'HIGH',
  MEDIUM: 'MEDIUM',
  LOW: 'LOW',
};

export const TICKET_PRIORITY_CONFIG = {
  HIGH: { label: 'Cao (SLA 4h)', badgeClass: 'ticket-badge-high' },
  MEDIUM: { label: 'Trung bình (SLA 1 ngày)', badgeClass: 'ticket-badge-medium' },
  LOW: { label: 'Thấp (SLA 3 ngày)', badgeClass: 'ticket-badge-low' },
};

