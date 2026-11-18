/**
 * Constants & Cấu hình nhãn trạng thái ca làm việc cho Admin
 */

export const SHIFT_STATUS_CONFIG = {
  DRAFT: {
    label: 'Bản Nháp',
    bg: '#f1f5f9',
    color: '#475569',
    badgeClass: 'badge-draft',
  },
  ACTIVE: {
    label: 'Đang Tuyển',
    bg: '#eff6ff',
    color: '#2563eb',
    badgeClass: 'badge-active',
  },
  FULLY_STAFFED: {
    label: 'Đã Đủ Người',
    bg: '#f0fdf4',
    color: '#16a34a',
    badgeClass: 'badge-filled',
  },
  ONGOING: {
    label: 'Đang Diễn Ra',
    bg: '#fef3c7',
    color: '#d97706',
    badgeClass: 'badge-ongoing',
  },
  COMPLETED: {
    label: 'Hoàn Thành',
    bg: '#ecfdf5',
    color: '#059669',
    badgeClass: 'badge-completed',
  },
  CANCELLED: {
    label: 'Đã Hủy',
    bg: '#fef2f2',
    color: '#dc2626',
    badgeClass: 'badge-cancelled',
  },
};

export const WORKER_ASSIGNMENT_STATUS_CONFIG = {
  PENDING: { label: 'Chờ duyệt', color: '#d97706', bg: '#fef3c7' },
  CONFIRMED: { label: 'Đã xác nhận', color: '#16a34a', bg: '#f0fdf4' },
  CHECKED_IN: { label: 'Đã điểm danh', color: '#2563eb', bg: '#eff6ff' },
  COMPLETED: { label: 'Hoàn thành', color: '#059669', bg: '#ecfdf5' },
  CANCELLED: { label: 'Đã hủy', color: '#dc2626', bg: '#fef2f2' },
  NO_SHOW: { label: 'Vắng mặt', color: '#991b1b', bg: '#fee2e2' },
};
