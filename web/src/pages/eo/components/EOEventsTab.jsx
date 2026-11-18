import React, { useState, useMemo } from 'react';
import SkeletonCard from '../../../components/common/SkeletonLoader';
import EventShiftsDrawer from './EventShiftsDrawer';
import EORecruitmentTab from './EORecruitmentTab';
import {
  getEventStatusPriority,
  getDynamicEventStatus,
  getDynamicShiftStatus,
} from '../utils/eventHelpers';

export default function EOEventsTab({
  loading,
  allEvents = [],
  filteredEvents,
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
  onDeleteEvent,
  onOpenCreateEvent,
  onOpenAddShift,
  onOpenEditEvent,
  onCancelEvent,
  onOpenConfirmPublish,
  onOpenMap,
  onOpenQr,
  onOpenInvite,
  onOpenAttendance,
  onEditShift,
  onCancelShift,
  onReloadEvents,
  onOpenPaymentDeclaration,
}) {
  const [activeMenuEventId, setActiveMenuEventId] = useState(null);

  const toggleKebabMenu = (e, eventId) => {
    e.stopPropagation();
    setActiveMenuEventId((prev) => (prev === eventId ? null : eventId));
  };

  const closeMenu = () => setActiveMenuEventId(null);

  // Danh sách sự kiện chưa hủy (ưu tiên Đang hoạt động lên đầu, Bản nháp ở dưới)
  const nonCancelledEvents = useMemo(
    () =>
      [...allEvents]
        .filter((e) => e.status !== 'CANCELLED')
        .sort((a, b) => getEventStatusPriority(a.status) - getEventStatusPriority(b.status)),
    [allEvents]
  );

  // Danh sách sự kiện đã hủy và ca làm việc đã hủy (tách riêng hoàn toàn)
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
    if (subKey === 'detail') {
      const target = nonCancelledEvents[0] || allEvents[0];
      if (target && onOpenEventDetail) {
        onOpenEventDetail(target);
        return;
      }
    }
    onSubTabChange?.(subKey);
    if (subKey === 'cancelled') {
      setStatusFilter('CANCELLED');
    } else if (statusFilter === 'CANCELLED') {
      setStatusFilter('ALL');
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }} onClick={closeMenu}>
      {/* Thanh Điều Hướng Phân Khu Quản Lý Sự Kiện & Tuyển Dụng & Điểm Danh QR */}
      <div
        className="eo-widget-card"
        style={{
          padding: '0.85rem 1.25rem',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '0.75rem',
        }}
      >
        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
          <button
            type="button"
            onClick={() => handleSelectSubTab('list')}
            style={{
              padding: '0.55rem 1rem',
              borderRadius: '10px',
              border: activeSubTab === 'list' ? '1.5px solid #2563eb' : '1px solid #e2e8f0',
              background: activeSubTab === 'list' ? '#eff6ff' : '#ffffff',
              color: activeSubTab === 'list' ? '#1d4ed8' : '#475569',
              fontWeight: 700,
              fontSize: '0.85rem',
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.4rem',
            }}
          >
            <span>Danh sách sự kiện</span>
          </button>

          <button
            type="button"
            onClick={() => handleSelectSubTab('detail')}
            style={{
              padding: '0.55rem 1rem',
              borderRadius: '10px',
              border: activeSubTab === 'detail' ? '1.5px solid #2563eb' : '1px solid #e2e8f0',
              background: activeSubTab === 'detail' ? '#eff6ff' : '#ffffff',
              color: activeSubTab === 'detail' ? '#1d4ed8' : '#475569',
              fontWeight: 700,
              fontSize: '0.85rem',
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.4rem',
            }}
          >
            <span>Chi tiết sự kiện & Điểm danh</span>
          </button>

          <button
            type="button"
            onClick={() => handleSelectSubTab('cancelled')}
            style={{
              padding: '0.55rem 1rem',
              borderRadius: '10px',
              border: activeSubTab === 'cancelled' ? '1.5px solid #dc2626' : '1px solid #e2e8f0',
              background: activeSubTab === 'cancelled' ? '#fef2f2' : '#ffffff',
              color: activeSubTab === 'cancelled' ? '#dc2626' : '#64748b',
              fontWeight: 700,
              fontSize: '0.85rem',
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.4rem',
            }}
          >
            <span>Sự kiện đã hủy</span>
          </button>
        </div>

        <button
          type="button"
          className="table-btn-create-event"
          onClick={onOpenCreateEvent}
        >
          <span>+</span>
          <span>Tạo sự kiện mới</span>
        </button>
      </div>

      {/* PHÂN KHU 4: DANH SÁCH CA & SỰ KIỆN ĐÃ HỦY (TÁCH RIÊNG HOÀN TOÀN) */}
      {activeSubTab === 'cancelled' && (
        <div className="eo-widget-card animate-fade-in-up" style={{ padding: '1.5rem' }}>
          <div style={{ marginBottom: '1.25rem' }}>
            <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 800, color: '#991b1b' }}>
              Khu Vực Lưu Trữ Sự Kiện & Ca Làm Việc Đã Hủy
            </h3>
            <p style={{ margin: '0.25rem 0 0', fontSize: '0.84rem', color: '#64748b' }}>
              Các sự kiện và ca làm việc đã hủy được lưu trữ tại đây nhằm phục vụ việc đối soát lịch sử.
            </p>
          </div>

          {cancelledEventsList.length === 0 && cancelledShiftsFromActiveEvents.length === 0 ? (
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

      {/* PHÂN KHU 1: DANH SÁCH SỰ KIỆN */}
      {activeSubTab === 'list' && (
        <section className="eo-events-section-card animate-fade-in-up">
          {/* 1. Header Bar: Tiêu đề + Tìm kiếm + Lọc */}
          <div className="table-card-header-bar">
            <div className="table-header-title-group">
              <h3 className="table-card-heading">Danh sách sự kiện</h3>
            </div>

            <div className="table-header-actions-group">
              <div className="table-search-input-wrapper">
                <svg
                  width="14"
                  height="14"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="#94a3b8"
                  strokeWidth="2.5"
                  className="search-lens-icon"
                >
                  <circle cx="11" cy="11" r="8"></circle>
                  <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                </svg>
                <input
                  type="text"
                  placeholder="Tìm kiếm theo tên sự kiện..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="table-search-input"
                />
              </div>

              <div className="table-select-wrapper">
                <select
                  value={statusFilter === 'CANCELLED' ? 'ALL' : statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  className="table-filter-select"
                >
                  <option value="ALL">Tất cả (Hoạt động & Bản nháp)</option>
                  <option value="RECRUITING">Đang tìm kiếm nhân sự</option>
                  <option value="IN_PROGRESS">Đang diễn ra</option>
                  <option value="READY">Đủ nhân sự • Chờ diễn ra</option>
                  <option value="DRAFT">Chưa bắt đầu (Bản nháp)</option>
                  <option value="COMPLETED">Đã kết thúc</option>
                </select>
              </div>

              <div className="table-select-wrapper">
                <select
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value)}
                  className="table-filter-select"
                >
                  <option value="NEWEST">Mới nhất</option>
                  <option value="OLDEST">Cũ nhất</option>
                  <option value="UPCOMING">Sắp diễn ra</option>
                </select>
              </div>
            </div>
          </div>

      {/* 2. Bảng Danh Sách Sự Kiện (Table View Chuẩn Mockup Bản 3) */}
      <div className="table-responsive-wrapper">
        {loading ? (
          <div style={{ padding: '1.5rem' }}>
            <SkeletonCard count={3} height="80px" />
          </div>
        ) : filteredEvents.length === 0 ? (
          <div className="eo-empty-state-card">
            <div className="empty-calendar-icon">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                <line x1="16" y1="2" x2="16" y2="6"></line>
                <line x1="8" y1="2" x2="8" y2="6"></line>
                <line x1="3" y1="10" x2="21" y2="10"></line>
              </svg>
            </div>
            <p className="empty-text">Chưa tìm thấy sự kiện nào trong danh sách.</p>
            <button
              type="button"
              className="table-btn-create-event"
              onClick={onOpenCreateEvent}
            >
              + Tạo Sự Kiện Đầu Tiên
            </button>
          </div>
        ) : (
          <table className="cani-modern-table">
            <thead>
              <tr>
                <th style={{ width: '27%' }}>Sự kiện ⇅</th>
                <th style={{ width: '19%' }}>Địa điểm</th>
                <th style={{ width: '20%' }}>Nhân sự theo ca</th>
                <th style={{ width: '13%' }}>Dự toán chi phí</th>
                <th style={{ width: '14%' }}>Trạng thái</th>
                <th style={{ width: '7%', textAlign: 'center' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredEvents.map((evt, idx) => {
                const isExpanded = expandedEventId === evt.eventId;
                const isMenuOpen = activeMenuEventId === evt.eventId;

                const demoImages = [
                  'https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=150&q=80',
                  'https://images.unsplash.com/photo-1511578314322-379afb476865?auto=format&fit=crop&w=150&q=80',
                  'https://images.unsplash.com/photo-1540575467063-178a50c2df87?auto=format&fit=crop&w=150&q=80',
                  'https://images.unsplash.com/photo-1464366400600-7168b8af9bc3?auto=format&fit=crop&w=150&q=80',
                ];
                const thumbImg = evt.imageUrl || demoImages[idx % demoImages.length];
                const activeShifts = Array.isArray(evt.shifts)
                  ? evt.shifts.filter((s) => s.status !== 'CANCELLED')
                  : [];

                return (
                  <React.Fragment key={evt.eventId}>
                    <tr
                      className={`event-table-row ${isExpanded ? 'row-expanded' : ''}`}
                      onClick={() => setExpandedEventId(isExpanded ? null : evt.eventId)}
                    >
                      {/* Cột 1: Tên sự kiện + Ảnh nằm xuống 1 div riêng cho to rõ */}
                      <td>
                        <div
                          onClick={(e) => {
                            e.stopPropagation();
                            onOpenEventDetail?.(evt);
                          }}
                          style={{ cursor: 'pointer', display: 'flex', flexDirection: 'column', gap: '0.45rem' }}
                          title="Nhấn để xem chi tiết sự kiện & nhân sự"
                        >
                          <div className="event-row-meta">
                            <span className="event-row-title" title={evt.name}>
                              {evt.name}
                            </span>
                            <span className="event-row-hint" style={{ fontSize: '0.75rem', color: '#2563eb', fontWeight: 600 }}>
                              Chi tiết sự kiện & nhân sự &rarr;
                            </span>
                          </div>
                          <div
                            style={{
                              width: '100%',
                              maxWidth: '210px',
                              height: '105px',
                              borderRadius: '10px',
                              overflow: 'hidden',
                              border: '1px solid #cbd5e1',
                              backgroundColor: '#f8fafc',
                            }}
                          >
                            <img
                              src={thumbImg}
                              alt={evt.name}
                              style={{
                                width: '100%',
                                height: '100%',
                                objectFit: 'cover',
                                display: 'block',
                              }}
                              onError={(e) => {
                                e.target.style.display = 'none';
                              }}
                            />
                          </div>
                        </div>
                      </td>

                      {/* Cột 2: Địa điểm */}
                      <td>
                        <div className="table-venue-cell">
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#64748b" strokeWidth="2" style={{ flexShrink: 0 }}>
                            <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
                            <circle cx="12" cy="10" r="3"></circle>
                          </svg>
                          <span className="venue-address-text" title={evt.venueAddress}>
                            {evt.venueAddress}
                          </span>
                        </div>
                      </td>

                      {/* Cột 3: Nhân sự theo ca (Hiển thị tổng & chi tiết từng ca kèm trạng thái) */}
                      <td>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                          <div className="table-workers-cell">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#64748b" strokeWidth="2" style={{ flexShrink: 0 }}>
                              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                              <circle cx="9" cy="7" r="4"></circle>
                            </svg>
                            <span className="workers-count" style={{ fontWeight: 700, color: '#0f172a' }}>
                              Tổng: {evt.totalConfirmedWorkers || 0}/{evt.totalRequiredWorkers || 0} người
                            </span>
                          </div>

                          {activeShifts.length > 0 ? (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                              {activeShifts.map((sh, sIdx) => {
                                const shStatus = getDynamicShiftStatus(sh, evt.status);
                                return (
                                  <div
                                    key={sh.shiftId || sIdx}
                                    style={{
                                      display: 'flex',
                                      alignItems: 'center',
                                      justifyContent: 'space-between',
                                      gap: '0.4rem',
                                      fontSize: '0.73rem',
                                      padding: '0.2rem 0.45rem',
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
                            </div>
                          ) : (
                            <span style={{ fontSize: '0.73rem', color: '#94a3b8', fontStyle: 'italic' }}>
                              {evt.totalShifts ? `${evt.totalShifts} ca làm việc` : 'Chưa có ca'}
                            </span>
                          )}
                        </div>
                      </td>

                      {/* Cột 4: Dự toán chi phí */}
                      <td>
                        <span className="table-cost-amount">
                          {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(
                            evt.totalEstimatedCost || 1020000
                          )}
                        </span>
                      </td>

                      {/* Cột 5: Trạng thái Badge động theo thời gian + Nút Kích Hoạt Tìm Kiếm Ngay cho Bản nháp */}
                      <td onClick={(e) => e.stopPropagation()}>
                        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: '0.35rem' }}>
                          {formatStatusBadge(evt)}
                          {evt.status === 'DRAFT' && (
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
                          )}
                        </div>
                      </td>

                      {/* Cột 6: Thao tác Kebab */}
                      <td style={{ textAlign: 'center', position: 'relative' }} onClick={(e) => e.stopPropagation()}>
                        <div style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
                          <button
                            type="button"
                            className="btn-kebab-menu"
                            onClick={(e) => toggleKebabMenu(e, evt.eventId)}
                            title="Tùy chọn sự kiện"
                          >
                            ⋮
                          </button>
                        </div>

                        {/* Dropdown Menu Thao tác */}
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
                              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                                <circle cx="12" cy="12" r="3"></circle>
                              </svg>
                              <span>Chi tiết sự kiện & nhân sự</span>
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
                              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <rect x="3" y="3" width="7" height="7"></rect>
                                <rect x="14" y="3" width="7" height="7"></rect>
                                <rect x="14" y="14" width="7" height="7"></rect>
                                <rect x="3" y="14" width="7" height="7"></rect>
                              </svg>
                              <span>Điểm danh & Mã QR ca</span>
                            </button>

                            <button
                              type="button"
                              className="dropdown-item"
                              onClick={() => {
                                setExpandedEventId(isExpanded ? null : evt.eventId);
                                closeMenu();
                              }}
                            >
                              <span>{isExpanded ? 'Thu gọn ca làm' : `Quản lý ${evt.totalShifts || 0} ca`}</span>
                            </button>

                            {evt.status === 'DRAFT' && (
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
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                  <polyline points="3 6 5 6 21 6"></polyline>
                                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                                </svg>
                                <span>Xóa bản nháp</span>
                              </button>
                            )}
                          </div>
                        )}
                      </td>
                    </tr>

                    {/* Hàng mở rộng Drawer Quản lý Ca làm việc khi được bấm */}
                    {isExpanded && (
                      <tr className="drawer-expanded-row">
                        <td colSpan={6} style={{ padding: 0 }}>
                          <div className="drawer-container-inside-table">
                            <EventShiftsDrawer
                              event={evt}
                              onOpenDetail={onOpenEventDetail}
                              onAddShift={onOpenAddShift}
                              onOpenQr={onOpenQr}
                              onOpenInvite={onOpenInvite}
                              onOpenAttendance={onOpenAttendance}
                              onEditShift={onEditShift}
                              onCancelShift={onCancelShift}
                              onReloadEvents={onReloadEvents}
                              onOpenPaymentDeclaration={onOpenPaymentDeclaration}
                            />
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
        </section>
      )}
    </div>
  );
}
