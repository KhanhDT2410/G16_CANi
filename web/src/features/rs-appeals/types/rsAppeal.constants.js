/**
 * Constants & Helpers cho UC-94: RS Penalty & Appeals (Kháng cáo điểm uy tín)
 */

export const RS_APPEAL_STATUS = {
  SUBMITTED: 'SUBMITTED',
  UNDER_REVIEW: 'UNDER_REVIEW',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
};

export const RS_APPEAL_STATUS_CONFIG = {
  SUBMITTED: {
    label: 'Chờ duyệt',
    className: 'badge-submitted',
    color: '#d97706',
    bg: '#fef3c7',
  },
  UNDER_REVIEW: {
    label: 'Đang xem xét',
    className: 'badge-under-review',
    color: '#2563eb',
    bg: '#dbeafe',
  },
  APPROVED: {
    label: 'Đã chấp thuận (+RS)',
    className: 'badge-approved',
    color: '#059669',
    bg: '#d1fae5',
  },
  REJECTED: {
    label: 'Đã từ chối',
    className: 'badge-rejected',
    color: '#dc2626',
    bg: '#fee2e2',
  },
};

export const REASON_CODE_LABELS = {
  FORCE_MAJEURE_ACCIDENT: 'Tai nạn giao thông bất khả kháng',
  FORCE_MAJEURE_MEDICAL: 'Ốm đau / Cấp cứu y tế đột xuất',
  FORCE_MAJEURE_WEATHER: 'Thiên tai / Thời tiết cực đoan',
  SYSTEM_ERROR: 'Lỗi hệ thống / Thiết bị xác nhận',
  OTHER: 'Lý do khác kèm minh chứng',
};

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

