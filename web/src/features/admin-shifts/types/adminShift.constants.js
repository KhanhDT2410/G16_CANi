/**
 * Constants & Cấu hình nhãn trạng thái ca làm việc cho Admin
 */

export const SHIFT_STATUS_CONFIG = {
  DRAFT: {
    label: 'Bản Nháp',
    bg: '#f8fafc',
    border: '#e2e8f0',
    color: '#475569',
    badgeClass: 'badge-draft',
  },
  ACTIVE: {
    label: 'Đang Tuyển',
    bg: '#eff6ff',
    border: '#bfdbfe',
    color: '#1d4ed8',
    badgeClass: 'badge-active',
  },
  FULLY_STAFFED: {
    label: 'Đã Đủ Người',
    bg: '#f0fdf4',
    border: '#bbf7d0',
    color: '#15803d',
    badgeClass: 'badge-filled',
  },
  IN_PROGRESS: {
    label: 'Đang Diễn Ra',
    bg: '#fffbeb',
    border: '#fde68a',
    color: '#b45309',
    badgeClass: 'badge-ongoing',
  },
  ONGOING: {
    label: 'Đang Diễn Ra',
    bg: '#fffbeb',
    border: '#fde68a',
    color: '#b45309',
    badgeClass: 'badge-ongoing',
  },
  COMPLETED: {
    label: 'Hoàn Thành',
    bg: '#f0fdf4',
    border: '#bbf7d0',
    color: '#15803d',
    badgeClass: 'badge-completed',
  },
  SUSPENDED: {
    label: 'Tạm Đình Chỉ',
    bg: '#fffbeb',
    border: '#fde68a',
    color: '#b45309',
    badgeClass: 'badge-suspended',
  },
  CANCELLED: {
    label: 'Đã Hủy',
    bg: '#fef2f2',
    border: '#fecaca',
    color: '#b91c1c',
    badgeClass: 'badge-cancelled',
  },
};

export const WORKER_ASSIGNMENT_STATUS_CONFIG = {
  PENDING: { label: 'Chờ duyệt', color: '#b45309', bg: '#fffbeb', border: '#fde68a' },
  CONFIRMED: { label: 'Đã xác nhận', color: '#15803d', bg: '#f0fdf4', border: '#bbf7d0' },
  CHECKED_IN: { label: 'Đã điểm danh', color: '#1d4ed8', bg: '#eff6ff', border: '#bfdbfe' },
  COMPLETED: { label: 'Hoàn thành', color: '#15803d', bg: '#f0fdf4', border: '#bbf7d0' },
  CANCELLED: { label: 'Đã hủy', color: '#b91c1c', bg: '#fef2f2', border: '#fecaca' },
  NO_SHOW: { label: 'Vắng mặt', color: '#b91c1c', bg: '#fef2f2', border: '#fecaca' },
};

export const PAYMENT_STATUS_CONFIG = {
  PENDING: { label: 'Chờ thanh toán', bg: '#fffbeb', border: '#fde68a', color: '#b45309' },
  ESCROW_PAID: { label: 'Đã giữ tiền cọc', bg: '#eff6ff', border: '#bfdbfe', color: '#1d4ed8' },
  RELEASED: { label: 'Đã giải ngân thù lao', bg: '#f0fdf4', border: '#bbf7d0', color: '#15803d' },
  REFUNDED: { label: 'Đã hoàn tiền', bg: '#f8fafc', border: '#e2e8f0', color: '#475569' },
  DISPUTED: { label: 'Đang tranh chấp tiền', bg: '#fef2f2', border: '#fecaca', color: '#b91c1c' },
};

/**
 * Xác định trạng thái hiển thị của ca làm việc (kết hợp thời gian thực).
 * Giúp Admin phân biệt chính xác: Ca đang làm, Ca đã hoàn thành, Ca chưa làm.
 */
export const resolveShiftStatus = (shift = {}) => {
  const rawStatus = (shift.status || '').toUpperCase();
  if (rawStatus === 'CANCELLED') return SHIFT_STATUS_CONFIG.CANCELLED;
  if (rawStatus === 'SUSPENDED') return SHIFT_STATUS_CONFIG.SUSPENDED;
  if (rawStatus === 'DRAFT') return SHIFT_STATUS_CONFIG.DRAFT;
  if (rawStatus === 'COMPLETED') return SHIFT_STATUS_CONFIG.COMPLETED;
  if (rawStatus === 'IN_PROGRESS' || rawStatus === 'ONGOING') return SHIFT_STATUS_CONFIG.IN_PROGRESS;

  const now = new Date();
  const start = shift.startTime ? new Date(shift.startTime) : null;
  const end = shift.endTime ? new Date(shift.endTime) : null;

  if (end && !isNaN(end.getTime()) && now > end) {
    return SHIFT_STATUS_CONFIG.COMPLETED;
  }
  if (start && end && !isNaN(start.getTime()) && !isNaN(end.getTime()) && now >= start && now <= end) {
    return SHIFT_STATUS_CONFIG.IN_PROGRESS;
  }
  if (rawStatus === 'FULLY_STAFFED') {
    return SHIFT_STATUS_CONFIG.FULLY_STAFFED;
  }
  return SHIFT_STATUS_CONFIG.ACTIVE || {
    label: 'Đang Tuyển',
    bg: '#eff6ff',
    color: '#2563eb',
  };
};

