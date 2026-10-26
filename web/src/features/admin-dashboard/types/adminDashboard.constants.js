/**
 * Constants & mock data cho Admin Dashboard (W-38).
 */

/** Trạng thái dịch vụ hệ thống */
export const SERVICE_STATUS = {
  HEALTHY: 'HEALTHY',
  DEGRADED: 'DEGRADED',
  DOWN: 'DOWN',
};

export const SERVICE_STATUS_LABELS = {
  [SERVICE_STATUS.HEALTHY]: 'Hoạt động tốt',
  [SERVICE_STATUS.DEGRADED]: 'Chậm / Giảm hiệu suất',
  [SERVICE_STATUS.DOWN]: 'Ngừng hoạt động',
};

/** Trạng thái tranh chấp */
export const DISPUTE_STATUS = {
  SUBMITTED: 'SUBMITTED',
  IN_REVIEW: 'IN_REVIEW',
  RESOLVED: 'RESOLVED',
  ESCALATED: 'ESCALATED',
};

export const DISPUTE_STATUS_LABELS = {
  [DISPUTE_STATUS.SUBMITTED]: 'Chờ xử lý',
  [DISPUTE_STATUS.IN_REVIEW]: 'Đang điều tra',
  [DISPUTE_STATUS.RESOLVED]: 'Đã giải quyết',
  [DISPUTE_STATUS.ESCALATED]: 'Escalate Admin',
};

/** Mock dữ liệu metrics tổng quan */
export const MOCK_METRICS = {
  totalActiveUsers: 1842,
  activeUsersChange: 12.5,
  totalActiveEvents: 28,
  activeEventsChange: 8.3,
  openDisputes: 7,
  openDisputesChange: -14.2,
  totalRevenue: 48200000,
  revenueChange: 22.1,
  totalWorkers: 3654,
  totalEOs: 186,
  totalAssignmentsToday: 312,
  avgMatchScore: 87.4,
  noShowRate: 2.8,
  cskhResolutionRate: 98.4,
};

/** Mock sức khỏe hệ thống */
export const MOCK_SYSTEM_HEALTH = [
  { name: 'API Gateway', status: SERVICE_STATUS.HEALTHY, uptime: 99.98, latency: 42 },
  { name: 'PostgreSQL Database', status: SERVICE_STATUS.HEALTHY, uptime: 99.99, latency: 8 },
  { name: 'Matching Engine', status: SERVICE_STATUS.HEALTHY, uptime: 99.95, latency: 156 },
  { name: 'Notification Service', status: SERVICE_STATUS.HEALTHY, uptime: 99.92, latency: 65 },
  { name: 'File Storage (S3)', status: SERVICE_STATUS.HEALTHY, uptime: 99.99, latency: 34 },
  { name: 'VNPay Payment Gateway', status: SERVICE_STATUS.DEGRADED, uptime: 98.70, latency: 890 },
];

/** Mock sự kiện đang hoạt động */
export const MOCK_ACTIVE_EVENTS = [
  {
    id: 'EVT-2026-401',
    name: 'Lễ hội Âm nhạc Summer Beat 2026',
    eoName: 'Công ty CP Sự kiện Sao Mai',
    location: 'Quận 7, TP.HCM',
    startDate: '27/10/2026',
    endDate: '28/10/2026',
    totalShifts: 24,
    filledShifts: 22,
    status: 'ACTIVE',
  },
  {
    id: 'EVT-2026-398',
    name: 'Hội nghị Công Nghệ Quốc Tế 2026',
    eoName: 'Tập đoàn Truyền thông Metan',
    location: 'Quận 1, TP.HCM',
    startDate: '29/10/2026',
    endDate: '30/10/2026',
    totalShifts: 16,
    filledShifts: 14,
    status: 'ACTIVE',
  },
  {
    id: 'EVT-2026-395',
    name: 'Tiệc Cưới Gala Royal Palace',
    eoName: 'Apex Event Agency',
    location: 'Quận 3, TP.HCM',
    startDate: '30/10/2026',
    endDate: '30/10/2026',
    totalShifts: 8,
    filledShifts: 8,
    status: 'ACTIVE',
  },
  {
    id: 'EVT-2026-390',
    name: 'Triển Lãm Ô Tô Việt Nam 2026',
    eoName: 'VN AutoExpo Ltd.',
    location: 'SECC, Quận 7, TP.HCM',
    startDate: '01/11/2026',
    endDate: '05/11/2026',
    totalShifts: 42,
    filledShifts: 30,
    status: 'RECRUITING',
  },
];

/** Mock tranh chấp gần đây */
export const MOCK_RECENT_DISPUTES = [
  {
    id: 'DISP-9021',
    workerName: 'Nguyễn Văn Minh',
    eoName: 'Sao Mai Events',
    type: 'Chưa thanh toán',
    amount: 320000,
    status: DISPUTE_STATUS.ESCALATED,
    createdAt: '22/10/2026',
    priority: 'HIGH',
  },
  {
    id: 'DISP-9018',
    workerName: 'Trần Thị Thu Thảo',
    eoName: 'Metan Group',
    type: 'Thiếu phụ cấp',
    amount: 70000,
    status: DISPUTE_STATUS.IN_REVIEW,
    createdAt: '21/10/2026',
    priority: 'MEDIUM',
  },
  {
    id: 'DISP-9015',
    workerName: 'Lê Quang Dũng',
    eoName: 'Apex Agency',
    type: 'Worker không đến ca',
    amount: 0,
    status: DISPUTE_STATUS.SUBMITTED,
    createdAt: '20/10/2026',
    priority: 'MEDIUM',
  },
  {
    id: 'DISP-9012',
    workerName: 'Phạm Hùng Cường',
    eoName: 'VN AutoExpo',
    type: 'Sai số tài khoản',
    amount: 220000,
    status: DISPUTE_STATUS.RESOLVED,
    createdAt: '19/10/2026',
    priority: 'LOW',
  },
];

/** Mock CSKH operations */
export const MOCK_CSKH_OPS = {
  ticketsOpen: 12,
  ticketsInProgress: 8,
  ticketsClosed: 345,
  avgResponseTime: '18 phút',
  avgResolutionTime: '2.4 giờ',
  cskhOnline: 4,
  cskhTotal: 6,
};
