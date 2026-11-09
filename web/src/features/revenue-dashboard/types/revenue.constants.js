/**
 * Constants & Helpers cho UC-108: Revenue Dashboard
 */

export const PLAN_TYPE_LABELS = {
  INVITE_ONLY: 'Gói Giới Hạn Mời (Invite Only)',
  MATCHING_PLUS: 'Gói Đối Soát & Ghép Nâng Cao (Matching + Invite)',
};

export const TRANSACTION_TYPE_LABELS = {
  PACKAGE_SALE: 'Mua gói dịch vụ',
  DIRECT_PAYMENT: 'Chi trả thù lao ca',
};

export const PAYMENT_STATUS_COLORS = {
  CONFIRMED_FULL: '#10b981', // green
  CONFIRMED_PARTIAL: '#3b82f6', // blue
  DECLARED_PENDING_WORKER: '#f59e0b', // amber
  NOT_DECLARED: '#9ca3af', // gray
  DISPUTED: '#ef4444', // red
  UNVERIFIED: '#ec4899', // pink
  ACTIVE: '#10b981',
  EXPIRED: '#6b7280',
  CANCELLED: '#ef4444',
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

