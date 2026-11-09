import {
  DashboardIcon,
  RevenueIcon,
  UsersIcon,
  NoShowIcon,
  AppealIcon,
  TicketIcon,
  DisputeIcon,
  FaqIcon,
  ConfigIcon,
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
    groupLabel: 'Cấu hình hệ thống',
    items: [
      { id: 'configs', label: 'Tham Số Hệ Thống', icon: <ConfigIcon />, hasChildren: true },
    ],
  },
];

export const CONFIG_SUB_ITEMS = [
  { key: 'ALL', label: 'Tất cả tham số' },
  { key: 'MATCHING', label: 'Thuật toán Matching' },
  { key: 'OPERATION', label: 'Vận hành & Điểm uy tín' },
  { key: 'FINANCIAL', label: 'Tài chính & Hoa hồng' },
  { key: 'GENERAL', label: 'Hệ thống & Tài khoản' },
];

export const TICKET_SUB_ITEMS = [
  { key: 'ALL', label: 'Tất cả phiếu' },
  { key: 'MINE', label: 'Được giao cho tôi' },
  { key: 'OPEN', label: 'Chờ tiếp nhận' },
  { key: 'IN_PROGRESS', label: 'Đang xử lý' },
  { key: 'SLA_RISK', label: 'Sắp quá hạn SLA' },
];
