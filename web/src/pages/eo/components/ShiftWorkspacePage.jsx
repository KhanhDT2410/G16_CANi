import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { eoService } from '../../../services/eoService';
import { blockListService } from '../../../services/blockListService';
import { showToast } from '../../../components/common/Toast';
import { getDynamicShiftStatus } from '../utils/eventHelpers';
import RateWorkerModal from './RateWorkerModal';
import { WorkerProfileModal } from './worker-profile';
import { useWorkerProfile } from '../hooks/useWorkerProfile';

/**
 * ShiftWorkspacePage
 * Trang quản lý độc lập & toàn diện cho MỘT Ca làm việc (Shift) cụ thể.
 * - Giải phóng 100% chiều rộng màn hình cho Quản lý nhân sự, Tiến độ Checklist chi tiết và Thanh toán.
 * - Mã QR Điểm danh (Check-in / Check-out) được tách thành nút/Modal chuyên dụng chiếu đầu & cuối ca.
 */
export default function ShiftWorkspacePage({
  shift,
  event,
  onSelectShift,
  onBackToEvent,
  onEditShift,
  onCancelShift,
  onOpenInvite,
  onReloadEvents,
  onOpenPaymentDeclaration,
  skillTaxonomy = [],
}) {
  const [attendanceList, setAttendanceList] = useState([]);
  const [loadingAttendance, setLoadingAttendance] = useState(false);
  const [verifyingId, setVerifyingId] = useState(null);
  const [selectedForReview, setSelectedForReview] = useState(null);
  const [submittingReview, setSubmittingReview] = useState(false);
  const [searchWorker, setSearchWorker] = useState('');
  const workerProfile = useWorkerProfile();

  // State cho Modal Mã QR Điểm Danh (chỉ bật đầu/cuối ca)
  const [showQrModal, setShowQrModal] = useState(false);
  const [qrMode, setQrMode] = useState('CHECKIN'); // 'CHECKIN' | 'CHECKOUT'
  const ROTATE_INTERVAL = 20;
  const [countdown, setCountdown] = useState(ROTATE_INTERVAL);
  const [refreshNonce, setRefreshNonce] = useState(Date.now());
  const [serverQrData, setServerQrData] = useState(null);

  // State xem ảnh (biên lai thanh toán hoặc ảnh check-out)
  const [previewImage, setPreviewImage] = useState(null);

  const loadAttendance = useCallback(async () => {
    if (!shift?.shiftId) return;
    setLoadingAttendance(true);
    try {
      const data = await eoService.getShiftAttendance(shift.shiftId);
      setAttendanceList(Array.isArray(data) ? data : []);
    } catch (e) {
      console.warn('Lỗi khi tải danh sách điểm danh ca:', e);
      setAttendanceList([]);
    } finally {
      setLoadingAttendance(false);
    }
  }, [shift?.shiftId]);

  useEffect(() => {
    if (shift?.shiftId) {
      loadAttendance();
    }
  }, [shift?.shiftId, loadAttendance]);

  // Lấy mã QR động khi mở Modal QR
  const fetchLiveQr = useCallback(async () => {
    if (!shift?.shiftId || !showQrModal) return;
    try {
      const data = await eoService.getShiftQr(shift.shiftId);
      if (data) setServerQrData(data);
    } catch (err) {
      console.warn('Không thể lấy mã QR từ server:', err);
    }
  }, [shift?.shiftId, showQrModal]);

  useEffect(() => {
    if (showQrModal && shift?.shiftId) {
      fetchLiveQr();
    }
  }, [showQrModal, shift?.shiftId, refreshNonce, fetchLiveQr]);

  useEffect(() => {
    if (!showQrModal || !shift?.shiftId) return;
    const timer = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          setRefreshNonce(Date.now());
          return ROTATE_INTERVAL;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [showQrModal, shift?.shiftId]);

  // Parse checklist chuẩn của ca
  const shiftChecklistTasks = useMemo(() => {
    if (!shift?.checklist) return [];
    try {
      const parsed = typeof shift.checklist === 'string' ? JSON.parse(shift.checklist) : shift.checklist;
      if (Array.isArray(parsed)) {
        return parsed.map((t, idx) =>
          typeof t === 'string'
            ? { id: `task_${idx}`, label: t }
            : { id: String(t.id ?? t.taskId ?? idx), label: String(t.title ?? t.label ?? t.name ?? `Nhiệm vụ ${idx + 1}`) }
        );
      }
    } catch {
      // ignore invalid json
    }
    return [];
  }, [shift?.checklist]);

  // Parse tiến độ checklist của từng worker
  const parseWorkerChecklistStatus = (rawProgress) => {
    if (!rawProgress) return {};
    try {
      const parsed = typeof rawProgress === 'string' ? JSON.parse(rawProgress) : rawProgress;
      if (Array.isArray(parsed)) {
        const map = {};
        parsed.forEach((item) => {
          if (typeof item === 'string') {
            map[item] = true;
          } else if (item && (item.id || item.taskId || item.title)) {
            const key = String(item.id ?? item.taskId ?? item.title);
            map[key] = Boolean(item.completed ?? item.done ?? item.checked ?? true);
          }
        });
        return map;
      }
      if (typeof parsed === 'object' && parsed !== null) {
        return parsed;
      }
    } catch {
      // ignore
    }
    return {};
  };

  // Map skillId -> tên kỹ năng
  const skillNames = useMemo(() => {
    if (!Array.isArray(shift?.requiredSkills) || shift.requiredSkills.length === 0) return [];
    const m = {};
    (Array.isArray(skillTaxonomy) ? skillTaxonomy : []).forEach((sk) => {
      if (sk?.skillId) m[sk.skillId] = sk.name;
    });
    return shift.requiredSkills.map((id) => m[id]).filter(Boolean);
  }, [shift?.requiredSkills, skillTaxonomy]);

  if (!shift || !event) return null;

  const dynStatus = getDynamicShiftStatus(shift, event.status);
  const shiftConfirmed = shift.confirmedWorkers || attendanceList.length || 0;
  const shiftRequired = Math.max(1, shift.requiredWorkers || 1);
  const fillRate = Math.round((shiftConfirmed / shiftRequired) * 100);
  const isShortage = dynStatus.isCritical || dynStatus.isEmergency || dynStatus.isUrgent || shiftConfirmed < shiftRequired;

  // Các chỉ số thống kê của riêng ca
  const checkedInCount = attendanceList.filter((a) => a.assignmentStatus === 'CHECKED_IN' || a.checkinTime != null).length;
  const completedCount = attendanceList.filter((a) => a.assignmentStatus === 'COMPLETED' || a.checkoutTime != null).length;
  const checklistDoneCount = attendanceList.filter((a) => Boolean(a.isChecklistCompleted)).length;
  const paidCount = attendanceList.filter((a) => a.paymentStatus === 'CONFIRMED_FULL').length;
  const pendingPayCount = attendanceList.filter((a) => a.paymentStatus === 'DECLARED_PENDING_WORKER').length;

  const allShifts = (event.shifts || []).filter((s) => s.status !== 'CANCELLED');

  const filteredWorkers = attendanceList.filter((w) => {
    if (!searchWorker.trim()) return true;
    const q = searchWorker.toLowerCase();
    return (
      (w.workerName || '').toLowerCase().includes(q) ||
      (w.workerPhone || '').toLowerCase().includes(q)
    );
  });

  const formatTime = (timeStr, fallback = '--:--') => {
    if (!timeStr) return fallback;
    try {
      return new Date(timeStr).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
    } catch {
      return fallback;
    }
  };

  const handleToggleEmergency = async (enable) => {
    const confirmMsg = enable
      ? `BẬT điều phối khẩn cấp (Emergency Dispatch) cho ca "${shift.jobType}"?\n\n- Phát thông báo đẩy khẩn cấp đến nhân sự xung quanh (bán kính 15km).\n- Mở khóa cho phép nhân sự nhận ca trực tiếp trên Radar.`
      : `TẮT chế độ điều phối khẩn cấp cho ca "${shift.jobType}"?`;
    if (!window.confirm(confirmMsg)) return;
    try {
      if (enable) {
        await eoService.enableEmergencyDispatch(shift.shiftId, 15);
        showToast(`Đã bật chế độ khẩn cấp cho ca "${shift.jobType}"!`, 'success');
      } else {
        await eoService.disableEmergencyDispatch(shift.shiftId);
        showToast(`Đã tắt chế độ khẩn cấp cho ca "${shift.jobType}".`, 'info');
      }
      onReloadEvents?.();
    } catch (err) {
      showToast(err.response?.data?.message || err.message || 'Lỗi thao tác điều phối khẩn cấp', 'error');
    }
  };

  const handleVerify = async (assignmentId) => {
    setVerifyingId(assignmentId);
    try {
      await eoService.verifyAttendance(assignmentId);
      showToast('Đã xác nhận hoàn thành ca và duyệt công cho nhân sự!', 'success');
      await Promise.allSettled([loadAttendance(), onReloadEvents ? Promise.resolve(onReloadEvents()) : Promise.resolve()]);
    } catch (e) {
      showToast('Không thể duyệt công: ' + (e.response?.data?.message || e.message), 'error');
    } finally {
      setVerifyingId(null);
    }
  };

  const handleReportIncomplete = async (item) => {
    const reason = window.prompt(
      `Báo cáo nhân sự "${item.workerName}" chưa hoàn thành công việc / vi phạm ca?\n\nNhập lý do cụ thể (tối thiểu 5 ký tự) để mở hồ sơ tranh chấp:`,
      'Chưa hoàn thành đủ các đầu việc trong checklist ca'
    );
    if (!reason || reason.trim().length < 5) {
      if (reason !== null) showToast('Vui lòng nhập lý do rõ ràng ít nhất 5 ký tự.', 'warning');
      return;
    }
    try {
      await eoService.reportIncompleteWork(item.assignmentId, {
        title: `Báo cáo chưa hoàn thành công việc - ${shift.jobType}`,
        description: reason.trim(),
      });
      showToast(`Đã gửi báo cáo chưa hoàn thành đối với "${item.workerName}".`, 'success');
      loadAttendance();
    } catch (err) {
      showToast(err.response?.data?.message || err.message || 'Lỗi gửi báo cáo', 'error');
    }
  };

  const handleBlockWorker = async (worker) => {
    if (!worker?.userId) return;
    const reason = window.prompt(`Chặn nhân sự "${worker.workerName}" khỏi các sự kiện tương lai? Nhập lý do:`, 'Không tuân thủ quy định ca làm');
    if (reason === null) return;
    try {
      await blockListService.blockUser(worker.userId, reason);
      showToast(`Đã chặn "${worker.workerName}" thành công.`, 'success');
      loadAttendance();
    } catch (err) {
      showToast(err.response?.data?.message || err.message || 'Lỗi khi chặn nhân sự', 'error');
    }
  };

  const handleSubmitReview = async (reviewData) => {
    setSubmittingReview(true);
    try {
      await eoService.rateWorker(reviewData.assignmentId, reviewData);
      showToast('Đã gửi đánh giá nhân sự thành công!', 'success');
      setSelectedForReview(null);
      loadAttendance();
    } catch (e) {
      showToast('Không thể gửi đánh giá: ' + (e.response?.data?.message || e.message), 'error');
    } finally {
      setSubmittingReview(false);
    }
  };

  // QR variables
  const isCheckin = qrMode === 'CHECKIN';
  const qrAccent = isCheckin ? '#2563eb' : '#0d9488';
  const epochSeconds = Math.floor(refreshNonce / 1000);
  const fallbackPayload = isCheckin
    ? `CANI_CHECKIN:${shift.shiftId}:${epochSeconds}:${refreshNonce.toString(36)}`
    : `CANI_CHECKOUT:${shift.shiftId}:${epochSeconds}:${refreshNonce.toString(36)}`;
  const currentPayload = isCheckin
    ? (serverQrData?.checkinQrContent || fallbackPayload)
    : (serverQrData?.checkoutQrContent || fallbackPayload);

  return (
    <div className="shift-workspace-container animate-fade-in-up" style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
      
      {/* 1. THANH ĐIỀU HƯỚNG BREADCRUMB & CHUYỂN CA NHANH */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '0.75rem',
          background: '#ffffff',
          padding: '0.85rem 1.25rem',
          borderRadius: '14px',
          border: '1px solid #e2e8f0',
          boxShadow: '0 1px 3px rgba(15, 23, 42, 0.04)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <button
            type="button"
            onClick={onBackToEvent}
            style={{
              background: '#f1f5f9',
              color: '#0f172a',
              border: '1px solid #cbd5e1',
              borderRadius: '8px',
              padding: '0.45rem 0.9rem',
              fontSize: '0.85rem',
              fontWeight: 700,
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '5px',
            }}
          >
            ← Quay lại sự kiện "{event.name}"
          </button>

          {allShifts.length > 1 && onSelectShift && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem' }}>
              <span style={{ fontSize: '0.8rem', color: '#64748b', fontWeight: 600 }}>Chuyển ca:</span>
              <select
                value={shift.shiftId}
                onChange={(e) => {
                  const found = allShifts.find((s) => s.shiftId === e.target.value);
                  if (found) onSelectShift(found);
                }}
                style={{
                  padding: '0.4rem 0.75rem',
                  borderRadius: '8px',
                  border: '1px solid #cbd5e1',
                  fontSize: '0.85rem',
                  fontWeight: 700,
                  color: '#0f172a',
                  background: '#f8fafc',
                  cursor: 'pointer',
                }}
              >
                {allShifts.map((s) => (
                  <option key={s.shiftId} value={s.shiftId}>
                    {s.jobType} ({formatTime(s.startTime)} - {formatTime(s.endTime)})
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
          <button
            type="button"
            onClick={() => {
              setQrMode('CHECKIN');
              setShowQrModal(true);
            }}
            style={{
              background: 'linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%)',
              color: '#ffffff',
              border: 'none',
              borderRadius: '8px',
              padding: '0.5rem 1rem',
              fontSize: '0.84rem',
              fontWeight: 800,
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              boxShadow: '0 2px 8px rgba(37, 99, 235, 0.25)',
            }}
            title="Mở mã QR to để nhân sự quét vào ca (đầu ca) hoặc tan ca (cuối ca)"
          >
            📱 Chiếu Mã QR Điểm Danh
          </button>

          {shift.status !== 'CANCELLED' && shift.status !== 'COMPLETED' && (
            <button
              type="button"
              onClick={() => handleToggleEmergency(!shift.emergency)}
              style={{
                background: shift.emergency ? '#fef2f2' : 'linear-gradient(135deg, #e11d48 0%, #be123c 100%)',
                color: shift.emergency ? '#b91c1c' : '#ffffff',
                border: shift.emergency ? '1.5px solid #f87171' : 'none',
                borderRadius: '8px',
                padding: '0.5rem 0.95rem',
                fontSize: '0.84rem',
                fontWeight: 800,
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '5px',
              }}
            >
              {shift.emergency ? '🛑 Tắt Khẩn Cấp' : '🚨 Bật Khẩn Cấp'}
            </button>
          )}

          {onOpenInvite && (
            <button
              type="button"
              onClick={() => onOpenInvite(shift, event)}
              style={{
                background: '#f5f3ff',
                color: '#6d28d9',
                border: '1px solid #ddd6fe',
                borderRadius: '8px',
                padding: '0.5rem 0.85rem',
                fontSize: '0.82rem',
                fontWeight: 700,
                cursor: 'pointer',
              }}
            >
              🔗 Mã Mời
            </button>
          )}

          {onEditShift && (
            <button
              type="button"
              onClick={() => onEditShift(shift)}
              style={{
                background: '#ffffff',
                color: '#334155',
                border: '1px solid #cbd5e1',
                borderRadius: '8px',
                padding: '0.5rem 0.85rem',
                fontSize: '0.82rem',
                fontWeight: 700,
                cursor: 'pointer',
              }}
            >
              ✏️ Sửa Ca
            </button>
          )}
        </div>
      </div>

      {/* 2. HEADER THÔNG TIN CA & TRẠNG THÁI THỜI GIAN THỰC */}
      <div
        className="eo-widget-card"
        style={{
          padding: '1.35rem 1.5rem',
          borderLeft: `5px solid ${dynStatus.color}`,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          flexWrap: 'wrap',
          gap: '1rem',
        }}
      >
        <div style={{ flex: 1, minWidth: '280px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap', marginBottom: '0.5rem' }}>
            <h2 style={{ margin: 0, fontSize: '1.35rem', fontWeight: 800, color: '#0f172a' }}>
              Ca: {shift.jobType}
            </h2>
            <span
              style={{
                backgroundColor: dynStatus.bg,
                color: dynStatus.color,
                border: `1px solid ${dynStatus.border}`,
                padding: '0.25rem 0.75rem',
                borderRadius: '999px',
                fontSize: '0.78rem',
                fontWeight: 800,
              }}
            >
              ● {dynStatus.label}
            </span>
          </div>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1.25rem', fontSize: '0.85rem', color: '#475569', marginBottom: '0.5rem' }}>
            <span>
              🕒 Khung giờ: <strong>{formatTime(shift.startTime)} - {formatTime(shift.endTime)}</strong>
            </span>
            <span>
              💰 Thù lao: <strong style={{ color: '#059669' }}>{new Intl.NumberFormat('vi-VN').format(shift.hourlyWage || 50000)} đ/giờ</strong>
              {shift.allowance > 0 && ` (+${new Intl.NumberFormat('vi-VN').format(shift.allowance)} đ phụ cấp)`}
            </span>
            {shift.dressCode && (
              <span>👔 Đồng phục: <strong>{shift.dressCode}</strong></span>
            )}
          </div>

          {skillNames.length > 0 && (
            <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap', marginTop: '0.5rem' }}>
              <span style={{ fontSize: '0.78rem', color: '#64748b', fontWeight: 600 }}>Kỹ năng yêu cầu:</span>
              {skillNames.map((name, i) => (
                <span
                  key={i}
                  style={{
                    background: '#eff6ff',
                    color: '#1d4ed8',
                    border: '1px solid #bfdbfe',
                    borderRadius: '6px',
                    padding: '1px 8px',
                    fontSize: '0.75rem',
                    fontWeight: 600,
                  }}
                >
                  {name}
                </span>
              ))}
            </div>
          )}
        </div>

        <button
          type="button"
          onClick={() => {
            loadAttendance();
            onReloadEvents?.();
          }}
          style={{
            background: '#f8fafc',
            border: '1px solid #cbd5e1',
            borderRadius: '8px',
            padding: '0.45rem 0.85rem',
            fontSize: '0.8rem',
            fontWeight: 700,
            color: '#334155',
            cursor: 'pointer',
          }}
        >
          🔄 Cập nhật số liệu
        </button>
      </div>

      {/* 3. CÁC THẺ CHỈ SỐ TIẾN ĐỘ CỦA RIÊNG CA NÀY */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: '1rem' }}>
        <div
          className="eo-widget-card"
          style={{
            padding: '1rem 1.25rem',
            backgroundColor: isShortage ? '#fff1f2' : '#f0fdf4',
            borderColor: isShortage ? '#fecdd3' : '#bbf7d0',
          }}
        >
          <div style={{ fontSize: '0.75rem', fontWeight: 700, color: isShortage ? '#be123c' : '#15803d', textTransform: 'uppercase' }}>
            {isShortage ? '⚠️ Tuyển Dụng (Thiếu người)' : '✅ Tuyển Dụng Ca'}
          </div>
          <div style={{ fontSize: '1.5rem', fontWeight: 800, color: isShortage ? '#e11d48' : '#166534', margin: '0.25rem 0' }}>
            {shiftConfirmed} / {shiftRequired} <span style={{ fontSize: '0.9rem', fontWeight: 600 }}>người ({fillRate}%)</span>
          </div>
          <div style={{ fontSize: '0.75rem', color: isShortage ? '#9f1239' : '#15803d' }}>
            {shiftConfirmed < shiftRequired ? `Còn thiếu ${shiftRequired - shiftConfirmed} vị trí` : 'Đã chốt đủ nhân sự'}
          </div>
        </div>

        <div className="eo-widget-card" style={{ padding: '1rem 1.25rem' }}>
          <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>
            Điểm Danh Thực Tế
          </div>
          <div style={{ fontSize: '1.5rem', fontWeight: 800, color: '#0f172a', margin: '0.25rem 0' }}>
            {checkedInCount} <span style={{ fontSize: '0.9rem', color: '#64748b', fontWeight: 600 }}>vào ca</span>
          </div>
          <div style={{ fontSize: '0.75rem', color: '#0ea5e9', fontWeight: 600 }}>
            {completedCount} nhân sự đã kết ca
          </div>
        </div>

        <div className="eo-widget-card" style={{ padding: '1rem 1.25rem' }}>
          <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>
            Tiến Độ Checklist ({shiftChecklistTasks.length} việc)
          </div>
          <div style={{ fontSize: '1.5rem', fontWeight: 800, color: '#4f46e5', margin: '0.25rem 0' }}>
            {checklistDoneCount} / {attendanceList.length}
          </div>
          <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
            Nhân sự hoàn thành 100% đầu việc
          </div>
        </div>

        <div className="eo-widget-card" style={{ padding: '1rem 1.25rem' }}>
          <div style={{ fontSize: '0.75rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase' }}>
            Quyết Toán Thù Lao
          </div>
          <div style={{ fontSize: '1.5rem', fontWeight: 800, color: '#059669', margin: '0.25rem 0' }}>
            {paidCount} <span style={{ fontSize: '0.9rem', color: '#64748b', fontWeight: 600 }}>đã trả đủ</span>
          </div>
          <div style={{ fontSize: '0.75rem', color: pendingPayCount > 0 ? '#d97706' : '#64748b', fontWeight: 600 }}>
            {pendingPayCount} chờ Worker xác nhận
          </div>
        </div>
      </div>

      {/* 4. BẢNG QUẢN LÝ NHÂN SỰ & TIẾN ĐỘ CHI TIẾT TRONG CA (100% FULL WIDTH) */}
      <div className="eo-widget-card" style={{ padding: '1.35rem 1.5rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.75rem' }}>
          <div>
            <h3 style={{ margin: 0, fontSize: '1.05rem', fontWeight: 800, color: '#0f172a' }}>
              Nhân Sự & Tiến Độ Công Việc Trong Ca ({attendanceList.length})
            </h3>
            <p style={{ margin: '0.2rem 0 0', fontSize: '0.8rem', color: '#64748b' }}>
              Theo dõi thời gian vào/ra, tiến độ từng đầu việc trong checklist và thanh toán VietQR của riêng ca này
            </p>
          </div>

          <input
            type="text"
            value={searchWorker}
            onChange={(e) => setSearchWorker(e.target.value)}
            placeholder="Tìm theo tên hoặc SĐT nhân sự..."
            style={{
              padding: '0.45rem 0.85rem',
              borderRadius: '8px',
              border: '1px solid #cbd5e1',
              fontSize: '0.85rem',
              minWidth: '240px',
            }}
          />
        </div>

        {loadingAttendance ? (
          <div style={{ padding: '3rem 1rem', textAlign: 'center', color: '#64748b', background: '#f8fafc', borderRadius: '10px' }}>
            Đang tải dữ liệu nhân sự và tiến độ ca làm việc...
          </div>
        ) : filteredWorkers.length === 0 ? (
          <div style={{ padding: '3rem 1.5rem', textAlign: 'center', background: '#f8fafc', borderRadius: '12px', border: '1px dashed #cbd5e1' }}>
            <h4 style={{ margin: '0 0 0.5rem', color: '#334155', fontSize: '1rem' }}>
              Chưa có nhân sự nào trong ca "{shift.jobType}"
            </h4>
            <p style={{ margin: '0 0 1.25rem', fontSize: '0.85rem', color: '#64748b' }}>
              Bạn có thể bật Điều phối khẩn cấp (Emergency Dispatch) hoặc gửi Mã Mời trực tiếp để nhanh chóng lấp đầy ca!
            </p>
            <div style={{ display: 'flex', justifyContent: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
              {!shift.emergency && shift.status !== 'COMPLETED' && shift.status !== 'CANCELLED' && (
                <button
                  type="button"
                  onClick={() => handleToggleEmergency(true)}
                  style={{
                    background: '#e11d48',
                    color: '#ffffff',
                    border: 'none',
                    borderRadius: '8px',
                    padding: '0.55rem 1.15rem',
                    fontSize: '0.85rem',
                    fontWeight: 800,
                    cursor: 'pointer',
                  }}
                >
                  🚨 Bật Điều Phối Khẩn Cấp Ngay
                </button>
              )}
              {onOpenInvite && (
                <button
                  type="button"
                  onClick={() => onOpenInvite(shift, event)}
                  style={{
                    background: '#7c3aed',
                    color: '#ffffff',
                    border: 'none',
                    borderRadius: '8px',
                    padding: '0.55rem 1.15rem',
                    fontSize: '0.85rem',
                    fontWeight: 700,
                    cursor: 'pointer',
                  }}
                >
                  🔗 Lấy Link / Mã Mời
                </button>
              )}
            </div>
          </div>
        ) : (
          <div style={{ overflowX: 'auto', border: '1px solid #e2e8f0', borderRadius: '10px' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem', minWidth: '880px' }}>
              <thead>
                <tr style={{ background: '#f8fafc', borderBottom: '1px solid #e2e8f0', textAlign: 'left', color: '#475569' }}>
                  <th style={{ padding: '12px 14px' }}>Nhân Sự & Uy Tín</th>
                  <th style={{ padding: '12px 14px' }}>Trạng Thái Điểm Danh</th>
                  <th style={{ padding: '12px 14px' }}>Thời Gian Vào / Ra</th>
                  <th style={{ padding: '12px 14px', minWidth: '220px' }}>Tiến Độ Checklist Công Việc</th>
                  <th style={{ padding: '12px 14px' }}>Thanh Toán & Bằng Chứng</th>
                  <th style={{ padding: '12px 14px', textAlign: 'right' }}>Thao Tác</th>
                </tr>
              </thead>
              <tbody>
                {filteredWorkers.map((item) => {
                  const isCheckedIn = item.assignmentStatus === 'CHECKED_IN';
                  const isCompleted = item.assignmentStatus === 'COMPLETED' || item.checkoutTime != null;
                  const workerProgressMap = parseWorkerChecklistStatus(item.checklistRawProgress);

                  return (
                    <tr key={item.assignmentId} style={{ borderBottom: '1px solid #f1f5f9', verticalAlign: 'top' }}>
                      {/* 1. Nhân sự */}
                      <td style={{ padding: '14px' }}>
                        <button
                          type="button"
                          onClick={() => item.workerId && workerProfile.openProfile(item.workerId)}
                          style={{
                            background: 'none',
                            border: 'none',
                            padding: 0,
                            margin: 0,
                            fontWeight: 800,
                            color: '#1d4ed8',
                            cursor: 'pointer',
                            textAlign: 'left',
                            fontSize: '0.9rem',
                          }}
                        >
                          {item.workerName}
                        </button>
                        <div style={{ fontSize: '0.78rem', color: '#64748b', marginTop: 2 }}>{item.workerPhone}</div>
                        <div style={{ fontSize: '0.75rem', color: '#2563eb', marginTop: 4, fontWeight: 600 }}>
                          ⭐ {item.workerRatingAvg || 5.0} • Uy tín: {item.workerReliabilityScore || 100} RS
                        </div>
                      </td>

                      {/* 2. Trạng thái */}
                      <td style={{ padding: '14px' }}>
                        {isCheckedIn && (
                          <span style={{ background: '#dcfce7', color: '#15803d', padding: '4px 10px', borderRadius: 12, fontWeight: 700, fontSize: '0.75rem', display: 'inline-block' }}>
                            Đã Check-in {item.isOnTime ? '(Đúng giờ)' : '(Trễ)'}
                          </span>
                        )}
                        {isCompleted && (
                          <span style={{ background: '#e0e7ff', color: '#3730a3', padding: '4px 10px', borderRadius: 12, fontWeight: 700, fontSize: '0.75rem', display: 'inline-block' }}>
                            Đã Hoàn Thành
                          </span>
                        )}
                        {!isCheckedIn && !isCompleted && (
                          <span style={{ background: '#fef3c7', color: '#b45309', padding: '4px 10px', borderRadius: 12, fontWeight: 700, fontSize: '0.75rem', display: 'inline-block' }}>
                            Chờ Đến Ca
                          </span>
                        )}
                      </td>

                      {/* 3. Thời gian */}
                      <td style={{ padding: '14px', color: '#334155', fontSize: '0.8rem' }}>
                        <div>Vào: <strong>{item.checkinTime ? formatTime(item.checkinTime) : '—'}</strong></div>
                        <div style={{ marginTop: 2 }}>Ra: <strong>{item.checkoutTime ? formatTime(item.checkoutTime) : '—'}</strong></div>
                        {item.hoursWorked != null && (
                          <div style={{ color: '#0d9488', fontWeight: 700, marginTop: 3 }}>
                            {item.hoursWorked} giờ làm
                          </div>
                        )}
                      </td>

                      {/* 4. Tiến độ Checklist chi tiết */}
                      <td style={{ padding: '14px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                          <span
                            style={{
                              background: item.isChecklistCompleted ? '#dcfce7' : '#f1f5f9',
                              color: item.isChecklistCompleted ? '#15803d' : '#334155',
                              padding: '2px 8px',
                              borderRadius: 6,
                              fontWeight: 700,
                              fontSize: '0.75rem',
                            }}
                          >
                            {item.isChecklistCompleted ? '✅ Xong 100%' : (item.checklistProgress || 'Chưa cập nhật')}
                          </span>
                        </div>
                        {shiftChecklistTasks.length > 0 && (
                          <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                            {shiftChecklistTasks.map((t) => {
                              const isTaskDone = Boolean(workerProgressMap[t.id] || workerProgressMap[t.label] || item.isChecklistCompleted);
                              return (
                                <div key={t.id} style={{ fontSize: '0.74rem', color: isTaskDone ? '#047857' : '#64748b', display: 'flex', alignItems: 'center', gap: 4 }}>
                                  <span>{isTaskDone ? '☑' : '☐'}</span>
                                  <span style={{ textDecoration: isTaskDone ? 'line-through' : 'none' }}>{t.label}</span>
                                </div>
                              );
                            })}
                          </div>
                        )}
                      </td>

                      {/* 5. Thanh toán & QR Worker */}
                      <td style={{ padding: '14px' }}>
                        {item.paymentStatus === 'CONFIRMED_FULL' && (
                          <span style={{ background: '#dcfce7', color: '#15803d', padding: '4px 10px', borderRadius: 8, fontWeight: 700, fontSize: '0.75rem', display: 'inline-block' }}>
                            ✅ Đã quyết toán
                          </span>
                        )}
                        {item.paymentStatus === 'DECLARED_PENDING_WORKER' && (
                          <div style={{ display: 'flex', flexDirection: 'column', gap: 4, alignItems: 'flex-start' }}>
                            <span style={{ background: '#fef3c7', color: '#b45309', padding: '3px 8px', borderRadius: 6, fontWeight: 700, fontSize: '0.74rem' }}>
                              ⏳ Chờ Worker xác nhận
                            </span>
                            {item.paymentProofUrl && (
                              <button
                                type="button"
                                onClick={() => setPreviewImage({ url: item.paymentProofUrl, title: `Biên lai trả lương cho ${item.workerName}` })}
                                style={{ background: 'none', border: 'none', padding: 0, color: '#2563eb', fontSize: '0.74rem', fontWeight: 700, cursor: 'pointer', textDecoration: 'underline' }}
                              >
                                📄 Xem biên lai đã chuyển
                              </button>
                            )}
                          </div>
                        )}
                        {item.paymentStatus === 'DISPUTED' && (
                          <span style={{ background: '#fee2e2', color: '#b91c1c', padding: '4px 10px', borderRadius: 8, fontWeight: 700, fontSize: '0.75rem', display: 'inline-block' }}>
                            🚨 Đang tranh chấp
                          </span>
                        )}
                        {(!item.paymentStatus || item.paymentStatus === 'NOT_DECLARED') && (
                          item.isChecklistCompleted ? (
                            <button
                              type="button"
                              onClick={() => onOpenPaymentDeclaration?.(item)}
                              style={{
                                background: 'linear-gradient(135deg, #059669 0%, #10b981 100%)',
                                color: '#ffffff',
                                border: 'none',
                                borderRadius: 8,
                                padding: '6px 12px',
                                fontSize: '0.78rem',
                                fontWeight: 800,
                                cursor: 'pointer',
                                boxShadow: '0 2px 6px rgba(16, 185, 129, 0.25)',
                              }}
                            >
                              💳 Trả lương & QR
                            </button>
                          ) : (
                            <span
                              title="Chỉ mở khai báo trả lương sau khi nhân sự hoàn thành 100% checklist công việc"
                              style={{
                                background: '#f8fafc',
                                color: '#64748b',
                                border: '1px solid #cbd5e1',
                                borderRadius: 6,
                                padding: '4px 8px',
                                fontSize: '0.73rem',
                                fontWeight: 600,
                                display: 'inline-block',
                              }}
                            >
                              🔒 Chờ xong checklist
                            </span>
                          )
                        )}
                      </td>

                      {/* 6. Thao tác */}
                      <td style={{ padding: '14px', textAlign: 'right' }}>
                        <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                          {item.proofImageUrl && (
                            <button
                              type="button"
                              onClick={() => setPreviewImage({ url: item.proofImageUrl, title: `Ảnh minh chứng công việc - ${item.workerName}` })}
                              style={{ background: '#f0f9ff', color: '#0369a1', border: '1px solid #bae6fd', borderRadius: 6, padding: '4px 8px', fontSize: '0.75rem', fontWeight: 600, cursor: 'pointer' }}
                            >
                              📷 Ảnh ca
                            </button>
                          )}

                          {!item.eoConfirmed && (isCheckedIn || isCompleted) && (
                            <button
                              type="button"
                              disabled={verifyingId === item.assignmentId}
                              onClick={() => handleVerify(item.assignmentId)}
                              style={{
                                background: verifyingId === item.assignmentId ? '#94a3b8' : '#2563eb',
                                color: '#ffffff',
                                border: 'none',
                                borderRadius: 6,
                                padding: '5px 10px',
                                fontSize: '0.76rem',
                                fontWeight: 700,
                                cursor: 'pointer',
                              }}
                            >
                              {verifyingId === item.assignmentId ? 'Đang duyệt...' : '✓ Duyệt công'}
                            </button>
                          )}

                          {item.eoConfirmed && !item.hasReviewedWorker && (
                            <button
                              type="button"
                              onClick={() => setSelectedForReview(item)}
                              style={{ background: '#fffbeb', color: '#b45309', border: '1px solid #fcd34d', borderRadius: 6, padding: '4px 9px', fontSize: '0.76rem', fontWeight: 700, cursor: 'pointer' }}
                            >
                              ⭐ Đánh giá
                            </button>
                          )}

                          {item.eoConfirmed && item.hasReviewedWorker && (
                            <span style={{ color: '#059669', fontSize: '0.75rem', fontWeight: 700, padding: '4px' }}>
                              ✓ Đã duyệt & Đánh giá
                            </span>
                          )}

                          <button
                            type="button"
                            onClick={() => {
                              const shiftCode = 'SH-' + (shift.shiftId ? String(shift.shiftId).replace(/-/g, '').substring(0, 6).toUpperCase() : '0000');
                              window.dispatchEvent(
                                new CustomEvent('cani_open_chat_shift', {
                                  detail: { shiftId: shift.shiftId, shiftCode, shiftName: `${event.name} - ${shift.jobType}` },
                                })
                              );
                            }}
                            style={{ background: '#f0fdf4', color: '#15803d', border: '1px solid #bbf7d0', borderRadius: 6, padding: '4px 8px', fontSize: '0.75rem', fontWeight: 600, cursor: 'pointer' }}
                          >
                            💬 Chat
                          </button>

                          {!item.eoConfirmed && (isCheckedIn || isCompleted) && (
                            <button
                              type="button"
                              onClick={() => handleReportIncomplete(item)}
                              style={{ background: '#fff7ed', color: '#c2410c', border: '1px solid #fed7aa', borderRadius: 6, padding: '4px 8px', fontSize: '0.75rem', fontWeight: 600, cursor: 'pointer' }}
                            >
                              ⚠️ Báo lỗi
                            </button>
                          )}

                          <button
                            type="button"
                            onClick={() => handleBlockWorker(item)}
                            style={{ background: '#fef2f2', color: '#dc2626', border: '1px solid #fecaca', borderRadius: 6, padding: '4px 8px', fontSize: '0.75rem', fontWeight: 600, cursor: 'pointer' }}
                          >
                            🚫 Chặn
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* MODAL MÃ QR ĐIỂM DANH CHUYÊN DỤNG (CHỈ BẬT ĐẦU/CUỐI CA) */}
      {showQrModal && (
        <div
          className="eo-modal-overlay"
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.72)',
            zIndex: 9999,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '1rem',
          }}
          onClick={() => setShowQrModal(false)}
        >
          <div
            style={{
              background: '#ffffff',
              borderRadius: '20px',
              maxWidth: '460px',
              width: '100%',
              padding: '1.5rem',
              boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: '1.15rem', fontWeight: 800, color: '#0f172a' }}>
                  Mã QR Điểm Danh — {shift.jobType}
                </h3>
                <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: 2 }}>
                  Khung giờ: {formatTime(shift.startTime)} - {formatTime(shift.endTime)} • Đã vào ca: {checkedInCount}/{shiftConfirmed}
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowQrModal(false)}
                style={{ background: '#f1f5f9', border: 'none', borderRadius: '50%', width: 32, height: 32, fontSize: '1.2rem', cursor: 'pointer', color: '#475569' }}
              >
                &times;
              </button>
            </div>

            {/* Tab chọn Check-in / Check-out */}
            <div style={{ display: 'flex', background: '#f1f5f9', padding: 4, borderRadius: 10, marginBottom: '1rem' }}>
              <button
                type="button"
                onClick={() => setQrMode('CHECKIN')}
                style={{
                  flex: 1,
                  padding: '0.55rem',
                  border: 'none',
                  borderRadius: 8,
                  fontWeight: 800,
                  fontSize: '0.85rem',
                  cursor: 'pointer',
                  background: isCheckin ? '#2563eb' : 'transparent',
                  color: isCheckin ? '#ffffff' : '#475569',
                }}
              >
                Check-in (Đầu ca)
              </button>
              <button
                type="button"
                onClick={() => setQrMode('CHECKOUT')}
                style={{
                  flex: 1,
                  padding: '0.55rem',
                  border: 'none',
                  borderRadius: 8,
                  fontWeight: 800,
                  fontSize: '0.85rem',
                  cursor: 'pointer',
                  background: !isCheckin ? '#0d9488' : 'transparent',
                  color: !isCheckin ? '#ffffff' : '#475569',
                }}
              >
                Check-out (Cuối ca)
              </button>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.8rem', marginBottom: '0.75rem' }}>
              <span style={{ color: '#475569', fontWeight: 600 }}>
                Tự động đổi mã sau: <strong style={{ color: qrAccent }}>{countdown}s</strong>
              </span>
              <button
                type="button"
                onClick={() => {
                  setRefreshNonce(Date.now());
                  setCountdown(ROTATE_INTERVAL);
                }}
                style={{ background: 'none', border: 'none', color: qrAccent, fontWeight: 700, cursor: 'pointer' }}
              >
                🔄 Đổi mã ngay
              </button>
            </div>

            <div
              style={{
                padding: '1.25rem',
                background: '#ffffff',
                border: `2px solid ${qrAccent}`,
                borderRadius: '16px',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
              }}
            >
              <QRCodeSVG value={currentPayload} size={230} level="M" includeMargin />
              <div style={{ marginTop: 8, fontSize: '0.7rem', color: '#94a3b8', fontFamily: 'monospace', wordBreak: 'break-all', textAlign: 'center' }}>
                {currentPayload}
              </div>
            </div>

            <div style={{ marginTop: '1rem', padding: '0.65rem', background: '#f0fdf4', borderRadius: 10, border: '1px solid #bbf7d0', fontSize: '0.76rem', color: '#166534' }}>
              🛡️ <strong>Bảo mật HMAC-SHA256:</strong> Mã QR xoay vòng mỗi 20 giây để chống gian lận điểm danh từ xa hoặc chụp ảnh gửi lại.
            </div>
          </div>
        </div>
      )}

      {/* MODAL XEM ẢNH PHÓNG TO (BIÊN LAI / MINH CHỨNG) */}
      {previewImage && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.85)',
            zIndex: 10000,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '1.5rem',
          }}
          onClick={() => setPreviewImage(null)}
        >
          <div style={{ color: '#ffffff', fontWeight: 700, marginBottom: '1rem', fontSize: '1.05rem' }}>{previewImage.title}</div>
          <img
            src={previewImage.url}
            alt={previewImage.title}
            style={{ maxWidth: '90vw', maxHeight: '80vh', borderRadius: '12px', boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.3)' }}
            onClick={(e) => e.stopPropagation()}
          />
          <button
            type="button"
            onClick={() => setPreviewImage(null)}
            style={{ marginTop: '1rem', background: '#ffffff', color: '#0f172a', border: 'none', borderRadius: '8px', padding: '0.5rem 1.25rem', fontWeight: 700, cursor: 'pointer' }}
          >
            Đóng ảnh
          </button>
        </div>
      )}

      {/* MODAL ĐÁNH GIÁ WORKER */}
      <RateWorkerModal
        isOpen={Boolean(selectedForReview)}
        worker={selectedForReview}
        isSubmitting={submittingReview}
        onClose={() => setSelectedForReview(null)}
        onSubmit={handleSubmitReview}
      />

      {/* MODAL XEM HỒ SƠ CHI TIẾT WORKER */}
      <WorkerProfileModal
        isOpen={workerProfile.isOpen}
        loading={workerProfile.loading}
        error={workerProfile.error}
        profile={workerProfile.profile}
        onClose={workerProfile.closeProfile}
        onRetry={workerProfile.retry}
      />
    </div>
  );
}
