import React, { useState } from 'react';
import SkeletonCard from '../../../components/common/SkeletonLoader';
import EventShiftsDrawer from './EventShiftsDrawer';

export default function EOEventsTab({
  loading,
  filteredEvents,
  searchQuery,
  setSearchQuery,
  statusFilter,
  setStatusFilter,
  sortBy,
  setSortBy,
  expandedEventId,
  setExpandedEventId,
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
}) {
  const [activeMenuEventId, setActiveMenuEventId] = useState(null);

  const toggleKebabMenu = (e, eventId) => {
    e.stopPropagation();
    setActiveMenuEventId((prev) => (prev === eventId ? null : eventId));
  };

  const closeMenu = () => setActiveMenuEventId(null);

  const formatStatusBadge = (status) => {
    switch (status) {
      case 'DRAFT':
        return <span className="table-status-pill yellow"><span className="status-dot-solid" />Bản nháp</span>;
      case 'ACTIVE':
        return <span className="table-status-pill green"><span className="status-dot-solid" />Đang diễn ra</span>;
      case 'UPCOMING':
        return <span className="table-status-pill blue"><span className="status-dot-solid" />Sắp diễn ra</span>;
      case 'APPROVED':
        return <span className="table-status-pill purple"><span className="status-dot-solid" />Đã duyệt</span>;
      case 'COMPLETED':
        return <span className="table-status-pill gray"><span className="status-dot-solid" />Đã hoàn thành</span>;
      case 'CANCELLED':
        return <span className="table-status-pill red"><span className="status-dot-solid" />Đã hủy</span>;
      default:
        return <span className="table-status-pill blue"><span className="status-dot-solid" />{status}</span>;
    }
  };

  return (
    <section className="eo-events-section-card animate-fade-in-up" onClick={closeMenu}>
      {/* 1. Header Bar: Tiêu đề + Tìm kiếm + Lọc + Nút Tạo Sự Kiện Mới */}
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
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="table-filter-select"
            >
              <option value="ALL">Tất cả trạng thái</option>
              <option value="ACTIVE">Đang diễn ra</option>
              <option value="DRAFT">Bản nháp</option>
              <option value="UPCOMING">Sắp diễn ra</option>
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

          <button
            type="button"
            className="table-btn-create-event"
            onClick={onOpenCreateEvent}
          >
            <span>+</span>
            <span>Tạo sự kiện mới</span>
          </button>
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
                <th style={{ width: '32%' }}>Sự kiện ⇅</th>
                <th style={{ width: '22%' }}>Địa điểm</th>
                <th style={{ width: '12%' }}>Nhân sự</th>
                <th style={{ width: '15%' }}>Dự toán chi phí</th>
                <th style={{ width: '12%' }}>Trạng thái</th>
                <th style={{ width: '7%', textAlign: 'center' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredEvents.map((evt, idx) => {
                const isExpanded = expandedEventId === evt.eventId;
                const isMenuOpen = activeMenuEventId === evt.eventId;

                // Ảnh minh họa ngẫu nhiên nhưng cố định theo index
                const demoImages = [
                  'https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=150&q=80',
                  'https://images.unsplash.com/photo-1511578314322-379afb476865?auto=format&fit=crop&w=150&q=80',
                  'https://images.unsplash.com/photo-1540575467063-178a50c2df87?auto=format&fit=crop&w=150&q=80',
                  'https://images.unsplash.com/photo-1464366400600-7168b8af9bc3?auto=format&fit=crop&w=150&q=80',
                ];
                const thumbImg = demoImages[idx % demoImages.length];

                return (
                  <React.Fragment key={evt.eventId}>
                    <tr
                      className={`event-table-row ${isExpanded ? 'row-expanded' : ''}`}
                      onClick={() => setExpandedEventId(isExpanded ? null : evt.eventId)}
                    >
                      {/* Cột 1: Thumbnail + Tên sự kiện */}
                      <td>
                        <div
                          className="table-event-cell"
                          onClick={(e) => {
                            e.stopPropagation();
                            onOpenEventDetail?.(evt);
                          }}
                          style={{ cursor: 'pointer' }}
                          title="Nhấn để xem chi tiết sự kiện & nhân sự"
                        >
                          <img
                            src={thumbImg}
                            alt={evt.name}
                            className="event-row-thumb"
                          />
                          <div className="event-row-meta">
                            <span className="event-row-title" title={evt.name}>
                              {evt.name}
                            </span>
                            <span className="event-row-hint" style={{ fontSize: '0.75rem', color: '#2563eb', fontWeight: 600 }}>
                              Chi tiết sự kiện & nhân sự &rarr;
                            </span>
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

                      {/* Cột 3: Nhân sự (0/5, 12/20) */}
                      <td>
                        <div className="table-workers-cell">
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#64748b" strokeWidth="2" style={{ flexShrink: 0 }}>
                            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                            <circle cx="9" cy="7" r="4"></circle>
                          </svg>
                          <span className="workers-count">
                            {evt.totalConfirmedWorkers || 0}/{evt.totalRequiredWorkers || 0}
                          </span>
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

                      {/* Cột 5: Trạng thái Badge */}
                      <td>
                        {formatStatusBadge(evt.status)}
                      </td>

                      {/* Cột 6: Thao tác Kebab & Xóa */}
                      <td style={{ textAlign: 'center', position: 'relative' }} onClick={(e) => e.stopPropagation()}>
                        <div style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '0.35rem' }}>
                          {evt.status === 'CANCELLED' && (
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                onDeleteEvent?.(evt);
                              }}
                              title="Xóa sự kiện đã hủy khỏi danh sách"
                              style={{
                                background: '#fee2e2',
                                border: '1px solid #fecaca',
                                color: '#dc2626',
                                borderRadius: '6px',
                                width: '28px',
                                height: '28px',
                                display: 'inline-flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                cursor: 'pointer',
                                transition: 'all 0.15s ease',
                              }}
                            >
                              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <polyline points="3 6 5 6 21 6"></polyline>
                                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                              </svg>
                            </button>
                          )}

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
                                onOpenEventDetail?.(evt);
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
                                <span>Kích hoạt Matching</span>
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
                                onOpenMap({
                                  isOpen: true,
                                  eventName: evt.name,
                                  venueAddress: evt.venueAddress,
                                  lat: evt.venueLat || 10.7328,
                                  lng: evt.venueLng || 106.7214,
                                  radiusKm: 15,
                                });
                                closeMenu();
                              }}
                            >
                              <span>Xem bản đồ Radar</span>
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

                            {evt.status !== 'CANCELLED' && evt.status !== 'COMPLETED' && (
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

                            {(evt.status === 'CANCELLED' || evt.status === 'DRAFT') && (
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
                                <span>Xóa khỏi danh sách</span>
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
  );
}
