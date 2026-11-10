import {
  DashboardIcon,
  RevenueIcon,
  UsersIcon,
  NoShowIcon,
  AppealIcon,
  TicketIcon,
  DisputeIcon,
  FaqIcon,
  MatchingConfigIcon,
  OperationConfigIcon,
  FinancialConfigIcon,
  GeneralConfigIcon,
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
      { id: 'faqs', label: 'Câu Hỏi Thường Gặp', icon: <FaqIcon /> },
    ],
  },
  {
    groupKey: 'system',
    groupLabel: 'Cấu hình tham số hệ thống',
    items: [
      { id: 'config_matching', label: 'Thuật Toán Ghép Nối', icon: <MatchingConfigIcon /> },
      { id: 'config_operation', label: 'Vận Hành & Điểm Uy Tín', icon: <OperationConfigIcon /> },
      { id: 'config_financial', label: 'Tài Chính & Hoa Hồng', icon: <FinancialConfigIcon /> },
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
];
