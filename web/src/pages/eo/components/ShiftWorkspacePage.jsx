import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { eoService } from '../../../services/eoService';
import { blockListService } from '../../../services/blockListService';
import { showToast } from '../../../components/common/Toast';
import { showConfirm, showPrompt } from '../../../components/common/PromptModal';
import { getDynamicShiftStatus, getShiftStaffingSummary, formatVndWhole } from '../utils/eventHelpers';
import WorkerReviewForm from './WorkerReviewForm';
import ConfirmVerifySkillModal, { resolvePreassignedPerformedSkillId } from './ConfirmVerifySkillModal';
import ReportNoShowModal from './ReportNoShowModal';
import ShiftEmergencyBanner from './ShiftEmergencyBanner';
import EmergencyTriggerButton from './EmergencyTriggerButton';
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
  const [refreshingAttendance, setRefreshingAttendance] = useState(false);
  const [verifyingId, setVerifyingId] = useState(null);
  const [emergencyDispatchResult, setEmergencyDispatchResult] = useState(null);
  const [selectedForVerify, setSelectedForVerify] = useState(null);
  const [selectedForReview, setSelectedForReview] = useState(null);
  const [selectedForNoShow, setSelectedForNoShow] = useState(null);
  const [submittingReview, setSubmittingReview] = useState(false);
  const [searchWorker, setSearchWorker] = useState('');
  const [workerFilter, setWorkerFilter] = useState('ALL');
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

  const loadAttendance = useCallback(async ({ silent = false } = {}) => {
    if (!shift?.shiftId) return;
    if (silent) {
      setRefreshingAttendance(true);
    } else {
      setLoadingAttendance(true);
    }
    try {
      const data = await eoService.getShiftAttendance(shift.shiftId);
      setAttendanceList(Array.isArray(data) ? data : []);
    } catch (e) {
      console.warn('Lỗi khi tải danh sách điểm danh ca:', e);
      if (!silent) {
        setAttendanceList([]);
      }
    } finally {
      if (silent) {
        setRefreshingAttendance(false);
      } else {
        setLoadingAttendance(false);
      }
    }
  }, [shift?.shiftId]);

  useEffect(() => {
    if (shift?.shiftId) {
      loadAttendance({ silent: false });
    }
  }, [shift?.shiftId, loadAttendance]);

  useEffect(() => {
    const handlePaymentUpdated = () => {
      loadAttendance({ silent: true });
      if (onReloadEvents) {
        Promise.resolve(onReloadEvents({ silent: true })).catch(() => { });
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

  const workerFilterCounts = {
    ALL: attendanceList.length,
    NEED_VERIFY: attendanceList.filter((w) => {
      const noShow = w.assignmentStatus === 'NO_SHOW';
      const checkedInOrDone =
        w.assignmentStatus === 'CHECKED_IN' || w.assignmentStatus === 'COMPLETED' || Boolean(w.checkinTime) || Boolean(w.checkoutTime);
      return !noShow && checkedInOrDone && !w.eoConfirmed;
    }).length,
    NEED_PAYMENT: attendanceList.filter(
      (w) => w.assignmentStatus !== 'NO_SHOW' && Boolean(w.eoConfirmed) && w.paymentStatus !== 'CONFIRMED_FULL'
    ).length,
    WAITING_OR_ABSENT: attendanceList.filter((w) => {
      const noShow = w.assignmentStatus === 'NO_SHOW';
      const checkedInOrDone =
        w.assignmentStatus === 'CHECKED_IN' || w.assignmentStatus === 'COMPLETED' || Boolean(w.checkinTime) || Boolean(w.checkoutTime);
      return noShow || !checkedInOrDone;
    }).length,
    SETTLED: attendanceList.filter((w) => w.paymentStatus === 'CONFIRMED_FULL').length,
  };

  const filteredWorkers = attendanceList.filter((w) => {
    const noShow = w.assignmentStatus === 'NO_SHOW';
    const checkedInOrDone =
      w.assignmentStatus === 'CHECKED_IN' || w.assignmentStatus === 'COMPLETED' || Boolean(w.checkinTime) || Boolean(w.checkoutTime);

    if (workerFilter === 'NEED_VERIFY' && (noShow || !checkedInOrDone || w.eoConfirmed)) return false;
    if (workerFilter === 'NEED_PAYMENT' && (noShow || !w.eoConfirmed || w.paymentStatus === 'CONFIRMED_FULL')) return false;
    if (workerFilter === 'WAITING_OR_ABSENT' && !noShow && checkedInOrDone) return false;
    if (workerFilter === 'SETTLED' && w.paymentStatus !== 'CONFIRMED_FULL') return false;

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
      setAttendanceList((prev) =>
        prev.map((w) => (w.assignmentId === assignmentId ? { ...w, eoConfirmed: true } : w))
      );
      setSelectedForVerify(null);
      showToast('Đã xác nhận hoàn thành ca và duyệt công cho nhân sự.', 'success');
      await Promise.allSettled([
        loadAttendance({ silent: true }),
        onReloadEvents ? Promise.resolve(onReloadEvents({ silent: true })) : Promise.resolve(),
      ]);
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
      loadAttendance({ silent: true });
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
      loadAttendance({ silent: true });
    } catch (err) {
      showToast(err.response?.data?.message || err.message || 'Lỗi khi chặn nhân sự', 'error');
    }
  };

  const handleSubmitReview = async (reviewData) => {
    setSubmittingReview(true);
    try {
      await eoService.rateWorker(reviewData.assignmentId, reviewData);
      setAttendanceList((prev) =>
        prev.map((w) =>
          w.assignmentId === reviewData.assignmentId ? { ...w, hasReviewedWorker: true } : w
        )
      );
      showToast('Đã gửi đánh giá nhân sự thành công.', 'success');
      setSelectedForReview(null);
      loadAttendance({ silent: true });
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
              minHeight: '38px',
              background: 'linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%)',
              color: '#ffffff',
              border: 'none',
              borderRadius: '8px',
              padding: '0.45rem 1rem',
              fontSize: '0.84rem',
              fontWeight: 700,
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 6,
              boxShadow: '0 2px 6px rgba(37, 99, 235, 0.25)',
              transition: 'all 0.15s ease',
            }}
            title="Mở mã QR điểm danh Vào ca cho nhân sự"
          >
            Mở QR Check-in (Vào ca)
          </button>

          <button
            type="button"
            onClick={() => {
              setQrMode('CHECKOUT');
              setShowQrModal(true);
            }}
            style={{
              minHeight: '38px',
              background: 'linear-gradient(135deg, #0d9488 0%, #0f766e 100%)',
              color: '#ffffff',
              border: 'none',
              borderRadius: '8px',
              padding: '0.45rem 1rem',
              fontSize: '0.84rem',
              fontWeight: 700,
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 6,
              boxShadow: '0 2px 6px rgba(13, 148, 136, 0.25)',
              transition: 'all 0.15s ease',
            }}
            title="Mở mã QR điểm danh Kết ca cho nhân sự"
          >
            Mở QR Check-out (Kết ca)
          </button>

          {shift.emergency && staffingSummary.missing > 0 ? (
            <span
              style={{
                minHeight: '38px',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: '#fff1f2',
                color: '#e11d48',
                border: '1.5px solid #fecdd3',
                borderRadius: '8px',
                padding: '0.45rem 0.95rem',
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
          ) : (
            <EmergencyTriggerButton shift={shift} event={event} />
          )}

          {onOpenInvite && (
            <button
              type="button"
              onClick={() => onOpenInvite(shift, event)}
              style={{
                minHeight: '38px',
                background: '#f5f3ff',
                color: '#6d28d9',
                border: '1px solid #ddd6fe',
                borderRadius: '8px',
                padding: '0.45rem 0.95rem',
                fontSize: '0.82rem',
                fontWeight: 700,
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'all 0.15s ease',
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
                minHeight: '38px',
                background: '#ffffff',
                color: '#334155',
                border: '1px solid #cbd5e1',
                borderRadius: '8px',
                padding: '0.45rem 0.95rem',
                fontSize: '0.82rem',
                fontWeight: 700,
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'all 0.15s ease',
              }}
            >
              Sửa Ca
            </button>
          )}
        </div>
      </div>

      {/* Cảnh báo thiếu nhân sự sát giờ hoặc đã bật khẩn cấp */}
      <ShiftEmergencyBanner
        shift={shift}
        event={event}
        localDispatchResult={emergencyDispatchResult}
        onOpenInvite={onOpenInvite}
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
            loadAttendance({ silent: true });
            onReloadEvents?.({ silent: true });
          }}
          disabled={refreshingAttendance}
          style={{
            background: '#f8fafc',
            border: '1px solid #cbd5e1',
            borderRadius: '8px',
            padding: '0.45rem 0.85rem',
            fontSize: '0.8rem',
            fontWeight: 700,
            color: '#334155',
            cursor: refreshingAttendance ? 'wait' : 'pointer',
            opacity: refreshingAttendance ? 0.75 : 1,
          }}
        >
          {refreshingAttendance ? 'Đang đồng bộ...' : 'Cập nhật số liệu'}
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

      {/* 4. DANH SÁCH QUẢN LÝ NHÂN SỰ TRONG CA (DẠNG THẺ TÁC VỤ NHANH, KHÔNG LOAD LẠI TRANG) */}
      <div className="eo-widget-card" style={{ padding: '1.35rem 1.5rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.85rem', flexWrap: 'wrap', gap: '0.75rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' }}>
              <h3 style={{ margin: 0, fontSize: '1.05rem', fontWeight: 800, color: '#0f172a' }}>
                Nhân Sự & Tiến Độ Công Việc Trong Ca ({attendanceList.length})
              </h3>
              {refreshingAttendance && (
                <span style={{ fontSize: '0.74rem', fontWeight: 600, color: '#2563eb', background: '#eff6ff', border: '1px solid #bfdbfe', padding: '2px 8px', borderRadius: '999px' }}>
                  Đang đồng bộ...
                </span>
              )}
            </div>
            <p style={{ margin: '0.2rem 0 0', fontSize: '0.8rem', color: '#64748b' }}>
              Xử lý duyệt công, đối soát đầu việc, thanh toán và đánh giá trực tiếp trên từng thẻ nhân sự
            </p>
          </div>

          <input
            type="text"
            value={searchWorker}
            onChange={(e) => setSearchWorker(e.target.value)}
            placeholder="Tìm theo tên hoặc số điện thoại..."
            style={{
              padding: '0.48rem 0.9rem',
              borderRadius: '8px',
              border: '1px solid #cbd5e1',
              fontSize: '0.85rem',
              minWidth: '240px',
            }}
          />
        </div>

        {/* Bộ lọc trạng thái xử lý nhanh */}
        {attendanceList.length > 0 && (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.45rem', marginBottom: '1rem', paddingBottom: '0.85rem', borderBottom: '1px solid #f1f5f9' }}>
            {[
              { key: 'ALL', label: `Tất cả (${workerFilterCounts.ALL})` },
              { key: 'NEED_VERIFY', label: `Cần duyệt công (${workerFilterCounts.NEED_VERIFY})` },
              { key: 'NEED_PAYMENT', label: `Chờ thanh toán (${workerFilterCounts.NEED_PAYMENT})` },
              { key: 'WAITING_OR_ABSENT', label: `Chờ đến / Vắng (${workerFilterCounts.WAITING_OR_ABSENT})` },
              { key: 'SETTLED', label: `Đã quyết toán (${workerFilterCounts.SETTLED})` },
            ].map((tab) => {
              const isActive = workerFilter === tab.key;
              return (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => setWorkerFilter(tab.key)}
                  style={{
                    padding: '0.36rem 0.8rem',
                    borderRadius: '999px',
                    border: isActive ? '1px solid #2563eb' : '1px solid #e2e8f0',
                    background: isActive ? '#eff6ff' : '#f8fafc',
                    color: isActive ? '#1d4ed8' : '#475569',
                    fontSize: '0.78rem',
                    fontWeight: isActive ? 700 : 600,
                    cursor: 'pointer',
                  }}
                >
                  {tab.label}
                </button>
              );
            })}
          </div>
        )}

        {loadingAttendance && attendanceList.length === 0 ? (
          <div style={{ padding: '3rem 1rem', textAlign: 'center', color: '#64748b', background: '#f8fafc', borderRadius: '10px' }}>
            Đang tải dữ liệu nhân sự và tiến độ ca làm việc...
          </div>
        ) : filteredWorkers.length === 0 ? (
          <div style={{ padding: '3rem 1.5rem', textAlign: 'center', background: '#f8fafc', borderRadius: '12px', border: '1px dashed #cbd5e1' }}>
            <h4 style={{ margin: '0 0 0.5rem', color: '#334155', fontSize: '1rem' }}>
              {attendanceList.length === 0
                ? `Chưa có nhân sự nào trong ca "${shift.jobType}"`
                : 'Không có nhân sự phù hợp bộ lọc hiện tại'}
            </h4>
            <p style={{ margin: '0 0 1.25rem', fontSize: '0.85rem', color: '#64748b' }}>
              {attendanceList.length === 0
                ? 'Bạn có thể gửi mã mời trực tiếp để nhanh chóng lấp đầy ca làm việc này.'
                : 'Hãy chọn bộ lọc khác hoặc xóa từ khóa tìm kiếm để xem toàn bộ nhân sự.'}
            </p>
            <div style={{ display: 'flex', justifyContent: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
              {attendanceList.length > 0 && (
                <button
                  type="button"
                  onClick={() => {
                    setWorkerFilter('ALL');
                    setSearchWorker('');
                  }}
                  style={{
                    background: '#ffffff',
                    color: '#334155',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    padding: '0.5rem 1.05rem',
                    fontSize: '0.85rem',
                    fontWeight: 700,
                    cursor: 'pointer',
                  }}
                >
                  Xem tất cả nhân sự
                </button>
              )}
              {attendanceList.length === 0 && onOpenInvite && (
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
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.9rem' }}>
            {filteredWorkers.map((item) => {
              const isNoShow = item.assignmentStatus === 'NO_SHOW';
              const isCheckedIn = item.assignmentStatus === 'CHECKED_IN' || Boolean(item.checkinTime);
              const isCompleted = item.assignmentStatus === 'COMPLETED' || item.checkoutTime != null;
              const workerProgressMap = parseWorkerChecklistProgressMap(item.checklistRawProgress);
              const isTargetHighlighted = highlightedAssignmentId === item.assignmentId;
              const canVerify = canEoConfirmWorker(item);
              const isReviewExpanded = selectedForReview?.assignmentId === item.assignmentId;

              const workerQrUrl = buildWorkerVietQrUrl(item);
              const hasDeclared =
                Boolean(item.paymentStatus && item.paymentStatus !== 'NOT_DECLARED') ||
                item.declaredAmount != null;
              const isTransfer = item.paymentMethod === 'BANK_TRANSFER';
              const isCash = item.paymentMethod === 'CASH';

              const sourceLabel = (() => {
                switch (item.source) {
                  case 'GROUP': return 'Nhóm';
                  case 'MATCHING': return 'Hệ thống';
                  case 'DIRECT': return 'Trực tiếp';
                  case 'BULK': return 'Hàng loạt';
                  default: return item.source;
                }
              })();

              const attendanceBadge = isNoShow
                ? { text: 'Vắng mặt (No-Show)', bg: '#fee2e2', color: '#b91c1c', border: '#fecaca' }
                : isCompleted
                  ? { text: 'Đã hoàn thành ca', bg: '#e0e7ff', color: '#3730a3', border: '#c7d2fe' }
                  : isCheckedIn
                    ? {
                        text: `Đã vào ca ${item.isOnTime ? '(Đúng giờ)' : '(Trễ giờ)'}`,
                        bg: '#dcfce7',
                        color: '#15803d',
                        border: '#bbf7d0',
                      }
                    : { text: 'Chờ đến ca', bg: '#fef3c7', color: '#b45309', border: '#fde68a' };

              const paymentStageBadge =
                item.workerConfirmStatus === 'RECEIVED_FULL' || item.paymentStatus === 'CONFIRMED_FULL'
                  ? { text: 'Đã quyết toán đủ', bg: '#dcfce7', color: '#15803d', border: '#bbf7d0' }
                  : item.paymentStatus === 'DISPUTED' || item.workerConfirmStatus === 'NOT_RECEIVED' || item.workerConfirmStatus === 'RECEIVED_PARTIAL'
                    ? { text: 'Cần đối soát thanh toán', bg: '#fee2e2', color: '#b91c1c', border: '#fecaca' }
                    : item.paymentStatus === 'DECLARED_PENDING_WORKER'
                      ? { text: 'Chờ Worker xác nhận', bg: '#fef3c7', color: '#b45309', border: '#fde68a' }
                      : item.eoConfirmed
                        ? { text: 'Sẵn sàng thanh toán', bg: '#ecfdf5', color: '#047857', border: '#a7f3d0' }
                        : { text: 'Chờ duyệt công', bg: '#f1f5f9', color: '#475569', border: '#e2e8f0' };

              return (
                <div
                  key={item.assignmentId}
                  id={`assignment-row-${item.assignmentId}`}
                  style={{
                    borderRadius: '12px',
                    border: isTargetHighlighted ? '2px solid #eab308' : '1px solid #e2e8f0',
                    backgroundColor: isTargetHighlighted ? '#fefce8' : '#ffffff',
                    boxShadow: '0 1px 2px rgba(15, 23, 42, 0.04)',
                    overflow: 'hidden',
                    transition: 'background-color 0.3s ease, border-color 0.2s ease',
                  }}
                >
                  {/* VÙNG 1: THÔNG TIN NHÂN SỰ + TRẠNG THÁI CHUNG */}
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      flexWrap: 'wrap',
                      gap: '0.75rem',
                      padding: '0.9rem 1.15rem',
                      backgroundColor: '#f8fafc',
                      borderBottom: '1px solid #e2e8f0',
                    }}
                  >
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.55rem', flexWrap: 'wrap' }}>
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
                            fontSize: '0.98rem',
                          }}
                        >
                          {item.workerName}
                        </button>
                        {item.workerPhone && (
                          <span style={{ fontSize: '0.82rem', color: '#475569', fontWeight: 600 }}>
                            {item.workerPhone}
                          </span>
                        )}
                        {sourceLabel && (
                          <span
                            style={{
                              padding: '2px 8px',
                              fontSize: '0.7rem',
                              fontWeight: 700,
                              borderRadius: '999px',
                              backgroundColor: '#f0fdfa',
                              border: '1px solid #ccfbf1',
                              color: '#0d9488',
                              textTransform: 'uppercase',
                            }}
                          >
                            {sourceLabel}
                          </span>
                        )}
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '0.9rem', fontSize: '0.78rem', color: '#475569' }}>
                        <span style={{ color: '#2563eb', fontWeight: 600 }}>
                          Đánh giá: {item.workerRatingAvg || 5.0} • Uy tín: {item.workerReliabilityScore || 100}
                        </span>
                        <span>
                          Vào: <strong style={{ color: '#0f172a' }}>{item.checkinTime ? formatTime(item.checkinTime) : '--:--'}</strong>
                        </span>
                        <span>
                          Ra: <strong style={{ color: '#0f172a' }}>{item.checkoutTime ? formatTime(item.checkoutTime) : '--:--'}</strong>
                        </span>
                        {item.hoursWorked != null && (
                          <span style={{ color: '#0d9488', fontWeight: 700 }}>
                            {item.hoursWorked} giờ làm
                          </span>
                        )}
                      </div>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem', flexWrap: 'wrap' }}>
                      <span
                        style={{
                          background: attendanceBadge.bg,
                          color: attendanceBadge.color,
                          border: `1px solid ${attendanceBadge.border}`,
                          padding: '4px 10px',
                          borderRadius: '999px',
                          fontWeight: 700,
                          fontSize: '0.75rem',
                        }}
                      >
                        {attendanceBadge.text}
                      </span>
                      {!isNoShow && (
                        <span
                          style={{
                            background: paymentStageBadge.bg,
                            color: paymentStageBadge.color,
                            border: `1px solid ${paymentStageBadge.border}`,
                            padding: '4px 10px',
                            borderRadius: '999px',
                            fontWeight: 700,
                            fontSize: '0.75rem',
                          }}
                        >
                          {paymentStageBadge.text}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* VÙNG 2: CHI TIẾT ĐẦU VIỆC (TRÁI) & THÙ LAO / BẰNG CHỨNG (PHẢI) */}
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(290px, 1fr))',
                      gap: '1rem',
                      padding: '0.95rem 1.15rem',
                    }}
                  >
                    {/* Cột trái: Đầu việc & Minh chứng */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
                        <span style={{ fontSize: '0.76rem', fontWeight: 700, color: '#475569', textTransform: 'uppercase' }}>
                          Tiến độ đầu việc
                        </span>
                        <span
                          style={{
                            background: item.isChecklistCompleted ? '#dcfce7' : '#f1f5f9',
                            color: item.isChecklistCompleted ? '#15803d' : '#334155',
                            padding: '2px 8px',
                            borderRadius: 6,
                            fontWeight: 700,
                            fontSize: '0.73rem',
                          }}
                        >
                          {item.isChecklistCompleted ? 'Đã xong 100%' : (item.checklistProgress || 'Chưa cập nhật')}
                        </span>
                      </div>

                      {shiftChecklistTasks.length > 0 ? (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                          {shiftChecklistTasks.map((t) => {
                            const prog = workerProgressMap[t.id] || workerProgressMap[t.label] || {};
                            const isTaskDone = Boolean(prog.isCompleted || item.isChecklistCompleted);
                            const hasEvidence = Boolean(prog.evidenceImageUrl && String(prog.evidenceImageUrl).trim().length > 0);

                            return (
                              <div
                                key={t.id}
                                style={{
                                  fontSize: '0.78rem',
                                  padding: '0.45rem 0.65rem',
                                  borderRadius: '8px',
                                  background: isTaskDone ? '#f0fdf4' : '#f8fafc',
                                  border: `1px solid ${isTaskDone ? '#bbf7d0' : '#e2e8f0'}`,
                                  display: 'flex',
                                  justifyContent: 'space-between',
                                  alignItems: 'center',
                                  flexWrap: 'wrap',
                                  gap: '0.5rem',
                                }}
                              >
                                <div style={{ display: 'flex', alignItems: 'center', gap: 6, flex: 1, minWidth: '160px' }}>
                                  <span
                                    style={{
                                      fontSize: '0.68rem',
                                      fontWeight: 700,
                                      padding: '1px 6px',
                                      borderRadius: '4px',
                                      background: isTaskDone ? '#dcfce7' : '#e2e8f0',
                                      color: isTaskDone ? '#15803d' : '#475569',
                                    }}
                                  >
                                    {isTaskDone ? 'Xong' : 'Chưa'}
                                  </span>
                                  <span style={{ fontWeight: 600, color: isTaskDone ? '#1e293b' : '#64748b' }}>
                                    {t.label}
                                  </span>
                                </div>

                                <div>
                                  {t.isRequiredEvidence ? (
                                    hasEvidence ? (
                                      <button
                                        type="button"
                                        onClick={() => setPreviewImage({
                                          url: prog.evidenceImageUrl,
                                          title: `Minh chứng việc "${t.label}" - Nhân sự ${item.workerName}`,
                                        })}
                                        style={{
                                          background: '#eff6ff',
                                          color: '#1d4ed8',
                                          border: '1px solid #bfdbfe',
                                          borderRadius: 5,
                                          padding: '2px 8px',
                                          fontSize: '0.72rem',
                                          fontWeight: 700,
                                          cursor: 'pointer',
                                        }}
                                      >
                                        Xem ảnh{(prog.evidenceSubmittedAt || prog.completedAt) ? ` (${formatTime(prog.evidenceSubmittedAt || prog.completedAt)})` : ''}
                                      </button>
                                    ) : (
                                      <span style={{ color: '#dc2626', fontSize: '0.72rem', fontWeight: 700 }}>
                                        Thiếu minh chứng
                                      </span>
                                    )
                                  ) : (
                                    <span style={{ color: '#94a3b8', fontSize: '0.7rem' }}>
                                      Không cần ảnh
                                    </span>
                                  )}
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      ) : (
                        <div style={{ fontSize: '0.78rem', color: '#94a3b8', fontStyle: 'italic' }}>
                          Ca không có danh mục đầu việc mẫu
                        </div>
                      )}

                      {item.proofImageUrl && (
                        <div>
                          <button
                            type="button"
                            onClick={() => setPreviewImage({ url: item.proofImageUrl, title: `Ảnh hoàn thành ca chung - ${item.workerName}` })}
                            style={{
                              background: '#f8fafc',
                              color: '#334155',
                              border: '1px dashed #cbd5e1',
                              borderRadius: 6,
                              padding: '3px 8px',
                              fontSize: '0.73rem',
                              fontWeight: 600,
                              cursor: 'pointer',
                            }}
                          >
                            Xem ảnh nộp ca chung
                          </button>
                        </div>
                      )}
                    </div>

                    {/* Cột phải: Thù lao & Thanh toán */}
                    <div
                      style={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '0.45rem',
                        background: '#f8fafc',
                        borderRadius: '8px',
                        padding: '0.75rem 0.9rem',
                        border: '1px solid #e2e8f0',
                      }}
                    >
                      <span style={{ fontSize: '0.76rem', fontWeight: 700, color: '#475569', textTransform: 'uppercase' }}>
                        Thanh toán & Chứng từ
                      </span>

                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', color: '#475569' }}>
                        <span>Thù lao thỏa thuận:</span>
                        <strong style={{ color: '#0f172a' }}>{formatVndWhole(item.agreedWage || shift.salary)}</strong>
                      </div>

                      {item.declaredAmount != null && (
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.8rem', color: '#0369a1' }}>
                          <span>Số tiền đã khai:</span>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                            <strong>{formatVndWhole(item.declaredAmount)}</strong>
                            {(isTransfer || isCash) && (
                              <span
                                style={{
                                  padding: '1px 6px',
                                  borderRadius: 4,
                                  fontSize: '0.68rem',
                                  fontWeight: 700,
                                  background: isTransfer ? '#e0e7ff' : '#fef3c7',
                                  color: isTransfer ? '#3730a3' : '#92400e',
                                }}
                              >
                                {isTransfer ? 'Chuyển khoản' : 'Tiền mặt'}
                              </span>
                            )}
                          </div>
                        </div>
                      )}

                      {item.externalRef && (
                        <div style={{ fontSize: '0.74rem', color: '#475569', fontFamily: 'monospace' }}>
                          Mã GD: <strong>{item.externalRef}</strong>
                        </div>
                      )}

                      {item.declaredAt && (
                        <div style={{ fontSize: '0.72rem', color: '#64748b' }}>
                          Khai lúc: {formatTime(item.declaredAt)}
                        </div>
                      )}

                      {item.workerConfirmStatus === 'RECEIVED_PARTIAL' && (
                        <div style={{ background: '#fef3c7', color: '#b45309', padding: '5px 8px', borderRadius: 6, fontSize: '0.75rem' }}>
                          <strong>Worker nhận thiếu:</strong> {item.workerComment || 'Chưa ghi chú'}
                        </div>
                      )}

                      {item.workerConfirmStatus === 'NOT_RECEIVED' && (
                        <div style={{ background: '#fee2e2', color: '#b91c1c', padding: '5px 8px', borderRadius: 6, fontSize: '0.75rem' }}>
                          <strong>Worker chưa nhận được:</strong> {item.workerComment || 'Chưa ghi chú'}
                        </div>
                      )}

                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: '0.2rem' }}>
                        {item.paymentProofUrl ? (
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
                              fontSize: '0.73rem',
                              fontWeight: 700,
                              cursor: 'pointer',
                            }}
                          >
                            <img
                              src={resolveAssetUrl(item.paymentProofUrl)}
                              alt="Biên lai"
                              style={{ width: 20, height: 20, objectFit: 'cover', borderRadius: 4 }}
                            />
                            <span>Xem chứng từ</span>
                          </button>
                        ) : hasDeclared && isCash ? (
                          <span style={{ fontSize: '0.72rem', color: '#64748b', fontStyle: 'italic' }}>
                            Tiền mặt (đã khai báo)
                          </span>
                        ) : null}

                        {workerQrUrl && (
                          <button
                            type="button"
                            onClick={() => setPreviewImage({
                              url: workerQrUrl,
                              title: `Mã VietQR nhận lương của ${item.workerName} (${item.bankCode || ''} ${item.bankAccountNumberMasked || item.bankAccountNumber || ''})`,
                            })}
                            style={{
                              background: '#f5f3ff',
                              color: '#6d28d9',
                              border: '1px solid #ddd6fe',
                              borderRadius: 6,
                              padding: '4px 9px',
                              fontSize: '0.73rem',
                              fontWeight: 700,
                              cursor: 'pointer',
                            }}
                          >
                            Mã QR nhận lương
                          </button>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* VÙNG 3: THANH THAO TÁC NHANH (CHÍNH BÊN TRÁI - PHỤ BÊN PHẢI) */}
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      flexWrap: 'wrap',
                      gap: '0.6rem',
                      padding: '0.7rem 1.15rem',
                      borderTop: '1px solid #f1f5f9',
                      backgroundColor: '#ffffff',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                      {!item.eoConfirmed && !isNoShow && (
                        <button
                          type="button"
                          disabled={!canVerify || verifyingId === item.assignmentId}
                          onClick={() => {
                            const preassignedSkillId = resolvePreassignedPerformedSkillId(item, shift);
                            if (preassignedSkillId) {
                              handleVerify(item.assignmentId, {
                                confirmedPerformedSkillId: preassignedSkillId,
                                confirmPerformedSkill: true,
                              });
                            } else {
                              setSelectedForVerify(item);
                            }
                          }}
                          title={
                            !isCheckedIn
                              ? 'Nhân sự chưa check-in vào ca'
                              : !canVerify
                                ? 'Chưa hoàn tất các đầu việc bắt buộc hoặc còn thiếu minh chứng ảnh'
                                : 'Xác nhận hoàn thành công việc và duyệt công'
                          }
                          style={{
                            background: !canVerify || verifyingId === item.assignmentId ? '#cbd5e1' : '#2563eb',
                            color: !canVerify || verifyingId === item.assignmentId ? '#475569' : '#ffffff',
                            border: 'none',
                            borderRadius: 7,
                            padding: '6px 14px',
                            fontSize: '0.8rem',
                            fontWeight: 700,
                            cursor: !canVerify || verifyingId === item.assignmentId ? 'not-allowed' : 'pointer',
                          }}
                        >
                          {verifyingId === item.assignmentId ? 'Đang duyệt...' : 'Duyệt công'}
                        </button>
                      )}

                      {!item.eoConfirmed && !isNoShow && !canVerify && (
                        <span style={{ fontSize: '0.74rem', color: '#64748b' }}>
                          {!isCheckedIn ? 'Cần check-in trước khi duyệt' : 'Cần đủ đầu việc & ảnh minh chứng'}
                        </span>
                      )}

                      {item.eoConfirmed && (!item.paymentStatus || item.paymentStatus === 'NOT_DECLARED') && (
                        <button
                          type="button"
                          onClick={() => onOpenPaymentDeclaration?.(item)}
                          style={{
                            background: '#059669',
                            color: '#ffffff',
                            border: 'none',
                            borderRadius: 7,
                            padding: '6px 14px',
                            fontSize: '0.8rem',
                            fontWeight: 700,
                            cursor: 'pointer',
                          }}
                        >
                          Thanh toán & QR
                        </button>
                      )}

                      {item.eoConfirmed && item.paymentStatus && item.paymentStatus !== 'NOT_DECLARED' && item.paymentStatus !== 'CONFIRMED_FULL' && (
                        <button
                          type="button"
                          onClick={() => onOpenPaymentDeclaration?.(item)}
                          style={{
                            background: '#f8fafc',
                            color: '#0f172a',
                            border: '1px solid #cbd5e1',
                            borderRadius: 7,
                            padding: '6px 12px',
                            fontSize: '0.78rem',
                            fontWeight: 700,
                            cursor: 'pointer',
                          }}
                        >
                          Cập nhật chứng từ
                        </button>
                      )}

                      {item.eoConfirmed && !isCompleted && (
                        <span style={{ color: '#2563eb', background: '#eff6ff', border: '1px solid #bfdbfe', borderRadius: 6, fontSize: '0.74rem', fontWeight: 700, padding: '4px 9px' }}>
                          Đã duyệt • Chờ kết ca
                        </span>
                      )}

                      {isCompleted && !item.hasReviewedWorker && (
                        <button
                          type="button"
                          onClick={() =>
                            setSelectedForReview((prev) =>
                              prev?.assignmentId === item.assignmentId ? null : item
                            )
                          }
                          style={{
                            background: isReviewExpanded ? '#f1f5f9' : '#fffbeb',
                            color: isReviewExpanded ? '#334155' : '#b45309',
                            border: isReviewExpanded ? '1px solid #cbd5e1' : '1px solid #fcd34d',
                            borderRadius: 7,
                            padding: '6px 12px',
                            fontSize: '0.78rem',
                            fontWeight: 700,
                            cursor: 'pointer',
                          }}
                        >
                          {isReviewExpanded ? 'Đóng form đánh giá' : 'Đánh giá nhân sự'}
                        </button>
                      )}

                      {isCompleted && item.hasReviewedWorker && (
                        <span style={{ color: '#059669', fontSize: '0.78rem', fontWeight: 700 }}>
                          Đã đánh giá
                        </span>
                      )}
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem', flexWrap: 'wrap' }}>
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
                        style={{ background: '#f0fdf4', color: '#15803d', border: '1px solid #bbf7d0', borderRadius: 6, padding: '5px 10px', fontSize: '0.76rem', fontWeight: 600, cursor: 'pointer' }}
                      >
                        Nhắn tin
                      </button>

                      {!item.eoConfirmed && (isCheckedIn || isCompleted) && (
                        <button
                          type="button"
                          onClick={() => handleReportIncomplete(item)}
                          style={{ background: '#fff7ed', color: '#c2410c', border: '1px solid #fed7aa', borderRadius: 6, padding: '5px 10px', fontSize: '0.76rem', fontWeight: 600, cursor: 'pointer' }}
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
                            padding: '5px 10px',
                            fontSize: '0.76rem',
                            fontWeight: 700,
                            cursor: 'pointer',
                          }}
                          title="Báo cáo nhân sự không đến & Tự động tìm người thay thế"
                        >
                          Báo vắng
                        </button>
                      )}

                      <button
                        type="button"
                        onClick={() => handleBlockWorker(item)}
                        style={{ background: '#fef2f2', color: '#dc2626', border: '1px solid #fecaca', borderRadius: 6, padding: '5px 10px', fontSize: '0.76rem', fontWeight: 600, cursor: 'pointer' }}
                      >
                        Chặn
                      </button>
                    </div>
                  </div>

                  {isReviewExpanded && (
                    <div style={{ backgroundColor: '#f8fafc', borderTop: '1px solid #cbd5e1', padding: '1rem 1.15rem' }}>
                      <div style={{ marginBottom: '0.65rem', fontSize: '0.84rem', fontWeight: 700, color: '#0f172a' }}>
                        Đánh giá nhân sự: {item.workerName}
                      </div>
                      <WorkerReviewForm
                        assignment={selectedForReview}
                        submitting={submittingReview}
                        onCancel={() => setSelectedForReview(null)}
                        onSubmitReview={handleSubmitReview}
                        layout="inline"
                      />
                    </div>
                  )}
                </div>
              );
            })}
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
                  {isCheckin ? 'Mã QR Check-in (Vào ca)' : 'Mã QR Check-out (Kết ca)'} — {shift.jobType}
                </h3>
                <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: 2 }}>
                  Khung giờ: {formatTime(shift.startTime)} - {formatTime(shift.endTime)} • Đã vào ca: {checkedInCount}/{shiftConfirmed}
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowQrModal(false)}
                style={{ background: '#f1f5f9', border: 'none', borderRadius: '50%', width: 32, height: 32, fontSize: '1rem', cursor: 'pointer', color: '#475569', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}
              >
                ✕
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

      <ReportNoShowModal
        isOpen={Boolean(selectedForNoShow)}
        onClose={() => setSelectedForNoShow(null)}
        assignment={selectedForNoShow}
        shift={shift}
        onSuccess={async () => {
          const targetId = selectedForNoShow?.assignmentId;
          if (targetId) {
            setAttendanceList((prev) =>
              prev.map((w) => (w.assignmentId === targetId ? { ...w, assignmentStatus: 'NO_SHOW' } : w))
            );
          }
          await Promise.allSettled([
            loadAttendance({ silent: true }),
            onReloadEvents ? Promise.resolve(onReloadEvents({ silent: true })) : Promise.resolve(),
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
