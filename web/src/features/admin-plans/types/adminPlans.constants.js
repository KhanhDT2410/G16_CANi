/**
 * adminPlans.constants.js
 * Danh mục hằng số & đặc tả tính năng cốt lõi cho gói dịch vụ CANi (UC-114 & Gói cước)
 */

export const CORE_PLAN_FEATURES = [
  'Kích hoạt Thuật toán Ghép đôi (Matching Engine)',
  'Hiển thị ca làm việc ưu tiên trên Bản đồ Job Radar di động',
  'Bắn thông báo đẩy (FCM Push) đến ứng viên phù hợp',
  'Tự động kích hoạt hỗ trợ khi có biến động ca làm',
];

export const RECOMMENDED_EXTENDED_FEATURES = [
  'Mở khóa tính năng Gọi Nhân Sự Khẩn Cấp (Emergency Dispatch)',
  'Kênh chăm sóc & hỗ trợ kỹ thuật riêng 24/7',
  'Báo cáo số liệu chuyên sâu & đối soát đa chiều',
  'Huy hiệu Nhà tổ chức xác thực uy tín cao (Verified EO)',
];

export const PLAN_TYPE_OPTIONS = [
  { value: 'MATCHING_PLUS', label: 'Gói Tiêu Chuẩn (Standard)', badgeClass: 'badge-matching' },
  { value: 'ENTERPRISE', label: 'Gói Chuyên Nghiệp (Pro / Doanh Nghiệp)', badgeClass: 'badge-enterprise' },
  { value: 'FREE_TRIAL', label: 'Gói Trải Nghiệm (Khởi Nghiệp)', badgeClass: 'badge-trial' },
  { value: 'INVITE_ONLY', label: 'Gói Mời Trực Tiếp (Miễn Phí)', badgeClass: 'badge-invite' },
];

export const DEFAULT_PLAN_FORM = {
  planName: '',
  planType: 'MATCHING_PLUS',
  price: 99000,
  durationDays: 30,
  maxEvents: 5,
  maxShifts: 25,
  maxWorkers: 80,
  baseMatchingQuota: 5,
  selectedCoreFeatures: [...CORE_PLAN_FEATURES],
  extendedFeaturesText: '',
};
