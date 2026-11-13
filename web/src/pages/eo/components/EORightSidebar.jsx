import React from 'react';
import { showToast } from '../../../components/common/Toast';

export default function EORightSidebar({ metrics, events = [], onOpenQr, onOpenRecruitment, onOpenSubscription, onViewAllEvents }) {
  // Trích xuất các sự kiện thực tế sắp tới (sắp xếp theo ngày tăng dần)
  const upcomingList = (events || [])
    .filter((e) => e && e.status !== 'CANCELLED')
    .slice(0, 4)
    .map((evt) => {
      let day = '--';
      let month = 'Sự kiện';
      if (evt.eventDate) {
        try {
          const d = new Date(evt.eventDate);
          if (!isNaN(d.getTime())) {
            day = String(d.getDate()).padStart(2, '0');
            month = `Thg ${d.getMonth() + 1}`;
          }
        } catch {
          // ignore
        }
      }
      return {
        id: evt.eventId,
        day,
        month,
        title: evt.name || 'Sự kiện không tên',
        venue: evt.venueAddress || 'Chưa cập nhật địa điểm',
        dotColor: evt.status === 'ACTIVE' ? '#10b981' : '#f59e0b',
      };
    });

  // Dữ liệu tỷ lệ lấp đầy nhân sự
  const confirmed = metrics?.totalConfirmedWorkers ?? 0;
  const required = metrics?.totalRequiredWorkers ?? 0;
  const pending = Math.max(0, required - confirmed);
  const shortage = 0;
  const total = (confirmed + pending + shortage);
  const fillRate = required > 0 ? (metrics?.fillRate ?? Math.round((confirmed / required) * 100)) : 0;

  // Tham số Donut SVG (bán kính r = 38, chu vi C = 2 * PI * 38 ≈ 238.76)
  const radius = 38;
  const circumference = 2 * Math.PI * radius;
  const safeTotal = total > 0 ? total : 1;
  const confirmedLen = (confirmed / safeTotal) * circumference;
  const pendingLen = (pending / safeTotal) * circumference;
  const shortageLen = (shortage / safeTotal) * circumference;

  return (
    <aside className="eo-right-sidebar eo-widget-panel">
      {/* 1. Widget Sự Kiện Sắp Tới */}
      <div className="eo-widget-card upcoming-events-widget">
        <div className="widget-header">
          <h4 className="widget-title">Sự kiện sắp tới</h4>
          {upcomingList.length > 0 && (
            <button
              type="button"
              className="widget-link-btn"
              onClick={() => {
                if (onViewAllEvents) onViewAllEvents();
                else showToast('Đang chuyển sang Quản lý sự kiện', 'info');
              }}
            >
              Xem tất cả &rarr;
            </button>
          )}
        </div>

        {upcomingList.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '1.75rem 0.5rem', color: '#94a3b8', fontSize: '0.85rem' }}>
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#cbd5e1" strokeWidth="1.5" style={{ margin: '0 auto 0.5rem', display: 'block' }}>
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
              <line x1="16" y1="2" x2="16" y2="6"></line>
              <line x1="8" y1="2" x2="8" y2="6"></line>
              <line x1="3" y1="10" x2="21" y2="10"></line>
            </svg>
            Chưa có lịch sự kiện sắp tới
          </div>
        ) : (
          <div className="upcoming-events-list">
            {upcomingList.map((evt) => (
              <div key={evt.id} className="upcoming-event-item">
                <div className="event-date-box">
                  <span className="event-date-day">{evt.day}</span>
                  <span className="event-date-month">{evt.month}</span>
                </div>
                <div className="event-info-col">
                  <div className="event-title-row">
                    <span className="event-status-dot" style={{ backgroundColor: evt.dotColor }} />
                    <span className="event-item-name" title={evt.title}>{evt.title}</span>
                  </div>
                  <div className="event-item-venue">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#64748b" strokeWidth="2" style={{ flexShrink: 0 }}>
                      <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
                      <circle cx="12" cy="10" r="3"></circle>
                    </svg>
                    <span>{evt.venue}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 2. Widget Tỷ Lệ Lấp Đầy Nhân Sự (Biểu Đồ Tròn Donut Chuẩn Enterprise) */}
      <div className="eo-widget-card fill-rate-donut-widget">
        <div className="widget-header">
          <div className="widget-title-with-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2" style={{ flexShrink: 0 }}>
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
              <circle cx="9" cy="7" r="4"></circle>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
            </svg>
            <h4 className="widget-title">Tỷ lệ lấp đầy nhân sự</h4>
          </div>
        </div>

        <div className="donut-widget-body">
          {/* SVG Donut Chart */}
          <div className="donut-chart-box">
            <svg viewBox="0 0 100 100" className="donut-svg">
              {/* Vòng nền xám nhạt */}
              <circle
                cx="50"
                cy="50"
                r={radius}
                fill="none"
                stroke="#f1f5f9"
                strokeWidth="11"
              />

              {/* Lát 1: Đang tuyển / xử lý (Xanh dương nhạt) */}
              {total > 0 && pendingLen > 0 && (
                <circle
                  cx="50"
                  cy="50"
                  r={radius}
                  fill="none"
                  stroke="#60a5fa"
                  strokeWidth="11"
                  strokeDasharray={`${pendingLen} ${circumference}`}
                  strokeDashoffset={-confirmedLen}
                  transform="rotate(-90 50 50)"
                  strokeLinecap={confirmed === 0 && shortage === 0 ? 'butt' : 'round'}
                />
              )}

              {/* Lát 2: Đã chốt ca (Xanh dương đậm CANi) */}
              {total > 0 && confirmedLen > 0 && (
                <circle
                  cx="50"
                  cy="50"
                  r={radius}
                  fill="none"
                  stroke="#2563eb"
                  strokeWidth="11"
                  strokeDasharray={`${confirmedLen} ${circumference}`}
                  strokeDashoffset="0"
                  transform="rotate(-90 50 50)"
                  strokeLinecap="round"
                />
              )}

              {/* Văn bản trung tâm vòng Donut */}
              <text x="50" y="47" textAnchor="middle" dominantBaseline="middle" className="donut-center-num">
                {fillRate}%
              </text>
              <text x="50" y="63" textAnchor="middle" dominantBaseline="middle" className="donut-center-sub">
                Lấp đầy
              </text>
            </svg>
          </div>

          {/* Legend chú thích bên cạnh */}
          <div className="donut-legend-col">
            <div className="donut-legend-item">
              <span className="legend-bullet confirmed" />
              <span className="legend-label">Đã chốt ca:</span>
              <span className="legend-value">{confirmed}</span>
            </div>

            <div className="donut-legend-item">
              <span className="legend-bullet pending" />
              <span className="legend-label">Đang xử lý:</span>
              <span className="legend-value">{pending}</span>
            </div>

            <div className="donut-legend-item">
              <span className="legend-bullet shortage" />
              <span className="legend-label">Còn thiếu:</span>
              <span className="legend-value">{shortage}</span>
            </div>
          </div>
        </div>

        <div className="donut-widget-footer">
          <span>Tổng nhu cầu: <strong>{required}</strong> nhân sự</span>
        </div>
      </div>
    </aside>
  );
}
