import React from 'react';
import './EventDetailModal.css';

export default function EventDetailModal({
  isOpen,
  onClose,
  event,
  onOpenAddShift,
  onOpenQr,
  onOpenInvite,
  onOpenAttendance,
  onNavigateToRecruitment,
}) {
  if (!isOpen || !event) return null;

  const shifts = event.shifts || [];
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

  return (
    <div className="event-detail-modal-overlay" onClick={onClose}>
      <div className="event-detail-modal-box" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="event-detail-modal-header">
          <div className="event-detail-header-left">
            <div className="event-detail-icon-stage">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                <line x1="16" y1="2" x2="16" y2="6"></line>
                <line x1="8" y1="2" x2="8" y2="6"></line>
                <line x1="3" y1="10" x2="21" y2="10"></line>
              </svg>
            </div>
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
          <button type="button" className="event-detail-close-btn" onClick={onClose}>
            &times;
          </button>
        </div>

        {/* Body */}
        <div className="event-detail-modal-body">
          {/* Thông tin cốt lõi */}
          <div className="event-detail-info-grid">
            <div className="info-item">
              <span className="info-label">Thời gian tổ chức</span>
              <span className="info-value">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2">
                  <circle cx="12" cy="12" r="10"></circle>
                  <polyline points="12 6 12 12 16 14"></polyline>
                </svg>
                {formatDate(event.eventDate)}
              </span>
            </div>

            <div className="info-item">
              <span className="info-label">Địa điểm tổ chức</span>
              <span className="info-value" title={event.venueAddress}>
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="#dc2626" strokeWidth="2">
                  <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
                  <circle cx="12" cy="10" r="3"></circle>
                </svg>
                {event.venueAddress || 'Chưa cập nhật địa điểm'}
              </span>
            </div>

            <div className="info-item">
              <span className="info-label">Chế độ tuyển dụng</span>
              <span className="info-value">
                <span className="mode-badge-pill">
                  {event.mode === 'INVITE' ? 'Mời trực tiếp (Link / QR)' : 'Matching Radar AI (15km)'}
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

          {/* Danh sách các ca làm việc & Nhân sự */}
          <div className="event-detail-shifts-section">
            <div className="shifts-section-header">
              <h4 className="shifts-title">
                Chi Tiết Ca Làm Việc & Điều Phối Nhân Sự ({shifts.length})
              </h4>
              <button
                type="button"
                className="btn-add-shift-pill"
                onClick={() => {
                  onClose();
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
                    onClose();
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

                  return (
                    <div key={shift.shiftId || sIdx} className="detail-shift-card">
                      <div className="shift-card-header">
                        <div className="shift-card-title-group">
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
                            className="btn-shift-action blue"
                            onClick={() => {
                              onClose();
                              onOpenQr(shift, event);
                            }}
                            title="Hiển thị mã QR Check-in / Check-out"
                          >
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                              <rect x="3" y="3" width="7" height="7"></rect>
                              <rect x="14" y="3" width="7" height="7"></rect>
                              <rect x="14" y="14" width="7" height="7"></rect>
                              <rect x="3" y="14" width="7" height="7"></rect>
                            </svg>
                            Mã QR Điểm Danh
                          </button>

                          <button
                            type="button"
                            className="btn-shift-action purple"
                            onClick={() => {
                              onClose();
                              onOpenInvite(shift, event);
                            }}
                            title="Lấy link & mã mời trực tiếp cho nhân sự"
                          >
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                              <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path>
                              <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path>
                            </svg>
                            Mã Mời Nhân Sự
                          </button>

                          <button
                            type="button"
                            className="btn-shift-action emerald"
                            onClick={() => {
                              onClose();
                              onOpenAttendance(shift, event);
                            }}
                            title="Theo dõi bảng điểm danh & xác nhận duyệt công"
                          >
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                              <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                              <circle cx="9" cy="7" r="4"></circle>
                              <polyline points="16 11 18 13 22 9"></polyline>
                            </svg>
                            Danh Sách Điểm Danh
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="event-detail-modal-footer">
          {onNavigateToRecruitment && (
            <button
              type="button"
              className="btn-footer-secondary"
              onClick={() => {
                onClose();
                onNavigateToRecruitment();
              }}
            >
              Chuyển sang Quản Lý Tuyển Dụng &rarr;
            </button>
          )}
          <button type="button" className="btn-footer-primary" onClick={onClose}>
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}

