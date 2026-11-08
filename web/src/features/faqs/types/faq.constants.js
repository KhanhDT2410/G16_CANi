/**
 * Hằng số và cấu hình cho phân hệ Quản lý FAQ & Help Center (SRS FE-12, UC-110).
 */

export const FAQ_CATEGORY = {
  ACCOUNT: 'ACCOUNT',
  SHIFT: 'SHIFT',
  PAYMENT: 'PAYMENT',
  DISPUTE: 'DISPUTE',
};

export const FAQ_CATEGORY_CONFIG = {
  ACCOUNT: { label: 'Tài khoản & Hồ sơ', badgeClass: 'faq-badge-account', icon: '👤' },
  SHIFT: { label: 'Ca làm & Lịch trình', badgeClass: 'faq-badge-shift', icon: '📅' },
  PAYMENT: { label: 'Thanh toán & Thù lao', badgeClass: 'faq-badge-payment', icon: '💳' },
  DISPUTE: { label: 'Tranh chấp & Khiếu nại', badgeClass: 'faq-badge-dispute', icon: '⚖️' },
};

export const FAQ_STATUS = {
  DRAFT: 'DRAFT',
  PUBLISHED: 'PUBLISHED',
  ARCHIVED: 'ARCHIVED',
};

export const FAQ_STATUS_CONFIG = {
  DRAFT: { label: 'Bản nháp', badgeClass: 'faq-status-draft' },
  PUBLISHED: { label: 'Đã xuất bản', badgeClass: 'faq-status-published' },
  ARCHIVED: { label: 'Đã lưu trữ', badgeClass: 'faq-status-archived' },
};

