export const USER_ROLES = {
  ADMIN: 'ADMIN',
  CUSTOMER_CARE: 'CUSTOMER_CARE',
  EVENT_ORGANIZER: 'EVENT_ORGANIZER',
  WORKER: 'WORKER',
};

export const USER_ROLE_LABELS = {
  ADMIN: { label: 'Quản trị viên', badgeClass: 'badge-role-admin' },
  CUSTOMER_CARE: { label: 'Chăm sóc KH', badgeClass: 'badge-role-cc' },
  EVENT_ORGANIZER: { label: 'Nhà tổ chức (EO)', badgeClass: 'badge-role-eo' },
  WORKER: { label: 'Nhân sự (Worker)', badgeClass: 'badge-role-worker' },
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
