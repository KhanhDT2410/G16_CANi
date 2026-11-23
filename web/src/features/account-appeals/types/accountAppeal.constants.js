/**
 * Constants & Helpers cho Module Giải Trình Mở Khóa Tài Khoản (Locked Account Appeal)
 */

export const APPEAL_REASON_CATEGORIES = [
  {
    value: 'VIOLATION_RESOLVED',
    label: 'Đã khắc phục hoàn toàn vi phạm (Đã thanh toán/bồi thường/bổ sung hồ sơ)',
    desc: 'Đơn vị đã hoàn tất nghĩa vụ tài chính hoặc khắc phục vấn đề vi phạm.',
  },
  {
    value: 'POLICY_MISUNDERSTANDING',
    label: 'Hiểu nhầm quy chế / điều khoản vận hành CANi',
    desc: 'Do chưa nắm rõ chính sách vận hành của nền tảng và cam kết không tái phạm.',
  },
  {
    value: 'IDENTITY_DISPUTE',
    label: 'Tranh chấp danh tính / Ủy quyền quản lý sự kiện',
    desc: 'Cần xác minh lại quyền đại diện pháp nhân hoặc xử lý tài khoản bị truy cập trái phép.',
  },
  {
    value: 'FORCE_MAJEURE',
    label: 'Sự cố bất khả kháng / Thiên tai / Lỗi hệ thống ngoài ý muốn',
    desc: 'Gặp sự cố khách quan có văn bản/chứng từ minh chứng rõ ràng.',
  },
  {
    value: 'OTHER',
    label: 'Lý do giải trình khác',
    desc: 'Các trường hợp đặc biệt khác cần trình bày cụ thể với Ban Quản trị.',
  },
];

export const ACCOUNT_APPEAL_STATUS = {
  SUBMITTED: 'SUBMITTED',
  UNDER_REVIEW: 'UNDER_REVIEW',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
};

export const ACCOUNT_APPEAL_STATUS_CONFIG = {
  SUBMITTED: {
    label: 'Chờ Tiếp Nhận',
    badgeClass: 'badge-appeal-submitted',
    color: '#b45309',
    bg: '#fef3c7',
    desc: 'Đơn giải trình đã gửi thành công và đang chờ Admin tiếp nhận.',
  },
  UNDER_REVIEW: {
    label: 'Đang Thẩm Định',
    badgeClass: 'badge-appeal-reviewing',
    color: '#1d4ed8',
    bg: '#dbeafe',
    desc: 'Ban Quản trị đang đối soát chứng từ và thẩm định giải trình của bạn.',
  },
  APPROVED: {
    label: 'Đã Chấp Thuận',
    badgeClass: 'badge-appeal-approved',
    color: '#15803d',
    bg: '#dcfce7',
    desc: 'Đơn giải trình được chấp thuận. Tài khoản đã được khôi phục hoạt động.',
  },
  REJECTED: {
    label: 'Bị Từ Chối',
    badgeClass: 'badge-appeal-rejected',
    color: '#b91c1c',
    bg: '#fee2e2',
    desc: 'Giải trình chưa đủ cơ sở hoặc chứng từ không hợp lệ.',
  },
};
