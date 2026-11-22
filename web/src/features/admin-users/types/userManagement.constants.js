export const USER_ROLES = {
  ADMIN: 'ADMIN',
  CUSTOMER_CARE: 'CUSTOMER_CARE',
  EVENT_ORGANIZER: 'EVENT_ORGANIZER',
  WORKER: 'WORKER',
};

export const USER_ROLE_LABELS = {
  ADMIN: { label: 'Quản trị viên', badgeClass: 'badge-role-admin' },
  CUSTOMER_CARE: { label: 'Chăm sóc khách hàng', badgeClass: 'badge-role-cc' },
  EVENT_ORGANIZER: { label: 'Nhà tổ chức sự kiện', badgeClass: 'badge-role-eo' },
  WORKER: { label: 'Người lao động', badgeClass: 'badge-role-worker' },
};

export const USER_STATUSES = {
  ACTIVE: 'ACTIVE',
  LOCKED: 'LOCKED',
  SUSPENDED: 'SUSPENDED',
  PENDING_VERIFICATION: 'PENDING_VERIFICATION',
};

export const USER_STATUS_LABELS = {
  ACTIVE: { label: 'Đang hoạt động', badgeClass: 'badge-status-active' },
  LOCKED: { label: 'Đã khóa', badgeClass: 'badge-status-locked' },
  SUSPENDED: { label: 'Tạm đình chỉ', badgeClass: 'badge-status-suspended' },
  PENDING_VERIFICATION: { label: 'Chờ xác thực', badgeClass: 'badge-status-pending' },
};

export const KYC_STATUS_LABELS = {
  APPROVED: { label: 'Đã xác thực', badgeClass: 'badge-status-active' },
  PENDING: { label: 'Chờ xác minh', badgeClass: 'badge-status-pending' },
  PROCESSING: { label: 'Đang xác minh', badgeClass: 'badge-status-pending' },
  PENDING_REVIEW: { label: 'Chờ duyệt', badgeClass: 'badge-status-pending' },
  TECHNICAL_FAILURE: { label: 'Lỗi kỹ thuật', badgeClass: 'badge-status-suspended' },
  REJECTED: { label: 'Bị từ chối', badgeClass: 'badge-status-locked' },
  SUSPICIOUS: { label: 'Nghi vấn', badgeClass: 'badge-status-suspended' },
  NOT_SUBMITTED: { label: 'Chưa nộp', badgeClass: 'badge-status-pending' },
  UNVERIFIED: { label: 'Chưa xác thực', badgeClass: 'badge-status-pending' },
};

export const SKILL_STATUS_LABELS = {
  APPROVED: { label: 'Đã duyệt', badgeClass: 'badge-status-active' },
  SELF_DECLARED: { label: 'Đã tự khai báo', badgeClass: 'badge-status-pending' },
  PENDING_REVIEW: { label: 'Chờ duyệt', badgeClass: 'badge-status-pending' },
  REJECTED: { label: 'Bị từ chối', badgeClass: 'badge-status-locked' },
  NOT_FOUND: { label: 'Chưa kê khai', badgeClass: 'badge-status-pending' },
};
