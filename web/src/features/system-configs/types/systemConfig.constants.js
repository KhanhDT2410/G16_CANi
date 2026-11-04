/**
 * systemConfig.constants.js
 * Danh mục nhóm, nhãn thân thiện và cấu hình thuộc tính cho tham số hệ thống
 */

export const CONFIG_CATEGORIES = {
  ALL: { key: 'ALL', label: 'Tất cả' },
  FINANCIAL: { key: 'FINANCIAL', label: 'Tài chính & Hoa hồng' },
  OPERATION: { key: 'OPERATION', label: 'Vận hành & Đánh giá' },
  GENERAL: { key: 'GENERAL', label: 'Hệ thống & Tài khoản' },
};

export const SYSTEM_CONFIG_METAS = {
  commission_rate: {
    label: 'Tỷ lệ hoa hồng sàn',
    category: 'FINANCIAL',
    unit: '%',
    type: 'number',
    step: '0.01',
    hint: 'Tỷ lệ % hoa hồng CANi thu trên tổng giá trị ca làm (VD: 0.05 là 5%)',
  },
  direct_payment_hold_hours: {
    label: 'Thời gian tạm giữ thanh toán',
    category: 'FINANCIAL',
    unit: 'giờ',
    type: 'number',
    hint: 'Thời gian hệ thống ký quỹ trước khi tự động quyết toán cho Worker',
  },
  noshow_penalty_hours: {
    label: 'Ngưỡng thời gian tính No-show',
    category: 'OPERATION',
    unit: 'giờ',
    type: 'number',
    hint: 'Hủy ca sát giờ trước ngưỡng này sẽ bị tính lỗi vắng mặt không phép',
  },
  rating_deadline_days: {
    label: 'Hạn chót đánh giá sau sự kiện',
    category: 'OPERATION',
    unit: 'ngày',
    type: 'number',
    hint: 'Số ngày tối đa EO và Worker có thể chấm điểm lẫn nhau sau ca làm',
  },
  app_version: {
    label: 'Phiên bản ứng dụng tối thiểu',
    category: 'GENERAL',
    unit: 'version',
    type: 'text',
    hint: 'Phiên bản client app tối thiểu được phép đăng nhập vào hệ thống',
  },
  support_email: {
    label: 'Email tiếp nhận khiếu nại CSKH',
    category: 'GENERAL',
    unit: 'email',
    type: 'email',
    hint: 'Hộp thư CSKH chính thức gửi thông báo và hỗ trợ người dùng',
  },
  min_registration_age: {
    label: 'Độ tuổi đăng ký tối thiểu',
    category: 'GENERAL',
    unit: 'tuổi',
    type: 'number',
    hint: 'Độ tuổi hợp pháp tối thiểu theo quy định lao động',
  },
};

/**
 * Trả về thông tin meta của tham số cấu hình (có giá trị fallback mặc định cho tham số mới)
 */
export const getConfigMeta = (key) => {
  if (SYSTEM_CONFIG_METAS[key]) {
    return SYSTEM_CONFIG_METAS[key];
  }

  // Tự động phân loại dựa trên tiền tố của key nếu là key mới
  let category = 'GENERAL';
  if (key.includes('commission') || key.includes('payment') || key.includes('fee') || key.includes('wage')) {
    category = 'FINANCIAL';
  } else if (key.includes('shift') || key.includes('event') || key.includes('penalty') || key.includes('rating')) {
    category = 'OPERATION';
  }

  return {
    label: key.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()),
    category,
    unit: '',
    type: 'text',
    hint: 'Tham số cấu hình hệ thống',
  };
};

