import { useState } from 'react';
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
  'Lễ tân / Check-in': { min: 45000, max: 60000, defaultWage: 50000, note: 'Giao tiếp tốt, ngoại hình sáng' },
  'Phục vụ bàn': { min: 35000, max: 50000, defaultWage: 40000, note: 'Nhanh nhẹn, cẩn thận' },
  'Pha chế (Bartender/Barista)': { min: 60000, max: 85000, defaultWage: 70000, note: 'Có tay nghề pha chế' },
  'Thu ngân': { min: 40000, max: 55000, defaultWage: 45000, note: 'Trung thực, tính toán chuẩn xác' },
  'Điều phối sự kiện': { min: 70000, max: 100000, defaultWage: 80000, note: 'Kinh nghiệm quản lý nhóm' },
  'Kỹ thuật âm thanh - ánh sáng': { min: 80000, max: 120000, defaultWage: 95000, note: 'Chuyên môn thiết bị sự kiện' },
  'Bảo vệ / An ninh': { min: 40000, max: 55000, defaultWage: 45000, note: 'Thể lực tốt, giữ trật tự' },
  'Hậu cần / Setup sự kiện': { min: 50000, max: 70000, defaultWage: 55000, note: 'Sức khỏe tốt, khuân vác, setup' },
  'PG / PB': { min: 70000, max: 110000, defaultWage: 85000, note: 'Ngoại hình, giới thiệu sản phẩm' },
};

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

  const [newShift, setNewShift] = useState({
    jobType: ROLES[0],
    description: '',
    startTime: '2026-11-20T08:00',
    endTime: '2026-11-20T12:00',
    requiredWorkers: 4,
    hourlyWage: 50000,
    allowance: 20000,
    minReliabilityScore: 60,
    maxDistanceKm: 15,
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
      return (
        hours *
          Number(newShift.requiredWorkers || 1) *
          Number(newShift.hourlyWage || 0) +
        Number(newShift.allowance || 0)
      );
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
    try {
      await eoService.addShiftToEvent(selectedEventId, {
        ...newShift,
        startTime: new Date(newShift.startTime).toISOString(),
        endTime: new Date(newShift.endTime).toISOString(),
      });
      setShowShiftModal(false);
      onReloadEvents?.();
      showToast('Thêm ca làm việc thành công! Bạn có thể kích hoạt Matching ngay.', 'success');
    } catch (err) {
      showToast('Không thể thêm ca làm việc: ' + (err.response?.data?.message || err.message), 'error');
    }
  };

  const handleUpdateShift = async (shiftId, shiftData) => {
    setSubmittingEditShift(true);
    try {
      await eoService.updateShift(shiftId, shiftData);
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

