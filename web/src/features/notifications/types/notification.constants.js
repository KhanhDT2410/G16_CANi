/**
 * Cấu hình hiển thị theo từng loại thông báo nghiệp vụ (SRS UC-117)
 */
export const NOTIFICATION_TYPE_CONFIG = {
  PROPOSAL: {
    label: 'Lời mời nhận ca',
    icon: 'CV',
    badgeClass: 'notif-type-proposal',
  },
  ASSIGNMENT: {
    label: 'Ca làm việc',
    icon: 'CA',
    badgeClass: 'notif-type-assignment',
  },
  REMINDER: {
    label: 'Nhắc nhở ca làm',
    icon: 'NN',
    badgeClass: 'notif-type-reminder',
  },
  PAYMENT: {
    label: 'Thanh toán & Gói',
    icon: 'TT',
    badgeClass: 'notif-type-payment',
  },
  DISPUTE: {
    label: 'Tranh chấp khiếu nại',
    icon: 'TC',
    badgeClass: 'notif-type-dispute',
  },
  RATING: {
    label: 'Đánh giá & Uy tín',
    icon: 'DG',
    badgeClass: 'notif-type-rating',
  },
  SYSTEM: {
    label: 'Hệ thống',
    icon: 'HT',
    badgeClass: 'notif-type-system',
  },
  GROUP: {
    label: 'Nhóm nhân sự',
    icon: 'NH',
    badgeClass: 'notif-type-group',
  },
  SUPPORT_TICKET: {
    label: 'Phiếu hỗ trợ',
    icon: 'PH',
    badgeClass: 'notif-type-ticket',
  },
  RS_APPEAL: {
    label: 'Kháng cáo điểm uy tín',
    icon: 'UT',
    badgeClass: 'notif-type-appeal',
  },
  EMERGENCY: {
    label: 'Cứu trợ khẩn cấp',
    icon: '🚨',
    badgeClass: 'notif-type-emergency',
  },
};

/**
 * Kiểm tra xem thông báo có thuộc sự kiện Emergency Dispatch hay không (Ticket EO-011)
 */
export const isEmergencyNotification = (notification) => {
  if (!notification) return false;
  const title = String(notification.title || '').toUpperCase();
  if (
    title.includes('KHẨN CẤP') ||
    title.includes('CỨU TRỢ') ||
    title.includes('EMERGENCY')
  ) {
    return true;
  }
  let parsedData = {};
  if (notification.data) {
    try {
      parsedData = typeof notification.data === 'string' ? JSON.parse(notification.data) : notification.data;
    } catch {
      // ignore
    }
  }
  const type = String(parsedData?.type || '').toUpperCase();
  return type.startsWith('EMERGENCY') || type === 'UNDERSTAFFED_NEAR_START';
};

/**
 * Định dạng thời gian tương đối thân thiện bằng tiếng Việt
 */
export const formatRelativeTime = (dateInput) => {
  if (!dateInput) return '';
  const date = new Date(dateInput);
  if (isNaN(date.getTime())) return '';

  const now = new Date();
  const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

  if (diffInSeconds < 0) return 'Vừa xong';
  if (diffInSeconds < 60) return 'Vừa xong';
  const diffInMinutes = Math.floor(diffInSeconds / 60);
  if (diffInMinutes < 60) return `${diffInMinutes} phút trước`;
  const diffInHours = Math.floor(diffInMinutes / 60);
  if (diffInHours < 24) return `${diffInHours} giờ trước`;
  const diffInDays = Math.floor(diffInHours / 24);
  if (diffInDays < 7) return `${diffInDays} ngày trước`;

  const day = String(date.getDate()).padStart(2, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const year = date.getFullYear();
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${day}/${month}/${year} ${hours}:${minutes}`;
};

