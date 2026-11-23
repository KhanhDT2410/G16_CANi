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
  ACCOUNT: { label: 'Tài khoản & Hồ sơ', badgeClass: 'faq-badge-account' },
  SHIFT: { label: 'Ca làm & Lịch trình', badgeClass: 'faq-badge-shift' },
  PAYMENT: { label: 'Thanh toán & Thù lao', badgeClass: 'faq-badge-payment' },
  DISPUTE: { label: 'Tranh chấp & Khiếu nại', badgeClass: 'faq-badge-dispute' },
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

export const FAQ_TARGET_ROLE = {
  ALL: 'ALL',
  WORKER: 'WORKER',
  EVENT_ORGANIZER: 'EVENT_ORGANIZER',
};

export const FAQ_TARGET_ROLE_CONFIG = {
  ALL: {
    label: 'Tất cả đối tượng',
    shortLabel: 'Tất cả',
    badgeClass: 'faq-role-badge-all',
  },
  WORKER: {
    label: 'Người tìm việc',
    shortLabel: 'Nhân sự',
    badgeClass: 'faq-role-badge-worker',
  },
  EVENT_ORGANIZER: {
    label: 'Nhà tổ chức sự kiện (Organizer)',
    shortLabel: 'Nhà tổ chức',
    badgeClass: 'faq-role-badge-eo',
  },
};

