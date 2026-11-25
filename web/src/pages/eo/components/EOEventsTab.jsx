import React, { useState, useMemo, useEffect } from 'react';
import EOEventDataState from './EOEventDataState';
import {
  getEventStatusPriority,
  getDynamicEventStatus,
  getDynamicShiftStatus,
  getEventUnderstaffedSummary,
  resolveEventEstimatedCost,
} from '../utils/eventHelpers';
import './EOEventsTab.css';

const QUICK_STATUS_FILTERS = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'IN_PROGRESS', label: 'Đang diễn ra' },
  { key: 'RECRUITING', label: 'Đang tìm nhân sự' },
  { key: 'READY', label: 'Đủ nhân sự' },
  { key: 'DRAFT', label: 'Bản nháp' },
  { key: 'COMPLETED', label: 'Đã kết thúc' },
];

function formatEventTimeRange(evt) {
  const start = evt?.startTime || evt?.eventDate;
  const end = evt?.endTime;
  if (!start) return 'Chưa thiết lập thời gian';
  const s = new Date(start);
  if (isNaN(s.getTime())) return 'Chưa thiết lập thời gian';

  const timeFmt = { hour: '2-digit', minute: '2-digit' };
  const dateStr = s.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
  const startTimeStr = s.toLocaleTimeString('vi-VN', timeFmt);

  if (end) {
    const e = new Date(end);
    if (!isNaN(e.getTime())) {
      const sameDay = s.toDateString() === e.toDateString();
      const endTimeStr = e.toLocaleTimeString('vi-VN', timeFmt);
      if (sameDay) {
        return `${dateStr} • ${startTimeStr} – ${endTimeStr}`;
      }
      const endDateStr = e.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
      return `${startTimeStr} ${dateStr} – ${endTimeStr} ${endDateStr}`;
    }
  }
  return `${dateStr} • ${startTimeStr}`;
}

function getEventMonogram(name = '') {
  const words = String(name || '')
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  if (words.length === 0) return 'SK';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}

function EventThumb({ imageUrl, name }) {
  const [imgFailed, setImgFailed] = useState(false);
  return (
    <div className="ev-thumb-box">
      {imageUrl && !imgFailed ? (
        <img
          src={imageUrl}
          alt={name}
          onError={() => setImgFailed(true)}
        />
      ) : (
        <span className="ev-thumb-monogram">{getEventMonogram(name)}</span>
      )}
    </div>
  );
}

export default function EOEventsTab({
  loading,
  loadError,
  allEvents = [],
  filteredEvents = [],
  paginatedEvents,
  currentPage: controlledPage,
  setCurrentPage: setControlledPage,
  pageSize = 10,
  totalPages: controlledTotalPages,
  searchQuery,
  setSearchQuery,
  statusFilter,
  setStatusFilter,
  sortBy,
  setSortBy,
  expandedEventId,
  setExpandedEventId,
  activeSubTab = 'list',
  onSubTabChange,
  onOpenEventDetail,
  onOpenShiftWorkspace,
  onDeleteEvent,
  onOpenCreateEvent,
  onOpenAddShift,
  onOpenEditEvent,
  onCancelEvent,
  onOpenConfirmPublish,
  onOpenQr,
  onOpenInvite,
  onOpenAttendance,
  onEditShift,
  onCancelShift,
  onReloadEvents,
  onOpenPaymentDeclaration,
}) {
  const [activeMenuEventId, setActiveMenuEventId] = useState(null);
  const [internalPage, setInternalPage] = useState(1);

  const currentPage = controlledPage ?? internalPage;
  const setCurrentPage = setControlledPage ?? setInternalPage;
  const effectivePageSize = Math.max(1, Number(pageSize) || 10);

  useEffect(() => {
    setInternalPage(1);
  }, [searchQuery, statusFilter, sortBy]);

  const totalPages =
    controlledTotalPages ?? Math.max(1, Math.ceil(filteredEvents.length / effectivePageSize));
  const safeCurrentPage = Math.min(Math.max(1, currentPage), totalPages);
  const startItemIndex = (safeCurrentPage - 1) * effectivePageSize;
  const endItemIndex = Math.min(startItemIndex + effectivePageSize, filteredEvents.length);

  const displayedEvents = useMemo(() => {
    if (Array.isArray(paginatedEvents) && controlledPage !== undefined) {
      return paginatedEvents;
    }
    return filteredEvents.slice(startItemIndex, endItemIndex);
  }, [paginatedEvents, controlledPage, filteredEvents, startItemIndex, endItemIndex]);

  const toggleKebabMenu = (e, eventId) => {
    e.stopPropagation();
    setActiveMenuEventId((prev) => (prev === eventId ? null : eventId));
  };

  const closeMenu = () => setActiveMenuEventId(null);

  const nonCancelledEvents = useMemo(
    () =>
      [...allEvents]
        .filter((e) => e.status !== 'CANCELLED')
        .sort((a, b) => getEventStatusPriority(a.status) - getEventStatusPriority(b.status)),
    [allEvents]
  );

  const cancelledEventsList = useMemo(
    () => allEvents.filter((e) => e.status === 'CANCELLED'),
    [allEvents]
  );

  const cancelledShiftsFromActiveEvents = useMemo(() => {
    const list = [];
    allEvents.forEach((evt) => {
      if (evt.status !== 'CANCELLED' && Array.isArray(evt.shifts)) {
        evt.shifts.forEach((sh) => {
          if (sh.status === 'CANCELLED') {
            list.push({ ...sh, eventName: evt.name, eventId: evt.eventId });
          }
        });
      }
    });
    return list;
  }, [allEvents]);

  // Tổng hợp KPI thực tế từ danh sách sự kiện đang quản lý
  const listSummary = useMemo(() => {
    let recruitingCount = 0;
    let inProgressCount = 0;
    let totalReq = 0;
    let totalConf = 0;

    nonCancelledEvents.forEach((evt) => {
      const dyn = getDynamicEventStatus(evt);
      if (dyn.code === 'RECRUITING') recruitingCount += 1;
      if (dyn.code === 'IN_PROGRESS') inProgressCount += 1;
      totalReq += Number(evt.totalRequiredWorkers || 0);
      totalConf += Number(evt.totalConfirmedWorkers || 0);
    });

    const fillRate = totalReq > 0 ? Math.min(100, Math.round((totalConf / totalReq) * 100)) : 0;
    return {
      totalActive: nonCancelledEvents.length,
      recruitingCount,
      inProgressCount,
      totalReq,
      totalConf,
      fillRate,
    };
  }, [nonCancelledEvents]);

  const formatStatusBadge = (evtOrStatus) => {
    const dyn =
      typeof evtOrStatus === 'object' && evtOrStatus !== null
        ? getDynamicEventStatus(evtOrStatus)
        : getDynamicEventStatus({ status: evtOrStatus });

    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: '0.2rem' }}>
        <span className={`table-status-pill ${dyn.colorClass || 'blue'}`}>
          <span className="status-dot-solid" />
          {dyn.label}
        </span>
        {dyn.subText && typeof evtOrStatus === 'object' && (
          <span style={{ fontSize: '0.72rem', color: '#64748b', fontWeight: 500 }}>
            {dyn.subText}
          </span>
        )}
      </div>
    );
  };

  const handleSelectSubTab = (subKey) => {
    onSubTabChange?.(subKey);
    if (subKey === 'cancelled') {
      setStatusFilter('CANCELLED');
    } else if (statusFilter === 'CANCELLED') {
      setStatusFilter('ALL');
    }
  };

  const archivedCount = cancelledEventsList.length + cancelledShiftsFromActiveEvents.length;
  const hasEventData = !loading && !loadError;

  return (
    <div className="ev-page-stack" onClick={closeMenu}>
      <header className="ev-page-heading">
        <div>
          <h1>Quản lý sự kiện</h1>
          <p>Theo dõi lịch tổ chức, ca làm và tiến độ tuyển nhân sự.</p>
        </div>
        <button type="button" className="eo-workspace-create" onClick={onOpenCreateEvent}>
          <span aria-hidden="true">+</span> Tạo sự kiện
        </button>
      </header>
      {/* Thanh Điều Hướng Phân Khu Sự Kiện */}
      <nav className="ev-subnav-card" aria-label="Các mục quản lý sự kiện">
        <div className="ev-subnav-tabs">
          <button
            type="button"
            className={`ev-subnav-btn ${activeSubTab === 'list' ? 'is-active' : ''}`}
            aria-current={activeSubTab === 'list' ? 'page' : undefined}
            onClick={() => handleSelectSubTab('list')}
          >
            <span>Danh sách sự kiện</span>
            {hasEventData && <span className="ev-subnav-count">{nonCancelledEvents.length}</span>}
          </button>

          <button
            type="button"
            className={`ev-subnav-btn ${activeSubTab === 'detail' ? 'is-active' : ''}`}
            disabled={!hasEventData || allEvents.length === 0}
            onClick={() => handleSelectSubTab('detail')}
          >
            <span>Chi tiết sự kiện &amp; Điểm danh</span>
          </button>

          <button
            type="button"
            className={`ev-subnav-btn is-danger ${activeSubTab === 'cancelled' ? 'is-active' : ''}`}
            aria-current={activeSubTab === 'cancelled' ? 'page' : undefined}
            onClick={() => handleSelectSubTab('cancelled')}
          >
            <span>Sự kiện đã hủy</span>
            {hasEventData && archivedCount > 0 && <span className="ev-subnav-count">{archivedCount}</span>}
          </button>
        </div>

      </nav>

      {!hasEventData && <EOEventDataState loading={loading} onRetry={onReloadEvents} />}

      {/* PHÂN KHU LƯU TRỮ SỰ KIỆN ĐÃ HỦY */}
      {hasEventData && activeSubTab === 'cancelled' && (
        <div className="eo-widget-card animate-fade-in-up" style={{ padding: '1.5rem' }}>
          <div style={{ marginBottom: '1.25rem' }}>
            <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 800, color: '#991b1b' }}>
              Khu Vực Lưu Trữ Sự Kiện &amp; Ca Làm Việc Đã Hủy
            </h3>
            <p style={{ margin: '0.25rem 0 0', fontSize: '0.84rem', color: '#64748b' }}>
              Các sự kiện và ca làm việc đã hủy được lưu trữ tại đây nhằm phục vụ việc đối soát lịch sử.
            </p>
          </div>

          {archivedCount === 0 ? (
            <div style={{ padding: '2.5rem', textAlign: 'center', color: '#94a3b8' }}>
              Hiện không có sự kiện hay ca làm việc nào bị hủy.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              {cancelledEventsList.length > 0 && (
                <div>
                  <h4 style={{ margin: '0 0 0.75rem', fontSize: '0.92rem', color: '#dc2626', fontWeight: 700 }}>
                    Sự kiện đã hủy ({cancelledEventsList.length})
                  </h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                    {cancelledEventsList.map((evt) => (
                      <div
                        key={evt.eventId}
                        style={{
                          padding: '0.9rem 1.15rem',
                          borderRadius: '10px',
                          backgroundColor: '#fef2f2',
                          border: '1px solid #fecaca',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          flexWrap: 'wrap',
                          gap: '0.75rem',
                        }}
                      >
                        <div>
                          <div style={{ fontWeight: 700, color: '#64748b', textDecoration: 'line-through' }}>
                            {evt.name}
                          </div>
                          <div style={{ fontSize: '0.8rem', color: '#94a3b8', marginTop: '0.2rem' }}>
                            Địa chỉ: {evt.venueAddress} • {evt.totalShifts || 0} ca làm việc
                          </div>
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                          {formatStatusBadge('CANCELLED')}
                          <span
                            style={{
                              background: '#fee2e2',
                              color: '#991b1b',
                              borderRadius: '8px',
                              padding: '0.35rem 0.75rem',
                              fontSize: '0.78rem',
                              fontWeight: 700,
                            }}
                          >
                            Đã lưu trữ hồ sơ
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {cancelledShiftsFromActiveEvents.length > 0 && (
                <div>
                  <h4 style={{ margin: '0 0 0.75rem', fontSize: '0.92rem', color: '#dc2626', fontWeight: 700 }}>
                    Ca làm việc đã hủy trong các sự kiện ({cancelledShiftsFromActiveEvents.length})
                  </h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                    {cancelledShiftsFromActiveEvents.map((sh) => (
                      <div
                        key={sh.shiftId}
                        style={{
                          padding: '0.8rem 1.15rem',
                          borderRadius: '10px',
                          backgroundColor: '#fff1f2',
                          border: '1px solid #fecdd3',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          flexWrap: 'wrap',
                          gap: '0.75rem',
                        }}
                      >
                        <div>
                          <strong style={{ color: '#64748b', textDecoration: 'line-through' }}>
                            Ca: {sh.jobType}
                          </strong>
                          <span style={{ marginLeft: '0.75rem', fontSize: '0.82rem', color: '#94a3b8' }}>
                            Thuộc sự kiện: <strong>{sh.eventName}</strong>
                          </span>
                        </div>
                        <span className="table-status-pill red">Đã hủy ca</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* PHÂN KHU CHÍNH: DANH SÁCH SỰ KIỆN */}
      {hasEventData && activeSubTab === 'list' && (
        <>
          {/* Dải chỉ số tổng quan nhanh từ dữ liệu thật */}
          <div className="ev-kpi-grid">
            <div className="ev-kpi-card">
              <span className="ev-kpi-label">Sự kiện đang quản lý</span>
              <strong className="ev-kpi-val">{listSummary.totalActive}</strong>
              <span className="ev-kpi-sub">Không bao gồm sự kiện đã hủy</span>
            </div>

            <div className="ev-kpi-card">
              <span className="ev-kpi-label">Đang diễn ra</span>
              <strong className="ev-kpi-val is-emerald">{listSummary.inProgressCount}</strong>
              <span className="ev-kpi-sub">Theo khung giờ thực tế</span>
            </div>

            <div className="ev-kpi-card">
              <span className="ev-kpi-label">Đang tìm nhân sự</span>
              <strong className="ev-kpi-val is-amber">{listSummary.recruitingCount}</strong>
              <span className="ev-kpi-sub">Đang mở tuyển / thiếu người</span>
            </div>

            <div className="ev-kpi-card">
              <span className="ev-kpi-label">Tỷ lệ chốt nhân sự</span>
              <strong className="ev-kpi-val is-blue">{listSummary.fillRate}%</strong>
              <span className="ev-kpi-sub">
                {listSummary.totalConf}/{listSummary.totalReq} vị trí đã xác nhận
              </span>
            </div>
          </div>

          <section className="eo-events-section-card animate-fade-in-up">
            {/* Header Bar: Tiêu đề + Tìm kiếm + Sắp xếp */}
            <div className="table-card-header-bar">
              <div className="table-header-title-group">
                <h3 className="table-card-heading">
                  Danh sách sự kiện
                </h3>
                <span className="ev-result-count" aria-live="polite">{filteredEvents.length} kết quả</span>
              </div>

              <div className="table-header-actions-group">
                <div className="table-search-input-wrapper">
                  <input
                    type="text"
                    aria-label="Tìm theo tên hoặc địa điểm sự kiện"
                    placeholder="Tìm theo tên hoặc địa điểm..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="table-search-input"
                  />
                </div>

                <div className="table-select-wrapper">
                  <select
                    value={sortBy}
                    onChange={(e) => setSortBy(e.target.value)}
                    className="table-filter-select"
                    aria-label="Sắp xếp danh sách sự kiện"
                  >
                    <option value="NEWEST">Mới nhất</option>
                    <option value="OLDEST">Cũ nhất</option>
                    <option value="UPCOMING">Sắp diễn ra</option>
                  </select>
                </div>
              </div>
            </div>

            {/* Thanh chọn nhanh trạng thái */}
            <div className="ev-filter-chips-bar" role="group" aria-label="Lọc trạng thái sự kiện">
              {QUICK_STATUS_FILTERS.map((f) => {
                const active = (statusFilter === 'CANCELLED' ? 'ALL' : statusFilter) === f.key;
                return (
                  <button
                    key={f.key}
                    type="button"
                    className={`ev-filter-chip ${active ? 'is-active' : ''}`}
                    aria-pressed={active}
                    onClick={() => setStatusFilter(f.key)}
                  >
                    {f.label}
                  </button>
                );
              })}
            </div>

            {/* Bảng Danh Sách Sự Kiện */}
            <div className="table-responsive-wrapper">
              {filteredEvents.length === 0 ? (
                <div className="eo-empty-state-card">
                  <p className="empty-text">
                    {searchQuery || statusFilter !== 'ALL'
                      ? 'Không có sự kiện nào khớp với bộ lọc hiện tại.'
                      : 'Chưa có sự kiện nào trong danh sách.'}
                  </p>
                  {searchQuery || statusFilter !== 'ALL' ? (
                    <button
                      type="button"
                      className="eo-btn-secondary"
                      onClick={() => {
                        setSearchQuery('');
                        setStatusFilter('ALL');
                      }}
                    >
                      Xóa bộ lọc
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="table-btn-create-event"
                      onClick={onOpenCreateEvent}
                    >
                      + Tạo Sự Kiện Đầu Tiên
                    </button>
                  )}
                </div>
              ) : (
                <table className="cani-modern-table">
                  <thead>
                    <tr>
                      <th style={{ width: '28%' }}>Sự kiện &amp; Thời gian</th>
                      <th style={{ width: '19%' }}>Địa điểm</th>
                      <th style={{ width: '21%' }}>Nhân sự theo ca</th>
                      <th style={{ width: '11%' }}>Dự toán</th>
                      <th style={{ width: '12%' }}>Trạng thái</th>
                      <th style={{ width: '9%', textAlign: 'center' }}>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {displayedEvents.map((evt) => {
                      const isMenuOpen = activeMenuEventId === evt.eventId;
                      const activeShifts = Array.isArray(evt.shifts)
                        ? evt.shifts.filter((s) => s.status !== 'CANCELLED')
                        : [];
                      const shiftRequiredSum = activeShifts.reduce(
                        (sum, sh) => sum + Number(sh.requiredWorkers || 0),
                        0
                      );
                      const shiftConfirmedSum = activeShifts.reduce(
                        (sum, sh) => sum + Number(sh.confirmedWorkers || 0),
                        0
                      );
                      const requiredTotal = Math.max(Number(evt.totalRequiredWorkers || 0), shiftRequiredSum);
                      const confirmedTotal = Math.max(Number(evt.totalConfirmedWorkers || 0), shiftConfirmedSum);
                      const fillPct =
                        requiredTotal > 0 ? Math.min(100, Math.round((confirmedTotal / requiredTotal) * 100)) : 0;
                      const estCost = resolveEventEstimatedCost(evt);
                      const understaffedSummary = getEventUnderstaffedSummary(evt);

                      return (
                        <tr
                          key={evt.eventId}
                          className="event-table-row"
                          onClick={() => onOpenEventDetail?.(evt)}
                          title="Nhấn để xem chi tiết sự kiện &amp; quản lý ca"
                        >
                          {/* Cột 1: Poster nhỏ gọn + Tên + Thời gian */}
                          <td>
                            <div
                              className="ev-info-cell"
                              onClick={(e) => {
                                e.stopPropagation();
                                onOpenEventDetail?.(evt);
                              }}
                              title="Nhấn để xem chi tiết sự kiện & nhân sự"
                            >
                              <EventThumb key={evt.imageUrl} imageUrl={evt.imageUrl} name={evt.name} />

                              <div className="ev-title-stack">
                                <span className="ev-title-link" title={evt.name}>
                                  {evt.name}
                                </span>
                                <span className="ev-time-line">
                                  {formatEventTimeRange(evt)}
                                </span>
                                <span className="ev-detail-hint">
                                  Mở chi tiết &amp; quản lý ca &rarr;
                                </span>
                              </div>
                            </div>
                          </td>

                          {/* Cột 2: Địa điểm */}
                          <td>
                            <div className="table-venue-cell" style={{ alignItems: 'flex-start' }}>
                              <span className="ev-venue-clamp" title={evt.venueAddress}>
                                {evt.venueAddress || 'Chưa cập nhật địa chỉ'}
                              </span>
                            </div>
                          </td>

                          {/* Cột 3: Tiến độ nhân sự & từng ca */}
                          <td>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                              <div className="table-workers-cell" style={{ justifyContent: 'space-between' }}>
                                <span className="workers-count" style={{ fontWeight: 700, color: '#0f172a', fontSize: '0.82rem' }}>
                                  {confirmedTotal}/{requiredTotal} người
                                </span>
                                <span style={{ fontSize: '0.73rem', fontWeight: 700, color: '#2563eb' }}>
                                  {fillPct}%
                                </span>
                              </div>

                              <div className="ev-fill-bar-track">
                                <div className="ev-fill-bar-progress" style={{ width: `${fillPct}%` }} />
                              </div>

                              {activeShifts.length > 0 ? (
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                                  {activeShifts.slice(0, 3).map((sh, sIdx) => {
                                    const shStatus = getDynamicShiftStatus(sh, evt.status);
                                    return (
                                      <div
                                        key={sh.shiftId || sIdx}
                                        style={{
                                          display: 'flex',
                                          alignItems: 'center',
                                          justifyContent: 'space-between',
                                          gap: '0.4rem',
                                          fontSize: '0.72rem',
                                          padding: '0.18rem 0.45rem',
                                          borderRadius: '6px',
                                          backgroundColor: shStatus.bg,
                                          border: `1px solid ${shStatus.border}`,
                                          color: shStatus.color,
                                          fontWeight: 600,
                                        }}
                                      >
                                        <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                          Ca {sIdx + 1} ({sh.jobType}): <strong>{sh.confirmedWorkers || 0}/{sh.requiredWorkers || 0}</strong>
                                        </span>
                                        <span style={{ fontSize: '0.68rem', opacity: 0.9, whiteSpace: 'nowrap' }}>
                                          • {shStatus.label}
                                        </span>
                                      </div>
                                    );
                                  })}
                                  {activeShifts.length > 3 && (
                                    <span style={{ fontSize: '0.71rem', color: '#64748b', fontWeight: 600 }}>
                                      +{activeShifts.length - 3} ca khác
                                    </span>
                                  )}
                                </div>
                              ) : (
                                <span style={{ fontSize: '0.73rem', color: '#94a3b8', fontStyle: 'italic' }}>
                                  {evt.totalShifts ? `${evt.totalShifts} ca làm việc` : 'Chưa thiết lập ca'}
                                </span>
                              )}
                            </div>
                          </td>

                          {/* Cột 4: Dự toán thực tế (không hardcode 1020000) */}
                          <td>
                            <span className="table-cost-amount">
                              {estCost.toLocaleString('vi-VN')} đ
                            </span>
                          </td>

                          {/* Cột 5: Trạng thái & Đăng tuyển nhanh */}
                          <td onClick={(e) => e.stopPropagation()}>
                            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: '0.35rem' }}>
                              {formatStatusBadge(evt)}
                              {understaffedSummary.count > 0 && (
                                <span
                                  style={{
                                    display: 'inline-flex',
                                    alignItems: 'center',
                                    gap: '0.3rem',
                                    backgroundColor: '#fff1f2',
                                    color: '#be123c',
                                    border: '1px solid #fda4af',
                                    borderRadius: '999px',
                                    padding: '0.18rem 0.55rem',
                                    fontSize: '0.71rem',
                                    fontWeight: 700,
                                    whiteSpace: 'nowrap',
                                  }}
                                  title={`Có ${understaffedSummary.count} ca đang thiếu nhân sự sát giờ`}
                                >
                                  Thiếu nhân sự sát giờ ({understaffedSummary.count} ca)
                                </span>
                              )}
                              {(() => {
                                if (evt.status !== 'DRAFT') return null;
                                const dyn = getDynamicEventStatus(evt);
                                const shiftCnt = activeShifts.length || Number(evt.totalShifts || 0);
                                if (dyn.isExpiredDraft) {
                                  return (
                                    <button
                                      type="button"
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        onOpenEditEvent?.(evt);
                                      }}
                                      style={{
                                        background: '#fff7ed',
                                        color: '#c2410c',
                                        border: '1px solid #fed7aa',
                                        borderRadius: '6px',
                                        padding: '0.28rem 0.65rem',
                                        fontSize: '0.73rem',
                                        fontWeight: 700,
                                        cursor: 'pointer',
                                        whiteSpace: 'nowrap',
                                      }}
                                    >
                                      Sửa lại thời gian
                                    </button>
                                  );
                                }
                                if (shiftCnt === 0) {
                                  return (
                                    <button
                                      type="button"
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        onOpenAddShift?.(evt.eventId);
                                      }}
                                      style={{
                                        background: '#eff6ff',
                                        color: '#1d4ed8',
                                        border: '1px solid #bfdbfe',
                                        borderRadius: '6px',
                                        padding: '0.28rem 0.65rem',
                                        fontSize: '0.73rem',
                                        fontWeight: 700,
                                        cursor: 'pointer',
                                        whiteSpace: 'nowrap',
                                      }}
                                    >
                                      + Thêm ca làm
                                    </button>
                                  );
                                }
                                return (
                                  <button
                                    type="button"
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      onOpenConfirmPublish(evt);
                                    }}
                                    style={{
                                      background: '#2563eb',
                                      color: '#ffffff',
                                      border: 'none',
                                      borderRadius: '6px',
                                      padding: '0.28rem 0.65rem',
                                      fontSize: '0.73rem',
                                      fontWeight: 700,
                                      cursor: 'pointer',
                                      whiteSpace: 'nowrap',
                                    }}
                                  >
                                    Đăng Tuyển Ngay
                                  </button>
                                );
                              })()}
                            </div>
                          </td>

                          {/* Cột 6: Thao tác trực tiếp + Menu Kebab */}
                          <td style={{ textAlign: 'center', position: 'relative' }} onClick={(e) => e.stopPropagation()}>
                            <div className="ev-actions-cell">
                              <button
                                type="button"
                                className="ev-quick-detail-btn"
                                onClick={() => onOpenEventDetail?.(evt, 'SHIFTS')}
                              >
                                Chi tiết
                              </button>
                              <button
                                type="button"
                                className="btn-kebab-menu"
                                onClick={(e) => toggleKebabMenu(e, evt.eventId)}
                                title="Thêm tùy chọn"
                                aria-expanded={isMenuOpen}
                              >
                                ⋮
                              </button>
                            </div>

                            {isMenuOpen && (
                              <div className="table-kebab-dropdown" onClick={(e) => e.stopPropagation()}>
                                <button
                                  type="button"
                                  className="dropdown-item"
                                  style={{ color: '#2563eb', fontWeight: 700 }}
                                  onClick={() => {
                                    onOpenEventDetail?.(evt, 'SHIFTS');
                                    closeMenu();
                                  }}
                                >
                                  <span>Chi tiết sự kiện &amp; nhân sự</span>
                                </button>

                                <button
                                  type="button"
                                  className="dropdown-item"
                                  style={{ color: '#0d9488', fontWeight: 700 }}
                                  onClick={() => {
                                    onOpenEventDetail?.(evt, 'QR_ATTENDANCE');
                                    closeMenu();
                                  }}
                                >
                                  <span>Điểm danh &amp; Mã QR ca</span>
                                </button>

                                {evt.status === 'DRAFT' &&
                                  !getDynamicEventStatus(evt).isExpiredDraft &&
                                  (activeShifts.length > 0 || Number(evt.totalShifts || 0) > 0) && (
                                    <button
                                      type="button"
                                      className="dropdown-item text-blue"
                                      onClick={() => {
                                        onOpenConfirmPublish(evt);
                                        closeMenu();
                                      }}
                                    >
                                      <span>Đăng Tuyển Ngay</span>
                                    </button>
                                  )}

                                <button
                                  type="button"
                                  className="dropdown-item"
                                  onClick={() => {
                                    onOpenAddShift(evt.eventId);
                                    closeMenu();
                                  }}
                                >
                                  <span>Thêm ca làm</span>
                                </button>

                                <button
                                  type="button"
                                  className="dropdown-item"
                                  onClick={() => {
                                    onOpenEditEvent(evt);
                                    closeMenu();
                                  }}
                                >
                                  <span>Chỉnh sửa sự kiện</span>
                                </button>

                                {(evt.status === 'ACTIVE' || evt.status === 'UPCOMING') && (
                                  <button
                                    type="button"
                                    className="dropdown-item text-danger"
                                    onClick={() => {
                                      onCancelEvent(evt);
                                      closeMenu();
                                    }}
                                  >
                                    <span>Hủy sự kiện</span>
                                  </button>
                                )}

                                {evt.status === 'DRAFT' && (
                                  <button
                                    type="button"
                                    className="dropdown-item text-danger"
                                    onClick={() => {
                                      onDeleteEvent?.(evt);
                                      closeMenu();
                                    }}
                                  >
                                    <span>Xóa bản nháp</span>
                                  </button>
                                )}
                              </div>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              )}
            </div>

            {filteredEvents.length > effectivePageSize && (
              <div className="ev-pagination-bar" role="navigation" aria-label="Phân trang danh sách sự kiện">
                <span className="ev-pagination-summary">
                  Hiển thị <strong>{startItemIndex + 1}–{endItemIndex}</strong> trên tổng số{' '}
                  <strong>{filteredEvents.length}</strong> sự kiện
                </span>

                <div className="ev-pagination-controls">
                  <button
                    type="button"
                    className="ev-page-btn"
                    disabled={safeCurrentPage <= 1}
                    onClick={() => setCurrentPage(safeCurrentPage - 1)}
                  >
                    &larr; Trang trước
                  </button>

                  {Array.from({ length: totalPages }, (_, idx) => idx + 1)
                    .filter((page) => {
                      if (totalPages <= 7) return true;
                      return (
                        page === 1 ||
                        page === totalPages ||
                        Math.abs(page - safeCurrentPage) <= 1
                      );
                    })
                    .reduce((acc, page, idx, arr) => {
                      if (idx > 0 && page - arr[idx - 1] > 1) {
                        acc.push({ type: 'ellipsis', key: `ellipsis-${page}` });
                      }
                      acc.push({ type: 'page', page, key: `page-${page}` });
                      return acc;
                    }, [])
                    .map((item) =>
                      item.type === 'ellipsis' ? (
                        <span key={item.key} className="ev-page-ellipsis" aria-hidden="true">
                          &hellip;
                        </span>
                      ) : (
                        <button
                          key={item.key}
                          type="button"
                          className={`ev-page-num-btn ${safeCurrentPage === item.page ? 'is-active' : ''}`}
                          aria-current={safeCurrentPage === item.page ? 'page' : undefined}
                          onClick={() => setCurrentPage(item.page)}
                        >
                          {item.page}
                        </button>
                      )
                    )}

                  <button
                    type="button"
                    className="ev-page-btn"
                    disabled={safeCurrentPage >= totalPages}
                    onClick={() => setCurrentPage(safeCurrentPage + 1)}
                  >
                    Trang sau &rarr;
                  </button>
                </div>
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
}
