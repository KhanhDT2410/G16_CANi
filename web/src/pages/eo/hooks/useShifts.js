import { useState, useEffect } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';

export const ROLES = [
  'Lễ tân / Check-in',
  'Phục vụ bàn',
  'Pha chế (Bartender/Barista)',
  'Thu ngân',
  'Điều phối sự kiện',
  'Kỹ thuật âm thanh - ánh sáng',
  'Bảo vệ / An ninh',
  'Hậu cần / Setup sự kiện',
  'PG / PB',
];

export const WAGE_RECOMMENDATIONS_MAP = {
  'Lễ tân / Check-in': { min: 45000, max: 60000, defaultWage: 50000, recommended: 50000, note: 'Giao tiếp tốt, ngoại hình sáng' },
  'Phục vụ bàn': { min: 35000, max: 50000, defaultWage: 40000, recommended: 40000, note: 'Nhanh nhẹn, cẩn thận' },
  'Pha chế (Bartender/Barista)': { min: 60000, max: 85000, defaultWage: 70000, recommended: 70000, note: 'Có tay nghề pha chế' },
  'Thu ngân': { min: 40000, max: 55000, defaultWage: 45000, recommended: 45000, note: 'Trung thực, tính toán chuẩn xác' },
  'Điều phối sự kiện': { min: 70000, max: 100000, defaultWage: 80000, recommended: 80000, note: 'Kinh nghiệm quản lý nhóm' },
  'Kỹ thuật âm thanh - ánh sáng': { min: 80000, max: 120000, defaultWage: 95000, recommended: 95000, note: 'Chuyên môn thiết bị sự kiện' },
  'Bảo vệ / An ninh': { min: 40000, max: 55000, defaultWage: 45000, recommended: 45000, note: 'Thể lực tốt, giữ trật tự' },
  'Hậu cần / Setup sự kiện': { min: 50000, max: 70000, defaultWage: 55000, recommended: 55000, note: 'Sức khỏe tốt, khuân vác, setup' },
  'PG / PB': { min: 70000, max: 110000, defaultWage: 85000, recommended: 85000, note: 'Ngoại hình, giới thiệu sản phẩm' },
};

/**
 * Chuẩn hóa danh sách checklist về dạng mảng object { id, title, isRequiredEvidence }
 */
export function normalizeChecklistItems(rawChecklist) {
  if (!rawChecklist) return [];
  let list = rawChecklist;
  if (typeof rawChecklist === 'string') {
    try {
      const parsed = JSON.parse(rawChecklist);
      list = Array.isArray(parsed) ? parsed : [];
    } catch {
      list = [];
    }
  }
  if (!Array.isArray(list)) return [];
  return list
    .map((item, idx) => {
      if (typeof item === 'object' && item !== null) {
        const title = String(item.title || item.name || item.text || '').trim();
        if (!title) return null;
        return {
          id: item.id || `chk_${idx + 1}`,
          title,
          isRequiredEvidence:
            item.isRequiredEvidence !== undefined
              ? Boolean(item.isRequiredEvidence)
              : item.requiresEvidence !== undefined
              ? Boolean(item.requiresEvidence)
              : false,
        };
      }
      const title = String(item).trim();
      if (!title) return null;
      return {
        id: `chk_${idx + 1}`,
        title,
        isRequiredEvidence: false,
      };
    })
    .filter(Boolean);
}

/**
 * Chuyển mảng checklist sang chuỗi JSON lưu xuống Backend
 */
export function serializeChecklistItems(items = []) {
  return JSON.stringify(
    normalizeChecklistItems(items).map((item, idx) => ({
      id: `chk_${idx + 1}`,
      title: item.title,
      isRequiredEvidence: Boolean(item.isRequiredEvidence),
    }))
  );
}

/**
 * Phân tích gợi ý mức lương & Đánh giá khả năng tìm nhân sự
 */
export function calculateShiftRiskAnalysis(shift = {}) {
  const role = shift.jobType || ROLES[0];
  const baseRec = WAGE_RECOMMENDATIONS_MAP[role] || {
    min: 40000,
    max: 65000,
    defaultWage: 50000,
    recommended: 50000,
    note: 'Mức thù lao tiêu chuẩn thị trường sự kiện',
  };
  const rec = {
    ...baseRec,
    recommended: baseRec.recommended || baseRec.defaultWage || 50000,
    defaultWage: baseRec.defaultWage || baseRec.recommended || 50000,
  };

  const wage = Math.max(0, Number(shift.hourlyWage || 0));
  const allowance = Math.max(0, Number(shift.allowance || 0));
  const workers = Math.max(1, Number(shift.requiredWorkers || 1));
  const radius = Math.max(1, Number(shift.maxDistanceKm || 15));
  const minScore = Number(shift.minReliabilityScore || 60);

  let riskScore = 20;
  const reasons = [];

  if (wage < rec.min * 0.8) {
    riskScore += 55;
    reasons.push(
      `Thù lao (${(wage || 0).toLocaleString()}đ/giờ) thấp hơn trên 20% so với mức sàn khuyến nghị (${((rec && rec.min) || 40000).toLocaleString()}đ/giờ) cho vị trí "${role}".`
    );
  } else if (wage < rec.min) {
    riskScore += 35;
    reasons.push(
      `Thù lao (${(wage || 0).toLocaleString()}đ/giờ) đang thấp hơn mức tối thiểu đề xuất (${((rec && rec.min) || 40000).toLocaleString()}đ/giờ) của vị trí "${role}".`
    );
  } else if (wage >= rec.max) {
    riskScore -= 10;
  }

  if (radius < 5) {
    riskScore += 25;
    reasons.push(`Bán kính tìm kiếm quá hẹp (${radius}km < 5km) làm giảm mạnh số lượng ứng viên quanh khu vực.`);
  } else if (radius < 8) {
    riskScore += 10;
    reasons.push(`Bán kính tìm kiếm dưới 8km (${radius}km) có thể hạn chế ứng viên ở các quận lân cận.`);
  }

  if (workers >= 15) {
    riskScore += 20;
    reasons.push(`Cần tuyển số lượng lớn (${workers} nhân sự) trong cùng một ca làm việc.`);
  } else if (workers >= 8) {
    riskScore += 10;
  }

  if (minScore > 85) {
    riskScore += 20;
    reasons.push(`Yêu cầu điểm uy tín tối thiểu quá cao (${minScore}/100) lọc bỏ nhiều ứng viên tiềm năng.`);
  }

  if (allowance >= 30000) {
    riskScore -= 10;
  }

  const clampedScore = Math.max(5, Math.min(95, riskScore));
  const level = clampedScore >= 55 ? 'HIGH' : clampedScore >= 35 ? 'MEDIUM' : 'LOW';
  const label =
    level === 'HIGH'
      ? 'Khó tìm nhân sự'
      : level === 'MEDIUM'
      ? 'Khá khó tìm nhân sự'
      : 'Dễ tìm nhân sự';

  return {
    recommendation: rec,
    riskScore: clampedScore,
    level,
    label,
    reasons,
  };
}

export function useShifts({ onReloadEvents }) {
  const [showShiftModal, setShowShiftModal] = useState(false);
  const [selectedEventId, setSelectedEventId] = useState(null);
  const [editingShift, setEditingShift] = useState(null);
  const [submittingEditShift, setSubmittingEditShift] = useState(false);
  const [expandedEventId, setExpandedEventId] = useState(null);

  const [selectedShiftForQr, setSelectedShiftForQr] = useState(null);
  const [qrModalData, setQrModalData] = useState(null);
  const [loadingQr, setLoadingQr] = useState(false);
  const [selectedShiftForAttendance, setSelectedShiftForAttendance] = useState(null);

  // Danh mục kỹ năng (taxonomy) để chọn requiredSkills cho ca — tải 1 lần khi hook mount
  const [skillTaxonomy, setSkillTaxonomy] = useState([]);
  useEffect(() => {
    let active = true;
    eoService.getSkillTaxonomy().then((list) => {
      if (active && Array.isArray(list) && list.length > 0) setSkillTaxonomy(list);
    });
    return () => {
      active = false;
    };
  }, []);

  const [newShift, setNewShift] = useState({
    jobType: ROLES[0],
    description: 'Hỗ trợ công việc theo phân công của quản lý sự kiện, thái độ niềm nở, chủ động.',
    startTime: '2026-11-20T08:00',
    endTime: '2026-11-20T12:00',
    arrivalTime: '',
    requiredWorkers: 4,
    hourlyWage: 50000,
    allowance: 20000,
    minReliabilityScore: 60,
    maxDistanceKm: 15,
    dressCode: '',
    requiredSkills: [],
    checklistItems: [
      { id: 'chk_1', title: 'Có mặt trước 15-30 phút nhận vị trí', isRequiredEvidence: false },
      { id: 'chk_2', title: 'Đồng phục chỉn chu theo hướng dẫn', isRequiredEvidence: true },
      { id: 'chk_3', title: 'Thực hiện điểm danh check-in bằng mã QR', isRequiredEvidence: false },
    ],
  });

  const getShiftDurationHours = () => {
    try {
      const start = new Date(newShift.startTime);
      const end = new Date(newShift.endTime);
      const diff = (end - start) / (1000 * 60 * 60);
      return diff > 0 ? parseFloat(diff.toFixed(1)) : 0;
    } catch {
      return 0;
    }
  };

  const calculateShiftTotal = () => {
    try {
      const hours = getShiftDurationHours() || 0.5;
      const safeWorkers = Math.max(1, Number(newShift.requiredWorkers || 1));
      const safeWage = Math.max(0, Number(newShift.hourlyWage || 0));
      const safeAllowance = Math.max(0, Number(newShift.allowance || 0));
      return hours * safeWorkers * safeWage + safeAllowance;
    } catch {
      return 0;
    }
  };

  const handleRoleChange = (role) => {
    const rec = WAGE_RECOMMENDATIONS_MAP[role];
    setNewShift((prev) => ({
      ...prev,
      jobType: role,
      hourlyWage: rec ? rec.defaultWage : prev.hourlyWage,
    }));
  };

  const handleAddShift = async (e) => {
    e.preventDefault();
    if (!selectedEventId) return;
    if (Number(newShift.allowance || 0) < 0) {
      showToast('Phụ cấp thêm không được là số âm!', 'error');
      return;
    }
    if (Number(newShift.hourlyWage || 0) < 10000) {
      showToast('Mức thù lao giờ không hợp lệ!', 'error');
      return;
    }
    if (Number(newShift.requiredWorkers || 0) < 1) {
      showToast('Số lượng nhân sự cần tuyển tối thiểu là 1 người!', 'error');
      return;
    }
    // arrivalTime (giờ mở điểm danh) phải nằm trong khung giờ ca nếu có điền
    if (newShift.arrivalTime) {
      const aTime = new Date(newShift.arrivalTime);
      const sTime = new Date(newShift.startTime);
      const eTime = new Date(newShift.endTime);
      if (isNaN(aTime.getTime()) || aTime < sTime || aTime > eTime) {
        showToast('Thời gian mở điểm danh phải nằm trong khoảng giờ của ca làm việc!', 'error');
        return;
      }
    }
    try {
      const checklistStr = Array.isArray(newShift.checklistItems)
        ? serializeChecklistItems(newShift.checklistItems)
        : (newShift.checklist || '[]');

      await eoService.addShiftToEvent(selectedEventId, {
        ...newShift,
        requiredWorkers: Math.max(1, Number(newShift.requiredWorkers || 1)),
        hourlyWage: Math.max(20000, Number(newShift.hourlyWage || 20000)),
        allowance: Math.max(0, Number(newShift.allowance || 0)),
        checklist: checklistStr,
        arrivalTime: newShift.arrivalTime ? new Date(newShift.arrivalTime).toISOString() : null,
        requiredSkills: Array.isArray(newShift.requiredSkills) ? newShift.requiredSkills : [],
        dressCode: newShift.dressCode || '',
        startTime: new Date(newShift.startTime).toISOString(),
        endTime: new Date(newShift.endTime).toISOString(),
      });
      setShowShiftModal(false);
      onReloadEvents?.();
      showToast('Thêm ca làm việc thành công!', 'success');
    } catch (err) {
      showToast('Không thể thêm ca làm việc: ' + (err.response?.data?.message || err.message), 'error');
    }
  };

  const handleUpdateShift = async (shiftId, shiftData) => {
    if (Number(shiftData.allowance || 0) < 0) {
      showToast('Phụ cấp thêm không được là số âm!', 'error');
      return;
    }
    setSubmittingEditShift(true);
    try {
      const checklistStr = Array.isArray(shiftData.checklistItems)
        ? serializeChecklistItems(shiftData.checklistItems)
        : shiftData.checklist;

      await eoService.updateShift(shiftId, {
        ...shiftData,
        requiredWorkers: Math.max(1, Number(shiftData.requiredWorkers || 1)),
        hourlyWage: Math.max(20000, Number(shiftData.hourlyWage || 20000)),
        allowance: Math.max(0, Number(shiftData.allowance || 0)),
        ...(checklistStr !== undefined ? { checklist: checklistStr } : {}),
      });
      showToast('Cập nhật ca làm việc thành công!', 'success');
      setEditingShift(null);
      onReloadEvents?.();
    } catch (err) {
      showToast('Lỗi cập nhật ca: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setSubmittingEditShift(false);
    }
  };

  const handleCancelShift = async (shift) => {
    if (!window.confirm(`Bạn có chắc chắn muốn HỦY ca "${shift.jobType}"?`)) {
      return;
    }
    try {
      await eoService.cancelShift(shift.shiftId, 'Ban tổ chức hủy ca');
      showToast('Đã hủy ca làm việc thành công!', 'success');
      onReloadEvents?.();
    } catch (err) {
      showToast('Lỗi hủy ca: ' + (err.response?.data?.message || err.message), 'error');
    }
  };

  const handleOpenShiftQr = async (shift, event) => {
    setSelectedShiftForQr(shift);
    setLoadingQr(true);
    try {
      const data = await eoService.getShiftQr(shift.shiftId);
      setQrModalData(data || {
        shiftId: shift.shiftId,
        eventName: event?.name || shift.eventName || 'Sự kiện',
        jobType: shift.jobType,
        venueAddress: shift.locationAddress || event?.venueAddress || '',
        checkinQrContent: `CANI_CHECKIN:${shift.shiftId}`,
        checkoutQrContent: `CANI_CHECKOUT:${shift.shiftId}`,
      });
    } catch {
      setQrModalData({
        shiftId: shift.shiftId,
        eventName: event?.name || shift.eventName || 'Sự kiện',
        jobType: shift.jobType,
        venueAddress: shift.locationAddress || event?.venueAddress || '',
        checkinQrContent: `CANI_CHECKIN:${shift.shiftId}`,
        checkoutQrContent: `CANI_CHECKOUT:${shift.shiftId}`,
      });
    } finally {
      setLoadingQr(false);
    }
  };

  const handleOpenAttendance = (shift, event) => {
    setSelectedShiftForAttendance({
      ...shift,
      eventName: event?.name || shift.eventName,
    });
  };

  return {
    showShiftModal,
    setShowShiftModal,
    selectedEventId,
    setSelectedEventId,
    newShift,
    setNewShift,
    editingShift,
    setEditingShift,
    submittingEditShift,
    expandedEventId,
    setExpandedEventId,
    selectedShiftForQr,
    setSelectedShiftForQr,
    qrModalData,
    setQrModalData,
    loadingQr,
    selectedShiftForAttendance,
    setSelectedShiftForAttendance,
    skillTaxonomy,
    getShiftDurationHours,
    calculateShiftTotal,
    handleRoleChange,
    handleAddShift,
    handleUpdateShift,
    handleCancelShift,
    handleOpenShiftQr,
    handleOpenAttendance,
  };
}
