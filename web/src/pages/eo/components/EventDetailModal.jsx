import React, { useState, useEffect, useMemo } from 'react';
import { createPortal } from 'react-dom';
import { eoService } from '../../../services/eoService';
import { blockListService } from '../../../services/blockListService';
import { showToast } from '../../../components/common/Toast';
import { showPrompt } from '../../../components/common/PromptModal';
import ShiftQrAndAttendancePanel from './ShiftQrAndAttendancePanel';
import ShiftEmergencyBanner from './ShiftEmergencyBanner';
import EmergencyTriggerButton from './EmergencyTriggerButton';
import {
  getActiveEmergencyAlerts,
  getDynamicEventStatus,
  getDynamicShiftStatus,
  getShiftStaffingAlerts,
  getShiftStaffingSummary,
  resolveEventEstimatedCost,
} from '../utils/eventHelpers';
import { WorkerProfileModal } from './worker-profile';
import { useWorkerProfile } from '../hooks/useWorkerProfile';
import './EventDetailModal.css';

export default function EventDetailModal({
  isOpen,
  onClose,
  event,
  allEvents = [],
  onSelectEvent,
  asPage = false,
  initialTab = 'SHIFTS',
  onOpenAddShift,
  onOpenInvite,
  onOpenConfirmPublish,
  onOpenEditEvent,
  onEditShift,
  onCancelShift,
  onNavigateToRecruitment,
  onReloadEvents,
  onOpenPaymentDeclaration,
  onOpenShiftWorkspace,
  skillTaxonomy = [],
}) {
  const [activeTab, setActiveTab] = useState(initialTab || 'SHIFTS'); // 'SHIFTS' | 'QR_ATTENDANCE' | 'WORKERS'
  const [eventWorkers, setEventWorkers] = useState([]);
  const [loadingWorkers, setLoadingWorkers] = useState(false);
  const [searchWorker, setSearchWorker] = useState('');
  const [workerShiftFilter, setWorkerShiftFilter] = useState('ALL');
  const [workerStatusFilter, setWorkerStatusFilter] = useState('ALL');
  const [workerPage, setWorkerPage] = useState(1);
  const workerPageSize = 15;
  const workerProfile = useWorkerProfile();
  const [activeWorkspaceShiftId, setActiveWorkspaceShiftId] = useState(null);
  const [showCancelledShifts, setShowCancelledShifts] = useState(false);
  const [dispatchResultsByShiftId, setDispatchResultsByShiftId] = useState({});

  useEffect(() => {
    if (initialTab) {
      setActiveTab(initialTab);
    }
  }, [initialTab, event?.eventId]);

  useEffect(() => {
    if (isOpen && event?.eventId) {
      const validShifts = (event.shifts || []).filter((s) => s.status !== 'CANCELLED');
      if (validShifts.length > 0) {
        setActiveWorkspaceShiftId((prev) =>
          validShifts.some((s) => s.shiftId === prev) ? prev : validShifts[0].shiftId
        );
      } else {
        setActiveWorkspaceShiftId(null);
      }
      setLoadingWorkers(true);
      eoService.getEventWorkers(event.eventId)
        .then((data) => {
          setEventWorkers(Array.isArray(data) ? data : []);
        })
        .catch((err) => {
          console.warn('Lỗi khi tải danh sách nhân sự sự kiện:', err);
          setEventWorkers([]);
        })
        .finally(() => setLoadingWorkers(false));
    }
  }, [isOpen, event?.eventId, event?.shifts]);

  const handleBlockWorker = async (worker) => {
    if (!worker?.userId) {
      showToast('Không tìm thấy thông tin tài khoản để chặn.', 'error');
      return;
    }
    const reason = await showPrompt({
      title: `Chặn nhân sự "${worker.workerName}"`,
      description: 'Người này sẽ không thể nhận ca làm việc của bạn trong tương lai.',
      type: 'danger',
      confirmText: 'Xác nhận chặn',
      inputLabel: 'Lý do chặn (tùy chọn)',
      initialValue: 'Không tuân thủ quy định ca làm',
      isMultiline: true,
      allowEmpty: true,
    });
    if (reason === null) return;

    try {
      await blockListService.blockUser(worker.userId, reason);
      showToast(`Đã đưa nhân sự "${worker.workerName}" vào danh sách chặn thành công.`, 'success');
      if (event?.eventId) {
        eoService.getEventWorkers(event.eventId).then((data) => {
          setEventWorkers(Array.isArray(data) ? data : []);
        });
      }
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Lỗi khi chặn nhân sự';
      showToast(msg, 'error');
    }
  };

  const rawShifts = event?.shifts || [];
  const activeAndDraftShifts = rawShifts
    .filter((sh) => sh.status !== 'CANCELLED')
    .sort((a, b) => {
      const pA = a.status === 'ACTIVE' || a.status === 'OPEN' ? 1 : a.status === 'DRAFT' ? 2 : 1;
      const pB = b.status === 'ACTIVE' || b.status === 'OPEN' ? 1 : b.status === 'DRAFT' ? 2 : 1;
      return pA - pB;
    });
  const cancelledShifts = rawShifts.filter((sh) => sh.status === 'CANCELLED');
  const shifts = activeAndDraftShifts;
  const staffingAlerts = useMemo(() => getShiftStaffingAlerts(shifts, event), [shifts, event]);
  const activeEmergencyAlerts = useMemo(
    () => getActiveEmergencyAlerts(shifts, event, dispatchResultsByShiftId),
    [shifts, event, dispatchResultsByShiftId]
  );

  // Map skillId -> tên kỹ năng (taxonomy) để hiển thị badge requiredSkills
  const skillNameMap = useMemo(() => {
    const m = {};
    (Array.isArray(skillTaxonomy) ? skillTaxonomy : []).forEach((sk) => {
      if (sk && sk.skillId) m[sk.skillId] = sk.name;
    });
    return m;
  }, [skillTaxonomy]);

  if (!isOpen) return null;
  if (!event) {
    if (asPage) {
      return (
        <div className="eo-widget-card animate-fade-in-up" style={{ padding: '3.5rem 2rem', textAlign: 'center', backgroundColor: '#ffffff', borderRadius: '16px' }}>
          <h3 style={{ margin: '0 0 0.5rem', color: '#0f172a', fontSize: '1.2rem' }}>Chưa chọn sự kiện để xem chi tiết</h3>
          <p style={{ margin: '0 0 1.5rem', color: '#64748b', fontSize: '0.9rem' }}>
            Vui lòng chọn một sự kiện từ danh sách để xem đầy đủ thông tin, ca làm việc và bảng điểm danh.
          </p>
          <button type="button" className="eo-btn-primary" onClick={onClose}>
            Quay lại Danh sách sự kiện
          </button>
        </div>
      );
    }
    return null;
  }

  const totalRequired = event.totalRequiredWorkers || shifts.reduce((s, sh) => s + (sh.requiredWorkers || 0), 0);
  const totalConfirmed = event.totalConfirmedWorkers || shifts.reduce((s, sh) => s + (sh.confirmedWorkers || 0), 0);
  const fillRate = totalRequired > 0 ? Math.round((totalConfirmed / totalRequired) * 100) : 0;
  const hasStaffingShortage = staffingAlerts.length > 0 || (totalRequired > 0 && fillRate < 50);
  const totalCost = resolveEventEstimatedCost(event);

  const formatTime = (timeStr, fallback = '--:--') => {
    if (!timeStr) return fallback;
    try {
      return new Date(timeStr).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
    } catch {
      return fallback;
    }
  };

  const skillNames = (shift) => {
    const parts = [];
    const performedName = shift.performedSkillName || skillNameMap[shift.performedSkillId];
    if (performedName) {
      parts.push(`Chính: ${performedName}`);
    }
    if (Array.isArray(shift.skillRequirements) && shift.skillRequirements.length > 0) {
      const reqNames = shift.skillRequirements
        .map((r) => `${r.requirementType === 'PREFERRED' ? 'Ưu tiên' : 'Bắt buộc'}: ${r.skillName || skillNameMap[r.skillId] || 'Kỹ năng'}`)
        .join(', ');
      parts.push(reqNames);
    } else if (Array.isArray(shift.requiredSkills) && shift.requiredSkills.length > 0) {
      parts.push(
        shift.requiredSkills.map((id) => skillNameMap[id]).filter(Boolean).join(', ') ||
          `${shift.requiredSkills.length} kỹ năng`
      );
    }
    return parts.join(' • ');
  };

  const hasCustomArrival = (shift) =>
    shift.arrivalTime && shift.startTime &&
    new Date(shift.arrivalTime).getTime() !== new Date(shift.startTime).getTime();

  const formatDate = (dateStr) => {
    if (!dateStr) return 'Chưa ấn định ngày';
    try {
      return new Date(dateStr).toLocaleDateString('vi-VN', {
        weekday: 'long',
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      });
    } catch {
      return dateStr;
    }
  };

  const formatDateTimeRange = (start, end) => {
    if (!start && !end) return formatDate(event.eventDate);
    try {
      const s = start ? new Date(start) : null;
      const e = end ? new Date(end) : null;
      if (s && e) {
        return `${s.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })} ${s.toLocaleDateString('vi-VN')} - ${e.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })} ${e.toLocaleDateString('vi-VN')}`;
      }
      return formatDate(start || end);
    } catch {
      return formatDate(event.eventDate);
    }
  };

  const getStatusBadge = () => {
    const dyn = getDynamicEventStatus(event);
    return <span className={`detail-status-pill ${dyn.colorClass || 'blue'}`}>● {dyn.label}</span>;
  };

  const filteredWorkers = eventWorkers.filter((w) => {
    if (workerShiftFilter !== 'ALL' && String(w.shiftId || w.jobType || '') !== workerShiftFilter) {
      return false;
    }
    if (workerStatusFilter === 'CHECKED_IN' && !w.checkinTime) return false;
    if (workerStatusFilter === 'WAITING' && (w.checkinTime || w.assignmentStatus === 'NO_SHOW')) return false;
    if (workerStatusFilter === 'NO_SHOW' && w.assignmentStatus !== 'NO_SHOW') return false;
    if (!searchWorker.trim()) return true;
    const q = searchWorker.toLowerCase();
    return (
      (w.workerName && w.workerName.toLowerCase().includes(q)) ||
      (w.jobType && w.jobType.toLowerCase().includes(q)) ||
      (w.workerPhone && w.workerPhone.includes(q))
    );
  });

  const workerTotalPages = Math.max(1, Math.ceil(filteredWorkers.length / workerPageSize));
  const safeWorkerPage = Math.min(Math.max(1, workerPage), workerTotalPages);
  const paginatedEventWorkers = filteredWorkers.slice(
    (safeWorkerPage - 1) * workerPageSize,
    safeWorkerPage * workerPageSize
  );

  const selectedQrShift = shifts.find((s) => s.shiftId === activeWorkspaceShiftId) || shifts[0] || null;

  const contentBox = (
    <div
      className={asPage ? 'eo-widget-card event-detail-workspace' : 'event-detail-modal-box'}
      style={
        asPage
          ? {
              width: '100%',
              maxWidth: '100%',
              padding: 0,
              overflow: 'hidden',
              borderRadius: '16px',
              border: '1px solid #e2e8f0',
              backgroundColor: '#ffffff',
            }
          : undefined
      }
      onClick={(e) => e.stopPropagation()}
    >
      {asPage && (
        <div
          style={{
            padding: '0.85rem 1.5rem',
            backgroundColor: '#f8fafc',
            borderBottom: '1px solid #e2e8f0',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '0.75rem',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
            <button
              type="button"
              onClick={onClose}
              style={{
                background: '#ffffff',
                border: '1px solid #cbd5e1',
                borderRadius: '8px',
                padding: '0.45rem 0.95rem',
                fontSize: '0.85rem',
                fontWeight: 700,
                color: '#1e293b',
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.4rem',
              }}
            >
              &larr; Quay lại danh sách sự kiện
            </button>

            {allEvents && allEvents.filter((e) => e && e.status !== 'CANCELLED').length > 1 && (
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span style={{ fontSize: '0.85rem', color: '#64748b', fontWeight: 600 }}>Xem sự kiện:</span>
                <select
                  value={event.eventId}
                  onChange={(e) => {
                    const next = allEvents.find((ev) => ev.eventId === e.target.value);
                    if (next && onSelectEvent) onSelectEvent(next);
                  }}
                  style={{
                    padding: '0.4rem 0.75rem',
                    borderRadius: '8px',
                    border: '1px solid #cbd5e1',
                    fontSize: '0.85rem',
                    fontWeight: 700,
                    color: '#0f172a',
                    backgroundColor: '#ffffff',
                    cursor: 'pointer',
                    maxWidth: '260px',
                  }}
                >
                  {allEvents
                    .filter((e) => e && e.status !== 'CANCELLED')
                    .map((ev) => (
                      <option key={ev.eventId} value={ev.eventId}>
                        {ev.name} {ev.status === 'DRAFT' ? '• [Bản nháp]' : ''}
                      </option>
                    ))}
                </select>
              </div>
            )}
          </div>

          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center' }}>
            {event.status === 'DRAFT' && (() => {
              const dynEvt = getDynamicEventStatus(event);
              const activeShiftCnt = shifts.length || Number(event.totalShifts || 0);
              if (dynEvt.isExpiredDraft) {
                return (
                  <span
                    style={{
                      background: '#fef2f2',
                      color: '#dc2626',
                      border: '1px solid #fecaca',
                      borderRadius: '8px',
                      padding: '0.4rem 0.8rem',
                      fontSize: '0.8rem',
                      fontWeight: 700,
                    }}
                  >
                    Bản nháp đã qua giờ — hãy sửa lại thời gian
                  </span>
                );
              }
              return (
                <>
                  <button
                    type="button"
                    onClick={() => {
                      showToast('Đã lưu sự kiện ở trạng thái Bản nháp!', 'success');
                      onReloadEvents?.();
                    }}
                    style={{
                      background: '#ffffff',
                      color: '#334155',
                      border: '1px solid #cbd5e1',
                      borderRadius: '8px',
                      padding: '0.45rem 0.95rem',
                      fontSize: '0.82rem',
                      fontWeight: 700,
                      cursor: 'pointer',
                    }}
                  >
                    Lưu Bản Nháp
                  </button>
                  {onOpenConfirmPublish && activeShiftCnt > 0 && (
                    <button
                      type="button"
                      onClick={() => onOpenConfirmPublish(event)}
                      style={{
                        background: '#2563eb',
                        color: '#ffffff',
                        border: 'none',
                        borderRadius: '8px',
                        padding: '0.45rem 1rem',
                        fontSize: '0.82rem',
                        fontWeight: 700,
                        cursor: 'pointer',
                      }}
                    >
                      Đăng Tuyển Ngay
                    </button>
                  )}
                </>
              );
            })()}
            {onOpenEditEvent && (
              <button
                type="button"
                onClick={() => onOpenEditEvent(event)}
                style={{
                  background: '#ffffff',
                  color: '#475569',
                  border: '1px solid #cbd5e1',
                  borderRadius: '8px',
                  padding: '0.45rem 0.9rem',
                  fontSize: '0.82rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                Chỉnh sửa sự kiện
              </button>
            )}
          </div>
        </div>
      )}

        {/* Header */}
        <div className="event-detail-modal-header">
          <div className="event-detail-header-left">
            {event.imageUrl ? (
              <img
                src={event.imageUrl}
                alt={event.name}
                style={{
                  width: '48px',
                  height: '48px',
                  borderRadius: '12px',
                  objectFit: 'cover',
                  border: '1.5px solid #bfdbfe',
                }}
                onError={(e) => { e.target.style.display = 'none'; }}
              />
            ) : null}
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' }}>
                {asPage ? (
                  <h1 className="event-detail-title">{event.name}</h1>
                ) : (
                  <h3 className="event-detail-title">{event.name}</h3>
                )}
                {getStatusBadge(event.status)}
              </div>
              <p className="event-detail-sub">
                {event.createdAt && <span>Ngày tạo {formatDate(event.createdAt)}</span>}
                {event.eventId && <code title="Mã sự kiện">{event.eventId}</code>}
              </p>
            </div>
          </div>
          {!asPage && (
            <button type="button" className="event-detail-close-btn" onClick={onClose}>
              ✕
            </button>
          )}
        </div>

        {/* Body */}
        <div className="event-detail-modal-body">
          {/* Ảnh Poster / Banner sự kiện xuống 1 div riêng cho ảnh to rõ */}
          {event.imageUrl && (
            <div
              style={{
                marginBottom: '1.25rem',
                borderRadius: '12px',
                overflow: 'hidden',
                border: '1px solid #cbd5e1',
                backgroundColor: '#f8fafc',
              }}
            >
              <img
                src={event.imageUrl}
                alt={event.name}
                style={{
                  width: '100%',
                  maxHeight: '280px',
                  objectFit: 'cover',
                  display: 'block',
                }}
                onError={(e) => {
                  e.target.style.display = 'none';
                }}
              />
            </div>
          )}

          {/* Thông tin cốt lõi */}
          <div className="event-detail-info-grid">
            <div className="info-item">
              <span className="info-label">Thời gian sự kiện</span>
              <span className="info-value">
                {formatDateTimeRange(event.startTime, event.endTime)}
              </span>
            </div>

            <div className="info-item">
              <span className="info-label">Địa chỉ tổ chức cụ thể</span>
              <span className="info-value" title={event.venueAddress}>
                {event.venueAddress || 'Chưa cập nhật địa chỉ cụ thể'}
              </span>
            </div>

            <div className="info-item">
              <span className="info-label">Chế độ tuyển dụng</span>
              <span className="info-value">
                <span className="mode-badge-pill">
                  {event.mode === 'INVITE' || event.mode === 'INVITE_ONLY' ? 'Mời trực tiếp (Liên kết / Mã QR)' : 'Điều phối tự động'}
                </span>
              </span>
            </div>
          </div>

          {event.description && (
            <div className="event-detail-desc-box">
              <div className="desc-title">Mô tả sự kiện:</div>
              <div className="desc-content">{event.description}</div>
            </div>
          )}

          {/* Metrics Nhân sự & Ngân sách */}
          <div className="event-detail-kpi-row">
            <div className="detail-kpi-card">
              <span className="kpi-label">Tổng ca làm việc</span>
              <span className="kpi-value">{shifts.length}</span>
              <span className="kpi-sub">ca trực</span>
            </div>
            <div className="detail-kpi-card">
              <span className="kpi-label">Nhân sự cần tuyển</span>
              <span className="kpi-value">{totalRequired}</span>
              <span className="kpi-sub">vị trí</span>
            </div>
            <div
              className={`detail-kpi-card ${hasStaffingShortage ? 'highlight-alert' : 'highlight-green'}`}
              style={hasStaffingShortage ? { backgroundColor: '#fff1f2', borderColor: '#fecdd3' } : undefined}
            >
              <span className="kpi-label">{hasStaffingShortage ? 'Nhân sự chốt ca (Thiếu)' : 'Nhân sự đã chốt ca'}</span>
              <span className="kpi-value" style={hasStaffingShortage ? { color: '#e11d48' } : undefined}>
                {totalConfirmed} / {totalRequired}
              </span>
              <span className="kpi-sub" style={hasStaffingShortage ? { color: '#be123c', fontWeight: 700 } : undefined}>
                {hasStaffingShortage ? `Thiếu ${Math.max(0, totalRequired - totalConfirmed)} người (${fillRate}%)` : `đạt ${fillRate}%`}
              </span>
            </div>
            <div className="detail-kpi-card">
              <span className="kpi-label">Dự toán chi phí</span>
              <span className="kpi-value text-emerald">
                {new Intl.NumberFormat('vi-VN').format(totalCost)} <small>đ</small>
              </span>
              <span className="kpi-sub">thù lao nhân sự</span>
            </div>
          </div>

          {/* Tab Switcher: Ca làm việc | Điểm danh & Mã QR | Danh sách nhân sự */}
          <div className="event-detail-tabs-header">
            <button
              type="button"
              className={`event-detail-tab-btn ${activeTab === 'SHIFTS' ? 'active' : ''}`}
              onClick={() => setActiveTab('SHIFTS')}
            >
              Chi Tiết Ca Làm Việc ({shifts.length})
            </button>
            <button
              type="button"
              className={`event-detail-tab-btn ${activeTab === 'QR_ATTENDANCE' ? 'active' : ''}`}
              onClick={() => setActiveTab('QR_ATTENDANCE')}
            >
              Trạm Điểm Danh & Mã QR Ca
            </button>
            <button
              type="button"
              className={`event-detail-tab-btn ${activeTab === 'WORKERS' ? 'active' : ''}`}
              onClick={() => setActiveTab('WORKERS')}
            >
              Tổng Hợp Nhân Sự Sự Kiện ({eventWorkers.length || totalConfirmed})
            </button>
          </div>

          {/* TAB 1: DANH SÁCH CA LÀM VIỆC */}
          {activeTab === 'SHIFTS' && (
            <div className="event-detail-shifts-section">
              <div className="shifts-section-header">
                <h4 className="shifts-title">
                  Phân Chia Ca & Lịch Trình Chi Tiết
                </h4>
                <button
                  type="button"
                  className="btn-add-shift-pill"
                  onClick={() => {
                    if (!asPage) onClose();
                    onOpenAddShift(event.eventId);
                  }}
                >
                  + Thêm ca làm việc
                </button>
              </div>

              {/* Khung điều phối khẩn cấp: chỉ hiển thị sau khi ca đã được kích hoạt khẩn cấp */}
              {activeEmergencyAlerts.length > 0 && (
                <div
                  style={{
                    marginBottom: '1.15rem',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '0.6rem',
                  }}
                >
                  <div style={{ fontSize: '0.86rem', fontWeight: 800, color: '#be123c' }}>
                    Đang điều phối khẩn cấp ({activeEmergencyAlerts.length} ca)
                  </div>
                  {activeEmergencyAlerts.map((alertSummary) => (
                    <ShiftEmergencyBanner
                      key={alertSummary.shift.shiftId}
                      shift={alertSummary.shift}
                      event={event}
                      localDispatchResult={dispatchResultsByShiftId[alertSummary.shift.shiftId]}
                      onOpenInvite={onOpenInvite}
                    />
                  ))}
                </div>
              )}

              {shifts.length === 0 ? (
                <div className="detail-empty-shifts">
                  <p>Sự kiện này hiện chưa có ca làm việc nào.</p>
                  <button
                    type="button"
                    className="btn-add-shift-primary"
                    onClick={() => {
                      if (!asPage) onClose();
                      onOpenAddShift(event.eventId);
                    }}
                  >
                    + Thiết lập ca làm việc đầu tiên
                  </button>
                </div>
              ) : (
                <div className="detail-shifts-list">
                  {shifts.map((shift, sIdx) => {
                    const shiftConfirmed = shift.confirmedWorkers || 0;
                    const shiftRequired = shift.requiredWorkers || 1;
                    const shiftRate = Math.round((shiftConfirmed / shiftRequired) * 100);
                    const shDynStatus = getDynamicShiftStatus(shift, event.status);
                    const isDraft = event?.status === 'DRAFT' || shift?.status === 'DRAFT';

                    return (
                      <div key={shift.shiftId || sIdx} style={{ display: 'flex', flexDirection: 'column' }}>
                        <div className="detail-shift-card">
                          <div className="shift-card-header">
                            <div className="shift-card-title-group">
                              <span className="shift-job-badge">{shift.jobType}</span>
                              <span className="shift-time-badge">
                                {formatTime(shift.startTime, '08:00')} - {formatTime(shift.endTime, '12:00')}
                                {hasCustomArrival(shift) && (
                                  <span style={{ marginLeft: 6, color: '#b45309', fontWeight: 600 }}>
                                    (Có mặt từ {formatTime(shift.arrivalTime, '')})
                                  </span>
                                )}
                              </span>
                              <span
                                style={{
                                  fontSize: '0.75rem',
                                  padding: '0.2rem 0.6rem',
                                  borderRadius: '999px',
                                  backgroundColor: shDynStatus.bg,
                                  color: shDynStatus.color,
                                  border: `1px solid ${shDynStatus.border}`,
                                  fontWeight: 700,
                                }}
                              >
                                ● {shDynStatus.label}
                              </span>
                              {shift.dressCode && (
                                <span className="shift-dresscode-tag">Đồng phục: {shift.dressCode}</span>
                              )}
                              {skillNames(shift) && (
                                <span
                                  title="Kỹ năng bắt buộc (matching lọc cứng theo kỹ năng)"
                                  style={{
                                    fontSize: '0.72rem',
                                    color: '#1d4ed8',
                                    background: '#dbeafe',
                                    border: '1px solid #bfdbfe',
                                    padding: '0.15rem 0.5rem',
                                    borderRadius: '999px',
                                    fontWeight: 600,
                                  }}
                                >
                                  Kỹ năng: {skillNames(shift)}
                                </span>
                              )}
                            </div>
                            <div className="shift-wage-tag">
                              {new Intl.NumberFormat('vi-VN').format(shift.hourlyWage || 50000)} đ/h
                              {shift.allowance > 0 && ` (+${new Intl.NumberFormat('vi-VN').format(shift.allowance)}đ phụ cấp)`}
                            </div>
                          </div>

                          {/* Ảnh minh họa ca làm việc xuống 1 div riêng cho to rõ */}
                          {shift.imageUrl && (
                            <div
                              style={{
                                margin: '0.65rem 0',
                                borderRadius: '8px',
                                overflow: 'hidden',
                                border: '1px solid #e2e8f0',
                                backgroundColor: '#f8fafc',
                              }}
                            >
                              <img
                                src={shift.imageUrl}
                                alt={shift.jobType}
                                style={{
                                  width: '100%',
                                  maxHeight: '200px',
                                  objectFit: 'cover',
                                  display: 'block',
                                }}
                                onError={(e) => {
                                  e.target.style.display = 'none';
                                }}
                              />
                            </div>
                          )}

                          <div className="shift-card-body">
                            {/* Tiến độ nhân sự của ca */}
                            <div className="shift-worker-progress">
                              <div className="progress-labels">
                                <span className="progress-title">
                                  Nhân sự nhận ca: <strong>{shiftConfirmed} / {shiftRequired}</strong> người
                                </span>
                                <span className="progress-percent">{shiftRate}%</span>
                              </div>
                              <div className="progress-track">
                                <div
                                  className="progress-fill"
                                  style={{
                                    width: `${Math.min(100, shiftRate)}%`,
                                    backgroundColor: shiftRate >= 100 ? '#10b981' : '#2563eb',
                                  }}
                                />
                              </div>
                            </div>

                            {/* Thao tác ca */}
                            <div className="shift-card-actions">
                              {/* Trạng thái mở rộng khẩn cấp hoặc nút kích hoạt khẩn cấp (chỉ bật khi gần giờ) */}
                              {shift.emergency && shiftRequired > shiftConfirmed ? (() => {
                                const summary = getShiftStaffingSummary(shift, event);
                                const localRes = dispatchResultsByShiftId[shift.shiftId];
                                const hasCandidates =
                                  localRes && typeof localRes.hasCandidates === 'boolean'
                                    ? localRes.hasCandidates
                                    : summary.hasCandidates;
                                return (
                                  <span
                                    style={{
                                      display: 'inline-flex',
                                      alignItems: 'center',
                                      background: '#fff1f2',
                                      color: '#e11d48',
                                      border: '1.5px solid #fecdd3',
                                      borderRadius: '8px',
                                      padding: '0.35rem 0.65rem',
                                      fontSize: '0.74rem',
                                      fontWeight: 700,
                                    }}
                                  >
                                    {hasCandidates ? 'Đang điều phối khẩn cấp' : 'Đã bật điều phối · Chưa tìm được Worker phù hợp'}
                                  </span>
                                );
                              })() : (
                                <EmergencyTriggerButton shift={shift} event={event} compact />
                              )}

                              {/* Mã mời nhân sự: Ẩn khi sự kiện hoặc ca là Bản nháp (DRAFT) */}
                              {!isDraft && onOpenInvite && (
                                <button
                                  type="button"
                                  className="btn-shift-action purple"
                                  onClick={() => {
                                    if (!asPage) onClose();
                                    onOpenInvite(shift, event);
                                  }}
                                  title="Lấy link & mã mời trực tiếp cho nhân sự"
                                >
                                  Mã Mời Nhân Sự
                                </button>
                              )}

                              {onEditShift && (
                                <button
                                  type="button"
                                  className="btn-shift-action"
                                  style={{ background: '#f1f5f9', color: '#334155', border: '1px solid #cbd5e1' }}
                                  onClick={() => onEditShift(shift)}
                                >
                                  Sửa Ca
                                </button>
                              )}

                              {onCancelShift && shift.status !== 'CANCELLED' && (
                                <button
                                  type="button"
                                  className="btn-shift-action"
                                  style={{ background: '#fef2f2', color: '#dc2626', border: '1px solid #fecaca' }}
                                  onClick={() => onCancelShift(shift)}
                                >
                                  Hủy Ca
                                </button>
                              )}

                              {/* Quản lý ca & nhân sự: Ẩn khi sự kiện hoặc ca là Bản nháp (DRAFT) */}
                              {!isDraft && (
                                <button
                                  type="button"
                                  className="btn-shift-action emerald"
                                  onClick={() => {
                                    if (onOpenShiftWorkspace) {
                                      onOpenShiftWorkspace(shift);
                                    } else {
                                      setActiveWorkspaceShiftId(shift.shiftId);
                                      setActiveTab('QR_ATTENDANCE');
                                    }
                                  }}
                                  title="Mở trang quản lý chi tiết riêng cho ca làm việc này"
                                >
                                  Quản Lý Ca & Nhân Sự
                                </button>
                              )}
                            </div>
                          </div>
                        </div>
                      </div>
                    );
                  })}

                  {/* Khu vực tách riêng các ca đã hủy / đã xóa */}
                  {cancelledShifts.length > 0 && (
                    <div style={{ marginTop: '0.75rem', borderTop: '1px dashed #cbd5e1', paddingTop: '0.75rem' }}>
                      <button
                        type="button"
                        onClick={() => setShowCancelledShifts((prev) => !prev)}
                        style={{
                          background: '#fef2f2',
                          border: '1px solid #fecaca',
                          color: '#dc2626',
                          borderRadius: '8px',
                          padding: '0.4rem 0.85rem',
                          fontSize: '0.78rem',
                          fontWeight: 700,
                          cursor: 'pointer',
                        }}
                      >
                        {showCancelledShifts
                          ? `Ẩn ${cancelledShifts.length} ca đã hủy / đã xóa ▲`
                          : `Xem ${cancelledShifts.length} ca đã hủy / đã xóa (Tách riêng) ▼`}
                      </button>

                      {showCancelledShifts && (
                        <div style={{ marginTop: '0.6rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                          {cancelledShifts.map((shift, idx) => (
                            <div
                              key={shift.shiftId || idx}
                              style={{
                                padding: '0.65rem 1rem',
                                borderRadius: '8px',
                                backgroundColor: '#fef2f2',
                                border: '1px solid #fecaca',
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                                opacity: 0.75,
                              }}
                            >
                              <span style={{ textDecoration: 'line-through', color: '#64748b', fontWeight: 600 }}>
                                {shift.jobType} ({formatTime(shift.startTime, '08:00')} - {formatTime(shift.endTime, '12:00')})
                              </span>
                              <span style={{ fontSize: '0.72rem', fontWeight: 700, color: '#dc2626' }}>Đã hủy</span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* TAB 2: TRẠM ĐIỂM DANH & MÃ QR ĐỘNG GỘP TRỰC TIẾP VÀO SỰ KIỆN */}
          {activeTab === 'QR_ATTENDANCE' && (
            <div className="event-detail-attendance-section animate-fade-in-up">
              {shifts.length === 0 ? (
                <div
                  style={{
                    padding: '3rem 1.5rem',
                    textAlign: 'center',
                    backgroundColor: '#f8fafc',
                    borderRadius: '12px',
                    border: '1.5px dashed #cbd5e1',
                    color: '#64748b',
                  }}
                >
                  <p style={{ margin: '0 0 0.5rem', fontWeight: 700, color: '#334155', fontSize: '1rem' }}>
                    Sự kiện này chưa có ca làm việc nào để tạo Mã QR & Điểm danh.
                  </p>
                  <p style={{ margin: '0 0 1.25rem', fontSize: '0.85rem' }}>
                    Vui lòng thiết lập ít nhất một ca làm việc trước khi mở trạm điểm danh.
                  </p>
                  <button
                    type="button"
                    className="eo-btn-primary"
                    onClick={() => {
                      if (!asPage) onClose();
                      onOpenAddShift(event.eventId);
                    }}
                  >
                    + Thêm Ca Làm Việc Ngay
                  </button>
                </div>
              ) : (
                <div>
                  {/* Thanh chọn ca nếu sự kiện có nhiều ca */}
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      flexWrap: 'wrap',
                      gap: '0.75rem',
                      marginBottom: '1rem',
                      padding: '0.85rem 1.25rem',
                      backgroundColor: '#f0fdfa',
                      borderRadius: '12px',
                      border: '1px solid #ccfbf1',
                    }}
                  >
                    <div>
                      <h4 style={{ margin: 0, fontSize: '1rem', fontWeight: 800, color: '#0f766e' }}>
                        Trạm Điểm Danh & Mã QR Động
                      </h4>
                      <p style={{ margin: '0.2rem 0 0', fontSize: '0.82rem', color: '#115e59' }}>
                        Mã QR xoay vòng 20 giây và danh sách điểm danh, duyệt thù lao nhân sự cho từng ca của sự kiện này.
                      </p>
                    </div>

                    {shifts.length > 1 && (
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        <span style={{ fontSize: '0.85rem', fontWeight: 700, color: '#0f766e' }}>Chọn ca trực:</span>
                        <select
                          className="eo-form-input"
                          style={{
                            width: 'auto',
                            minWidth: '220px',
                            padding: '0.45rem 0.85rem',
                            fontSize: '0.85rem',
                            fontWeight: 700,
                            borderRadius: '8px',
                            borderColor: '#99f6e4',
                            backgroundColor: '#ffffff',
                          }}
                          value={selectedQrShift?.shiftId || ''}
                          onChange={(e) => setActiveWorkspaceShiftId(e.target.value)}
                        >
                          {shifts.map((s) => (
                            <option key={s.shiftId} value={s.shiftId}>
                              {s.jobType} ({formatTime(s.startTime, '08:00')} - {formatTime(s.endTime, '12:00')}) - {s.confirmedWorkers || 0}/{s.requiredWorkers || 0} người
                            </option>
                          ))}
                        </select>
                      </div>
                    )}
                  </div>

                  {selectedQrShift ? (
                    <ShiftQrAndAttendancePanel
                      shift={selectedQrShift}
                      event={event}
                      skillTaxonomy={skillTaxonomy}
                      onSelectShift={(nextShift) => setActiveWorkspaceShiftId(nextShift.shiftId)}
                      onReloadEvents={onReloadEvents}
                      onOpenPaymentDeclaration={onOpenPaymentDeclaration}
                    />
                  ) : (
                    <div style={{ padding: '2rem', textAlign: 'center', color: '#64748b' }}>
                      Vui lòng chọn một ca làm việc để hiển thị trạm điểm danh.
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* TAB 3: DANH SÁCH NHÂN SỰ SỰ KIỆN (WORKFORCE LIST) */}
          {activeTab === 'WORKERS' && (
            <div className="event-detail-workers-section">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.5rem' }}>
                <h4 style={{ margin: 0, fontSize: '1rem', fontWeight: 700, color: '#1e293b' }}>
                  Danh Sách Nhân Sự Tham Gia Sự Kiện ({filteredWorkers.length}/{eventWorkers.length})
                </h4>
                <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center' }}>
                  <select
                    className="eo-form-input"
                    style={{ width: 'auto', minWidth: '160px', padding: '0.4rem 0.65rem', fontSize: '0.82rem' }}
                    value={workerShiftFilter}
                    onChange={(e) => {
                      setWorkerShiftFilter(e.target.value);
                      setWorkerPage(1);
                    }}
                    aria-label="Lọc nhân sự theo ca"
                  >
                    <option value="ALL">Tất cả các ca</option>
                    {shifts.map((sh) => (
                      <option key={sh.shiftId} value={String(sh.shiftId)}>
                        {sh.jobType} ({formatTime(sh.startTime)})
                      </option>
                    ))}
                  </select>
                  <select
                    className="eo-form-input"
                    style={{ width: 'auto', minWidth: '150px', padding: '0.4rem 0.65rem', fontSize: '0.82rem' }}
                    value={workerStatusFilter}
                    onChange={(e) => {
                      setWorkerStatusFilter(e.target.value);
                      setWorkerPage(1);
                    }}
                    aria-label="Lọc theo trạng thái điểm danh"
                  >
                    <option value="ALL">Mọi trạng thái</option>
                    <option value="CHECKED_IN">Đã vào ca</option>
                    <option value="WAITING">Chưa vào ca</option>
                    <option value="NO_SHOW">Vắng mặt</option>
                  </select>
                  <input
                    type="text"
                    placeholder="Tìm theo tên, vị trí ca, SĐT..."
                    className="eo-form-input"
                    style={{ width: '220px', padding: '0.4rem 0.75rem', fontSize: '0.825rem' }}
                    value={searchWorker}
                    onChange={(e) => {
                      setSearchWorker(e.target.value);
                      setWorkerPage(1);
                    }}
                  />
                </div>
              </div>

              {loadingWorkers ? (
                <div style={{ padding: '2.5rem 0', textAlign: 'center', color: '#64748b' }}>
                  Đang tải danh sách nhân sự sự kiện...
                </div>
              ) : filteredWorkers.length === 0 ? (
                <div style={{
                  padding: '2.5rem 1.5rem',
                  textAlign: 'center',
                  backgroundColor: '#f8fafc',
                  borderRadius: '12px',
                  border: '1.5px dashed #cbd5e1',
                  color: '#64748b',
                }}>
                  <p style={{ margin: '0 0 0.5rem', fontWeight: 600, color: '#334155' }}>
                    Chưa có nhân sự nào nhận ca làm việc trong sự kiện này.
                  </p>
                  <p style={{ margin: 0, fontSize: '0.825rem' }}>
                    Hãy xuất bản sự kiện hoặc gửi mã mời trực tiếp để thu hút các ứng viên tham gia.
                  </p>
                </div>
              ) : (
                <>
                  <div className="event-workers-list">
                    {paginatedEventWorkers.map((w, idx) => {
                    const isReliableHigh = (w.workerReliabilityScore || 100) >= 90;
                    return (
                      <div key={w.assignmentId || idx} className="event-worker-card">
                        <div className="worker-card-left">
                          <div
                            onClick={() => workerProfile.openProfile(w.workerId || w.userId)}
                            style={{ cursor: 'pointer' }}
                            title="Bấm để xem hồ sơ năng lực chi tiết"
                          >
                            {w.avatarUrl ? (
                              <img
                                src={w.avatarUrl}
                                alt={w.workerName}
                                className="worker-avatar-frame"
                                onError={(e) => { e.target.style.display = 'none'; }}
                              />
                            ) : (
                              <div className="worker-avatar-placeholder">
                                {(w.workerName || 'W').charAt(0).toUpperCase()}
                              </div>
                            )}
                          </div>

                          <div className="worker-info-main">
                            <div className="worker-name-row">
                              <span
                                className="worker-name-text"
                                onClick={() => workerProfile.openProfile(w.workerId || w.userId)}
                                style={{ cursor: 'pointer', color: '#0066FF' }}
                                title="Bấm để xem hồ sơ năng lực chi tiết"
                              >
                                {w.workerName}
                              </span>
                              <span className="worker-age-badge">
                                {w.meetsMinimumAge === true
                                  ? 'Đủ tuổi quy định'
                                  : w.meetsMinimumAge === false
                                    ? 'Chưa đủ tuổi tối thiểu'
                                    : w.workerAge
                                      ? `${w.workerAge} tuổi`
                                      : 'Chưa xác minh tuổi'}
                              </span>
                              {w.yearsExperience > 0 && (
                                <span className="worker-age-badge" style={{ background: '#e0f2fe', color: '#0369a1' }}>
                                  {w.yearsExperience} năm kinh nghiệm
                                </span>
                              )}
                              <span className={`worker-reliability-pill ${isReliableHigh ? 'high' : ''}`}>
                                {w.workerReliabilityScore || 100}/100 Uy tín
                              </span>
                              <span style={{ fontSize: '0.8rem', color: '#eab308', fontWeight: 700 }}>
                                {w.workerRatingAvg ? `${w.workerRatingAvg}` : '5.0'}
                              </span>
                            </div>

                            <div className="worker-sub-row">
                              <span className="worker-job-tag">Ca: {w.jobType || w.shiftName || 'Nhân sự ca'}</span>
                              <span>SĐT: <strong>{w.workerPhone || '09xx xxx xxx'}</strong></span>
                              {w.agreedWage && (
                                <span>Thù lao: <strong>{Number(w.agreedWage).toLocaleString()} đ</strong></span>
                              )}
                            </div>

                            {w.workerBio && (
                              <div style={{
                                marginTop: '0.35rem',
                                fontSize: '0.78rem',
                                color: '#334155',
                                fontStyle: 'italic',
                                background: '#f8fafc',
                                padding: '0.3rem 0.6rem',
                                borderRadius: '6px',
                                borderLeft: '3px solid #0066FF',
                                lineHeight: 1.35
                              }}>
                                "{w.workerBio}"
                              </div>
                            )}
                          </div>
                        </div>

                        {/* Trạng thái nhân sự & Nút hành động */}
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                          {w.checkinTime ? (
                            <span style={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '0.3rem',
                              padding: '0.35rem 0.75rem',
                              backgroundColor: '#ecfdf5',
                              border: '1px solid #a7f3d0',
                              color: '#065f46',
                              borderRadius: '999px',
                              fontSize: '0.775rem',
                              fontWeight: 700,
                            }}>
                              ● Đã vào ca ({formatTime(w.checkinTime)})
                            </span>
                          ) : w.assignmentStatus === 'CONFIRMED' || w.assignmentStatus === 'ACCEPTED' ? (
                            <span style={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '0.3rem',
                              padding: '0.35rem 0.75rem',
                              backgroundColor: '#eff6ff',
                              border: '1px solid #bfdbfe',
                              color: '#1d4ed8',
                              borderRadius: '4px',
                              fontSize: '0.78rem',
                              fontWeight: 500,
                            }}>
                              Đã nhận ca (Chờ check-in)
                            </span>
                          ) : (
                            <span style={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              padding: '0.35rem 0.75rem',
                              backgroundColor: '#f1f5f9',
                              color: '#64748b',
                              borderRadius: '4px',
                              fontSize: '0.78rem',
                              fontWeight: 500,
                            }}>
                              {w.assignmentStatus || 'Đã phân công'}
                            </span>
                          )}

                          <button
                            type="button"
                            onClick={() => workerProfile.openProfile(w.workerId || w.userId)}
                            className="eo-btn-secondary"
                            style={{
                              padding: '0.35rem 0.65rem',
                              fontSize: '0.75rem',
                              fontWeight: 600,
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '0.25rem',
                            }}
                            title="Xem chi tiết hồ sơ năng lực"
                          >
                            Xem Hồ Sơ
                          </button>

                          {w.userId && (
                            <button
                              type="button"
                              onClick={() => handleBlockWorker(w)}
                              style={{
                                padding: '0.35rem 0.65rem',
                                backgroundColor: '#fff1f2',
                                color: '#e11d48',
                                border: '1px solid #fecdd3',
                                borderRadius: '6px',
                                fontSize: '0.75rem',
                                fontWeight: 600,
                                cursor: 'pointer',
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: '0.25rem',
                                transition: 'all 0.15s ease',
                              }}
                              title="Chặn nhân sự này không cho nhận ca trong tương lai"
                            >
                              Chặn
                            </button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                  </div>
                  {filteredWorkers.length > workerPageSize && (
                    <div className="ev-pagination-bar" style={{ marginTop: '0.85rem', borderRadius: 10, border: '1px solid #e2e8f0' }}>
                      <span className="ev-pagination-summary">
                        Hiển thị <strong>{(safeWorkerPage - 1) * workerPageSize + 1}–{Math.min(safeWorkerPage * workerPageSize, filteredWorkers.length)}</strong> / <strong>{filteredWorkers.length}</strong> nhân sự
                      </span>
                      <div className="ev-pagination-controls">
                        <button type="button" className="ev-page-btn" disabled={safeWorkerPage <= 1} onClick={() => setWorkerPage(safeWorkerPage - 1)}>
                          ← Trước
                        </button>
                        <span style={{ fontSize: '0.8rem', color: '#475569', padding: '0 0.5rem', fontWeight: 600 }}>
                          Trang {safeWorkerPage} / {workerTotalPages}
                        </span>
                        <button type="button" className="ev-page-btn" disabled={safeWorkerPage >= workerTotalPages} onClick={() => setWorkerPage(safeWorkerPage + 1)}>
                          Sau →
                        </button>
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="event-detail-modal-footer">
          <button type="button" className="btn-footer-primary" onClick={onClose}>
            {asPage ? 'Quay lại danh sách sự kiện' : 'Đóng'}
          </button>
        </div>

        {/* Modal Xem Hồ Sơ Năng Lực Worker */}
        <WorkerProfileModal
          isOpen={workerProfile.isOpen}
          loading={workerProfile.loading}
          error={workerProfile.error}
          profile={workerProfile.profile}
          onClose={workerProfile.closeProfile}
        />
    </div>
  );

  if (asPage) {
    return contentBox;
  }

  const modalNode = (
    <div className="event-detail-modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      {contentBox}
    </div>
  );

  return typeof document !== 'undefined' ? createPortal(modalNode, document.body) : null;
}
