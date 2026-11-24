import {
  DashboardIcon,
  RevenueIcon,
  UsersIcon,
  ShiftIcon,
  NoShowIcon,
  AppealIcon,
  TicketIcon,
  DisputeIcon,
  FaqIcon,
  MatchingConfigIcon,
  OperationConfigIcon,
  FinancialConfigIcon,
  GeneralConfigIcon,
  PricingPlanIcon,
  WageMatrixIcon,
  SkillReviewIcon,
} from './AdminSidebarIcons';

// Danh mục phân nhóm menu chức năng Admin thuần tiếng Việt
export const ADMIN_NAV_GROUPS = [
  {
    groupKey: 'overview',
    groupLabel: 'Tổng quan & Báo cáo',
    items: [
      { id: 'dashboard', label: 'Bảng Điều Khiển', icon: <DashboardIcon /> },
      { id: 'revenue', label: 'Doanh Thu & Tài Chính', icon: <RevenueIcon /> },
    ],
  },
  {
    groupKey: 'operations',
    groupLabel: 'Quản trị & Vận hành',
    items: [
      { id: 'users', label: 'Quản Trị Người Dùng', icon: <UsersIcon /> },
      { id: 'account_appeals', label: 'Giải Trình Khóa Tài Khoản', icon: <AppealIcon /> },
      { id: 'shifts', label: 'Danh Sách Ca Làm Việc', icon: <ShiftIcon /> },
      { id: 'reviews', label: 'Kiểm Duyệt Đánh Giá', icon: <SkillReviewIcon /> },
      { id: 'noshows', label: 'Xử Lý Vi Phạm Vắng Mặt', icon: <NoShowIcon /> },
      { id: 'appeals', label: 'Kháng Cáo Điểm Uy Tín', icon: <AppealIcon /> },
    ],
  },
  {
    groupKey: 'support',
    groupLabel: 'Chăm sóc & Khiếu nại',
    items: [
      { id: 'tickets', label: 'Phiếu Hỗ Trợ Khách Hàng', icon: <TicketIcon />, hasChildren: true },
      { id: 'disputes', label: 'Tranh Chấp & Khiếu Nại', icon: <DisputeIcon /> },
      { id: 'dispute_rules', label: 'Quy tắc xử lý khiếu nại', icon: <SkillReviewIcon /> },
      { id: 'faqs', label: 'Câu Hỏi Thường Gặp', icon: <FaqIcon /> },
    ],
  },
  {
    groupKey: 'system',
    groupLabel: 'Cấu hình tham số hệ thống',
    items: [
      { id: 'skills', label: 'Danh Mục Kỹ Năng', icon: <SkillReviewIcon /> },
      { id: 'config_matching', label: 'Thuật Toán Ghép Nối', icon: <MatchingConfigIcon /> },
      { id: 'config_operation', label: 'Vận Hành & Điểm Uy Tín', icon: <OperationConfigIcon /> },
      { id: 'config_financial', label: 'Tài Chính & Hoa Hồng', icon: <FinancialConfigIcon /> },
      { id: 'plans', label: 'Thiết Lập Giá Gói Dịch Vụ', icon: <PricingPlanIcon /> },
      { id: 'wage_matrix', label: 'Biểu Thù Lao Chuẩn', icon: <WageMatrixIcon /> },
      { id: 'config_general', label: 'Hệ Thống & Tài Khoản', icon: <GeneralConfigIcon /> },
    ],
  },
];

export const TICKET_SUB_ITEMS = [
  { key: 'ALL', label: 'Tất cả phiếu' },
  { key: 'MINE', label: 'Được giao cho tôi' },
  { key: 'OPEN', label: 'Chờ tiếp nhận' },
  { key: 'IN_PROGRESS', label: 'Đang xử lý' },
  { key: 'SLA_RISK', label: 'Sắp quá hạn SLA' },
  { key: 'CHAT', label: 'Tin nhắn trực tiếp' },
];

export const ADMIN_TAB_METADATA = {
  dashboard: {
    title: 'Bảng Điều Khiển & Chỉ Số',
    subtitle: 'Tổng hợp số liệu người dùng, thẩm định và hoạt động vận hành thời gian thực',
  },
  revenue: {
    title: 'Doanh Thu & Tài Chính',
    subtitle: 'Theo dõi dòng tiền, phân tích gói đăng ký doanh nghiệp và lịch sử giao dịch',
  },
  users: {
    title: 'Quản Trị Người Dùng',
    subtitle: 'Tra cứu danh sách, kiểm tra hồ sơ chi tiết và phân quyền khóa hoặc mở tài khoản',
  },
  shifts: {
    title: 'Danh Sách Ca Làm Việc',
    subtitle: 'Tra cứu thông tin sự kiện, ca làm việc thực tế và đối soát nhân sự tham gia',
  },
  reviews: {
    title: 'Kiểm Duyệt Đánh Giá & Matching',
    subtitle: 'Quản trị đánh giá hai chiều, phát hiện gian lận bơm/kéo điểm và gỡ bỏ review sai lệch',
  },
  noshows: {
    title: 'Xử Lý Vi Phạm Vắng Mặt',
    subtitle: 'Giám sát vắng mặt không phép, tự động trừ điểm uy tín và điều động nhân sự thay thế',
  },
  appeals: {
    title: 'Kháng Cáo Điểm Uy Tín',
    subtitle: 'Thẩm định bằng chứng và xét duyệt khiếu nại hoàn trả điểm uy tín cho người lao động',
  },
  tickets: {
    title: 'Phiếu Hỗ Trợ Khách Hàng',
    subtitle: 'Hàng đợi tiếp nhận phiếu, phản hồi trực tiếp và ghi chú nội bộ bảo mật',
  },
  disputes: {
    title: 'Tranh Chấp & Khiếu Nại',
    subtitle: 'Tiếp nhận đơn khiếu nại hợp đồng ca làm việc, phân công và ra phán quyết cuối cùng',
  },
  dispute_rules: {
    title: 'Quy Tắc Xử Lý Khiếu Nại',
    subtitle: 'Quản trị danh mục chế tài, điều kiện áp dụng và mức phạt quy chuẩn cho Ban giải quyết tranh chấp',
  },
  faqs: {
    title: 'Câu Hỏi Thường Gặp',
    subtitle: 'Quản lý kho tri thức trợ giúp, kiểm soát trạng thái xuất bản và đo lường giảm tải',
  },
  config_matching: {
    title: 'Thuật Toán Ghép Nối & Matching',
    subtitle: 'Tinh chỉnh trọng số khoảng cách, đánh giá sao, kinh nghiệm và công thức phân phối đề xuất ca',
  },
  config_operation: {
    title: 'Vận Hành & Điểm Uy Tín',
    subtitle: 'Thiết lập ngưỡng thời gian vắng mặt, mức trừ điểm uy tín và thời hạn đánh giá',
  },
  config_financial: {
    title: 'Tài Chính & Hoa Hồng',
    subtitle: 'Quản trị tỷ lệ hoa hồng nền tảng và thời hạn đối soát chuyển khoản thù lao trực tiếp',
  },
  plans: {
    title: 'Thiết Lập Giá & Hạn Mức Gói Dịch Vụ',
    subtitle: 'Quy định đơn giá (VNĐ), hạn mức sự kiện, ca làm, nhân sự và tính năng Matching cho Ban tổ chức sự kiện',
  },
  wage_matrix: {
    title: 'Biểu Ma Trận Thù Lao Chuẩn',
    subtitle: 'Thiết lập định mức thù lao thị trường theo khu vực, thời điểm ca và mức độ khẩn cấp',
  },
  skills: {
    title: 'Danh Mục Kỹ Năng Hệ Thống',
    subtitle: 'Quản lý danh sách kỹ năng chuẩn, phân nhóm nghiệp vụ và kiểm soát trạng thái hiển thị cho sự kiện',
  },
  config_general: {
    title: 'Hệ Thống & Tài Khoản',
    subtitle: 'Quy định độ tuổi lao động tối thiểu, phiên bản ứng dụng di động bắt buộc và hộp thư CSKH',
  },
  configs: {
    title: 'Tham Số Hệ Thống',
    subtitle: 'Cấu hình linh hoạt các tham số thuật toán ghép ca, ngưỡng vi phạm và quy tắc nền tảng',
  },
};

export const getTicketMeta = (filter, isCc = false) => {
  switch (filter) {
    case 'CHAT':
      return {
        title: 'Tin Nhắn Trực Tiếp',
        subtitle: 'Hộp thư hội thoại trực tiếp với Nhà tổ chức sự kiện và Nhân viên theo thời gian thực',
      };
    case 'MINE':
      return {
        title: 'Phiếu Được Giao Cho Tôi',
        subtitle: 'Danh sách phiếu hỗ trợ được phân công phụ trách xử lý trực tiếp',
      };
    case 'OPEN':
      return {
        title: 'Phiếu Chờ Tiếp Nhận',
        subtitle: isCc
          ? 'Hàng đợi phiếu hỗ trợ mới gửi đang chờ tiếp nhận xử lý'
          : 'Hàng đợi phiếu hỗ trợ mới gửi đang chờ nhân sự tiếp nhận',
      };
    case 'IN_PROGRESS':
      return {
        title: 'Phiếu Đang Xử Lý',
        subtitle: 'Các phiếu hỗ trợ đang trong tiến trình trao đổi và giải quyết',
      };
    case 'SLA_RISK':
      return {
        title: 'Phiếu Sắp Quá Hạn Cam Kết',
        subtitle: 'Các phiếu hỗ trợ sắp chạm ngưỡng cam kết thời gian phản hồi cần ưu tiên',
      };
    case 'ALL':
    default:
      return {
        title: 'Tất Cả Phiếu Hỗ Trợ',
        subtitle: isCc
          ? 'Hàng đợi tiếp nhận phiếu, phản hồi hỗ trợ người dùng và kiểm soát thời hạn cam kết'
          : 'Hàng đợi tiếp nhận phiếu, phản hồi trực tiếp và ghi chú nội bộ bảo mật',
      };
  }
};
