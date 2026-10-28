import SkeletonCard from '../../../components/common/SkeletonLoader';
import { showToast } from '../../../components/common/Toast';
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
  onOpenCreateEvent,
  onOpenAddShift,
  onOpenEditEvent,
  onCancelEvent,
  onOpenConfirmPublish,
  onOpenMap,
  onOpenQr,
  onOpenAttendance,
  onEditShift,
  onCancelShift,
}) {
  const copyToClipboard = (text, label) => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text);
      showToast(`Đã sao chép ${label}: ${text}`, 'success');
    }
  };

  return (
    <section className="eo-events-section animate-fade-in-up">
      {/* Thanh Tìm Kiếm & Lọc */}
      <div className="eo-filter-bar">
        <div className="filter-title-group">
          <h3 className="section-title">Danh sách sự kiện</h3>
        </div>
        <div className="filter-controls">
          <div className="search-input-wrapper">
            <span className="search-icon">🔍</span>
            <input
              type="text"
              placeholder="Tìm kiếm theo tên sự kiện..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="event-search-input"
            />
          </div>

          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="filter-select"
          >
            <option value="ALL">Tất cả trạng thái</option>
            <option value="ACTIVE">Đang diễn ra</option>
            <option value="DRAFT">Bản nháp</option>
          </select>

          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value)}
            className="filter-select"
          >
            <option value="NEWEST">Mới nhất</option>
            <option value="NAME">Tên sự kiện (A-Z)</option>
            <option value="WORKERS">Số lượng nhân sự</option>
          </select>
        </div>
      </div>

      {/* Danh sách thẻ sự kiện */}
      <div className="eo-events-list">
        {loading ? (
          <SkeletonCard count={2} height="220px" />
        ) : filteredEvents.length === 0 ? (
          <div className="eo-empty-state">
            <div className="empty-icon">🎪</div>
            <p className="empty-text">Không tìm thấy sự kiện nào phù hợp.</p>
            <button
              type="button"
              className="eo-btn-primary"
              onClick={onOpenCreateEvent}
            >
              + Tạo Sự Kiện Mới
            </button>
          </div>
        ) : (
          filteredEvents.map((evt) => (
            <article key={evt.eventId} className="eo-event-card-modern">
              <div className="event-card-thumbnail">
                <img
                  src="https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=600&q=80"
                  alt={evt.name}
                  className="thumbnail-img"
                />
                <div className="thumbnail-overlay">
                  <span className="thumbnail-badge">SỰ KIỆN LIVE</span>
                </div>
              </div>

              <div className="event-card-details">
                <div className="card-top-row">
                  <h2 className="event-title">{evt.name}</h2>
                  <div className="card-top-actions">
                    <span className={`status-badge-modern ${evt.status === 'ACTIVE' ? 'active' : 'draft'}`}>
                      <span className="dot-pulse"></span>
                      {evt.status === 'ACTIVE' ? 'Đang diễn ra' : evt.status === 'DRAFT' ? 'Bản nháp' : evt.status}
                    </span>
                    <button
                      type="button"
                      className="btn-kebab"
                      title="Tùy chọn khác"
                      onClick={() => showToast(`Tùy chọn cho sự kiện: ${evt.name}`, 'info')}
                    >
                      ⋮
                    </button>
                  </div>
                </div>

                <div className="event-code-row">
                  <span className="event-code-text">Mã: {evt.eventId || 'EVT-98721'}</span>
                  <button
                    type="button"
                    className="btn-copy-code"
                    title="Sao chép mã sự kiện"
                    onClick={() => copyToClipboard(evt.eventId || 'EVT-98721', 'Mã sự kiện')}
                  >
                    📋
                  </button>
                </div>

                <div className="event-location-row">
                  <span className="location-pin">📍</span>
                  <span className="location-text">{evt.venueAddress}</span>
                </div>

                <div className="event-metrics-row">
                  <div className="meta-chip">
                    <span className="chip-icon">👥</span>
                    <span className="chip-label">
                      <strong>{evt.totalConfirmedWorkers || 0}/{evt.totalRequiredWorkers || 0}</strong> Nhân sự
                    </span>
                  </div>

                  <div className="meta-chip">
                    <span className="chip-icon">🕒</span>
                    <span className="chip-label">
                      <strong>{evt.totalShifts || 0}</strong> Ca làm việc
                    </span>
                  </div>

                  <div className="meta-chip wage">
                    <span className="chip-icon">💰</span>
                    <span className="chip-label wage-amount">
                      {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(
                        evt.totalEstimatedCost || 0
                      )} Dự toán
                    </span>
                  </div>
                </div>

                <div className="event-card-actions">
                  <button
                    type="button"
                    className="btn-action-map"
                    onClick={() =>
                      onOpenMap({
                        isOpen: true,
                        eventName: evt.name,
                        venueAddress: evt.venueAddress,
                        lat: evt.venueLat || 10.7328,
                        lng: evt.venueLng || 106.7214,
                        radiusKm: 15,
                      })
                    }
                  >
                    📍 Bản đồ
                  </button>

                  <button
                    type="button"
                    className="btn-action-outline"
                    onClick={() => onOpenAddShift(evt.eventId)}
                  >
                    + Thêm Ca
                  </button>

                  <button
                    type="button"
                    className="btn-action-outline"
                    onClick={() => onOpenEditEvent(evt)}
                    title="Chỉnh sửa thông tin sự kiện"
                  >
                    ✏️ Sửa
                  </button>

                  {evt.status !== 'CANCELLED' && evt.status !== 'COMPLETED' && (
                    <button
                      type="button"
                      className="btn-action-map"
                      style={{ color: '#ef4444', borderColor: '#fca5a5' }}
                      onClick={() => onCancelEvent(evt)}
                      title="Hủy sự kiện này"
                    >
                      ❌ Hủy
                    </button>
                  )}

                  {evt.status === 'DRAFT' && (
                    <button
                      type="button"
                      className="btn-action-publish"
                      onClick={() => onOpenConfirmPublish(evt)}
                    >
                      🚀 Kích Hoạt Matching
                    </button>
                  )}

                  <button
                    type="button"
                    className="btn-action-primary"
                    onClick={() => setExpandedEventId(expandedEventId === evt.eventId ? null : evt.eventId)}
                  >
                    {expandedEventId === evt.eventId
                      ? '▲ Thu Gọn'
                      : `📋 Quản Lý Ca (${evt.shifts?.length || evt.totalShifts || 0})`}
                  </button>
                </div>
              </div>

              {/* DRAWER DANH SÁCH CA LÀM VIỆC & ĐIỂM DANH QR */}
              {expandedEventId === evt.eventId && (
                <EventShiftsDrawer
                  event={evt}
                  onAddShift={onOpenAddShift}
                  onOpenQr={onOpenQr}
                  onOpenAttendance={onOpenAttendance}
                  onEditShift={onEditShift}
                  onCancelShift={onCancelShift}
                />
              )}
            </article>
          ))
        )}
      </div>
    </section>
  );
}
