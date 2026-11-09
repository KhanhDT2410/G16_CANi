import React from 'react';
import { showToast } from '../../../components/common/Toast';

const UPCOMING_EVENTS = [
  {
    id: 'evt-1',
    day: '20',
    month: 'Thg 10',
    title: 'Lễ hội âm nhạc Summer Fest',
    venue: 'Quận 7, TP.HCM',
    dotColor: '#10b981', // green (sắp diễn ra)
  },
  {
    id: 'evt-2',
    day: '28',
    month: 'Thg 10',
    title: 'Triển lãm Công nghệ Việt Nam',
    venue: 'Quận 1, TP.HCM',
    dotColor: '#2563eb', // blue
  },
  {
    id: 'evt-3',
    day: '05',
    month: 'Thg 11',
    title: 'Sự kiện ra mắt sản phẩm',
    venue: 'Thủ Đức, TP.HCM',
    dotColor: '#2563eb', // blue
  },
];

export default function EORightSidebar({ metrics, onOpenQr, onOpenRecruitment, onOpenSubscription }) {
  // Dữ liệu tỷ lệ lấp đầy nhân sự
  const confirmed = metrics?.totalConfirmedWorkers ?? 0;
  const required = metrics?.totalRequiredWorkers ?? 17;
  const pending = Math.max(0, required - confirmed);
  const shortage = 0;
  const total = (confirmed + pending + shortage) || 1;
  const fillRate = metrics?.fillRate ?? Math.round((confirmed / total) * 100);

  // Tham số Donut SVG (bán kính r = 38, chu vi C = 2 * PI * 38 ≈ 238.76)
  const radius = 38;
  const circumference = 2 * Math.PI * radius;
  const confirmedLen = (confirmed / total) * circumference;
  const pendingLen = (pending / total) * circumference;
  const shortageLen = (shortage / total) * circumference;

  return (
    <aside className="eo-right-sidebar eo-widget-panel">
      {/* 1. Widget Sự Kiện Sắp Tới (Chuẩn Mockup Bản 3) */}
      <div className="eo-widget-card upcoming-events-widget">
        <div className="widget-header">
          <h4 className="widget-title">Sự kiện sắp tới</h4>
          <button
            type="button"
            className="widget-link-btn"
            onClick={() => showToast('Đang mở toàn bộ lịch trình sự kiện', 'info')}
          >
            Xem tất cả &rarr;
          </button>
        </div>

        <div className="upcoming-events-list">
          {UPCOMING_EVENTS.map((evt) => (
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
              {pendingLen > 0 && (
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
              {confirmedLen > 0 && (
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

              {/* Lát 3: Còn thiếu (Xám) */}
              {shortageLen > 0 && (
                <circle
                  cx="50"
                  cy="50"
                  r={radius}
                  fill="none"
                  stroke="#cbd5e1"
                  strokeWidth="11"
                  strokeDasharray={`${shortageLen} ${circumference}`}
                  strokeDashoffset={-(confirmedLen + pendingLen)}
                  transform="rotate(-90 50 50)"
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

      {/* 3. Widget Thao Tác Nhanh (Gợi ý đắt giá từ Bản 2) */}
      <div className="eo-widget-card quick-actions-widget">
        <div className="widget-header">
          <h4 className="widget-title">Thao tác nhanh</h4>
        </div>

        <div className="quick-action-buttons-grid">
          <button
            type="button"
            className="quick-action-btn"
            onClick={() => {
              if (onOpenQr) onOpenQr();
              else showToast('Mở trình quét mã QR điểm danh ca', 'info');
            }}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2">
              <rect x="3" y="3" width="7" height="7" rx="1"></rect>
              <rect x="14" y="3" width="7" height="7" rx="1"></rect>
              <rect x="14" y="14" width="7" height="7" rx="1"></rect>
              <rect x="3" y="14" width="7" height="7" rx="1"></rect>
            </svg>
            <span className="action-label">Quét Check-in</span>
          </button>

          <button
            type="button"
            className="quick-action-btn"
            onClick={() => {
              if (onOpenRecruitment) onOpenRecruitment();
              else showToast('Mở giao diện điều phối tuyển dụng khẩn cấp', 'info');
            }}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#059669" strokeWidth="2">
              <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
              <circle cx="8.5" cy="7" r="4"></circle>
              <line x1="20" y1="8" x2="20" y2="14"></line>
              <line x1="23" y1="11" x2="17" y2="11"></line>
            </svg>
            <span className="action-label">Tuyển nhân sự</span>
          </button>

          <button
            type="button"
            className="quick-action-btn"
            onClick={() => {
              if (onOpenSubscription) onOpenSubscription();
              else showToast('Mở thanh toán gói cước', 'info');
            }}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#0284c7" strokeWidth="2">
              <rect x="1" y="4" width="22" height="16" rx="2" ry="2"></rect>
              <line x1="1" y1="10" x2="23" y2="10"></line>
            </svg>
            <span className="action-label">Thanh toán</span>
          </button>
        </div>
      </div>
    </aside>
  );
}
