export const DEFAULT_NEW_EVENT = {
  name: '',
  description: '',
  venueAddress: '',
  venueLat: 10.7328,
  venueLng: 106.7214,
  eventDate: '2026-11-20T08:00:00Z',
  startTime: '2026-11-20T08:00',
  endTime: '2026-11-20T22:00',
  imageUrl: '',
  mode: 'MATCHING',
};

export const DEMO_EVENTS = [
  {
    eventId: 'EVT-98721',
    name: 'Lễ Hội Âm Nhạc Mùa Hè 2026 - Summer Fest Live Stage',
    description: 'Đại nhạc hội ngoài trời quy tụ các nghệ sĩ trẻ hàng đầu, dự kiến đón tiếp 5.000 khán giả.',
    venueAddress: 'Trung tâm Hội chợ & Triển lãm Sài Gòn (SECC), 799 Nguyễn Văn Linh, Q.7, TP.HCM',
    venueLat: 10.7328,
    venueLng: 106.7214,
    eventDate: '2026-10-20T18:00:00Z',
    mode: 'MATCHING',
    status: 'ACTIVE',
    totalShifts: 3,
    totalRequiredWorkers: 15,
    totalConfirmedWorkers: 12,
    totalEstimatedCost: 4500000,
  },
];

/**
 * Xác định trạng thái chi tiết của từng Ca làm việc (Shift) theo thời gian thực và tiến độ nhân sự
 */
export function getDynamicShiftStatus(shift = {}, eventStatus = 'ACTIVE') {
  const rawStatus = (shift.status || '').toUpperCase();
  const evtStatus = (eventStatus || '').toUpperCase();

  if (rawStatus === 'CANCELLED' || evtStatus === 'CANCELLED') {
    return {
      code: 'CANCELLED',
      label: 'Đã hủy ca',
      colorClass: 'red',
      bg: '#fef2f2',
      color: '#dc2626',
      border: '#fecaca',
    };
  }

  if (evtStatus === 'DRAFT') {
    return {
      code: 'NOT_STARTED',
      label: 'Chưa bắt đầu (Nháp)',
      colorClass: 'yellow',
      bg: '#fffbeb',
      color: '#b45309',
      border: '#fde68a',
    };
  }

  const now = new Date();
  const start = shift.startTime ? new Date(shift.startTime) : null;
  const end = shift.endTime ? new Date(shift.endTime) : null;
  const confirmed = Number(shift.confirmedWorkers || 0);
  const required = Math.max(1, Number(shift.requiredWorkers || 1));
  const isEmergency = Boolean(shift.emergency);

  if (rawStatus === 'COMPLETED' || (end && !isNaN(end.getTime()) && now > end)) {
    return {
      code: 'COMPLETED',
      label: 'Đã kết thúc',
      colorClass: 'gray',
      bg: '#f1f5f9',
      color: '#475569',
      border: '#cbd5e1',
    };
  }

  // Nếu đang bật chế độ khẩn cấp (Emergency Dispatch)
  if (isEmergency) {
    return {
      code: 'EMERGENCY',
      label: `Điều phối khẩn cấp (Thiếu ${Math.max(0, required - confirmed)} nhân sự)`,
      colorClass: 'red',
      bg: '#fef2f2',
      color: '#b91c1c',
      border: '#ef4444',
      isEmergency: true,
    };
  }

  // Đang trong khung giờ ca làm việc (startTime <= now <= endTime)
  if (
    rawStatus === 'IN_PROGRESS' ||
    rawStatus === 'ONGOING' ||
    (start && !isNaN(start.getTime()) && now >= start && (!end || isNaN(end.getTime()) || now <= end))
  ) {
    if (confirmed < required) {
      return {
        code: 'UNDERSTAFFED_IN_PROGRESS',
        label: `Đang diễn ra - Thiếu ${required - confirmed} nhân sự`,
        colorClass: 'red',
        bg: '#fff1f2',
        color: '#e11d48',
        border: '#fda4af',
        isCritical: true,
      };
    }
    return {
      code: 'IN_PROGRESS',
      label: 'Đang diễn ra (Đủ nhân sự)',
      colorClass: 'green',
      bg: '#ecfdf5',
      color: '#059669',
      border: '#a7f3d0',
    };
  }

  // Trước giờ bắt đầu ca (now < start)
  if (confirmed >= required) {
    return {
      code: 'FULL_WAITING',
      label: 'Đủ nhân sự - Chờ diễn ra',
      colorClass: 'purple',
      bg: '#f0fdf4',
      color: '#15803d',
      border: '#bbf7d0',
    };
  }

  // Sát giờ bắt đầu (còn dưới 4 giờ) mà chưa đủ người
  if (start && !isNaN(start.getTime()) && (start.getTime() - now.getTime()) <= 4 * 3600 * 1000) {
    const hoursLeft = Math.max(0, Math.round((start.getTime() - now.getTime()) / 3600000));
    return {
      code: 'URGENT_STARTING_SOON',
      label: `Sắp diễn ra (~${hoursLeft} giờ) - Cần gấp ${required - confirmed} người`,
      colorClass: 'amber',
      bg: '#fffbeb',
      color: '#b45309',
      border: '#fde68a',
      isUrgent: true,
    };
  }

  return {
    code: 'RECRUITING',
    label: 'Đang tìm kiếm nhân sự',
    colorClass: 'blue',
    bg: '#eff6ff',
    color: '#1d4ed8',
    border: '#bfdbfe',
  };
}

/**
 * Lọc danh sách các ca làm việc đang bị thiếu hụt nhân sự khẩn cấp hoặc sát giờ
 */
export function getShiftStaffingAlerts(shifts = []) {
  const now = new Date();
  return (shifts || []).filter((s) => {
    if (!s || s.status === 'CANCELLED') return false;
    const start = s.startTime ? new Date(s.startTime) : null;
    const end = s.endTime ? new Date(s.endTime) : null;
    if (end && !isNaN(end.getTime()) && now > end) return false; // Đã kết thúc
    const confirmed = Number(s.confirmedWorkers || 0);
    const required = Math.max(1, Number(s.requiredWorkers || 1));
    if (confirmed >= required) return false; // Đã đủ người
    if (s.emergency) return true; // Đang bật emergency
    if (start && !isNaN(start.getTime()) && now >= start) return true; // Đang diễn ra mà thiếu người
    if (start && !isNaN(start.getTime()) && (start.getTime() - now.getTime()) <= 4 * 3600 * 1000) return true; // Sát giờ < 4h
    return false;
  });
}

/**
 * Xác định trạng thái chi tiết của Sự kiện (Event) dựa trên trạng thái gốc, khung thời gian và tiến độ tuyển dụng
 */
export function getDynamicEventStatus(evt = {}) {
  const rawStatus = (evt.status || 'DRAFT').toUpperCase();

  if (rawStatus === 'CANCELLED') {
    return {
      code: 'CANCELLED',
      label: 'Đã hủy',
      colorClass: 'red',
      subText: 'Sự kiện đã hủy',
    };
  }

  if (rawStatus === 'COMPLETED') {
    return {
      code: 'COMPLETED',
      label: 'Đã hoàn thành',
      colorClass: 'gray',
      subText: 'Sự kiện đã kết thúc',
    };
  }

  if (rawStatus === 'DRAFT') {
    return {
      code: 'DRAFT',
      label: 'Chưa bắt đầu (Bản nháp)',
      colorClass: 'yellow',
      subText: 'Chưa mở tìm kiếm nhân sự',
    };
  }

  const now = new Date();
  const start = evt.startTime ? new Date(evt.startTime) : evt.eventDate ? new Date(evt.eventDate) : null;
  const end = evt.endTime ? new Date(evt.endTime) : null;

  // Kiểm tra nếu có danh sách ca làm việc để lấy khoảng thời gian thực tế
  let earliestShiftStart = start;
  let latestShiftEnd = end;
  if (Array.isArray(evt.shifts) && evt.shifts.length > 0) {
    const activeShifts = evt.shifts.filter((s) => s.status !== 'CANCELLED');
    activeShifts.forEach((s) => {
      if (s.startTime) {
        const st = new Date(s.startTime);
        if (!isNaN(st.getTime()) && (!earliestShiftStart || isNaN(earliestShiftStart.getTime()) || st < earliestShiftStart)) {
          earliestShiftStart = st;
        }
      }
      if (s.endTime) {
        const et = new Date(s.endTime);
        if (!isNaN(et.getTime()) && (!latestShiftEnd || isNaN(latestShiftEnd.getTime()) || et > latestShiftEnd)) {
          latestShiftEnd = et;
        }
      }
    });
  }

  // Đã qua thời gian kết thúc sự kiện
  if (latestShiftEnd && !isNaN(latestShiftEnd.getTime()) && now > latestShiftEnd) {
    return {
      code: 'COMPLETED',
      label: 'Đã kết thúc',
      colorClass: 'gray',
      subText: 'Đã qua khung giờ sự kiện',
    };
  }

  // Đang nằm trong khung thời gian diễn ra sự kiện
  if (
    rawStatus === 'ONGOING' ||
    (earliestShiftStart &&
      !isNaN(earliestShiftStart.getTime()) &&
      now >= earliestShiftStart &&
      (!latestShiftEnd || isNaN(latestShiftEnd.getTime()) || now <= latestShiftEnd))
  ) {
    return {
      code: 'IN_PROGRESS',
      label: 'Đang diễn ra',
      colorClass: 'green',
      subText: 'Đang trong khung giờ sự kiện',
    };
  }

  // Sự kiện chưa tới giờ bắt đầu (now < startTime) nhưng đã xuất bản (ACTIVE / UPCOMING / APPROVED)
  const confirmed = Number(evt.totalConfirmedWorkers || 0);
  const required = Number(evt.totalRequiredWorkers || 0);

  if (required > 0 && confirmed >= required) {
    return {
      code: 'READY',
      label: 'Đủ nhân sự • Chờ diễn ra',
      colorClass: 'purple',
      subText: `Đã chốt đủ ${confirmed}/${required} nhân sự`,
    };
  }

  return {
    code: 'RECRUITING',
    label: 'Đang tìm kiếm nhân sự',
    colorClass: 'blue',
    subText: required > 0 ? `Đã tuyển ${confirmed}/${required} nhân sự` : 'Đang mở tìm kiếm',
  };
}

export function getEventStatusPriority(evtOrStatus) {
  const statusObj = typeof evtOrStatus === 'object' && evtOrStatus !== null
    ? getDynamicEventStatus(evtOrStatus)
    : { code: evtOrStatus };

  switch (statusObj.code) {
    case 'IN_PROGRESS':
      return 1; // Đang diễn ra ưu tiên số 1
    case 'RECRUITING':
    case 'ACTIVE':
    case 'UPCOMING':
      return 2; // Đang tìm kiếm nhân sự ưu tiên số 2
    case 'READY':
    case 'APPROVED':
      return 3; // Đã đủ nhân sự chờ diễn ra
    case 'DRAFT':
      return 4; // Chưa bắt đầu (Bản nháp)
    case 'COMPLETED':
      return 5; // Đã kết thúc
    case 'CANCELLED':
      return 6; // Đã hủy
    default:
      return 4;
  }
}

export function filterAndSortEvents(events, searchQuery, statusFilter, sortBy) {
  return events
    .filter((evt) => {
      const matchSearch =
        !searchQuery ||
        evt.name?.toLowerCase().includes(searchQuery.toLowerCase()) ||
        evt.venueAddress?.toLowerCase().includes(searchQuery.toLowerCase()) ||
        evt.eventId?.toLowerCase().includes(searchQuery.toLowerCase());

      if (statusFilter === 'CANCELLED') {
        return matchSearch && evt.status === 'CANCELLED';
      }

      if (evt.status === 'CANCELLED') {
        return false;
      }

      const dynStatus = getDynamicEventStatus(evt);

      const matchStatus =
        statusFilter === 'ALL' ||
        (statusFilter === 'ACTIVE' && (evt.status === 'ACTIVE' || evt.status === 'APPROVED')) ||
        statusFilter === dynStatus.code ||
        evt.status === statusFilter;

      return matchSearch && matchStatus;
    })
    .sort((a, b) => {
      const priorityDiff = getEventStatusPriority(a) - getEventStatusPriority(b);
      if (priorityDiff !== 0) return priorityDiff;

      if (sortBy === 'NAME') return (a.name || '').localeCompare(b.name || '');
      if (sortBy === 'WORKERS') return (b.totalRequiredWorkers || 0) - (a.totalRequiredWorkers || 0);
      if (sortBy === 'OLDEST') return new Date(a.createdAt || a.eventDate || 0) - new Date(b.createdAt || b.eventDate || 0);
      return new Date(b.createdAt || b.eventDate || 0) - new Date(a.createdAt || a.eventDate || 0);
    });
}

export function calculateEventMetrics(events) {
  const nonCancelled = events.filter((e) => e.status !== 'CANCELLED');
  const activeEvents = events.filter((e) => e.status === 'ACTIVE' || e.status === 'APPROVED');

  const totalEvents = nonCancelled.length;
  const activeEventsCount = activeEvents.length;
  const totalRequiredWorkers = nonCancelled.reduce((sum, e) => sum + (e.totalRequiredWorkers || 0), 0);
  const totalConfirmedWorkers = nonCancelled.reduce((sum, e) => sum + (e.totalConfirmedWorkers || 0), 0);
  const totalEstimatedCost = nonCancelled.reduce((sum, e) => sum + (e.totalEstimatedCost || 0), 0);
  const fillRate = totalRequiredWorkers > 0 ? Math.round((totalConfirmedWorkers / totalRequiredWorkers) * 100) : 0;

  return {
    totalEvents,
    activeEventsCount,
    totalRequiredWorkers,
    totalConfirmedWorkers,
    totalEstimatedCost,
    fillRate,
  };
}
