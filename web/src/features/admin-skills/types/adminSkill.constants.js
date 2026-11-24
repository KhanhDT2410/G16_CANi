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
  'F&B': { bg: '#fffbeb', text: '#b45309', border: '#fde68a' },
  'Guest Services': { bg: '#eff6ff', text: '#1d4ed8', border: '#bfdbfe' },
  'Logistics': { bg: '#f8fafc', text: '#475569', border: '#e2e8f0' },
  'Marketing': { bg: '#fdf2f8', text: '#be185d', border: '#fbcfe8' },
  'Operations': { bg: '#faf5ff', text: '#7e22ce', border: '#e9d5ff' },
  'Security': { bg: '#fef2f2', text: '#b91c1c', border: '#fecaca' },
  'Technical': { bg: '#eff6ff', text: '#1d4ed8', border: '#bfdbfe' },
};
