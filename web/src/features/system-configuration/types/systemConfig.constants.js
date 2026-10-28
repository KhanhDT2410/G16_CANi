/**
 * W-41: System Configuration – Screen (SRS 3.4.3.5 · UC-114/115/116)
 *
 * Schema khai báo 7 tab cấu hình của trang System Configuration. Mỗi tab gồm
 * một hoặc nhiều section, mỗi section là danh sách field. Renderer dạng
 * data-driven (ConfigForm) chỉ đọc schema này, nên code UI giữ được KISS/YAGNI.
 */

export const CONFIG_TABS = [
  {
    id: 'pricing',
    icon: '💰',
    label: 'Pricing',
    title: 'Định Giá & Gói Dịch Vụ',
    description: 'Mức giá dịch vụ huy động và giá gói đăng ký cho EO (UC-114).',
    saveMsg: 'MSG158',
    sections: [
      {
        title: 'Mức Giá Dịch Vụ Huy Động',
        description: 'Giá nền tảng đề xuất, cho phép Admin điều chỉnh theo chính sách kinh doanh.',
        fields: [
          { key: 'pricing.inviteModePrice', label: 'Giá chế độ mời (Invite Mode)', type: 'number', unit: 'VND', min: 0, step: 1000, help: 'Phí khi EO dùng chế độ mời nhân sự trực tiếp.' },
          { key: 'pricing.matchingInvitePrice', label: 'Giá Matching + Invite', type: 'number', unit: 'VND', min: 0, step: 1000, help: 'Phí khi kết hợp Matching và Invite.' },
        ],
      },
      {
        title: 'Gói Đăng Ký EO',
        description: 'Mức giá hằng tháng cho các gói Free / Basic / Pro.',
        fields: [
          { key: 'pricing.freePrice', label: 'Gói Free', type: 'number', unit: 'VND/tháng', min: 0, step: 1000 },
          { key: 'pricing.basicPrice', label: 'Gói Basic', type: 'number', unit: 'VND/tháng', min: 0, step: 1000 },
          { key: 'pricing.proPrice', label: 'Gói Pro', type: 'number', unit: 'VND/tháng', min: 0, step: 1000 },
        ],
      },
    ],
  },
  {
    id: 'matching',
    icon: '🎯',
    label: 'Matching',
    title: 'Ngưỡng Matching',
    description: 'Trọng số và ngưỡng điểm tối thiểu của bộ máy kết hợp Worker–Shift.',
    saveMsg: 'MSG158',
    sections: [
      {
        title: 'Trọng Số & Ngưỡng',
        description: 'Tổng trọng số các yếu tố nên bằng 1.0. Fallback theo chuỗi khi thiếu dữ liệu.',
        fields: [
          { key: 'matching.minMatchScore', label: 'Điểm match tối thiểu', type: 'number', unit: '%', min: 0, max: 100, step: 1 },
          { key: 'matching.autoApproveScore', label: 'Tự phê duyệt từ điểm', type: 'number', unit: '%', min: 0, max: 100, step: 1, help: 'Worker vượt mức này được đưa thẳng vào shortlist.' },
          { key: 'matching.maxDistanceKm', label: 'Quãng cách tối đa', type: 'number', unit: 'km', min: 0, step: 1 },
          { key: 'matching.skillWeight', label: 'Trọng số kỹ năng', type: 'number', min: 0, max: 1, step: 0.05 },
          { key: 'matching.experienceWeight', label: 'Trọng số kinh nghiệm', type: 'number', min: 0, max: 1, step: 0.05 },
          { key: 'matching.ratingWeight', label: 'Trọng số điểm đánh giá', type: 'number', min: 0, max: 1, step: 0.05 },
        ],
      },
    ],
  },
  {
    id: 'attendance',
    icon: '⏱️',
    label: 'Attendance',
    title: 'Điểm Danh & No-Show',
    description: 'Hậu kiểm (after-the-fact check-in) và bộ hẹn giờ no-show của Worker.',
    saveMsg: 'MSG158',
    sections: [
      {
        title: 'Bộ Hẹn Giờ',
        description: 'Quy tắc tính no-show dựa trên khoảng thời gian sau giờ ca bắt đầu.',
        fields: [
          { key: 'attendance.noShowThresholdMin', label: 'Ngưỡng no-show', type: 'number', unit: 'phút', min: 0, step: 5, help: 'Worker vắng quá mức này sau giờ bắt đầu sẽ bị đánh dấu no-show (mặc định 30).' },
          { key: 'attendance.gracePeriodMin', label: 'Thước dung sai (grace period)', type: 'number', unit: 'phút', min: 0, step: 5 },
          { key: 'attendance.reminderBeforeMin', label: 'Nhắc nhở trước ca', type: 'number', unit: 'phút', min: 0, step: 5 },
          { key: 'attendance.autoMarkNoShow', label: 'Tự động đánh dấu no-show', type: 'toggle' },
        ],
      },
    ],
  },
  {
    id: 'rating',
    icon: '⭐',
    label: 'Rating',
    title: 'Cửa Sổ Đánh Giá',
    description: 'Cửa sổ thời gian để EO chấm điểm Worker sau ca (UC-114).',
    saveMsg: 'MSG158',
    sections: [
      {
        title: 'Cấu Hình Chấm Điểm',
        description: 'Hạn mức và chính sách hiển thị điểm đánh giá.',
        fields: [
          { key: 'rating.ratingWindowDays', label: 'Cửa sổ đánh giá', type: 'number', unit: 'ngày', min: 0, step: 1, help: 'Số ngày EO được chấm điểm sau khi ca kết thúc (mặc định 7).' },
          { key: 'rating.scaleMax', label: 'Thang điểm tối đa', type: 'number', min: 1, max: 10, step: 1 },
          { key: 'rating.allowEditAfterSubmit', label: 'Cho phép sửa sau khi gửi', type: 'toggle' },
          { key: 'rating.anonymousRating', label: 'Chấm điểm ẩn danh (worker không thấy)', type: 'toggle' },
        ],
      },
    ],
  },
  {
    id: 'rsThresholds',
    icon: '📈',
    label: 'RS Thresholds',
    title: 'Ngưỡng Điểm Uy Tín (RS)',
    description: 'Ngưỡng chuyển hạng thang điểm uy tín (Reliability Score) của Worker.',
    saveMsg: 'MSG158',
    sections: [
      {
        title: 'Hạng Điểm Uy Tín',
        description: 'Mỗi hạng yêu cầu RS từ ngưỡng tối thiểu trở lên.',
        fields: [
          { key: 'rs.silverMin', label: 'Hạng Bạc từ', type: 'number', unit: 'điểm', min: 0, max: 100, step: 1 },
          { key: 'rs.goldMin', label: 'Hạng Vàng từ', type: 'number', unit: 'điểm', min: 0, max: 100, step: 1 },
          { key: 'rs.platinumMin', label: 'Hạng Bạch Kim từ', type: 'number', unit: 'điểm', min: 0, max: 100, step: 1 },
          { key: 'rs.recalculationWindowDays', label: 'Tô lại trong vòng', type: 'number', unit: 'ngày', min: 1, step: 1 },
        ],
      },
    ],
  },
  {
    id: 'skillPoint',
    icon: '✨',
    label: 'Skill Point',
    title: 'Quy Tắc Tích Điểm Kỹ Năng (SP)',
    description: 'Quy tắc cộng/ trừ điểm kỹ năng để SP phản ánh đúng hiệu suất Worker (UC-115).',
    saveMsg: 'MSG97',
    sections: [
      {
        title: 'SP Theo Loại Việc',
        description: 'Điểm nhận được hoặc bị trừ theo sự kiện.',
        fields: [
          { key: 'sp.completedShift', label: 'SP / ca hoàn thành', type: 'number', min: 0, step: 1 },
          { key: 'sp.earlyCheckIn', label: 'SP / điểm danh sớm', type: 'number', min: 0, step: 1 },
          { key: 'sp.noShowPenalty', label: 'Phạt / no-show', type: 'number', min: -100, max: 0, step: 1 },
          { key: 'sp.cancelledByWorker', label: 'Phạt / worker tự hủy', type: 'number', min: -100, max: 0, step: 1 },
        ],
      },
      {
        title: 'SP Từ Đánh Giá & Hệ Số',
        description: 'SP từ chấm điểm của EO và hệ số phù hợp vai trò (Role Alignment Factor).',
        fields: [
          { key: 'sp.perRatingStar', label: 'SP / sao đánh giá', type: 'number', min: 0, step: 1 },
          { key: 'sp.roleAlignmentDefault', label: 'Hệ số vai trò mặc định', type: 'number', min: 0, max: 3, step: 0.1 },
          { key: 'sp.spVerifyAccrual', label: 'sp_verify: xác minh mới cộng SP', type: 'toggle' },
        ],
      },
    ],
  },
  {
    id: 'features',
    icon: '🔧',
    label: 'Feature Flags & Bảo trì',
    title: 'Feature Flags, Cache & Cửa Sổ Bảo Trì',
    description: 'Quản lý cờ tính năng, cache và lịch bảo trì nền tảng (UC-116).',
    saveMsg: 'MSG158',
    sections: [
      {
        title: 'Cờ Tính Năng (Feature Flags)',
        description: 'Bật/tắt từng tính năng nền tảng không cần triển khai lại mã nguồn.',
        fields: [
          { key: 'flags.ekyc', label: 'Định danh eKYC', type: 'toggle' },
          { key: 'flags.matchingEngine', label: 'Bộ máy Matching', type: 'toggle' },
          { key: 'flags.vnpay', label: 'Thanh toán VNPay', type: 'toggle' },
          { key: 'flags.skillPoint', label: 'Hệ thống Skill Point', type: 'toggle' },
        ],
      },
      {
        title: 'Quản Lý Cache',
        description: 'Tuổi thọ cache và thủ công vô hiệu hóa cache.',
        fields: [
          { key: 'cache.ttlMinutes', label: 'Tuổi thọ cache', type: 'number', unit: 'phút', min: 0, step: 1 },
          { key: 'cache.invalidateNow', label: 'Vô hiệu hóa cache ngay', type: 'action', actionLabel: '🗑️ Vô hiệu hóa cache' },
        ],
      },
      {
        title: 'Cửa Sổ Bảo Trì',
        description: 'Lịch bảo trì nền tảng; hệ thống sẽ gửi thông báo cho người dùng (UC-117).',
        fields: [
          { key: 'maintenance.enabled', label: 'Bật chế độ bảo trì', type: 'toggle', critical: true, help: 'Tắt/bật tính năng quan trọng sẽ hiện xác nhận mức độ cao.' },
          { key: 'maintenance.startAt', label: 'Thời điểm bắt đầu', type: 'datetime' },
          { key: 'maintenance.durationMin', label: 'Thời lượng', type: 'number', unit: 'phút', min: 0, step: 5 },
          { key: 'maintenance.message', label: 'Thông điệp thông báo cho người dùng', type: 'text', placeholder: 'Ví dụ: Hệ thống bảo trì định kỳ lúc 02:00–04:00, xin thông cảm.' },
        ],
      },
    ],
  },
];

/** Giá trị mặc định của mọi field (đồng bộ với UC-114/115/116). */
export const DEFAULT_CONFIG = {
  'pricing.inviteModePrice': 50000,
  'pricing.matchingInvitePrice': 80000,
  'pricing.freePrice': 0,
  'pricing.basicPrice': 299000,
  'pricing.proPrice': 799000,
  'matching.minMatchScore': 70,
  'matching.autoApproveScore': 85,
  'matching.maxDistanceKm': 10,
  'matching.skillWeight': 0.4,
  'matching.experienceWeight': 0.3,
  'matching.ratingWeight': 0.3,
  'attendance.noShowThresholdMin': 30,
  'attendance.gracePeriodMin': 10,
  'attendance.reminderBeforeMin': 15,
  'attendance.autoMarkNoShow': true,
  'rating.ratingWindowDays': 7,
  'rating.scaleMax': 5,
  'rating.allowEditAfterSubmit': false,
  'rating.anonymousRating': true,
  'rs.silverMin': 70,
  'rs.goldMin': 85,
  'rs.platinumMin': 95,
  'rs.recalculationWindowDays': 30,
  'sp.completedShift': 10,
  'sp.earlyCheckIn': 2,
  'sp.noShowPenalty': -15,
  'sp.cancelledByWorker': -5,
  'sp.perRatingStar': 5,
  'sp.roleAlignmentDefault': 1.0,
  'sp.spVerifyAccrual': true,
  'flags.ekyc': true,
  'flags.matchingEngine': true,
  'flags.vnpay': true,
  'flags.skillPoint': true,
  'cache.ttlMinutes': 60,
  'cache.invalidateNow': false,
  'maintenance.enabled': false,
  'maintenance.startAt': '',
  'maintenance.durationMin': 120,
  'maintenance.message': '',
};

/** Ánh xạ mã thông điệp SRS → nội dung tiếng Việt. */
export const MESSAGE_LABELS = {
  MSG158: 'Đã lưu cấu hình hệ thống thành công (audit-log ghi nhận).',
  MSG97: 'Đã lưu quy tắc tích điểm kỹ năng (SP) thành công.',
  MSG98: 'Giá trị cấu hình ngoài phạm vi cho phép. Vui lòng kiểm tra lại.',
};
