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
  IN_PROGRESS: {
    label: 'Đang Diễn Ra',
    bg: '#fef3c7',
    color: '#b45309',
    badgeClass: 'badge-ongoing',
  },
  ONGOING: {
    label: 'Đang Diễn Ra',
    bg: '#fef3c7',
    color: '#b45309',
    badgeClass: 'badge-ongoing',
  },
  COMPLETED: {
    label: 'Hoàn Thành',
    bg: '#ecfdf5',
    color: '#059669',
    badgeClass: 'badge-completed',
  },
  SUSPENDED: {
    label: 'Tạm Đình Chỉ',
    bg: '#fffbeb',
    color: '#b45309',
    badgeClass: 'badge-suspended',
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

export const PAYMENT_STATUS_CONFIG = {
  PENDING: { label: 'Chờ thanh toán', bg: '#fef3c7', color: '#d97706' },
  ESCROW_PAID: { label: 'Đã giữ tiền cọc (Escrow)', bg: '#eff6ff', color: '#2563eb' },
  RELEASED: { label: 'Đã giải ngân thù lao', bg: '#ecfdf5', color: '#059669' },
  REFUNDED: { label: 'Đã hoàn tiền', bg: '#f1f5f9', color: '#475569' },
  DISPUTED: { label: 'Đang tranh chấp tiền', bg: '#fef2f2', color: '#dc2626' },
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

