/**
 * systemConfig.constants.js
 * Danh mục nhóm, nhãn thân thiện và cấu hình thuộc tính cho tham số hệ thống (ADM-09).
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */

export const CONFIG_CATEGORIES = {
  ALL: { key: 'ALL', label: 'Tất cả' },
  MATCHING: { key: 'MATCHING', label: 'Thuật toán Ghép nối & Matching' },
  OPERATION: { key: 'OPERATION', label: 'Vận hành & Điểm uy tín' },
  FINANCIAL: { key: 'FINANCIAL', label: 'Tài chính & Hoa hồng' },
  GENERAL: { key: 'GENERAL', label: 'Hệ thống & Tài khoản' },
};

export const SYSTEM_CONFIG_METAS = {
  // === 1. THUẬT TOÁN GHÉP NỐI & MATCHING (ADM-09) ===
  matching_weight_distance: {
    label: 'Trọng số Khoảng cách địa lý (W_dist)',
    category: 'MATCHING',
    unit: 'hệ số',
    defaultValue: '0.40',
    type: 'number',
    step: '0.05',
    min: 0,
    max: 1,
    hint: 'Trọng số ưu tiên ứng viên ở gần địa điểm sự kiện (Mặc định 0.40 tương đương 40%)',
  },
  matching_weight_rating: {
    label: 'Trọng số Điểm đánh giá sao (W_rating)',
    category: 'MATCHING',
    unit: 'hệ số',
    defaultValue: '0.30',
    type: 'number',
    step: '0.05',
    min: 0,
    max: 1,
    hint: 'Trọng số ưu tiên nhân sự có điểm đánh giá trung bình cao từ các sự kiện trước (Mặc định 0.30)',
  },
  matching_weight_experience: {
    label: 'Trọng số Kinh nghiệm ca làm (W_exp)',
    category: 'MATCHING',
    unit: 'hệ số',
    defaultValue: '0.20',
    type: 'number',
    step: '0.05',
    min: 0,
    max: 1,
    hint: 'Trọng số ưu tiên số lượng ca làm việc đã hoàn thành thành công (Mặc định 0.20)',
  },
  matching_weight_reliability: {
    label: 'Trọng số Điểm uy tín sàn (W_rs)',
    category: 'MATCHING',
    unit: 'hệ số',
    defaultValue: '0.10',
    type: 'number',
    step: '0.05',
    min: 0,
    max: 1,
    hint: 'Trọng số tính theo điểm uy tín không vi phạm của nhân sự (Mặc định 0.10)',
  },
  matching_max_distance_km: {
    label: 'Bán kính ghép nối tối đa',
    category: 'MATCHING',
    unit: 'km',
    defaultValue: '15.0',
    type: 'number',
    step: '1',
    hint: 'Khoảng cách GPS tối đa từ vị trí nhân sự đến địa điểm tổ chức sự kiện',
  },
  matching_min_rs_score: {
    label: 'Điểm uy tín tối thiểu nhận đề xuất ca',
    category: 'MATCHING',
    unit: 'điểm',
    defaultValue: '60',
    type: 'number',
    hint: 'Nhân sự có điểm uy tín dưới ngưỡng này sẽ không được hệ thống tự động ghép ca',
  },
  matching_new_worker_bonus: {
    label: 'Điểm thưởng ưu tiên người mới',
    category: 'MATCHING',
    unit: 'điểm',
    defaultValue: '10.0',
    type: 'number',
    hint: 'Điểm cộng khuyến khích cho nhân sự mới đăng ký làm dưới 3 ca đầu tiên',
  },
  matching_idle_worker_bonus: {
    label: 'Điểm thưởng khuyến khích quay lại',
    category: 'MATCHING',
    unit: 'điểm',
    defaultValue: '10.0',
    type: 'number',
    hint: 'Điểm cộng kích hoạt lại nhân sự đã nghỉ trên 7 ngày chưa nhận ca mới',
  },
  dispatch_proposal_timeout_hours: {
    label: 'Thời hạn phản hồi đề xuất nhận ca',
    category: 'MATCHING',
    unit: 'giờ',
    defaultValue: '1',
    type: 'number',
    hint: 'Khoảng thời gian nhân sự có thể bấm Chấp nhận ca trước khi đề xuất tự động hủy',
  },

  // === 2. VẬN HÀNH & ĐIỂM UY TÍN (OPERATION) ===
  noshow_penalty_hours: {
    label: 'Ngưỡng thời gian tính vắng mặt không phép',
    category: 'OPERATION',
    unit: 'giờ',
    defaultValue: '4',
    type: 'number',
    hint: 'Hủy ca sát giờ trước ngưỡng này sẽ bị hệ thống ghi nhận lỗi vắng mặt không phép (No-show)',
  },
  noshow_rs_deduction: {
    label: 'Mức trừ điểm uy tín khi vi phạm vắng mặt',
    category: 'OPERATION',
    unit: 'điểm',
    defaultValue: '25',
    type: 'number',
    hint: 'Số điểm uy tín bị tự động trừ khi nhân sự xác nhận vi phạm No-show trong ca làm',
  },
  noshow_lock_threshold_rs: {
    label: 'Ngưỡng điểm uy tín tự động khóa tài khoản',
    category: 'OPERATION',
    unit: 'điểm',
    defaultValue: '20',
    type: 'number',
    hint: 'Nếu điểm uy tín của nhân sự giảm xuống dưới ngưỡng này, tài khoản sẽ bị tự động khóa',
  },
  rating_deadline_days: {
    label: 'Hạn chót đánh giá hai chiều sau sự kiện',
    category: 'OPERATION',
    unit: 'ngày',
    defaultValue: '7',
    type: 'number',
    hint: 'Số ngày tối đa EO và Worker có thể chấm điểm sao và nhận xét lẫn nhau sau ca làm',
  },

  // === 3. TÀI CHÍNH & HOA HỒNG (FINANCIAL) ===
  commission_rate: {
    label: 'Tỷ lệ hoa hồng nền tảng',
    category: 'FINANCIAL',
    unit: '%',
    defaultValue: '0.00',
    type: 'number',
    step: '0.01',
    hint: 'Tỷ lệ % hoa hồng CANi thu trên tổng giá trị ca làm (VD: 0.00 là 0% cho giai đoạn MVP)',
  },
  direct_payment_confirm_window_hours: {
    label: 'Thời hạn đối soát thanh toán trực tiếp',
    category: 'FINANCIAL',
    unit: 'giờ',
    defaultValue: '48',
    type: 'number',
    hint: 'Thời gian tối đa để EO hoàn tất chuyển khoản và xác nhận thù lao trực tiếp cho Worker (Non-Escrow)',
  },

  // === 4. HỆ THỐNG & TÀI KHOẢN (GENERAL) ===
  min_registration_age: {
    label: 'Độ tuổi đăng ký lao động tối thiểu',
    category: 'GENERAL',
    unit: 'tuổi',
    defaultValue: '18',
    type: 'number',
    hint: 'Độ tuổi hợp pháp tối thiểu theo Bộ luật Lao động để nhận ca làm sự kiện',
  },
  app_version: {
    label: 'Phiên bản ứng dụng tối thiểu bắt buộc',
    category: 'GENERAL',
    unit: 'version',
    defaultValue: '1.0.0',
    type: 'text',
    hint: 'Phiên bản ứng dụng di động tối thiểu được phép đăng nhập và nhận ca',
  },
  support_email: {
    label: 'Email tiếp nhận khiếu nại CSKH',
    category: 'GENERAL',
    unit: 'email',
    defaultValue: 'hotro@cani.vn',
    type: 'email',
    hint: 'Hộp thư CSKH chính thức gửi thông báo và tiếp nhận giải quyết tranh chấp',
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
  if (key.includes('matching') || key.includes('dispatch') || key.includes('weight')) {
    category = 'MATCHING';
  } else if (key.includes('commission') || key.includes('payment') || key.includes('fee') || key.includes('wage')) {
    category = 'FINANCIAL';
  } else if (key.includes('shift') || key.includes('event') || key.includes('penalty') || key.includes('rating') || key.includes('noshow')) {
    category = 'OPERATION';
  }

  return {
    label: key.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()),
    category,
    unit: '',
    defaultValue: '',
    type: 'text',
    hint: 'Tham số cấu hình nghiệp vụ sàn',
  };
};
