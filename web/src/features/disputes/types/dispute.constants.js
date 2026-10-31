/**
 * Enums and constants for Dispute & Resolution (SRS FE-11: UC-96 -> UC-104)
 */

export const DISPUTE_STATUS = {
  OPEN: 'OPEN',
  INVESTIGATING: 'INVESTIGATING',
  RESOLVED: 'RESOLVED',
  UNVERIFIED: 'UNVERIFIED',
  ESCALATED: 'ESCALATED',
  FINAL_RESOLVED: 'FINAL_RESOLVED',
};

export const DISPUTE_STATUS_CONFIG = {
  OPEN: { label: 'Mới mở (Chờ CC)', badgeClass: 'badge-status-open' },
  INVESTIGATING: { label: 'Đang điều tra', badgeClass: 'badge-status-investigating' },
  RESOLVED: { label: 'Đã giải quyết (CC)', badgeClass: 'badge-status-resolved' },
  UNVERIFIED: { label: 'Không xác minh được', badgeClass: 'badge-status-unverified' },
  ESCALATED: { label: 'Đã kháng nghị lên Admin', badgeClass: 'badge-status-escalated' },
  FINAL_RESOLVED: { label: 'Phán quyết cuối (Admin)', badgeClass: 'badge-status-final' },
};

export const DISPUTE_TYPES = {
  PAYMENT: 'PAYMENT',
  WORK_QUALITY: 'WORK_QUALITY',
  ATTENDANCE: 'ATTENDANCE',
  CONDUCT: 'CONDUCT',
  OTHER: 'OTHER',
};

export const DISPUTE_TYPE_LABELS = {
  PAYMENT: 'Tranh chấp thù lao / Tiền công',
  WORK_QUALITY: 'Chất lượng công việc',
  ATTENDANCE: 'Điểm danh / Bỏ ca / Đi trễ',
  CONDUCT: 'Hành vi / Ứng xử vi phạm',
  OTHER: 'Khác',
};

export const DISPUTE_OUTCOMES = {
  EO_FAULT: 'EO_FAULT',
  WORKER_FAULT: 'WORKER_FAULT',
  PARTIAL: 'PARTIAL',
  UNVERIFIED: 'UNVERIFIED',
  UPHOLD: 'UPHOLD',
};

export const DISPUTE_OUTCOME_LABELS = {
  EO_FAULT: 'Lỗi thuộc về Nhà tổ chức (EO_FAULT)',
  WORKER_FAULT: 'Lỗi thuộc về Worker (-10 điểm RS) (WORKER_FAULT)',
  PARTIAL: 'Lỗi từ cả hai phía (PARTIAL)',
  UNVERIFIED: 'Không đủ chứng cứ xác minh (UNVERIFIED)',
  UPHOLD: 'Giữ nguyên phán quyết sơ bộ (UPHOLD)',
};

export const DISPUTE_EVIDENCE_TYPES = {
  IMAGE: 'IMAGE',
  VIDEO: 'VIDEO',
  DOCUMENT: 'DOCUMENT',
  TRANSACTION_RECORD: 'TRANSACTION_RECORD',
};

export const DISPUTE_EVIDENCE_LABELS = {
  IMAGE: 'Hình ảnh (Ảnh chụp / Biên lai)',
  VIDEO: 'Video clip',
  DOCUMENT: 'Tài liệu / Biên bản',
  TRANSACTION_RECORD: 'Bản ghi giao dịch ngân hàng',
};

