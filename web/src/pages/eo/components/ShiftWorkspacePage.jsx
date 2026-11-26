import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { createPortal } from 'react-dom';
import { QRCodeSVG } from 'qrcode.react';
import { eoService } from '../../../services/eoService';
import { blockListService } from '../../../services/blockListService';
import { showToast } from '../../../components/common/Toast';
import { showConfirm, showPrompt } from '../../../components/common/PromptModal';
import { getDynamicShiftStatus, getShiftStaffingSummary, formatVndWhole, roundVndAmount } from '../utils/eventHelpers';
import ConfirmVerifySkillModal, { resolvePreassignedPerformedSkillId } from './ConfirmVerifySkillModal';
import ReportNoShowModal from './ReportNoShowModal';
import ShiftEmergencyBanner from './ShiftEmergencyBanner';
import EmergencyTriggerButton from './EmergencyTriggerButton';
import { WorkerProfileModal } from './worker-profile';
import { useWorkerProfile } from '../hooks/useWorkerProfile';
import { resolveAssetUrl } from '../utils/resolveAssetUrl';
import WorkerDetailDrawer from './shift-workspace/WorkerDetailDrawer';
import QuickEvidenceReviewModal from './shift-workspace/QuickEvidenceReviewModal';
import {
  canEoConfirmWorker,
  formatTimeShort,
  getSourceLabel,
  getWorkerAttentionFlags,
  getWorkerChecklistSummary,
  getWorkerPriorityRank,
  parseShiftChecklistTasks,
} from './shift-workspace/shiftWorkspaceHelpers';
import './EOEventsTab.css';

/**
 * ShiftWorkspacePage (Master-Detail + Bulk Operations for 40+ Workers)
 * - Compact 6-column table with pagination & smart exception-first sorting
 * - Slide-over WorkerDetailDrawer for full checklist, payment, review, and moderation
 * - QuickEvidenceReviewModal for rapid photo inspection + approve-and-next
 * - Bulk verify + Speed Pay Queue + Bulk Cash declaration (100% Frontend-only over existing APIs)
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
  onOpenPaymentQueue,
  skillTaxonomy = [],
  targetAssignmentId = null,
}) {
  const [attendanceList, setAttendanceList] = useState([]);
  const [loadingAttendance, setLoadingAttendance] = useState(false);
  const [refreshingAttendance, setRefreshingAttendance] = useState(false);
  const [verifyingId, setVerifyingId] = useState(null);
  const [selectedForVerify, setSelectedForVerify] = useState(null);
  const [selectedForNoShow, setSelectedForNoShow] = useState(null);
  const [submittingReview, setSubmittingReview] = useState(false);

  const [searchWorker, setSearchWorker] = useState('');
  const [workerFilter, setWorkerFilter] = useState('ALL');
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(15);
  const [selectedIds, setSelectedIds] = useState([]);
  const [bulkProgress, setBulkProgress] = useState(null);

  const [drawerWorkerId, setDrawerWorkerId] = useState(null);
  const [evidenceQueueState, setEvidenceQueueState] = useState({ isOpen: false, initialIndex: 0 });
  const [highlightedAssignmentId, setHighlightedAssignmentId] = useState(null);
  const workerProfile = useWorkerProfile();

  // QR Attendance Modal
  const [showQrModal, setShowQrModal] = useState(false);
  const [qrMode, setQrMode] = useState('CHECKIN');
  const ROTATE_INTERVAL = 20;
  const [countdown, setCountdown] = useState(ROTATE_INTERVAL);
  const [refreshNonce, setRefreshNonce] = useState(Date.now());
  const [serverQrData, setServerQrData] = useState(null);
  const [previewImage, setPreviewImage] = useState(null);

  const loadAttendance = useCallback(async ({ silent = false } = {}) => {
    if (!shift?.shiftId) return;
    if (silent) setRefreshingAttendance(true);
    else setLoadingAttendance(true);
    try {
      const data = await eoService.getShiftAttendance(shift.shiftId);
      setAttendanceList(Array.isArray(data) ? data : []);
    } catch (e) {
      console.warn('Lỗi khi tải danh sách điểm danh ca:', e);
      if (!silent) setAttendanceList([]);
    } finally {
      if (silent) setRefreshingAttendance(false);
      else setLoadingAttendance(false);
    }
  }, [shift?.shiftId]);

  useEffect(() => {
    if (shift?.shiftId) {
      setSelectedIds([]);
      setDrawerWorkerId(null);
      setCurrentPage(1);
      loadAttendance({ silent: false });
    }
  }, [shift?.shiftId, loadAttendance]);

  useEffect(() => {
    const handlePaymentUpdated = () => {
      loadAttendance({ silent: true });
      if (onReloadEvents) {
        Promise.resolve(onReloadEvents({ silent: true })).catch(() => {});
      }
    };
    window.addEventListener('cani_payment_updated', handlePaymentUpdated);
    return () => window.removeEventListener('cani_payment_updated', handlePaymentUpdated);
  }, [loadAttendance, onReloadEvents]);

  useEffect(() => {
    if (targetAssignmentId && attendanceList.length > 0) {
      setHighlightedAssignmentId(targetAssignmentId);
      setWorkerFilter('ALL');
      setDrawerWorkerId(targetAssignmentId);
      setTimeout(() => {
        document.getElementById(`assignment-row-${targetAssignmentId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }, 200);
      const timer = setTimeout(() => setHighlightedAssignmentId(null), 4000);
      return () => clearTimeout(timer);
    }
    return undefined;
  }, [targetAssignmentId, attendanceList]);

  useEffect(() => {
    setCurrentPage(1);
    setSelectedIds([]);
  }, [workerFilter, searchWorker, pageSize]);

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
    if (showQrModal && shift?.shiftId) fetchLiveQr();
  }, [showQrModal, shift?.shiftId, refreshNonce, fetchLiveQr]);

  useEffect(() => {
    if (!showQrModal || !shift?.shiftId) return undefined;
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

  const shiftChecklistTasks = useMemo(() => parseShiftChecklistTasks(shift?.checklist), [shift?.checklist]);

  const skillNames = useMemo(() => {
    if (!Array.isArray(shift?.requiredSkills) || shift.requiredSkills.length === 0) return [];
    const m = {};
    (Array.isArray(skillTaxonomy) ? skillTaxonomy : []).forEach((sk) => {
      if (sk?.skillId) m[sk.skillId] = sk.name;
    });
    return shift.requiredSkills.map((id) => m[id]).filter(Boolean);
  }, [shift?.requiredSkills, skillTaxonomy]);

  const drawerItem = useMemo(
    () => attendanceList.find((w) => w.assignmentId === drawerWorkerId) || null,
    [attendanceList, drawerWorkerId]
  );

  const workerFilterCounts = useMemo(() => {
    let NEED_ATTENTION = 0;
    let NEED_VERIFY = 0;
    let NEED_PAYMENT = 0;
    let SETTLED = 0;
    attendanceList.forEach((w) => {
      const flags = getWorkerAttentionFlags(w);
      if (flags.needsAttention) NEED_ATTENTION += 1;
      if (!flags.isNoShow && (flags.isCheckedIn || flags.isCompleted) && !w.eoConfirmed) NEED_VERIFY += 1;
      if (!flags.isNoShow && Boolean(w.eoConfirmed) && w.paymentStatus !== 'CONFIRMED_FULL') NEED_PAYMENT += 1;
      if (w.paymentStatus === 'CONFIRMED_FULL') SETTLED += 1;
    });
    return {
      ALL: attendanceList.length,
      NEED_ATTENTION,
      NEED_VERIFY,
      NEED_PAYMENT,
      SETTLED,
    };
  }, [attendanceList]);

  const filteredWorkers = useMemo(() => {
    const q = searchWorker.trim().toLowerCase();
    return attendanceList
      .filter((w) => {
        const flags = getWorkerAttentionFlags(w);
        if (workerFilter === 'NEED_ATTENTION' && !flags.needsAttention) return false;
        if (workerFilter === 'NEED_VERIFY' && (flags.isNoShow || (!flags.isCheckedIn && !flags.isCompleted) || w.eoConfirmed)) return false;
        if (workerFilter === 'NEED_PAYMENT' && (flags.isNoShow || !w.eoConfirmed || w.paymentStatus === 'CONFIRMED_FULL')) return false;
        if (workerFilter === 'SETTLED' && w.paymentStatus !== 'CONFIRMED_FULL') return false;
        if (!q) return true;
        return (
          (w.workerName || '').toLowerCase().includes(q) ||
          (w.workerPhone || '').toLowerCase().includes(q)
        );
      })
      .sort((a, b) => getWorkerPriorityRank(a, shiftChecklistTasks) - getWorkerPriorityRank(b, shiftChecklistTasks));
  }, [attendanceList, workerFilter, searchWorker, shiftChecklistTasks]);

  const effectivePageSize = pageSize === 'ALL' ? Math.max(1, filteredWorkers.length) : Number(pageSize) || 15;
  const totalPages = Math.max(1, Math.ceil(filteredWorkers.length / effectivePageSize));
  const safePage = Math.min(Math.max(1, currentPage), totalPages);
  const startIdx = (safePage - 1) * effectivePageSize;
  const paginatedWorkers = filteredWorkers.slice(startIdx, startIdx + effectivePageSize);

  const verifyQueueWorkers = useMemo(
    () =>
      filteredWorkers.filter((w) => {
        const flags = getWorkerAttentionFlags(w);
        return !flags.isNoShow && (flags.isCheckedIn || flags.isCompleted) && !w.eoConfirmed;
      }),
    [filteredWorkers]
  );

  const unpaidQueueWorkers = useMemo(
    () =>
      filteredWorkers.filter(
        (w) =>
          w.assignmentStatus !== 'NO_SHOW' &&
          Boolean(w.eoConfirmed) &&
          (!w.paymentStatus || w.paymentStatus === 'NOT_DECLARED')
      ),
    [filteredWorkers]
  );

  if (!shift || !event) return null;

  const dynStatus = getDynamicShiftStatus(shift, event.status);
  const staffingSummary = getShiftStaffingSummary(shift, event);
  const shiftConfirmed = staffingSummary.confirmed;
  const shiftRequired = staffingSummary.required;
  const fillRate = Math.round((shiftConfirmed / shiftRequired) * 100);
  const isShortage = dynStatus.isCritical || dynStatus.isEmergency || dynStatus.isUrgent || shiftConfirmed < shiftRequired;

  const checkedInCount = attendanceList.filter((a) => a.assignmentStatus === 'CHECKED_IN' || a.checkinTime != null).length;
  const completedCount = attendanceList.filter((a) => a.assignmentStatus === 'COMPLETED' || a.checkoutTime != null).length;
  const paidCount = attendanceList.filter((a) => a.paymentStatus === 'CONFIRMED_FULL').length;
  const pendingPayCount = attendanceList.filter((a) => a.paymentStatus === 'DECLARED_PENDING_WORKER').length;
  const allShifts = (event.shifts || []).filter((s) => s.status !== 'CANCELLED');

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
      return true;
    } catch (e) {
      showToast('Không thể duyệt công: ' + (e.response?.data?.message || e.message), 'error');
      return false;
    } finally {
      setVerifyingId(null);
    }
  };

  const triggerSingleVerify = (item) => {
    const preassignedSkillId = resolvePreassignedPerformedSkillId(item, shift);
    if (preassignedSkillId) {
      return handleVerify(item.assignmentId, {
        confirmedPerformedSkillId: preassignedSkillId,
        confirmPerformedSkill: true,
      });
    }
    setSelectedForVerify(item);
    return false;
  };

  const handleReportIncomplete = async (item) => {
    const reason = await showPrompt({
      title: 'Báo cáo chưa hoàn thành công việc',
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
      await eoService.reportIncompleteWork(item.assignmentId, { reason: reason.trim() });
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
      setDrawerWorkerId(null);
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
        prev.map((w) => (w.assignmentId === reviewData.assignmentId ? { ...w, hasReviewedWorker: true } : w))
      );
      showToast('Đã gửi đánh giá nhân sự thành công.', 'success');
      loadAttendance({ silent: true });
    } catch (e) {
      showToast('Không thể gửi đánh giá: ' + (e.response?.data?.message || e.message), 'error');
    } finally {
      setSubmittingReview(false);
    }
  };

  const handleMessageWorker = (item) => {
    const targetUserId = item.userId || item.workerId;
    window.dispatchEvent(
      new CustomEvent('cani_open_direct_chat', {
        detail: targetUserId ? { targetUserId, shiftId: shift.shiftId } : { shiftId: shift.shiftId },
      })
    );
  };

  // Selection & Bulk Actions (chỉ hiện checkbox khi có nhân sự đủ điều kiện duyệt công hoặc chờ khai báo lương)
  const isWorkerBulkEligible = (w) =>
    canEoConfirmWorker(w, shiftChecklistTasks) ||
    (w.assignmentStatus !== 'NO_SHOW' && Boolean(w.eoConfirmed) && (!w.paymentStatus || w.paymentStatus === 'NOT_DECLARED'));

  const bulkEligibleOnPage = paginatedWorkers.filter(isWorkerBulkEligible);
  const showBulkCheckboxColumn = bulkEligibleOnPage.length > 0;

  const selectedWorkers = attendanceList.filter((w) => selectedIds.includes(w.assignmentId));
  const bulkVerifyCandidates = selectedWorkers.filter((w) => canEoConfirmWorker(w, shiftChecklistTasks));
  const bulkCashCandidates = selectedWorkers.filter(
    (w) => w.assignmentStatus !== 'NO_SHOW' && Boolean(w.eoConfirmed) && (!w.paymentStatus || w.paymentStatus === 'NOT_DECLARED')
  );

  const allVisibleSelected =
    bulkEligibleOnPage.length > 0 && bulkEligibleOnPage.every((w) => selectedIds.includes(w.assignmentId));

  const toggleSelectAllVisible = () => {
    const visibleIds = bulkEligibleOnPage.map((w) => w.assignmentId);
    if (allVisibleSelected) {
      setSelectedIds((prev) => prev.filter((id) => !visibleIds.includes(id)));
    } else {
      setSelectedIds((prev) => Array.from(new Set([...prev, ...visibleIds])));
    }
  };

  const toggleSelectOne = (assignmentId) => {
    setSelectedIds((prev) =>
      prev.includes(assignmentId) ? prev.filter((id) => id !== assignmentId) : [...prev, assignmentId]
    );
  };

  const handleBulkVerify = async () => {
    if (bulkVerifyCandidates.length === 0) {
      showToast('Không có nhân sự nào trong nhóm đã chọn đủ điều kiện duyệt công.', 'warning');
      return;
    }
    const readyList = bulkVerifyCandidates.filter((w) => Boolean(resolvePreassignedPerformedSkillId(w, shift)));
    const missingSkillCount = bulkVerifyCandidates.length - readyList.length;

    if (readyList.length === 0) {
      showToast('Ca này chưa cấu hình kỹ năng mặc định; vui lòng duyệt từng người để chọn kỹ năng thực hiện.', 'warning');
      setSelectedForVerify(bulkVerifyCandidates[0]);
      return;
    }

    const ok = await showConfirm({
      title: `Duyệt công hàng loạt (${readyList.length} nhân sự)`,
      description:
        missingSkillCount > 0
          ? `Xác nhận duyệt công cho ${readyList.length} nhân sự đủ điều kiện (${missingSkillCount} người chưa có kỹ năng mặc định sẽ được bỏ qua).`
          : `Xác nhận duyệt hoàn thành đầu việc cho ${readyList.length} nhân sự đã chọn?`,
      confirmText: `Duyệt ${readyList.length} người`,
    });
    if (!ok) return;

    let successCount = 0;
    for (let i = 0; i < readyList.length; i += 1) {
      const target = readyList[i];
      setBulkProgress({ label: 'Đang duyệt công', current: i + 1, total: readyList.length });
      try {
        const skillId = resolvePreassignedPerformedSkillId(target, shift);
        await eoService.verifyAttendance(target.assignmentId, {
          confirmedPerformedSkillId: skillId,
          confirmPerformedSkill: true,
        });
        successCount += 1;
      } catch {
        // continue remaining items
      }
    }
    setBulkProgress(null);
    setSelectedIds([]);
    showToast(`Đã duyệt công thành công ${successCount}/${readyList.length} nhân sự.`, successCount > 0 ? 'success' : 'error');
    await Promise.allSettled([
      loadAttendance({ silent: true }),
      onReloadEvents ? Promise.resolve(onReloadEvents({ silent: true })) : Promise.resolve(),
    ]);
  };

  const handleBulkCashPayment = async () => {
    if (bulkCashCandidates.length === 0) {
      showToast('Không có nhân sự nào đã duyệt công và chưa khai báo lương trong nhóm đã chọn.', 'warning');
      return;
    }
    const ok = await showConfirm({
      title: `Khai báo trả tiền mặt hàng loạt (${bulkCashCandidates.length} người)`,
      description: `Hệ thống sẽ ghi nhận đã trả tiền mặt đúng mức thù lao thỏa thuận cho ${bulkCashCandidates.length} nhân sự và gửi thông báo xác nhận tới họ.`,
      confirmText: `Xác nhận trả ${bulkCashCandidates.length} người`,
    });
    if (!ok) return;

    let successCount = 0;
    for (let i = 0; i < bulkCashCandidates.length; i += 1) {
      const target = bulkCashCandidates[i];
      setBulkProgress({ label: 'Đang khai báo trả tiền mặt', current: i + 1, total: bulkCashCandidates.length });
      try {
        await eoService.declarePayment({
          assignmentId: target.assignmentId,
          declaredAmount: roundVndAmount(target.agreedWage ?? target.agreedAmount ?? shift.salary ?? 200000),
          paymentMethod: 'CASH',
          externalRef: `CASH-BULK-${Date.now().toString().slice(-5)}-${i + 1}`,
          paymentProofUrl: '',
        });
        successCount += 1;
      } catch {
        // continue remaining items
      }
    }
    setBulkProgress(null);
    setSelectedIds([]);
    window.dispatchEvent(new CustomEvent('cani_payment_updated', { detail: { bulk: true } }));
    showToast(`Đã khai báo tiền mặt ${successCount}/${bulkCashCandidates.length} nhân sự.`, successCount > 0 ? 'success' : 'error');
    await loadAttendance({ silent: true });
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

  // Stacked progress bar percentages
  const safeTotalWorkers = Math.max(1, attendanceList.length);
  const pctSettled = Math.round((paidCount / safeTotalWorkers) * 100);
  const pctWaitPay = Math.round((workerFilterCounts.NEED_PAYMENT / safeTotalWorkers) * 100);
  const pctWaitVerify = Math.round((workerFilterCounts.NEED_VERIFY / safeTotalWorkers) * 100);
  const pctAttention = Math.max(0, 100 - pctSettled - pctWaitPay - pctWaitVerify);

  return (
    <div className="shift-workspace-container animate-fade-in-up" style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
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
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <button
            type="button"
            onClick={onBackToEvent}
            className="eo-btn-secondary"
            style={{ padding: '0.45rem 0.9rem', fontSize: '0.84rem' }}
          >
            ← Quay lại "{event.name}"
          </button>

          {allShifts.length > 1 && onSelectShift && (
            <select
              value={shift.shiftId}
              onChange={(e) => {
                const found = allShifts.find((s) => s.shiftId === e.target.value);
                if (found) onSelectShift(found);
              }}
              className="table-filter-select"
              aria-label="Chuyển ca làm việc"
            >
              {allShifts.map((s) => (
                <option key={s.shiftId} value={s.shiftId}>
                  {s.jobType} ({formatTimeShort(s.startTime)} - {formatTimeShort(s.endTime)})
                </option>
              ))}
            </select>
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
          <button
            type="button"
            className="eo-btn-primary"
            style={{ padding: '0.48rem 1rem', fontSize: '0.84rem' }}
            onClick={() => {
              setQrMode('CHECKIN');
              setShowQrModal(true);
            }}
          >
            Mở QR Check-in
          </button>

          <button
            type="button"
            className="eo-btn-secondary"
            style={{ padding: '0.48rem 0.95rem', fontSize: '0.84rem', color: '#1d4ed8', borderColor: '#bfdbfe' }}
            onClick={() => {
              setQrMode('CHECKOUT');
              setShowQrModal(true);
            }}
          >
            Mở QR Check-out
          </button>

          {shift.emergency && staffingSummary.missing > 0 ? (
            <span className="table-status-pill red">
              <span className="status-dot-solid" />
              {staffingSummary.hasCandidates ? 'Đang điều phối khẩn cấp' : 'Đã bật điều phối'}
            </span>
          ) : (
            <EmergencyTriggerButton shift={shift} event={event} />
          )}

          {onOpenInvite && (
            <button
              type="button"
              className="eo-btn-secondary"
              style={{ padding: '0.48rem 0.9rem', fontSize: '0.82rem' }}
              onClick={() => onOpenInvite(shift, event)}
            >
              Mã Mời
            </button>
          )}

          {onEditShift && (
            <button
              type="button"
              className="eo-btn-secondary"
              style={{ padding: '0.48rem 0.9rem', fontSize: '0.82rem' }}
              onClick={() => onEditShift(shift)}
            >
              Sửa Ca
            </button>
          )}
        </div>
      </div>

      <ShiftEmergencyBanner shift={shift} event={event} onOpenInvite={onOpenInvite} />

      {/* 2. TỔNG QUAN CA + THANH TIẾN ĐỘ PHÂN ĐOẠN */}
      <div className="eo-widget-card" style={{ padding: '1.15rem 1.35rem', display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '0.75rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' }}>
              <h2 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 800, color: '#0f172a' }}>
                Ca: {shift.jobType}
              </h2>
              <span className={`table-status-pill ${dynStatus.colorClass || 'blue'}`}>
                <span className="status-dot-solid" />
                {dynStatus.label}
              </span>
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1rem', fontSize: '0.82rem', color: '#475569', marginTop: '0.35rem' }}>
              <span>Giờ: <strong>{formatTimeShort(shift.startTime)} – {formatTimeShort(shift.endTime)}</strong></span>
              <span>
                Thù lao: <strong style={{ color: '#0f172a' }}>{new Intl.NumberFormat('vi-VN').format(shift.hourlyWage || 50000)} đ/giờ</strong>
                {shift.allowance > 0 && ` (+${new Intl.NumberFormat('vi-VN').format(shift.allowance)} đ)`}
              </span>
              <span>
                Chốt ca: <strong style={{ color: isShortage ? '#dc2626' : '#0f172a' }}>{shiftConfirmed}/{shiftRequired} ({fillRate}%)</strong>
              </span>
              {skillNames.length > 0 && <span>Kỹ năng: <strong>{skillNames.join(', ')}</strong></span>}
            </div>
          </div>

          <button
            type="button"
            className="eo-btn-secondary"
            style={{ fontSize: '0.8rem', padding: '0.4rem 0.85rem' }}
            disabled={refreshingAttendance}
            onClick={() => {
              loadAttendance({ silent: true });
              onReloadEvents?.({ silent: true });
            }}
          >
            {refreshingAttendance ? 'Đang đồng bộ...' : 'Làm mới số liệu'}
          </button>
        </div>

        {/* Stacked Progress Bar (Clickable filters for 40+ workers) */}
        {attendanceList.length > 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
            <div style={{ display: 'flex', height: 8, borderRadius: 999, overflow: 'hidden', background: '#f1f5f9', gap: 1 }}>
              {pctSettled > 0 && <div style={{ width: `${pctSettled}%`, background: '#10b981' }} title="Đã quyết toán" />}
              {pctWaitPay > 0 && <div style={{ width: `${pctWaitPay}%`, background: '#2563eb' }} title="Chờ thanh toán" />}
              {pctWaitVerify > 0 && <div style={{ width: `${pctWaitVerify}%`, background: '#f59e0b' }} title="Chờ duyệt công" />}
              {pctAttention > 0 && <div style={{ width: `${pctAttention}%`, background: '#cbd5e1' }} title="Chưa vào ca / Vắng" />}
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.9rem', fontSize: '0.76rem', color: '#475569' }}>
              <span>● Đã vào ca: <strong>{checkedInCount}</strong> ({completedCount} kết ca)</span>
              <span style={{ color: workerFilterCounts.NEED_ATTENTION > 0 ? '#b45309' : '#64748b' }}>
                ● Cần chú ý đầu ca: <strong>{workerFilterCounts.NEED_ATTENTION}</strong>
              </span>
              <span>● Chờ duyệt công: <strong>{workerFilterCounts.NEED_VERIFY}</strong></span>
              <span>● Chờ thanh toán: <strong>{workerFilterCounts.NEED_PAYMENT}</strong> ({pendingPayCount} chờ xác nhận)</span>
              <span style={{ color: '#059669' }}>● Đã trả đủ: <strong>{paidCount}</strong></span>
            </div>
          </div>
        )}
      </div>

      {/* 3. BẢNG QUẢN LÝ NHÂN SỰ GỌN (TỐI ƯU CHO 40+ WORKER) */}
      <section className="eo-events-section-card">
        <div className="table-card-header-bar">
          <div className="table-header-title-group">
            <h3 className="table-card-heading">Danh sách nhân sự ca ({filteredWorkers.length})</h3>
            <span className="ev-result-count">Tổng {attendanceList.length} người</span>
          </div>

          <div className="table-header-actions-group" style={{ gap: '0.5rem' }}>
            {verifyQueueWorkers.length > 0 && (
              <button
                type="button"
                className="eo-btn-secondary"
                style={{ minHeight: '38px', fontSize: '0.8rem', padding: '0 0.85rem', borderRadius: 8, borderColor: '#bfdbfe', color: '#1d4ed8' }}
                onClick={() => setEvidenceQueueState({ isOpen: true, initialIndex: 0 })}
              >
                Rà minh chứng nhanh ({verifyQueueWorkers.length})
              </button>
            )}

            {unpaidQueueWorkers.length > 0 && onOpenPaymentQueue && (
              <button
                type="button"
                className="eo-btn-primary"
                style={{ minHeight: '38px', fontSize: '0.8rem', padding: '0 0.9rem', borderRadius: 8 }}
                onClick={() => onOpenPaymentQueue(unpaidQueueWorkers)}
              >
                Thanh toán nối tiếp ({unpaidQueueWorkers.length})
              </button>
            )}

            <input
              type="text"
              value={searchWorker}
              onChange={(e) => setSearchWorker(e.target.value)}
              placeholder="Tìm tên hoặc SĐT..."
              className="table-search-input"
              style={{ minHeight: '38px', height: '38px' }}
              aria-label="Tìm nhân sự trong ca"
            />

            <select
              value={pageSize}
              onChange={(e) => setPageSize(e.target.value === 'ALL' ? 'ALL' : Number(e.target.value))}
              className="table-filter-select"
              style={{ minHeight: '38px', height: '38px' }}
              aria-label="Số dòng mỗi trang"
            >
              <option value={15}>15 / trang</option>
              <option value={30}>30 / trang</option>
              <option value="ALL">Tất cả</option>
            </select>
          </div>
        </div>

        {/* Bộ lọc trạng thái */}
        {attendanceList.length > 0 && (
          <div className="ev-filter-chips-bar" role="tablist" aria-label="Lọc nhân sự theo tiến độ">
            {[
              { key: 'ALL', label: `Tất cả (${workerFilterCounts.ALL})` },
              { key: 'NEED_ATTENTION', label: `Cần chú ý / Đầu ca (${workerFilterCounts.NEED_ATTENTION})` },
              { key: 'NEED_VERIFY', label: `Chờ duyệt công (${workerFilterCounts.NEED_VERIFY})` },
              { key: 'NEED_PAYMENT', label: `Chờ thanh toán (${workerFilterCounts.NEED_PAYMENT})` },
              { key: 'SETTLED', label: `Đã quyết toán (${workerFilterCounts.SETTLED})` },
            ].map((tab) => (
              <button
                key={tab.key}
                type="button"
                role="tab"
                aria-selected={workerFilter === tab.key}
                className={`ev-filter-chip ${workerFilter === tab.key ? 'is-active' : ''}`}
                onClick={() => setWorkerFilter(tab.key)}
              >
                {tab.label}
              </button>
            ))}
          </div>
        )}

        {/* Thanh Thao Tác Hàng Loạt khi đã tích Checkbox */}
        {(selectedIds.length > 0 || bulkProgress) && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: '0.65rem',
              padding: '0.7rem 1.25rem',
              background: '#eff6ff',
              borderBottom: '1px solid #bfdbfe',
              fontSize: '0.82rem',
            }}
          >
            {bulkProgress ? (
              <strong style={{ color: '#1d4ed8' }}>
                {bulkProgress.label}: {bulkProgress.current}/{bulkProgress.total}...
              </strong>
            ) : (
              <>
                <span style={{ color: '#1e40af', fontWeight: 700 }}>
                  Đã chọn {selectedIds.length} nhân sự
                </span>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                  {bulkVerifyCandidates.length > 0 && (
                    <button
                      type="button"
                      className="eo-btn-primary"
                      style={{ fontSize: '0.78rem', padding: '0.38rem 0.85rem' }}
                      onClick={handleBulkVerify}
                    >
                      Duyệt công đã chọn ({bulkVerifyCandidates.length})
                    </button>
                  )}
                  {bulkCashCandidates.length > 0 && (
                    <button
                      type="button"
                      className="eo-btn-secondary"
                      style={{ fontSize: '0.78rem', padding: '0.38rem 0.85rem', borderColor: '#bfdbfe', color: '#1d4ed8' }}
                      onClick={handleBulkCashPayment}
                    >
                      Khai báo tiền mặt ({bulkCashCandidates.length})
                    </button>
                  )}
                  {bulkCashCandidates.length > 1 && onOpenPaymentQueue && (
                    <button
                      type="button"
                      className="eo-btn-secondary"
                      style={{ fontSize: '0.78rem', padding: '0.38rem 0.85rem' }}
                      onClick={() => onOpenPaymentQueue(bulkCashCandidates)}
                    >
                      Trả nối tiếp VietQR ({bulkCashCandidates.length})
                    </button>
                  )}
                  <button
                    type="button"
                    className="eo-btn-secondary"
                    style={{ fontSize: '0.78rem', padding: '0.38rem 0.75rem' }}
                    onClick={() => setSelectedIds([])}
                  >
                    Bỏ chọn
                  </button>
                </div>
              </>
            )}
          </div>
        )}

        <div className="table-responsive-wrapper">
          {loadingAttendance && attendanceList.length === 0 ? (
            <div style={{ padding: '3rem 1rem', textAlign: 'center', color: '#64748b' }}>
              Đang tải dữ liệu nhân sự và tiến độ ca làm việc...
            </div>
          ) : filteredWorkers.length === 0 ? (
            <div className="eo-empty-state-card">
              <p className="empty-text">
                {attendanceList.length === 0
                  ? `Chưa có nhân sự nào trong ca "${shift.jobType}".`
                  : 'Không có nhân sự phù hợp bộ lọc hiện tại.'}
              </p>
              {attendanceList.length > 0 ? (
                <button
                  type="button"
                  className="eo-btn-secondary"
                  onClick={() => {
                    setWorkerFilter('ALL');
                    setSearchWorker('');
                  }}
                >
                  Xem tất cả nhân sự
                </button>
              ) : (
                onOpenInvite && (
                  <button type="button" className="eo-btn-primary" onClick={() => onOpenInvite(shift, event)}>
                    Lấy Liên Kết / Mã Mời
                  </button>
                )
              )}
            </div>
          ) : (
            <table className="cani-modern-table">
              <thead>
                <tr>
                  {showBulkCheckboxColumn && (
                    <th style={{ width: '44px', textAlign: 'center' }}>
                      <input
                        type="checkbox"
                        checked={allVisibleSelected}
                        onChange={toggleSelectAllVisible}
                        title="Chọn tất cả nhân sự có thể duyệt công / trả lương hàng loạt trên trang này"
                        aria-label="Chọn tất cả nhân sự đủ điều kiện trên trang"
                        style={{ width: 15, height: 15, cursor: 'pointer' }}
                      />
                    </th>
                  )}
                  <th style={{ width: showBulkCheckboxColumn ? '24%' : '26%' }}>Nhân sự</th>
                  <th style={{ width: '19%' }}>Điểm danh</th>
                  <th style={{ width: '19%' }}>Checklist &amp; Ảnh</th>
                  <th style={{ width: '18%' }}>Thanh toán</th>
                  <th style={{ width: showBulkCheckboxColumn ? '16%' : '18%', textAlign: 'right' }}>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {paginatedWorkers.map((item) => {
                  const flags = getWorkerAttentionFlags(item);
                  const canVerify = canEoConfirmWorker(item, shiftChecklistTasks);
                  const chkSummary = getWorkerChecklistSummary(item, shiftChecklistTasks);
                  const sourceLabel = getSourceLabel(item.source);
                  const isChecked = selectedIds.includes(item.assignmentId);
                  const isHighlighted = highlightedAssignmentId === item.assignmentId;

                  const attendanceStatus = flags.isNoShow
                    ? { pill: 'red', text: 'Vắng mặt' }
                    : flags.isCompleted
                      ? { pill: 'green', text: 'Đã kết ca' }
                      : flags.isCheckedIn
                        ? { pill: flags.isLate ? 'yellow' : 'blue', text: flags.isLate ? 'Trễ giờ' : 'Đúng giờ' }
                        : { pill: 'yellow', text: 'Chưa vào ca' };

                  const paymentStatusInfo =
                    item.workerConfirmStatus === 'RECEIVED_FULL' || item.paymentStatus === 'CONFIRMED_FULL'
                      ? { pill: 'green', text: 'Đã trả đủ' }
                      : flags.hasPaymentIssue
                        ? { pill: 'red', text: 'Cần đối soát' }
                        : item.paymentStatus === 'DECLARED_PENDING_WORKER'
                          ? { pill: 'blue', text: 'Chờ xác nhận' }
                          : item.eoConfirmed
                            ? { pill: 'yellow', text: 'Chờ trả lương' }
                            : { pill: 'gray', text: 'Chờ duyệt công' };

                  const verifyQueueIdx = verifyQueueWorkers.findIndex((w) => w.assignmentId === item.assignmentId);

                  const isRowBulkEligible = isWorkerBulkEligible(item);

                  return (
                    <tr
                      key={item.assignmentId}
                      id={`assignment-row-${item.assignmentId}`}
                      className="event-table-row"
                      style={{
                        backgroundColor: isHighlighted ? '#eff6ff' : isChecked ? '#f8fafc' : undefined,
                        cursor: 'pointer',
                      }}
                      onClick={() => setDrawerWorkerId(item.assignmentId)}
                    >
                      {showBulkCheckboxColumn && (
                        <td style={{ textAlign: 'center' }} onClick={(e) => e.stopPropagation()}>
                          {isRowBulkEligible && (
                            <input
                              type="checkbox"
                              checked={isChecked}
                              onChange={() => toggleSelectOne(item.assignmentId)}
                              title="Chọn để duyệt công hoặc khai báo tiền mặt hàng loạt"
                              aria-label={`Chọn ${item.workerName}`}
                              style={{ width: 15, height: 15, cursor: 'pointer' }}
                            />
                          )}
                        </td>
                      )}

                      <td>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                setDrawerWorkerId(item.assignmentId);
                              }}
                              style={{
                                background: 'none',
                                border: 'none',
                                padding: 0,
                                fontWeight: 700,
                                color: '#0f172a',
                                cursor: 'pointer',
                                fontSize: '0.88rem',
                                textAlign: 'left',
                              }}
                            >
                              {item.workerName}
                            </button>
                            {sourceLabel && (
                              <span style={{ fontSize: '0.68rem', color: '#64748b', background: '#f1f5f9', padding: '1px 6px', borderRadius: 4, fontWeight: 600 }}>
                                {sourceLabel}
                              </span>
                            )}
                          </div>
                          <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                            {item.workerPhone || 'Chưa có SĐT'} • {item.workerReliabilityScore || 100} uy tín
                          </span>
                        </div>
                      </td>

                      <td>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 3, alignItems: 'flex-start' }}>
                          <span className={`table-status-pill ${attendanceStatus.pill}`}>
                            <span className="status-dot-solid" />
                            {attendanceStatus.text}
                          </span>
                          <span style={{ fontSize: '0.73rem', color: '#64748b' }}>
                            {formatTimeShort(item.checkinTime)} – {formatTimeShort(item.checkoutTime)}
                          </span>
                        </div>
                      </td>

                      <td onClick={(e) => e.stopPropagation()}>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 3, alignItems: 'flex-start' }}>
                          <span style={{ fontSize: '0.8rem', fontWeight: 600, color: '#0f172a' }}>
                            {shiftChecklistTasks.length > 0
                              ? `${chkSummary.done}/${chkSummary.total} đầu việc`
                              : item.isChecklistCompleted
                                ? 'Đã xong'
                                : 'Không có mẫu'}
                          </span>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                            {chkSummary.evidenceItems.length > 0 && (
                              <button
                                type="button"
                                onClick={() => {
                                  if (verifyQueueIdx >= 0) {
                                    setEvidenceQueueState({ isOpen: true, initialIndex: verifyQueueIdx });
                                  } else {
                                    setPreviewImage({
                                      url: chkSummary.evidenceItems[0].url,
                                      title: `${chkSummary.evidenceItems[0].label} - ${item.workerName}`,
                                    });
                                  }
                                }}
                                style={{
                                  background: '#eff6ff',
                                  color: '#1d4ed8',
                                  border: '1px solid #bfdbfe',
                                  borderRadius: 4,
                                  padding: '1px 6px',
                                  fontSize: '0.71rem',
                                  fontWeight: 700,
                                  cursor: 'pointer',
                                }}
                              >
                                Xem {chkSummary.evidenceItems.length} ảnh
                              </button>
                            )}
                            {chkSummary.missingEvidence > 0 && !flags.isNoShow && (
                              <span style={{ fontSize: '0.71rem', color: '#dc2626', fontWeight: 600 }}>
                                Thiếu {chkSummary.missingEvidence} ảnh
                              </span>
                            )}
                          </div>
                        </div>
                      </td>

                      <td>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 3, alignItems: 'flex-start' }}>
                          <span className={`table-status-pill ${paymentStatusInfo.pill}`}>
                            <span className="status-dot-solid" />
                            {paymentStatusInfo.text}
                          </span>
                          <span style={{ fontSize: '0.74rem', color: '#475569', fontWeight: 600 }}>
                            {formatVndWhole(item.declaredAmount ?? item.agreedWage ?? shift.salary)}
                          </span>
                        </div>
                      </td>

                      <td style={{ textAlign: 'right' }} onClick={(e) => e.stopPropagation()}>
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem', justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                          {flags.isWaiting && (
                            <>
                              <button
                                type="button"
                                className="eo-btn-secondary"
                                style={{ fontSize: '0.74rem', padding: '0.3rem 0.6rem' }}
                                onClick={() => handleMessageWorker(item)}
                              >
                                Nhắn tin
                              </button>
                              <button
                                type="button"
                                onClick={() => setSelectedForNoShow(item)}
                                style={{
                                  background: '#fef2f2',
                                  color: '#b91c1c',
                                  border: '1px solid #fecaca',
                                  borderRadius: 6,
                                  padding: '0.3rem 0.6rem',
                                  fontSize: '0.74rem',
                                  fontWeight: 700,
                                  cursor: 'pointer',
                                }}
                              >
                                Báo vắng
                              </button>
                            </>
                          )}

                          {!flags.isWaiting && !flags.isNoShow && !item.eoConfirmed && (
                            <button
                              type="button"
                              disabled={!canVerify || verifyingId === item.assignmentId}
                              onClick={() => triggerSingleVerify(item)}
                              className="eo-btn-primary"
                              style={{
                                fontSize: '0.74rem',
                                padding: '0.32rem 0.7rem',
                                opacity: !canVerify || verifyingId === item.assignmentId ? 0.45 : 1,
                                cursor: !canVerify || verifyingId === item.assignmentId ? 'not-allowed' : 'pointer',
                              }}
                              title={canVerify ? 'Duyệt công nhân sự' : 'Cần đủ check-in, đầu việc và ảnh minh chứng'}
                            >
                              {verifyingId === item.assignmentId ? 'Đang duyệt...' : 'Duyệt công'}
                            </button>
                          )}

                          {!flags.isNoShow && item.eoConfirmed && (!item.paymentStatus || item.paymentStatus === 'NOT_DECLARED') && (
                            <button
                              type="button"
                              className="eo-btn-primary"
                              style={{ fontSize: '0.74rem', padding: '0.32rem 0.7rem' }}
                              onClick={() => onOpenPaymentDeclaration?.(item)}
                            >
                              Thanh toán
                            </button>
                          )}

                          <button
                            type="button"
                            className="eo-btn-secondary"
                            style={{ fontSize: '0.74rem', padding: '0.3rem 0.6rem' }}
                            onClick={() => setDrawerWorkerId(item.assignmentId)}
                          >
                            Chi tiết
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>

        {/* Pagination Bar */}
        {filteredWorkers.length > effectivePageSize && (
          <div className="ev-pagination-bar" role="navigation" aria-label="Phân trang danh sách nhân sự">
            <span className="ev-pagination-summary">
              Hiển thị <strong>{startIdx + 1}–{Math.min(startIdx + effectivePageSize, filteredWorkers.length)}</strong> trên{' '}
              <strong>{filteredWorkers.length}</strong> nhân sự
            </span>
            <div className="ev-pagination-controls">
              <button
                type="button"
                className="ev-page-btn"
                disabled={safePage <= 1}
                onClick={() => setCurrentPage(safePage - 1)}
              >
                ← Trước
              </button>
              <span style={{ fontSize: '0.8rem', color: '#475569', padding: '0 0.5rem', fontWeight: 600 }}>
                Trang {safePage} / {totalPages}
              </span>
              <button
                type="button"
                className="ev-page-btn"
                disabled={safePage >= totalPages}
                onClick={() => setCurrentPage(safePage + 1)}
              >
                Sau →
              </button>
            </div>
          </div>
        )}
      </section>

      {/* Slide-over Worker Detail Drawer */}
      <WorkerDetailDrawer
        item={drawerItem}
        shift={shift}
        event={event}
        shiftChecklistTasks={shiftChecklistTasks}
        verifyingId={verifyingId}
        submittingReview={submittingReview}
        onClose={() => setDrawerWorkerId(null)}
        onOpenProfile={(id) => workerProfile.openProfile(id)}
        onPreviewImage={(img) => setPreviewImage(img)}
        onVerify={(item) => triggerSingleVerify(item)}
        onOpenPayment={(item) => onOpenPaymentDeclaration?.(item)}
        onReportIncomplete={handleReportIncomplete}
        onReportNoShow={(item) => setSelectedForNoShow(item)}
        onBlockWorker={handleBlockWorker}
        onSubmitReview={handleSubmitReview}
        onMessageWorker={handleMessageWorker}
      />

      {/* Quick Evidence Lightbox Queue */}
      <QuickEvidenceReviewModal
        isOpen={evidenceQueueState.isOpen}
        queue={verifyQueueWorkers}
        initialIndex={evidenceQueueState.initialIndex}
        shiftChecklistTasks={shiftChecklistTasks}
        verifyingId={verifyingId}
        onClose={() => setEvidenceQueueState({ isOpen: false, initialIndex: 0 })}
        onVerify={(item) => triggerSingleVerify(item)}
        onReportIncomplete={handleReportIncomplete}
      />

      {/* HỘP THOẠI MÃ QR ĐIỂM DANH */}
      {showQrModal && typeof document !== 'undefined' && createPortal(
        <div
          className="eo-modal-overlay"
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.72)',
            zIndex: 10040,
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
                  Khung giờ: {formatTimeShort(shift.startTime)} - {formatTimeShort(shift.endTime)} • Đã vào ca: {checkedInCount}/{shiftConfirmed}
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowQrModal(false)}
                style={{ background: '#f1f5f9', border: 'none', borderRadius: '50%', width: 32, height: 32, fontSize: '1rem', cursor: 'pointer', color: '#475569' }}
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
                  fontWeight: 700,
                  fontSize: '0.85rem',
                  cursor: 'pointer',
                  background: isCheckin ? '#2563eb' : 'transparent',
                  color: isCheckin ? '#ffffff' : '#475569',
                }}
              >
                Điểm danh vào ca
              </button>
              <button
                type="button"
                onClick={() => setQrMode('CHECKOUT')}
                style={{
                  flex: 1,
                  padding: '0.55rem',
                  border: 'none',
                  borderRadius: 8,
                  fontWeight: 700,
                  fontSize: '0.85rem',
                  cursor: 'pointer',
                  background: !isCheckin ? '#2563eb' : 'transparent',
                  color: !isCheckin ? '#ffffff' : '#475569',
                }}
              >
                Điểm danh kết ca
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
                style={{ background: 'none', border: 'none', color: '#2563eb', fontWeight: 700, cursor: 'pointer' }}
              >
                Đổi mã ngay
              </button>
            </div>

            <div
              style={{
                padding: '1.25rem',
                background: '#ffffff',
                border: '2px solid #2563eb',
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
          </div>
        </div>,
        document.body
      )}

      {/* HỘP THOẠI XEM ẢNH PHÓNG TO (Portal trên document.body để luôn nổi trên Drawer/Modal) */}
      {previewImage && typeof document !== 'undefined' && createPortal(
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.85)',
            backdropFilter: 'blur(4px)',
            zIndex: 10060,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '1.5rem',
          }}
          onClick={() => setPreviewImage(null)}
        >
          <div style={{ color: '#ffffff', fontWeight: 700, marginBottom: '1rem', fontSize: '1.05rem', textAlign: 'center' }}>{previewImage.title}</div>
          <img
            src={resolveAssetUrl(previewImage.url)}
            alt={previewImage.title}
            style={{ maxWidth: '90vw', maxHeight: '78vh', borderRadius: '12px', backgroundColor: '#ffffff', boxShadow: '0 20px 40px rgba(0, 0, 0, 0.4)' }}
            onClick={(e) => e.stopPropagation()}
          />
          <button
            type="button"
            onClick={() => setPreviewImage(null)}
            style={{ marginTop: '1rem', background: '#ffffff', color: '#0f172a', border: 'none', borderRadius: '8px', padding: '0.5rem 1.25rem', fontWeight: 700, cursor: 'pointer' }}
          >
            Đóng ảnh
          </button>
        </div>,
        document.body
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
