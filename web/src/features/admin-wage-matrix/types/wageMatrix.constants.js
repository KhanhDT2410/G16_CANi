/**
 * Các hằng số, cấu hình nhãn hiển thị và định dạng màu sắc cho Biểu Ma Trận Thù Lao (Wage Matrix)
 * Phục vụ UC-34: Quản trị biểu ma trận thù lao theo chuẩn nền tảng
 */

export const CITY_OPTIONS = [
  { value: 'TP_HCM', label: 'TP. Hồ Chí Minh' },
  { value: 'HANOI', label: 'Hà Nội' },
  { value: 'DANANG', label: 'Đà Nẵng' },
];

export const CITY_LABELS = {
  TP_HCM: 'TP. Hồ Chí Minh',
  HANOI: 'Hà Nội',
  DANANG: 'Đà Nẵng',
};

export const TIME_SLOT_OPTIONS = [
  { value: 'DAYTIME_WEEKDAY', label: 'Ngày thường - Ban ngày (06:00 - 18:00)' },
  { value: 'EVENING_WEEKDAY', label: 'Ngày thường - Buổi tối (18:00 - 22:00)' },
  { value: 'NIGHT_WEEKDAY', label: 'Ngày thường - Ca đêm (Sau 22:00)' },
  { value: 'DAYTIME_WEEKEND', label: 'Cuối tuần - Ban ngày (06:00 - 18:00)' },
  { value: 'EVENING_WEEKEND', label: 'Cuối tuần - Buổi tối (Sau 18:00)' },
];

export const TIME_SLOT_CONFIG = {
  DAYTIME_WEEKDAY: {
    label: 'Ngày thường (Ban ngày)',
    color: '#15803d',
    bg: '#f0fdf4',
    border: '#bbf7d0',
  },
  EVENING_WEEKDAY: {
    label: 'Ngày thường (Buổi tối)',
    color: '#b45309',
    bg: '#fffbeb',
    border: '#fde68a',
  },
  NIGHT_WEEKDAY: {
    label: 'Ngày thường (Ca đêm)',
    color: '#4338ca',
    bg: '#eef2ff',
    border: '#c7d2fe',
  },
  DAYTIME_WEEKEND: {
    label: 'Cuối tuần (Ban ngày)',
    color: '#0369a1',
    bg: '#f0f9ff',
    border: '#bae6fd',
  },
  EVENING_WEEKEND: {
    label: 'Cuối tuần (Buổi tối)',
    color: '#9333ea',
    bg: '#faf5ff',
    border: '#f3e8ff',
  },
};

export const LEAD_TIME_OPTIONS = [
  { value: 'STANDARD', label: 'Tiêu chuẩn (Đặt trước ≥ 72 giờ)' },
  { value: 'SOON', label: 'Cận ngày (Đặt trước 24 - 72 giờ)' },
  { value: 'URGENT', label: 'Khẩn cấp (Đặt trước < 24 giờ)' },
];

export const LEAD_TIME_CONFIG = {
  STANDARD: {
    label: 'Tiêu chuẩn (≥ 72h)',
    color: '#1d4ed8',
    bg: '#eff6ff',
    border: '#bfdbfe',
  },
  SOON: {
    label: 'Cận ngày (24h - 72h)',
    color: '#d97706',
    bg: '#fffbeb',
    border: '#fef3c7',
  },
  URGENT: {
    label: 'Khẩn cấp (< 24h)',
    color: '#dc2626',
    bg: '#fef2f2',
    border: '#fecaca',
  },
};

export const COMMON_JOB_TYPES = [
  'Phục vụ bàn',
  'Phụ bếp',
  'Pha chế (Bartender/Barista)',
  'Thu ngân',
  'Lễ tân / Check-in',
  'Điều phối sự kiện',
  'Kỹ thuật âm thanh - ánh sáng',
  'Bảo vệ / An ninh',
  'Hậu cần / Setup sự kiện',
  'PG / PB',
];
