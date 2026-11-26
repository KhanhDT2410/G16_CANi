/**
 * Constants & Helpers cho UC-108: Revenue Dashboard
 */

export const TRANSACTION_STATUS_LABELS = {
  ACTIVE: 'Hoạt động',
  CONFIRMED_FULL: 'Xác nhận đủ',
  CONFIRMED_PARTIAL: 'Xác nhận một phần',
  DECLARED_PENDING_WORKER: 'Chờ Worker duyệt',
  DISPUTED: 'Đang tranh chấp',
  UNVERIFIED: 'Chưa xác minh',
  CANCELLED: 'Đã hủy',
  EXPIRED: 'Hết hạn',
};

/**
 * Định dạng số tiền VNĐ (VD: 15.000.000 ₫)
 */
export const formatCurrency = (amount) => {
  if (amount === null || amount === undefined) return '0 ₫';
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(amount);
};

/**
 * Định dạng phần trăm tăng trưởng (+12.5% hoặc -5.0%)
 */
export const formatGrowthRate = (rate) => {
  if (rate === null || rate === undefined) return '0.0%';
  const prefix = rate > 0 ? '+' : '';
  return `${prefix}${rate.toFixed(1)}%`;
};

/**
 * Định dạng thời gian hiển thị tiếng Việt
 */
export const formatDateTime = (isoString) => {
  if (!isoString) return '—';
  const d = new Date(isoString);
  return d.toLocaleString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
};

