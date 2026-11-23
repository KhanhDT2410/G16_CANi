import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { eoService } from '../../../services/eoService';
import { blockListService } from '../../../services/blockListService';
import { showToast } from '../../../components/common/Toast';
import { showConfirm, showPrompt } from '../../../components/common/PromptModal';
import { getDynamicShiftStatus, getShiftStaffingSummary } from '../utils/eventHelpers';
import RateWorkerModal from './RateWorkerModal';
import ConfirmVerifySkillModal from './ConfirmVerifySkillModal';
import ReportNoShowModal from './ReportNoShowModal';
import EmergencyDispatchConfirmModal from './EmergencyDispatchConfirmModal';
import ShiftEmergencyBanner from './ShiftEmergencyBanner';
import { WorkerProfileModal } from './worker-profile';
import { useWorkerProfile } from '../hooks/useWorkerProfile';
import { resolveAssetUrl, buildWorkerVietQrUrl } from '../utils/resolveAssetUrl';

/**
 * ShiftWorkspacePage
 * Trang quản lý độc lập cho một ca làm việc cụ thể.
 * - Dành toàn bộ chiều rộng màn hình cho quản lý nhân sự, tiến độ từng đầu việc và thanh toán.
 * - Mã QR điểm danh được tách thành hộp thoại chiếu riêng cho đầu ca và cuối ca.
 */
export default function ShiftWorkspacePage({
  shift,
  event,
  onSelectShift,
  onBackToEvent,
  onEditShift,
  onOpenInvite,
  onReloadEvents,
  onOpenPaymentDeclaration,
  skillTaxonomy = [],
  targetAssignmentId = null,
}) {
  const [attendanceList, setAttendanceList] = useState([]);
  const [loadingAttendance, setLoadingAttendance] = useState(false);
  const [verifyingId, setVerifyingId] = useState(null);
  const [emergencyConfirmOpen, setEmergencyConfirmOpen] = useState(false);
  const [emergencyDispatchResult, setEmergencyDispatchResult] = useState(null);
  const [selectedForVerify, setSelectedForVerify] = useState(null);
  const [selectedForReview, setSelectedForReview] = useState(null);
  const [selectedForNoShow, setSelectedForNoShow] = useState(null);
  const [submittingReview, setSubmittingReview] = useState(false);
  const [searchWorker, setSearchWorker] = useState('');
  const [highlightedAssignmentId, setHighlightedAssignmentId] = useState(null);
  const workerProfile = useWorkerProfile();

  // Highlight và tự động cuộn đến hàng Worker khi deep link từ thông báo
  useEffect(() => {
    const idToFocus = targetAssignmentId;
    if (idToFocus && attendanceList.length > 0) {
      setHighlightedAssignmentId(idToFocus);
      setTimeout(() => {
        const el = document.getElementById(`assignment-row-${idToFocus}`);
        if (el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
      }, 200);
      const timer = setTimeout(() => {
        setHighlightedAssignmentId(null);
      }, 4000);
      return () => clearTimeout(timer);
    }
  }, [targetAssignmentId, attendanceList]);

  // Trạng thái hộp thoại Mã QR Điểm Danh (chỉ bật đầu ca hoặc cuối ca)
  const [showQrModal, setShowQrModal] = useState(false);
  const [qrMode, setQrMode] = useState('CHECKIN'); // 'CHECKIN' | 'CHECKOUT'
  const ROTATE_INTERVAL = 20;
  const [countdown, setCountdown] = useState(ROTATE_INTERVAL);
  const [refreshNonce, setRefreshNonce] = useState(Date.now());
  const [serverQrData, setServerQrData] = useState(null);

  // Xem ảnh biên lai hoặc minh chứng công việc
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

  useEffect(() => {
    const handlePaymentUpdated = () => {
      loadAttendance();
      if (onReloadEvents) {
        Promise.resolve(onReloadEvents()).catch(() => {});
      }
    };
    window.addEventListener('cani_payment_updated', handlePaymentUpdated);
    return () => window.removeEventListener('cani_payment_updated', handlePaymentUpdated);
  }, [loadAttendance, onReloadEvents]);

  const fetchLiveQr = useCallback(async () => {
    if (!shift?.shiftId || !showQrModal) return;
    try {
      const data = await eoService.getShiftQr(shift.shiftId);
      if (data) setServerQrData(data);
    } catch (err) {
      console.warn('Không thể lấy mã QR từ máy chủ:', err);
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

  const shiftChecklistTasks = useMemo(() => {
    if (!shift?.checklist) return [];
    try {
      const parsed = typeof shift.checklist === 'string' ? JSON.parse(shift.checklist) : shift.checklist;
      if (Array.isArray(parsed)) {
        return parsed.map((t, idx) => {
          if (typeof t === 'string') {
            return {
              id: `task_${idx}`,
              label: t,
              isRequiredEvidence: false,
              instruction: null,
            };
          }
          const id = String(t.id ?? t.taskId ?? `chk_${idx + 1}`);
          const label = String(t.title ?? t.label ?? t.name ?? `Nhiệm vụ ${idx + 1}`);
          const isRequiredEvidence = Boolean(t.isRequiredEvidence ?? t.requiresEvidence ?? false);
          const instruction = t.instruction || null;
          return { id, label, isRequiredEvidence, instruction };
        });
      }
    } catch {
      // ignore invalid json
    }
    return [];
  }, [shift?.checklist]);

  const parseWorkerChecklistProgressMap = (rawProgress) => {
    if (!rawProgress) return {};
    try {
      const parsed = typeof rawProgress === 'string' ? JSON.parse(rawProgress) : rawProgress;
      if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
        return parsed;
      }
      if (Array.isArray(parsed)) {
        const map = {};
        parsed.forEach((item, idx) => {
          if (typeof item === 'string') {
            map[item] = { isCompleted: true };
          } else if (item && typeof item === 'object') {
            const key = String(item.id ?? item.taskId ?? item.title ?? `chk_${idx + 1}`);
            map[key] = {
              isCompleted: Boolean(item.isCompleted ?? item.completed ?? item.done ?? item.checked ?? false),
              evidenceImageUrl: item.evidenceImageUrl || item.proofUrl || null,
              evidenceSubmittedAt: item.evidenceSubmittedAt || null,
              completedAt: item.completedAt || null,
            };
          }
        });
        return map;
      }
    } catch {
      // ignore
    }
    return {};
  };

  const canEoConfirmWorker = (workerItem) => {
    const isCheckedIn = workerItem.assignmentStatus === 'CHECKED_IN' || Boolean(workerItem.checkinTime);
    if (!isCheckedIn) return false;
    if (workerItem.eoConfirmed) return false;

    if (!shiftChecklistTasks || shiftChecklistTasks.length === 0) {
      return true;
    }

    const progressMap = parseWorkerChecklistProgressMap(workerItem.checklistRawProgress);
    for (const task of shiftChecklistTasks) {
      const prog = progressMap[task.id] || progressMap[task.label];
      const isDone = Boolean(prog?.isCompleted || workerItem.isChecklistCompleted);
      if (!isDone) return false;
      if (task.isRequiredEvidence) {
        const hasProof = Boolean(prog?.evidenceImageUrl && String(prog.evidenceImageUrl).trim().length > 0);
        if (!hasProof) return false;
      }
    }
    return true;
  };

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
  const staffingSummary = getShiftStaffingSummary(shift, event);
  const shiftConfirmed = staffingSummary.confirmed;
  const shiftRequired = staffingSummary.required;
  const fillRate = Math.round((shiftConfirmed / shiftRequired) * 100);
  const isShortage = dynStatus.isCritical || dynStatus.isEmergency || dynStatus.isUrgent || shiftConfirmed < shiftRequired;

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

  const handleVerify = async (assignmentId, verifyPayload = null) => {
    setVerifyingId(assignmentId);
    try {
      await eoService.verifyAttendance(assignmentId, verifyPayload);
      setSelectedForVerify(null);
      showToast('Đã xác nhận hoàn thành ca và duyệt công cho nhân sự.', 'success');
      await Promise.allSettled([loadAttendance(), onReloadEvents ? Promise.resolve(onReloadEvents()) : Promise.resolve()]);
    } catch (e) {
      showToast('Không thể duyệt công: ' + (e.response?.data?.message || e.message), 'error');
    } finally {
      setVerifyingId(null);
    }
  };

  const handleReportIncomplete = async (item) => {
    const reason = await showPrompt({
      title: `Báo cáo chưa hoàn thành công việc`,
      description: `Nhân sự: ${item.workerName}. Vui lòng mô tả chi tiết (tối thiểu 20 ký tự) để CSKH đối soát và xử lý.`,
      type: 'warning',
      confirmText: 'Gửi báo cáo',
      inputLabel: 'Lý do báo cáo',
      initialValue: 'Chưa hoàn thành đủ các đầu việc trong danh mục công việc được giao',
      isMultiline: true,
      minLength: 20,
    });
    if (reason === null) return;
    try {
      await eoService.reportIncompleteWork(item.assignmentId, {
        reason: reason.trim(),
      });
      showToast(`Đã gửi báo cáo đối với "${item.workerName}".`, 'success');
      loadAttendance();
    } catch (err) {
      showToast(err.response?.data?.message || err.message || 'Lỗi gửi báo cáo', 'error');
    }
  };

  const handleBlockWorker = async (worker) => {
    if (!worker?.userId) {
      showToast('Không tìm thấy mã tài khoản để chặn.', 'error');
      return;
    }
    const reason = await showPrompt({
      title: `Chặn nhân sự "${worker.workerName}"`,
      description: 'Nhân sự này sẽ không thể nhìn thấy hoặc nhận ca trong các sự kiện tương lai của bạn.',
      type: 'danger',
      confirmText: 'Xác nhận chặn',
      inputLabel: 'Lý do chặn',
      initialValue: 'Không tuân thủ quy định ca làm',
      isMultiline: true,
      allowEmpty: true,
    });
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
      showToast('Đã gửi đánh giá nhân sự thành công.', 'success');
      setSelectedForReview(null);
      loadAttendance();
    } catch (e) {
      showToast('Không thể gửi đánh giá: ' + (e.response?.data?.message || e.message), 'error');
    } finally {
      setSubmittingReview(false);
    }
  };

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
      
      {/* 1. THANH ĐIỀU HƯỚNG & CÔNG CỤ CA */}
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
            }}
          >
            Quay lại sự kiện "{event.name}"
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
              padding: '0.5rem 0.95rem',
              fontSize: '0.84rem',
              fontWeight: 800,
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              boxShadow: '0 2px 6px rgba(37, 99, 235, 0.25)',
            }}
            title="Mở mã QR điểm danh Vào ca cho nhân sự"
          >
            <span>📥</span> Mở QR Check-in (Vào ca)
          </button>

          <button
            type="button"
            onClick={() => {
              setQrMode('CHECKOUT');
              setShowQrModal(true);
            }}
            style={{
              background: 'linear-gradient(135deg, #0d9488 0%, #0f766e 100%)',
              color: '#ffffff',
              border: 'none',
              borderRadius: '8px',
              padding: '0.5rem 0.95rem',
              fontSize: '0.84rem',
              fontWeight: 800,
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              boxShadow: '0 2px 6px rgba(13, 148, 136, 0.25)',
            }}
            title="Mở mã QR điểm danh Kết ca cho nhân sự"
          >
            <span>📤</span> Mở QR Check-out (Kết ca)
          </button>

          {shift.emergency && staffingSummary.missing > 0 && (
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                background: '#fff1f2',
                color: '#e11d48',
                border: '1.5px solid #fecdd3',
                borderRadius: '8px',
                padding: '0.45rem 0.85rem',
                fontSize: '0.82rem',
                fontWeight: 700,
              }}
              title="Trạng thái điều phối khẩn cấp hiện tại"
            >
              {(emergencyDispatchResult && typeof emergencyDispatchResult.hasCandidates === 'boolean'
                ? emergencyDispatchResult.hasCandidates
                : staffingSummary.hasCandidates)
                ? 'Đang điều phối khẩn cấp'
                : 'Đã bật điều phối · Chưa tìm được Worker phù hợp'}
            </span>
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
              Mã Mời
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
              Sửa Ca
            </button>
          )}
        </div>
      </div>

      {/* Cảnh báo thiếu nhân sự sát giờ / nút kích hoạt khẩn cấp hoặc mời thêm */}
      <ShiftEmergencyBanner
        shift={shift}
        event={event}
        localDispatchResult={emergencyDispatchResult}
        onTriggerEmergency={() => setEmergencyConfirmOpen(true)}
        onOpenInvite={onOpenInvite}
      />

      <EmergencyDispatchConfirmModal
        isOpen={emergencyConfirmOpen}
        shift={shift}
        event={event}
        onClose={() => setEmergencyConfirmOpen(false)}
        onSuccess={(_, res) => setEmergencyDispatchResult(res)}
        onReloadEvents={onReloadEvents}
      />

      {/* 2. THÔNG TIN CHUNG CỦA CA */}
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
              {dynStatus.label}
            </span>
          </div>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1.25rem', fontSize: '0.85rem', color: '#475569', marginBottom: '0.5rem' }}>
            <span>
              Khung giờ: <strong>{formatTime(shift.startTime)} - {formatTime(shift.endTime)}</strong>
            </span>
            <span>
              Thù lao: <strong style={{ color: '#059669' }}>{new Intl.NumberFormat('vi-VN').format(shift.hourlyWage || 50000)} đ/giờ</strong>
              {shift.allowance > 0 && ` (+${new Intl.NumberFormat('vi-VN').format(shift.allowance)} đ phụ cấp)`}
            </span>
            {shift.dressCode && (
              <span>Đồng phục: <strong>{shift.dressCode}</strong></span>
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
          Cập nhật số liệu
        </button>
      </div>

      {/* 3. CHỈ SỐ TIẾN ĐỘ CỦA RIÊNG CA */}
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
            {isShortage ? 'Tuyển Dụng (Thiếu người)' : 'Tuyển Dụng Ca'}
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
            Hoàn Thành Đầu Việc ({shiftChecklistTasks.length} việc)
          </div>
          <div style={{ fontSize: '1.5rem', fontWeight: 800, color: '#4f46e5', margin: '0.25rem 0' }}>
            {checklistDoneCount} / {attendanceList.length}
          </div>
          <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
            Nhân sự đã hoàn thành toàn bộ công việc
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
            {pendingPayCount} chờ nhân sự xác nhận
          </div>
        </div>
      </div>

      {/* 4. BẢNG QUẢN LÝ NHÂN SỰ TRONG CA */}
      <div className="eo-widget-card" style={{ padding: '1.35rem 1.5rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.75rem' }}>
          <div>
            <h3 style={{ margin: 0, fontSize: '1.05rem', fontWeight: 800, color: '#0f172a' }}>
              Nhân Sự & Tiến Độ Công Việc Trong Ca ({attendanceList.length})
            </h3>
            <p style={{ margin: '0.2rem 0 0', fontSize: '0.8rem', color: '#64748b' }}>
              Theo dõi thời gian vào/ra, tiến độ từng đầu việc và thanh toán của riêng ca làm việc này
            </p>
          </div>

          <input
            type="text"
            value={searchWorker}
            onChange={(e) => setSearchWorker(e.target.value)}
            placeholder="Tìm theo tên hoặc số điện thoại..."
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
              Bạn có thể gửi mã mời trực tiếp để nhanh chóng lấp đầy ca làm việc này.
            </p>
            <div style={{ display: 'flex', justifyContent: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
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
                  Lấy Liên Kết / Mã Mời
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
                  <th style={{ padding: '12px 14px', minWidth: '220px' }}>Tiến Độ Đầu Việc</th>
                  <th style={{ padding: '12px 14px' }}>Thanh Toán & Bằng Chứng</th>
                  <th style={{ padding: '12px 14px', textAlign: 'right' }}>Thao Tác</th>
                </tr>
              </thead>
              <tbody>
                {filteredWorkers.map((item) => {
                  const isNoShow = item.assignmentStatus === 'NO_SHOW';
                  const isCheckedIn = item.assignmentStatus === 'CHECKED_IN' || Boolean(item.checkinTime);
                  const isCompleted = item.assignmentStatus === 'COMPLETED' || item.checkoutTime != null;
                  const workerProgressMap = parseWorkerChecklistProgressMap(item.checklistRawProgress);
                  const isTargetHighlighted = highlightedAssignmentId === item.assignmentId;
                  const canVerify = canEoConfirmWorker(item);

                  return (
                    <tr
                      key={item.assignmentId}
                      id={`assignment-row-${item.assignmentId}`}
                      style={{
                        borderBottom: '1px solid #f1f5f9',
                        verticalAlign: 'top',
                        backgroundColor: isTargetHighlighted ? '#fefce8' : 'transparent',
                        outline: isTargetHighlighted ? '2px solid #eab308' : 'none',
                        transition: 'background-color 0.5s ease',
                      }}
                    >
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
                          Đánh giá: {item.workerRatingAvg || 5.0} • Uy tín: {item.workerReliabilityScore || 100}
                        </div>
                      </td>

                      {/* 2. Trạng thái */}
                      <td style={{ padding: '14px' }}>
                        {isNoShow && (
                          <span style={{ background: '#fee2e2', color: '#b91c1c', border: '1px solid #fecaca', padding: '4px 10px', borderRadius: 12, fontWeight: 700, fontSize: '0.75rem', display: 'inline-block' }}>
                            ✕ Vắng mặt (No-Show)
                          </span>
                        )}
                        {!isNoShow && isCheckedIn && (
                          <span style={{ background: '#dcfce7', color: '#15803d', padding: '4px 10px', borderRadius: 12, fontWeight: 700, fontSize: '0.75rem', display: 'inline-block' }}>
                            Đã vào ca {item.isOnTime ? '(Đúng giờ)' : '(Trễ giờ)'}
                          </span>
                        )}
                        {!isNoShow && isCompleted && (
                          <span style={{ background: '#e0e7ff', color: '#3730a3', padding: '4px 10px', borderRadius: 12, fontWeight: 700, fontSize: '0.75rem', display: 'inline-block' }}>
                            Đã hoàn thành
                          </span>
                        )}
                        {!isNoShow && !isCheckedIn && !isCompleted && (
                          <span style={{ background: '#fef3c7', color: '#b45309', padding: '4px 10px', borderRadius: 12, fontWeight: 700, fontSize: '0.75rem', display: 'inline-block' }}>
                            Chờ đến ca
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

                      {/* 4. Tiến độ đầu việc & Minh chứng */}
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
                            {item.isChecklistCompleted ? 'Đã xong 100%' : (item.checklistProgress || 'Chưa cập nhật')}
                          </span>
                        </div>
                        {shiftChecklistTasks.length > 0 ? (
                          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                            {shiftChecklistTasks.map((t) => {
                              const prog = workerProgressMap[t.id] || workerProgressMap[t.label] || {};
                              const isTaskDone = Boolean(prog.isCompleted || item.isChecklistCompleted);
                              const hasEvidence = Boolean(prog.evidenceImageUrl && String(prog.evidenceImageUrl).trim().length > 0);

                              return (
                                <div
                                  key={t.id}
                                  style={{
                                    fontSize: '0.75rem',
                                    padding: '4px 8px',
                                    borderRadius: 6,
                                    background: isTaskDone ? '#f0fdf4' : '#f8fafc',
                                    border: `1px solid ${isTaskDone ? '#bbf7d0' : '#e2e8f0'}`,
                                    display: 'flex',
                                    flexDirection: 'column',
                                    gap: 2,
                                  }}
                                >
                                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 6 }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, flex: 1 }}>
                                      <span style={{ fontWeight: 700, color: isTaskDone ? '#15803d' : '#94a3b8' }}>
                                        {isTaskDone ? '✓' : '○'}
                                      </span>
                                      <span style={{ fontWeight: 600, color: isTaskDone ? '#1e293b' : '#64748b', textDecoration: isTaskDone ? 'none' : 'none' }}>
                                        {t.label}
                                      </span>
                                    </div>
                                    <span style={{ fontSize: '0.7rem', color: isTaskDone ? '#15803d' : '#94a3b8', fontWeight: 600 }}>
                                      {isTaskDone ? 'Đã làm' : 'Chưa'}
                                    </span>
                                  </div>

                                  {/* Minh chứng ảnh của mục này */}
                                  <div style={{ marginTop: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 6 }}>
                                    {t.isRequiredEvidence ? (
                                      hasEvidence ? (
                                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                                          <button
                                            type="button"
                                            onClick={() => setPreviewImage({
                                              url: prog.evidenceImageUrl,
                                              title: `Minh chứng việc "${t.label}" - Nhân sự ${item.workerName}`
                                            })}
                                            style={{
                                              background: '#eff6ff',
                                              color: '#1d4ed8',
                                              border: '1px solid #bfdbfe',
                                              borderRadius: 4,
                                              padding: '2px 6px',
                                              fontSize: '0.7rem',
                                              fontWeight: 700,
                                              cursor: 'pointer',
                                              display: 'inline-flex',
                                              alignItems: 'center',
                                              gap: 3,
                                            }}
                                          >
                                            📷 Xem minh chứng
                                          </button>
                                          {(prog.evidenceSubmittedAt || prog.completedAt) && (
                                            <span style={{ fontSize: '0.68rem', color: '#64748b' }}>
                                              {formatTime(prog.evidenceSubmittedAt || prog.completedAt)}
                                            </span>
                                          )}
                                        </div>
                                      ) : (
                                        <span style={{ color: '#dc2626', fontSize: '0.7rem', fontWeight: 700 }}>
                                          ⚠️ Chưa nộp minh chứng
                                        </span>
                                      )
                                    ) : (
                                      <span style={{ color: '#94a3b8', fontSize: '0.68rem', fontStyle: 'italic' }}>
                                        Không yêu cầu ảnh
                                      </span>
                                    )}
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                        ) : (
                          <div style={{ fontSize: '0.74rem', color: '#94a3b8', fontStyle: 'italic' }}>
                            Ca không có danh mục đầu việc mẫu
                          </div>
                        )}

                        {item.proofImageUrl && (
                          <div style={{ marginTop: 6 }}>
                            <button
                              type="button"
                              onClick={() => setPreviewImage({ url: item.proofImageUrl, title: `Ảnh hoàn thành ca chung - ${item.workerName}` })}
                              style={{
                                background: '#f8fafc',
                                color: '#475569',
                                border: '1px dashed #cbd5e1',
                                borderRadius: 4,
                                padding: '2px 6px',
                                fontSize: '0.7rem',
                                cursor: 'pointer',
                              }}
                            >
                              🖼️ Xem ảnh nộp ca chung
                            </button>
                          </div>
                        )}
                      </td>

                      {/* 5. Thanh toán & Bằng chứng */}
                      <td style={{ padding: '14px' }}>
                        {(() => {
                          const workerQrUrl = buildWorkerVietQrUrl(item);
                          const hasDeclared = Boolean(
                            item.paymentStatus && item.paymentStatus !== 'NOT_DECLARED'
                          ) || item.declaredAmount != null;
                          const isTransfer = item.paymentMethod === 'BANK_TRANSFER';
                          const isCash = item.paymentMethod === 'CASH';

                          return (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 5, alignItems: 'flex-start' }}>
                              {/* Số tiền thỏa thuận */}
                              <div style={{ fontSize: '0.76rem', color: '#475569' }}>
                                Thỏa thuận: <strong>{item.agreedWage ? Number(item.agreedWage).toLocaleString('vi-VN') + ' đ' : (shift.salary ? Number(shift.salary).toLocaleString('vi-VN') + ' đ' : '—')}</strong>
                              </div>

                              {/* Số tiền EO khai + Phương thức + Mã GD */}
                              {item.declaredAmount != null && (
                                <div style={{ fontSize: '0.76rem', color: '#0369a1' }}>
                                  Đã khai: <strong>{Number(item.declaredAmount).toLocaleString('vi-VN')} đ</strong>
                                  {(isTransfer || isCash) && (
                                    <span style={{
                                      marginLeft: 6,
                                      padding: '1px 6px',
                                      borderRadius: 4,
                                      fontSize: '0.68rem',
                                      fontWeight: 700,
                                      background: isTransfer ? '#e0e7ff' : '#fef3c7',
                                      color: isTransfer ? '#3730a3' : '#92400e',
                                    }}>
                                      {isTransfer ? '🏦 Chuyển khoản' : '💵 Tiền mặt'}
                                    </span>
                                  )}
                                </div>
                              )}

                              {item.externalRef && (
                                <div style={{ fontSize: '0.72rem', color: '#475569', fontFamily: 'monospace' }}>
                                  Mã GD: <strong>{item.externalRef}</strong>
                                </div>
                              )}

                              {item.declaredAt && (
                                <div style={{ fontSize: '0.7rem', color: '#64748b' }}>
                                  Khai lúc: {formatTime(item.declaredAt)}
                                </div>
                              )}

                              {/* Bằng chứng đã chuyển tiền — LUÔN hiển thị khi đã khai báo */}
                              {item.paymentProofUrl ? (
                                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 2 }}>
                                  <button
                                    type="button"
                                    onClick={() => setPreviewImage({
                                      url: resolveAssetUrl(item.paymentProofUrl),
                                      title: `Chứng từ thanh toán cho ${item.workerName}${item.externalRef ? ' (' + item.externalRef + ')' : ''}`,
                                    })}
                                    style={{
                                      display: 'inline-flex',
                                      alignItems: 'center',
                                      gap: 6,
                                      background: '#eff6ff',
                                      color: '#1d4ed8',
                                      border: '1px solid #bfdbfe',
                                      borderRadius: 6,
                                      padding: '3px 8px',
                                      fontSize: '0.72rem',
                                      fontWeight: 700,
                                      cursor: 'pointer',
                                    }}
                                  >
                                    <img
                                      src={resolveAssetUrl(item.paymentProofUrl)}
                                      alt="Biên lai"
                                      style={{ width: 22, height: 22, objectFit: 'cover', borderRadius: 4 }}
                                    />
                                    <span>🧾 Xem chứng từ đã chuyển</span>
                                  </button>
                                </div>
                              ) : hasDeclared && isCash ? (
                                <span style={{ fontSize: '0.7rem', color: '#64748b', fontStyle: 'italic' }}>
                                  ✓ Tiền mặt (xác nhận trên hệ thống)
                                </span>
                              ) : null}

                              {/* Trạng thái xác nhận của Worker */}
                              {item.workerConfirmStatus === 'RECEIVED_FULL' && (
                                <span style={{ background: '#dcfce7', color: '#15803d', padding: '3px 8px', borderRadius: 6, fontWeight: 700, fontSize: '0.74rem', display: 'inline-block' }}>
                                  ✓ Worker đã nhận đủ
                                </span>
                              )}

                              {item.workerConfirmStatus === 'RECEIVED_PARTIAL' && (
                                <div style={{ background: '#fef3c7', color: '#b45309', padding: '4px 8px', borderRadius: 6, fontSize: '0.72rem' }}>
                                  <div style={{ fontWeight: 700 }}>⚠️ Worker nhận thiếu:</div>
                                  <div>{item.workerComment || 'Chưa ghi chú'}</div>
                                </div>
                              )}

                              {item.workerConfirmStatus === 'NOT_RECEIVED' && (
                                <div style={{ background: '#fee2e2', color: '#b91c1c', padding: '4px 8px', borderRadius: 6, fontSize: '0.72rem' }}>
                                  <div style={{ fontWeight: 700 }}>✕ Worker chưa nhận được:</div>
                                  <div>{item.workerComment || 'Chưa ghi chú'}</div>
                                </div>
                              )}

                              {item.paymentStatus === 'CONFIRMED_FULL' && !item.workerConfirmStatus && (
                                <span style={{ background: '#dcfce7', color: '#15803d', padding: '4px 10px', borderRadius: 8, fontWeight: 700, fontSize: '0.75rem', display: 'inline-block' }}>
                                  Đã quyết toán
                                </span>
                              )}

                              {item.paymentStatus === 'DECLARED_PENDING_WORKER' && !item.workerConfirmStatus && (
                                <span style={{ background: '#fef3c7', color: '#b45309', padding: '3px 8px', borderRadius: 6, fontWeight: 700, fontSize: '0.74rem' }}>
                                  ⏳ Chờ Worker xác nhận
                                </span>
                              )}

                              {item.paymentStatus === 'DISPUTED' && !item.workerConfirmStatus && (
                                <span style={{ background: '#fee2e2', color: '#b91c1c', padding: '4px 10px', borderRadius: 8, fontWeight: 700, fontSize: '0.75rem', display: 'inline-block' }}>
                                  Đang tranh chấp
                                </span>
                              )}

                              {/* Các nút thao tác thanh toán & QR */}
                              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 2 }}>
                                {workerQrUrl && (
                                  <button
                                    type="button"
                                    onClick={() => setPreviewImage({
                                      url: workerQrUrl,
                                      title: `Mã VietQR nhận lương của ${item.workerName} (${item.bankCode || ''} ${item.bankAccountNumber || ''})`,
                                    })}
                                    style={{
                                      background: '#f5f3ff',
                                      color: '#6d28d9',
                                      border: '1px solid #ddd6fe',
                                      borderRadius: 6,
                                      padding: '4px 8px',
                                      fontSize: '0.72rem',
                                      fontWeight: 700,
                                      cursor: 'pointer',
                                    }}
                                  >
                                    📱 Mã QR
                                  </button>
                                )}

                                {(!item.paymentStatus || item.paymentStatus === 'NOT_DECLARED') ? (
                                  item.eoConfirmed ? (
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
                                      }}
                                    >
                                      Thanh toán & QR
                                    </button>
                                  ) : (
                                    <span
                                      title="Nhân sự cần được duyệt công trước khi tiến hành thanh toán"
                                      style={{
                                        background: '#f8fafc',
                                        color: '#64748b',
                                        border: '1px solid #cbd5e1',
                                        borderRadius: 6,
                                        padding: '4px 8px',
                                        fontSize: '0.73rem',
                                        fontWeight: 700,
                                        display: 'inline-block',
                                      }}
                                    >
                                      Chờ duyệt công
                                    </span>
                                  )
                                ) : (
                                  item.eoConfirmed && item.paymentStatus !== 'CONFIRMED_FULL' && (
                                    <button
                                      type="button"
                                      onClick={() => onOpenPaymentDeclaration?.(item)}
                                      style={{
                                        background: '#f8fafc',
                                        color: '#0f172a',
                                        border: '1px solid #cbd5e1',
                                        borderRadius: 6,
                                        padding: '4px 8px',
                                        fontSize: '0.72rem',
                                        fontWeight: 700,
                                        cursor: 'pointer',
                                      }}
                                    >
                                      Cập nhật chứng từ
                                    </button>
                                  )
                                )}
                              </div>
                            </div>
                          );
                        })()}
                      </td>

                      {/* 6. Thao tác */}
                      <td style={{ padding: '14px', textAlign: 'right' }}>
                        <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                          {!item.eoConfirmed && (
                            <button
                              type="button"
                              disabled={!canVerify || verifyingId === item.assignmentId}
                              onClick={() => setSelectedForVerify(item)}
                              title={
                                !isCheckedIn
                                  ? 'Nhân sự chưa check-in vào ca'
                                  : !canVerify
                                  ? 'Chưa hoàn tất các đầu việc bắt buộc hoặc còn thiếu minh chứng ảnh'
                                  : 'Xác nhận hoàn thành công việc và duyệt công'
                              }
                              style={{
                                background: !canVerify ? '#94a3b8' : (verifyingId === item.assignmentId ? '#94a3b8' : '#2563eb'),
                                color: '#ffffff',
                                border: 'none',
                                borderRadius: 6,
                                padding: '5px 10px',
                                fontSize: '0.76rem',
                                fontWeight: 700,
                                cursor: !canVerify || verifyingId === item.assignmentId ? 'not-allowed' : 'pointer',
                              }}
                            >
                              {verifyingId === item.assignmentId ? 'Đang duyệt...' : 'Duyệt công'}
                            </button>
                          )}

                          {item.eoConfirmed && !isCompleted && (
                            <span style={{ color: '#2563eb', background: '#eff6ff', border: '1px solid #bfdbfe', borderRadius: 6, fontSize: '0.73rem', fontWeight: 700, padding: '4px 8px' }}>
                              Đã duyệt • Chờ kết ca
                            </span>
                          )}

                          {item.eoConfirmed && isCompleted && !item.hasReviewedWorker && (
                            <button
                              type="button"
                              onClick={() => setSelectedForReview(item)}
                              style={{ background: '#fffbeb', color: '#b45309', border: '1px solid #fcd34d', borderRadius: 6, padding: '4px 9px', fontSize: '0.76rem', fontWeight: 700, cursor: 'pointer' }}
                            >
                              Đánh giá
                            </button>
                          )}

                          {item.eoConfirmed && isCompleted && item.hasReviewedWorker && (
                            <span style={{ color: '#059669', fontSize: '0.75rem', fontWeight: 700, padding: '4px' }}>
                              ✓ Đã đánh giá
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
                            Nhắn tin
                          </button>

                          {!item.eoConfirmed && (isCheckedIn || isCompleted) && (
                            <button
                              type="button"
                              onClick={() => handleReportIncomplete(item)}
                              style={{ background: '#fff7ed', color: '#c2410c', border: '1px solid #fed7aa', borderRadius: 6, padding: '4px 8px', fontSize: '0.75rem', fontWeight: 600, cursor: 'pointer' }}
                            >
                              Báo lỗi
                            </button>
                          )}

                          {!isCheckedIn && !isCompleted && !isNoShow && item.assignmentStatus !== 'CANCELLED' && (
                            <button
                              type="button"
                              onClick={() => setSelectedForNoShow(item)}
                              style={{
                                background: '#fff1f2',
                                color: '#e11d48',
                                border: '1px solid #fecdd3',
                                borderRadius: 6,
                                padding: '4px 9px',
                                fontSize: '0.75rem',
                                fontWeight: 700,
                                cursor: 'pointer',
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: 3,
                              }}
                              title="Báo cáo nhân sự không đến & Tự động tìm người thay thế"
                            >
                              <span>⚠️</span> Báo vắng
                            </button>
                          )}

                          {isNoShow && (
                            <span style={{ color: '#e11d48', fontSize: '0.75rem', fontWeight: 700, padding: '4px' }}>
                              Đã phạt No-Show
                            </span>
                          )}

                          <button
                            type="button"
                            onClick={() => handleBlockWorker(item)}
                            style={{ background: '#fef2f2', color: '#dc2626', border: '1px solid #fecaca', borderRadius: 6, padding: '4px 8px', fontSize: '0.75rem', fontWeight: 600, cursor: 'pointer' }}
                          >
                            Chặn
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

      {/* HỘP THOẠI MÃ QR ĐIỂM DANH */}
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
                  {isCheckin ? '📥 Mã QR Check-in (Vào ca)' : '📤 Mã QR Check-out (Kết ca)'} — {shift.jobType}
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
                Điểm danh vào ca (Đầu ca)
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
                Điểm danh kết ca (Cuối ca)
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
                Đổi mã ngay
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
              <strong>Bảo mật:</strong> Mã QR xoay vòng mỗi 20 giây để chống gian lận điểm danh từ xa hoặc chụp ảnh gửi lại.
            </div>
          </div>
        </div>
      )}

      {/* HỘP THOẠI XEM ẢNH PHÓNG TO */}
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
            src={resolveAssetUrl(previewImage.url)}
            alt={previewImage.title}
            style={{ maxWidth: '90vw', maxHeight: '80vh', borderRadius: '12px', boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.3)', backgroundColor: '#ffffff' }}
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

      <ConfirmVerifySkillModal
        isOpen={Boolean(selectedForVerify)}
        onClose={() => setSelectedForVerify(null)}
        assignment={selectedForVerify}
        shift={shift}
        skillTaxonomy={skillTaxonomy}
        submitting={Boolean(selectedForVerify && verifyingId === selectedForVerify.assignmentId)}
        onConfirm={({ assignmentId, confirmedPerformedSkillId, confirmPerformedSkill }) =>
          handleVerify(assignmentId, { confirmedPerformedSkillId, confirmPerformedSkill })
        }
      />

      <RateWorkerModal
        isOpen={Boolean(selectedForReview)}
        worker={selectedForReview}
        isSubmitting={submittingReview}
        onClose={() => setSelectedForReview(null)}
        onSubmit={handleSubmitReview}
      />

      <ReportNoShowModal
        isOpen={Boolean(selectedForNoShow)}
        onClose={() => setSelectedForNoShow(null)}
        assignment={selectedForNoShow}
        shift={shift}
        onSuccess={async () => {
          await Promise.allSettled([
            loadAttendance(),
            onReloadEvents ? Promise.resolve(onReloadEvents()) : Promise.resolve(),
          ]);
        }}
      />

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
