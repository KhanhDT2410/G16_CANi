/**
 * Enums and constants for Dispute & Resolution (SRS FE-11: UC-96 -> UC-104)
 */

export const DISPUTE_STATUS = {
  OPEN: 'OPEN',
  INVESTIGATING: 'INVESTIGATING',
  RESOLVED: 'RESOLVED',
  UNVERIFIED: 'UNVERIFIED',
  ESCALATED: 'ESCALATED',
  FINAL_RESOLVED: 'FINAL_RESOLVED',
};

export const DISPUTE_STATUS_CONFIG = {
  OPEN: { label: 'Mới mở (Chờ CC)', badgeClass: 'badge-status-open' },
  INVESTIGATING: { label: 'Đang điều tra', badgeClass: 'badge-status-investigating' },
  RESOLVED: { label: 'Đã giải quyết (CC)', badgeClass: 'badge-status-resolved' },
  UNVERIFIED: { label: 'Không xác minh được', badgeClass: 'badge-status-unverified' },
  ESCALATED: { label: 'Đã kháng nghị lên Admin', badgeClass: 'badge-status-escalated' },
  FINAL_RESOLVED: { label: 'Phán quyết cuối (Admin)', badgeClass: 'badge-status-final' },
};

export const DISPUTE_TYPES = {
  PAYMENT: 'PAYMENT',
  WORK_QUALITY: 'WORK_QUALITY',
  ATTENDANCE: 'ATTENDANCE',
  CONDUCT: 'CONDUCT',
  OTHER: 'OTHER',
};

export const DISPUTE_TYPE_LABELS = {
  PAYMENT: 'Tranh chấp thù lao / Tiền công',
  WORK_QUALITY: 'Chất lượng công việc',
  ATTENDANCE: 'Điểm danh / Bỏ ca / Đi trễ',
  CONDUCT: 'Hành vi / Ứng xử vi phạm',
  OTHER: 'Khác',
};

export const DISPUTE_OUTCOMES = {
  EO_FAULT: 'EO_FAULT',
  WORKER_FAULT: 'WORKER_FAULT',
  PARTIAL: 'PARTIAL',
  UNVERIFIED: 'UNVERIFIED',
  UPHOLD: 'UPHOLD',
};

export const DISPUTE_OUTCOME_LABELS = {
  EO_FAULT: 'Lỗi thuộc về Nhà tổ chức (EO_FAULT)',
  WORKER_FAULT: 'Lỗi thuộc về Worker (-10 điểm RS) (WORKER_FAULT)',
  PARTIAL: 'Lỗi từ cả hai phía (PARTIAL)',
  UNVERIFIED: 'Không đủ chứng cứ xác minh (UNVERIFIED)',
  UPHOLD: 'Giữ nguyên phán quyết sơ bộ (UPHOLD)',
};

export const DISPUTE_EVIDENCE_TYPES = {
  IMAGE: 'IMAGE',
  VIDEO: 'VIDEO',
  DOCUMENT: 'DOCUMENT',
  TRANSACTION_RECORD: 'TRANSACTION_RECORD',
};

export const DISPUTE_EVIDENCE_LABELS = {
  IMAGE: 'Hình ảnh (Ảnh chụp / Biên lai)',
  VIDEO: 'Video clip',
  DOCUMENT: 'Tài liệu / Biên bản',
  TRANSACTION_RECORD: 'Bản ghi giao dịch ngân hàng',
};

/**
 * Dữ liệu mẫu phục vụ kiểm thử luồng nghiệp vụ khi database chưa có ca phát sinh tranh chấp
 */
export const SAMPLE_DISPUTES = [
  {
    id: 'disp-demo-01',
    assignmentId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
    complainantId: '08d4cbd3-9d94-4307-99c5-b66484d5de84',
    respondentId: 'f8b3d61a-8275-4cf9-b1e8-3a4c0d75922b',
    type: 'PAYMENT',
    title: 'Chưa nhận tiền công ca Lễ tân TechExpo',
    description:
      'Tôi đã hoàn thành ca làm việc từ 8h đến 17h nhưng đến nay nhà tổ chức vẫn chưa thanh toán 350.000đ tiền công và 50.000đ phụ cấp ăn trưa sau 24h đối soát.',
    status: 'OPEN',
    assignedTo: null,
    slaDeadline: new Date(Date.now() + 5 * 24 * 3600 * 1000).toISOString(),
    slaAtRisk: false,
    createdAt: new Date(Date.now() - 2 * 24 * 3600 * 1000).toISOString(),
    evidences: [
      {
        id: 'ev-01',
        party: '08d4cbd3-9d94-4307-99c5-b66484d5de84',
        fileType: 'IMAGE',
        fileUrl: 'https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?w=400&q=80',
        label: 'Ảnh chụp màn hình check-in mã QR tại ca làm',
        createdAt: new Date(Date.now() - 2 * 24 * 3600 * 1000).toISOString(),
      },
    ],
  },
  {
    id: 'disp-demo-02',
    assignmentId: 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
    complainantId: 'f8b3d61a-8275-4cf9-b1e8-3a4c0d75922b',
    respondentId: '08d4cbd3-9d94-4307-99c5-b66484d5de84',
    type: 'ATTENDANCE',
    title: 'Worker bỏ ca trực âm thanh không báo trước (SLA < 24h)',
    description:
      'Worker tự ý rời khỏi vị trí bàn điều khiển âm thanh trước khi chương trình kết thúc 1 tiếng, gây gián đoạn tiết mục biểu diễn chính của gala.',
    status: 'INVESTIGATING',
    assignedTo: 'cc-user-current',
    slaDeadline: new Date(Date.now() + 18 * 3600 * 1000).toISOString(),
    slaAtRisk: true,
    createdAt: new Date(Date.now() - 6 * 24 * 3600 * 1000).toISOString(),
    evidences: [
      {
        id: 'ev-02',
        party: 'f8b3d61a-8275-4cf9-b1e8-3a4c0d75922b',
        fileType: 'IMAGE',
        fileUrl: 'https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=500&q=80',
        label: 'Biên bản ghi nhận sự việc vắng mặt tại vị trí',
        createdAt: new Date(Date.now() - 5 * 24 * 3600 * 1000).toISOString(),
      },
    ],
  },
  {
    id: 'disp-demo-03',
    assignmentId: 'b2c3d4e5-f6a7-4b5c-9d0e-1f2a3b4c5d6e',
    complainantId: '08d4cbd3-9d94-4307-99c5-b66484d5de84',
    respondentId: 'f8b3d61a-8275-4cf9-b1e8-3a4c0d75922b',
    type: 'WORK_QUALITY',
    title: 'Kháng nghị quyết định CSKH lên Ban Quản trị viên (Admin)',
    description:
      'Bên Worker không đồng ý với kết luận sơ bộ của CSKH, khẳng định thiết bị hỏng do nguồn điện của ban tổ chức.',
    status: 'ESCALATED',
    outcome: 'PARTIAL',
    decisionRationale:
      'Chăm sóc khách hàng nhận định cả hai bên đều có một phần trách nhiệm trong việc kiểm tra thiết bị trước giờ diễn.',
    escalationReason:
      'Tôi có đầy đủ video quay lại hiện trường chứng minh nguồn điện sân khấu bị chập trước khi sự cố xảy ra. Đề nghị Admin thẩm định lại.',
    assignedTo: 'admin-lead',
    slaDeadline: new Date(Date.now() + 2 * 24 * 3600 * 1000).toISOString(),
    slaAtRisk: false,
    createdAt: new Date(Date.now() - 4 * 24 * 3600 * 1000).toISOString(),
    evidences: [],
  },
];

