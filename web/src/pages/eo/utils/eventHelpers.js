export function toDateTimeLocalInput(date) {
  if (!date) return '';
  const d = new Date(date);
  if (isNaN(d.getTime())) return '';
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const hours = String(d.getHours()).padStart(2, '0');
  const minutes = String(d.getMinutes()).padStart(2, '0');
  return `${year}-${month}-${day}T${hours}:${minutes}`;
}

export function createDefaultNewEvent() {
  const tomorrowStart = new Date();
  tomorrowStart.setDate(tomorrowStart.getDate() + 1);
  tomorrowStart.setHours(8, 0, 0, 0);

  const tomorrowEnd = new Date(tomorrowStart);
  tomorrowEnd.setHours(18, 0, 0, 0);

  return {
    name: '',
    description: '',
    venueAddress: '',
    venueLat: 10.7328,
    venueLng: 106.7214,
    locationPicked: false,
    venueCoordinatesConfirmed: false,
    eventDate: toDateTimeLocalInput(tomorrowStart).slice(0, 10),
    startTime: toDateTimeLocalInput(tomorrowStart),
    endTime: toDateTimeLocalInput(tomorrowEnd),
    imageUrl: '',
    mode: 'MATCHING',
  };
}

export const DEFAULT_NEW_EVENT = createDefaultNewEvent();

/**
 * Kiểm tra thời gian sự kiện (ngay khi chọn hoặc khi submit)
 */
export function validateEventTimeRange(startStr, endStr, { allowPastStart = false } = {}) {
  const errs = {};
  if (!startStr) errs.startTime = 'Vui lòng chọn thời gian bắt đầu sự kiện.';
  if (!endStr) errs.endTime = 'Vui lòng chọn thời gian kết thúc sự kiện.';

  if (startStr) {
    const start = new Date(startStr);
    if (isNaN(start.getTime())) {
      errs.time = 'Thời gian bắt đầu sự kiện không hợp lệ.';
      return errs;
    }
    if (!allowPastStart && start.getTime() < Date.now() - 5 * 60 * 1000) {
      errs.time = 'Thời gian bắt đầu sự kiện không được nằm trong quá khứ.';
      errs.startTime = errs.time;
      return errs;
    }
  }

  if (startStr && endStr) {
    const start = new Date(startStr);
    const end = new Date(endStr);
    if (isNaN(start.getTime()) || isNaN(end.getTime())) {
      errs.time = 'Thời gian sự kiện không hợp lệ.';
    } else if (start >= end) {
      errs.time = 'Thời gian kết thúc phải diễn ra sau thời gian bắt đầu sự kiện.';
    } else if (end.getTime() - start.getTime() < 30 * 60 * 1000) {
      errs.time = 'Thời lượng sự kiện tối thiểu phải từ 30 phút trở lên.';
    }
  }
  return errs;
}

/**
 * Kiểm tra giờ yêu cầu có mặt (Arrival Time) trước khi bắt đầu ca
 */
export function validateShiftArrivalTime(arrivalStr, startStr) {
  if (!arrivalStr) return null;
  const aTime = new Date(arrivalStr);
  const sTime = new Date(startStr);
  if (isNaN(aTime.getTime()) || isNaN(sTime.getTime())) {
    return 'Giờ yêu cầu có mặt không hợp lệ.';
  }
  if (aTime > sTime) {
    return 'Giờ yêu cầu có mặt phải trước hoặc bằng giờ bắt đầu ca.';
  }
  const diffMinutes = (sTime.getTime() - aTime.getTime()) / (60 * 1000);
  if (diffMinutes > 120) {
    return 'Giờ yêu cầu có mặt không được sớm hơn giờ bắt đầu ca quá 120 phút (2 giờ).';
  }
  return null;
}

/**
 * Kiểm tra khung giờ ca làm việc (chặn quá khứ khi tạo mới & bắt buộc nằm trong sự kiện)
 */
export function validateShiftTimeRange(
  startStr,
  endStr,
  eventStartStr,
  eventEndStr,
  { allowPastStart = false } = {}
) {
  if (!startStr || !endStr) {
    return 'Vui lòng chọn thời gian bắt đầu và kết thúc ca làm việc.';
  }
  const sStart = new Date(startStr);
  const sEnd = new Date(endStr);
  if (isNaN(sStart.getTime()) || isNaN(sEnd.getTime())) {
    return 'Thời gian ca làm việc không hợp lệ.';
  }
  if (!allowPastStart && sStart.getTime() < Date.now() - 5 * 60 * 1000) {
    return 'Thời gian bắt đầu ca làm việc không được nằm trong quá khứ.';
  }
  if (sStart >= sEnd) {
    return 'Thời gian kết thúc ca làm việc phải sau thời gian bắt đầu ca.';
  }
  if (eventStartStr && eventEndStr) {
    const eStart = new Date(eventStartStr);
    const eEnd = new Date(eventEndStr);
    if (!isNaN(eStart.getTime()) && !isNaN(eEnd.getTime()) && (sStart < eStart || sEnd > eEnd)) {
      return 'Thời gian ca làm việc phải nằm trọn trong khung giờ diễn ra sự kiện.';
    }
  }
  return null;
}

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

  if (rawStatus === 'SUSPENDED') {
    return {
      code: 'SUSPENDED',
      label: 'Tạm đình chỉ ca',
      colorClass: 'yellow',
      bg: '#fffbeb',
      color: '#b45309',
      border: '#fde68a',
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

export const DEFAULT_UNDERSTAFFED_ALERT_HOURS = 4;
export const DEFAULT_EMERGENCY_RADIUS_KM = 25;

export function formatShiftStartTime(timeStr, fallback = '--:--') {
  if (!timeStr) return fallback;
  try {
    const d = new Date(timeStr);
    if (isNaN(d.getTime())) return fallback;
    return d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  } catch {
    return fallback;
  }
}

/**
 * Đánh giá thống nhất trạng thái thiếu nhân sự & quyền kích hoạt Emergency Dispatch của một ca làm việc.
 * Dùng chung quy tắc với Backend ShiftStaffingPolicy.
 */
export function getShiftStaffingSummary(shift = {}, event = {}, now = new Date(), alertHours = DEFAULT_UNDERSTAFFED_ALERT_HOURS) {
  const rawStatus = (shift?.status || '').toUpperCase();
  const evtStatus = (event?.status || shift?.eventStatus || 'ACTIVE').toUpperCase();
  const mode = (event?.mode || shift?.eventMode || 'MATCHING').toUpperCase();
  const isInviteMode = mode === 'INVITE' || mode === 'INVITE_ONLY';

  const start = shift?.startTime ? new Date(shift.startTime) : null;
  const end = shift?.endTime ? new Date(shift.endTime) : null;
  const startValid = Boolean(start && !isNaN(start.getTime()));
  const endValid = Boolean(end && !isNaN(end.getTime()));

  const isDraft = rawStatus === 'DRAFT' || evtStatus === 'DRAFT';
  const isCancelled = rawStatus === 'CANCELLED' || evtStatus === 'CANCELLED';
  const isSuspended = rawStatus === 'SUSPENDED' || evtStatus === 'SUSPENDED';
  const isCompleted =
    rawStatus === 'COMPLETED' ||
    evtStatus === 'COMPLETED' ||
    (endValid && now.getTime() > end.getTime());

  const isInProgress =
    !isDraft &&
    !isCancelled &&
    !isCompleted &&
    !isSuspended &&
    (rawStatus === 'IN_PROGRESS' ||
      rawStatus === 'ONGOING' ||
      (startValid && now.getTime() >= start.getTime()));

  const required = Math.max(1, Number(shift?.requiredWorkers ?? 1));
  const confirmed = Math.max(0, Number(shift?.confirmedWorkers ?? 0));
  const missing =
    shift?.missingWorkers !== undefined && shift?.missingWorkers !== null
      ? Math.max(0, Number(shift.missingWorkers))
      : Math.max(0, required - confirmed);

  const hoursWindow = Math.max(1, Number(alertHours || DEFAULT_UNDERSTAFFED_ALERT_HOURS));
  const withinAlertWindow =
    !isDraft &&
    !isCancelled &&
    !isCompleted &&
    !isSuspended &&
    startValid &&
    now.getTime() >= start.getTime() - hoursWindow * 3600 * 1000;

  const isEmergency = Boolean(shift?.emergency) && missing > 0;
  const nearStartUnderstaffed =
    missing > 0 &&
    !isDraft &&
    !isCancelled &&
    !isCompleted &&
    !isSuspended &&
    (isEmergency ||
      (typeof shift?.nearStartUnderstaffed === 'boolean' ? shift.nearStartUnderstaffed : withinAlertWindow));

  const canTriggerEmergency =
    !isInviteMode &&
    !isDraft &&
    !isCancelled &&
    !isCompleted &&
    !isSuspended &&
    !isInProgress &&
    missing > 0 &&
    !isEmergency &&
    (typeof shift?.canTriggerEmergency === 'boolean' ? shift.canTriggerEmergency : true);

  const isFullyStaffed = !isDraft && !isCancelled && !isCompleted && !isSuspended && missing === 0 && required > 0;

  const hasCandidates =
    shift?.pendingProposalCount !== undefined && shift?.pendingProposalCount !== null
      ? Number(shift.pendingProposalCount) > 0
      : true;

  const defaultRadiusKm = Number(shift?.defaultEmergencyRadiusKm) > 0
    ? Number(shift.defaultEmergencyRadiusKm)
    : DEFAULT_EMERGENCY_RADIUS_KM;

  const startTimeText = formatShiftStartTime(shift?.startTime);
  const summaryLine = `Ca ${shift?.jobType || 'Nhân sự'} bắt đầu lúc ${startTimeText} · Đã chốt ${confirmed}/${required} · Còn thiếu ${missing} người`;

  return {
    shift,
    required,
    confirmed,
    missing,
    isInviteMode,
    isDraft,
    isCancelled,
    isSuspended,
    isCompleted,
    isInProgress,
    isEmergency,
    isFullyStaffed,
    withinAlertWindow,
    nearStartUnderstaffed,
    canTriggerEmergency,
    emergencyBlockReason: shift?.emergencyBlockReason || null,
    hasCandidates,
    defaultRadiusKm,
    startTimeText,
    summaryLine,
  };
}

/**
 * Lọc danh sách các ca làm việc đang bị thiếu hụt nhân sự sát giờ (mặc định <= 4h trước startTime đến trước endTime)
 */
export function getShiftStaffingAlerts(shifts = [], event = {}, now = new Date(), alertHours = DEFAULT_UNDERSTAFFED_ALERT_HOURS) {
  return (shifts || [])
    .map((s) => getShiftStaffingSummary(s, event, now, alertHours))
    .filter((summary) => summary.nearStartUnderstaffed);
}

/**
 * Đếm số ca thiếu nhân sự sát giờ cho một sự kiện trên danh sách sự kiện
 */
export function getEventUnderstaffedSummary(evt = {}, now = new Date(), alertHours = DEFAULT_UNDERSTAFFED_ALERT_HOURS) {
  const alerts = getShiftStaffingAlerts(evt?.shifts || [], evt, now, alertHours);
  return {
    count: alerts.length,
    alerts,
  };
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
    const now = new Date();
    const draftStart = evt.startTime ? new Date(evt.startTime) : evt.eventDate ? new Date(evt.eventDate) : null;
    const draftEnd = evt.endTime ? new Date(evt.endTime) : null;

    if (draftEnd && !isNaN(draftEnd.getTime()) && now > draftEnd) {
      return {
        code: 'DRAFT_EXPIRED',
        label: 'Bản nháp (Đã qua giờ)',
        colorClass: 'red',
        subText: 'Đã qua thời gian tổ chức — hãy sửa lịch hoặc xóa',
        isExpiredDraft: true,
      };
    }

    if (draftStart && !isNaN(draftStart.getTime()) && now > draftStart) {
      return {
        code: 'DRAFT',
        label: 'Bản nháp (Qua giờ bắt đầu)',
        colorClass: 'yellow',
        subText: 'Nên cập nhật lại lịch trước khi đăng tuyển',
        isPastStartDraft: true,
      };
    }

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
    case 'DRAFT_EXPIRED':
      return 4; // Chưa bắt đầu / Bản nháp quá hạn
    case 'COMPLETED':
      return 5; // Đã kết thúc
    case 'CANCELLED':
      return 6; // Đã hủy
    default:
      return 4;
  }
}

export function filterAndSortEvents(events, searchQuery, statusFilter, sortBy) {
  const now = Date.now();
  const upcomingStart = (event) => {
    const start = new Date(event.startTime || event.eventDate || 0).getTime();
    return start >= now ? start : Infinity;
  };

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
        (statusFilter === 'DRAFT' && (dynStatus.code === 'DRAFT' || dynStatus.code === 'DRAFT_EXPIRED')) ||
        statusFilter === dynStatus.code ||
        evt.status === statusFilter;

      return matchSearch && matchStatus;
    })
    .sort((a, b) => {
      if (sortBy === 'UPCOMING') {
        const aStart = upcomingStart(a);
        const bStart = upcomingStart(b);
        if (aStart !== bStart) return aStart < bStart ? -1 : 1;
      }
      const priorityDiff = getEventStatusPriority(a) - getEventStatusPriority(b);
      if (priorityDiff !== 0) return priorityDiff;

      if (sortBy === 'NAME') return (a.name || '').localeCompare(b.name || '');
      if (sortBy === 'WORKERS') return (b.totalRequiredWorkers || 0) - (a.totalRequiredWorkers || 0);
      if (sortBy === 'OLDEST') return new Date(a.createdAt || a.eventDate || 0) - new Date(b.createdAt || b.eventDate || 0);
      return new Date(b.createdAt || b.eventDate || 0) - new Date(a.createdAt || a.eventDate || 0);
    });
}

export function resolveEventEstimatedCost(event) {
  const total = Number(event.totalEstimatedCost);
  if (event.totalEstimatedCost != null && Number.isFinite(total)) return total;

  return (event.shifts || []).reduce((sum, shift) => (
    shift.status === 'CANCELLED' ? sum : sum + (Number(shift.totalCost) || 0)
  ), 0);
}

export function calculateEventMetrics(events) {
  const nonCancelled = events.filter((e) => e.status !== 'CANCELLED');
  const activeEvents = events.filter((e) => e.status === 'ACTIVE' || e.status === 'APPROVED');

  const totalEvents = nonCancelled.length;
  const activeEventsCount = activeEvents.length;
  const totalRequiredWorkers = nonCancelled.reduce((sum, e) => sum + (Number(e.totalRequiredWorkers) || 0), 0);
  const totalConfirmedWorkers = nonCancelled.reduce((sum, e) => sum + (Number(e.totalConfirmedWorkers) || 0), 0);
  const totalEstimatedCost = nonCancelled.reduce((sum, e) => sum + resolveEventEstimatedCost(e), 0);
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

/**
 * Định dạng tiền VNĐ theo số nguyên tròn (không hiển thị phần thập phân lẻ).
 */
export function formatVndWhole(value, fallback = '—') {
  if (value === null || value === undefined || value === '') return fallback;
  const num = Number(value);
  if (!Number.isFinite(num)) return fallback;
  return `${Math.round(num).toLocaleString('vi-VN', { maximumFractionDigits: 0 })} đ`;
}

/**
 * Làm tròn số tiền VNĐ về số nguyên gần nhất.
 */
export function roundVndAmount(value) {
  const num = Number(value);
  return Number.isFinite(num) ? Math.round(num) : 0;
}

/**
 * Quy tắc chỉnh sửa ca theo trạng thái thời gian:
 * Khi ca đã vào giờ diễn ra (không phải DRAFT/CANCELLED), khóa chỉnh sửa thời gian và thù lao/phụ cấp,
 * vẫn cho phép điều chỉnh số lượng nhân sự cần tuyển (tối thiểu bằng số người đã xác nhận).
 */
export function getShiftEditRestrictions(shift = {}, event = null, now = new Date()) {
  const rawShiftStatus = String(shift?.status || '').toUpperCase();
  const rawEventStatus = String(event?.status || shift?.eventStatus || '').toUpperCase();
  const isDraft = rawShiftStatus === 'DRAFT' || rawEventStatus === 'DRAFT';
  const isCancelled = rawShiftStatus === 'CANCELLED' || rawEventStatus === 'CANCELLED';

  const start = shift?.startTime ? new Date(shift.startTime) : null;
  const end = shift?.endTime ? new Date(shift.endTime) : null;
  const startValid = Boolean(start && !isNaN(start.getTime()));
  const endValid = Boolean(end && !isNaN(end.getTime()));

  const isCompleted =
    rawShiftStatus === 'COMPLETED' ||
    rawEventStatus === 'COMPLETED' ||
    (endValid && now.getTime() > end.getTime());

  const hasStarted =
    !isDraft &&
    !isCancelled &&
    (rawShiftStatus === 'IN_PROGRESS' ||
      rawShiftStatus === 'ONGOING' ||
      isCompleted ||
      (startValid && now.getTime() >= start.getTime()));

  const confirmedWorkers = Math.max(0, Number(shift?.confirmedWorkers ?? 0));

  return {
    isDraft,
    isCancelled,
    isCompleted,
    hasStarted,
    lockTimeAndMoney: hasStarted,
    canEditWorkers: !isCancelled,
    minRequiredWorkers: hasStarted ? Math.max(1, confirmedWorkers) : 1,
    confirmedWorkers,
  };
}
