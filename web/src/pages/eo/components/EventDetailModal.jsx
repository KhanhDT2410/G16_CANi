import React, { useState, useEffect } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';
import ShiftQrAndAttendancePanel from './ShiftQrAndAttendancePanel';
import './EventDetailModal.css';

export default function EventDetailModal({
  isOpen,
  onClose,
  event,
  allEvents = [],
  onSelectEvent,
  asPage = false,
  onOpenAddShift,
  onOpenInvite,
  onOpenConfirmPublish,
  onOpenEditEvent,
  onEditShift,
  onCancelShift,
  onNavigateToRecruitment,
  onReloadEvents,
  onOpenPaymentDeclaration,
}) {
  const [activeTab, setActiveTab] = useState('SHIFTS'); // 'SHIFTS' | 'WORKERS' | 'QR_ATTENDANCE'
  const [eventWorkers, setEventWorkers] = useState([]);
  const [loadingWorkers, setLoadingWorkers] = useState(false);
  const [searchWorker, setSearchWorker] = useState('');
  const [activeWorkspaceShiftId, setActiveWorkspaceShiftId] = useState(null);
  const [showCancelledShifts, setShowCancelledShifts] = useState(false);

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

  const rawShifts = event.shifts || [];
  const activeAndDraftShifts = rawShifts
    .filter((sh) => sh.status !== 'CANCELLED')
    .sort((a, b) => {
      const pA = a.status === 'ACTIVE' || a.status === 'OPEN' ? 1 : a.status === 'DRAFT' ? 2 : 1;
      const pB = b.status === 'ACTIVE' || b.status === 'OPEN' ? 1 : b.status === 'DRAFT' ? 2 : 1;
      return pA - pB;
    });
  const cancelledShifts = rawShifts.filter((sh) => sh.status === 'CANCELLED');
  const shifts = activeAndDraftShifts;
  const totalRequired = event.totalRequiredWorkers || shifts.reduce((s, sh) => s + (sh.requiredWorkers || 0), 0);
  const totalConfirmed = event.totalConfirmedWorkers || shifts.reduce((s, sh) => s + (sh.confirmedWorkers || 0), 0);
  const fillRate = totalRequired > 0 ? Math.round((totalConfirmed / totalRequired) * 100) : 0;
  const totalCost = event.totalEstimatedCost || shifts.reduce((s, sh) => {
    const hours = sh.startTime && sh.endTime ? Math.max(0.5, (new Date(sh.endTime) - new Date(sh.startTime)) / 3600000) : 4;
    return s + (hours * (sh.requiredWorkers || 1) * (sh.hourlyWage || 50000) + (sh.allowance || 0));
  }, 0);

  const formatTime = (timeStr, fallback = '--:--') => {
    if (!timeStr) return fallback;
    try {
      return new Date(timeStr).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
    } catch {
      return fallback;
    }
  };

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

  const getStatusBadge = (status) => {
    switch (status) {
      case 'ACTIVE':
        return <span className="detail-status-pill green">● Đang diễn ra</span>;
      case 'DRAFT':
        return <span className="detail-status-pill yellow">● Bản nháp</span>;
      case 'UPCOMING':
        return <span className="detail-status-pill blue">● Sắp diễn ra</span>;
      case 'APPROVED':
        return <span className="detail-status-pill purple">● Đã duyệt</span>;
      case 'COMPLETED':
        return <span className="detail-status-pill gray">● Đã hoàn thành</span>;
      case 'CANCELLED':
        return <span className="detail-status-pill red">● Đã hủy</span>;
      default:
        return <span className="detail-status-pill blue">● {status}</span>;
    }
  };

  const filteredWorkers = eventWorkers.filter((w) => {
    if (!searchWorker.trim()) return true;
    const q = searchWorker.toLowerCase();
    return (
      (w.workerName && w.workerName.toLowerCase().includes(q)) ||
      (w.jobType && w.jobType.toLowerCase().includes(q)) ||
      (w.workerPhone && w.workerPhone.includes(q))
    );
  });

  const selectedQrShift = shifts.find((s) => s.shiftId === activeWorkspaceShiftId) || shifts[0] || null;

  const contentBox = (
    <div
      className={asPage ? 'eo-widget-card animate-fade-in-up' : 'event-detail-modal-box'}
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
            {event.status === 'DRAFT' && (
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
                {onOpenConfirmPublish && (
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
            )}
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
            ) : (
              <div className="event-detail-icon-stage">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2">
                  <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                  <line x1="16" y1="2" x2="16" y2="6"></line>
                  <line x1="8" y1="2" x2="8" y2="6"></line>
                  <line x1="3" y1="10" x2="21" y2="10"></line>
                </svg>
              </div>
            )}
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' }}>
                <h3 className="event-detail-title">{event.name}</h3>
                {getStatusBadge(event.status)}
              </div>
              <p className="event-detail-sub">
                Mã sự kiện: <code>{event.eventId}</code> • Ngày tạo: {formatDate(event.createdAt || event.eventDate)}
              </p>
            </div>
          </div>
          {!asPage && (
            <button type="button" className="event-detail-close-btn" onClick={onClose}>
              &times;
            </button>
          )}
        </div>

        {/* Body */}
        <div className="event-detail-modal-body">
          {/* Thông tin cốt lõi */}
          <div className="event-detail-info-grid">
            <div className="info-item">
              <span className="info-label">Thời gian sự kiện</span>
              <span className="info-value">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2">
                  <circle cx="12" cy="12" r="10"></circle>
                  <polyline points="12 6 12 12 16 14"></polyline>
                </svg>
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
                  {event.mode === 'INVITE' || event.mode === 'INVITE_ONLY' ? 'Mời trực tiếp (Link / QR)' : 'Điều phối tự động (Matching)'}
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
            <div className="detail-kpi-card highlight-green">
              <span className="kpi-label">Nhân sự đã chốt ca</span>
              <span className="kpi-value">{totalConfirmed}</span>
              <span className="kpi-sub">đạt {fillRate}%</span>
            </div>
            <div className="detail-kpi-card">
              <span className="kpi-label">Dự toán chi phí</span>
              <span className="kpi-value text-emerald">
                {new Intl.NumberFormat('vi-VN').format(totalCost)} <small>đ</small>
              </span>
              <span className="kpi-sub">thù lao nhân sự</span>
            </div>
          </div>

          {/* Tab Switcher: Ca làm việc + Mã QR & Điểm danh vs Danh sách nhân sự */}
          <div className="event-detail-tabs-header">
            <button
              type="button"
              className={`event-detail-tab-btn ${activeTab === 'SHIFTS' ? 'active' : ''}`}
              onClick={() => setActiveTab('SHIFTS')}
            >
              Chi Tiết Ca Làm Việc & Điểm Danh QR ({shifts.length})
            </button>
            <button
              type="button"
              className={`event-detail-tab-btn ${activeTab === 'WORKERS' ? 'active' : ''}`}
              onClick={() => setActiveTab('WORKERS')}
            >
              Danh Sách Nhân Sự Sự Kiện ({eventWorkers.length || totalConfirmed})
            </button>
          </div>

          {/* TAB 1: DANH SÁCH CA LÀM VIỆC + TRẠM MÃ QR & ĐIỂM DANH CHUNG 1 TRANG */}
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
                    const isWorkspaceOpen = selectedQrShift?.shiftId === shift.shiftId;

                    return (
                      <div key={shift.shiftId || sIdx} style={{ display: 'flex', flexDirection: 'column' }}>
                        <div
                          className="detail-shift-card"
                          style={{
                            border: isWorkspaceOpen ? '2px solid #0d9488' : undefined,
                          }}
                        >
                          <div className="shift-card-header">
                            <div className="shift-card-title-group">
                              {shift.imageUrl && (
                                <img
                                  src={shift.imageUrl}
                                  alt={shift.jobType}
                                  style={{ width: 36, height: 36, borderRadius: 6, objectFit: 'cover' }}
                                  onError={(e) => { e.target.style.display = 'none'; }}
                                />
                              )}
                              <span className="shift-job-badge">{shift.jobType}</span>
                              <span className="shift-time-badge">
                                {formatTime(shift.startTime, '08:00')} - {formatTime(shift.endTime, '12:00')}
                              </span>
                              {shift.dressCode && (
                                <span className="shift-dresscode-tag">Đồng phục: {shift.dressCode}</span>
                              )}
                            </div>
                            <div className="shift-wage-tag">
                              {new Intl.NumberFormat('vi-VN').format(shift.hourlyWage || 50000)} đ/h
                              {shift.allowance > 0 && ` (+${new Intl.NumberFormat('vi-VN').format(shift.allowance)}đ phụ cấp)`}
                            </div>
                          </div>

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

                              <button
                                type="button"
                                className="btn-shift-action emerald"
                                onClick={() => setActiveWorkspaceShiftId(shift.shiftId)}
                                title="Xem Mã QR Động & Điểm Danh Trực Tiếp của ca này ngay bên dưới"
                              >
                                {isWorkspaceOpen ? 'Đang mở Mã QR & Điểm danh bên dưới' : 'Chọn xem Mã QR & Điểm danh'}
                              </button>
                            </div>
                          </div>
                        </div>
                      </div>
                    );
                  })}

                  {/* TRẠM MÃ QR ĐỘNG & BẢNG ĐIỂM DANH TRỰC TIẾP ĐẶT CHUNG TRÊN CÙNG 1 TRANG CHI TIẾT SỰ KIỆN */}
                  {selectedQrShift && (
                    <div
                      style={{
                        marginTop: '1.25rem',
                        paddingTop: '1.25rem',
                        borderTop: '2px solid #e2e8f0',
                      }}
                    >
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          flexWrap: 'wrap',
                          gap: '0.75rem',
                          marginBottom: '0.75rem',
                        }}
                      >
                        <div>
                          <h4 style={{ margin: 0, fontSize: '1.05rem', fontWeight: 800, color: '#0f172a' }}>
                            Trạm Mã QR Điểm Danh & Bảng Điểm Danh Trực Tiếp (Chung 1 Trang)
                          </h4>
                          <p style={{ margin: '0.2rem 0 0', fontSize: '0.82rem', color: '#64748b' }}>
                            Mã QR động xoay vòng 20s và danh sách điểm danh nhân sự hiển thị trực tiếp ngay trên trang chi tiết sự kiện.
                          </p>
                        </div>
                        {shifts.length > 1 && (
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                            <span style={{ fontSize: '0.82rem', fontWeight: 600, color: '#475569' }}>Đang hiển thị ca:</span>
                            <select
                              className="eo-form-input"
                              style={{ width: 'auto', minWidth: '220px', padding: '0.4rem 0.75rem', fontSize: '0.85rem', fontWeight: 700 }}
                              value={selectedQrShift.shiftId}
                              onChange={(e) => setActiveWorkspaceShiftId(e.target.value)}
                            >
                              {shifts.map((s) => (
                                <option key={s.shiftId} value={s.shiftId}>
                                  {s.jobType} ({formatTime(s.startTime, '08:00')} - {formatTime(s.endTime, '12:00')})
                                </option>
                              ))}
                            </select>
                          </div>
                        )}
                      </div>

                      <ShiftQrAndAttendancePanel
                        shift={selectedQrShift}
                        event={event}
                        onSelectShift={(nextShift) => setActiveWorkspaceShiftId(nextShift.shiftId)}
                        onReloadEvents={onReloadEvents}
                        onOpenPaymentDeclaration={onOpenPaymentDeclaration}
                      />
                    </div>
                  )}

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

          {/* TAB 2: DANH SÁCH NHÂN SỰ SỰ KIỆN (WORKFORCE LIST) */}
          {activeTab === 'WORKERS' && (
            <div className="event-detail-workers-section">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.5rem' }}>
                <h4 style={{ margin: 0, fontSize: '1rem', fontWeight: 700, color: '#1e293b' }}>
                  Danh Sách Nhân Sự Tham Gia Sự Kiện ({eventWorkers.length})
                </h4>
                <input
                  type="text"
                  placeholder="Tìm theo tên, vị trí ca, SĐT..."
                  className="eo-form-input"
                  style={{ width: '240px', padding: '0.4rem 0.75rem', fontSize: '0.825rem' }}
                  value={searchWorker}
                  onChange={(e) => setSearchWorker(e.target.value)}
                />
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
                <div className="event-workers-list">
                  {filteredWorkers.map((w, idx) => {
                    const isReliableHigh = (w.workerReliabilityScore || 100) >= 90;
                    return (
                      <div key={w.assignmentId || idx} className="event-worker-card">
                        <div className="worker-card-left">
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

                          <div className="worker-info-main">
                            <div className="worker-name-row">
                              <span className="worker-name-text">{w.workerName}</span>
                              <span className="worker-age-badge">
                                {w.workerAge ? `${w.workerAge} tuổi` : '22 tuổi'} {w.workerDob ? `(${w.workerDob})` : ''}
                              </span>
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
                          </div>
                        </div>

                        {/* Trạng thái nhân sự */}
                        <div>
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
                              borderRadius: '999px',
                              fontSize: '0.775rem',
                              fontWeight: 700,
                            }}>
                              ✓ Đã nhận ca (Chờ check-in)
                            </span>
                          ) : (
                            <span style={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              padding: '0.35rem 0.75rem',
                              backgroundColor: '#f1f5f9',
                              color: '#64748b',
                              borderRadius: '999px',
                              fontSize: '0.775rem',
                              fontWeight: 600,
                            }}>
                              {w.assignmentStatus || 'Đã phân công'}
                            </span>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
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
    </div>
  );

  if (asPage) {
    return contentBox;
  }

  return (
    <div className="event-detail-modal-overlay" onClick={onClose}>
      {contentBox}
    </div>
  );
}
