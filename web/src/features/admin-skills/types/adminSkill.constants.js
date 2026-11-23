/**
 * Constants & cấu hình cho Quản lý Kỹ năng (Admin Skills)
 */

export const DEFAULT_SKILL_CATEGORIES = [
  'F&B',
  'Guest Services',
  'Logistics',
  'Marketing',
  'Operations',
  'Security',
  'Technical',
  'Khác',
];

export const SKILL_STATUS_FILTER = {
  ALL: 'Tất cả trạng thái',
  ACTIVE: 'Đang hoạt động',
  INACTIVE: 'Tạm ẩn',
};

export const CATEGORY_COLOR_MAP = {
  'F&B': { bg: '#fef3c7', text: '#92400e', border: '#fde68a' },
  'Guest Services': { bg: '#e0f2fe', text: '#0369a1', border: '#bae6fd' },
  'Logistics': { bg: '#f1f5f9', text: '#334155', border: '#cbd5e1' },
  'Marketing': { bg: '#fce7f3', text: '#9d174d', border: '#fbcfe8' },
  'Operations': { bg: '#ede9fe', text: '#5b21b6', border: '#ddd6fe' },
  'Security': { bg: '#fee2e2', text: '#991b1b', border: '#fecaca' },
  'Technical': { bg: '#e0e7ff', text: '#3730a3', border: '#c7d2fe' },
};
