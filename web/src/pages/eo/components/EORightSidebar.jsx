import { useState } from 'react';
import { showToast } from '../../../components/common/Toast';

const RECENT_NOTIFICATIONS = [
  {
    id: 1,
    title: 'Nguyễn Văn A đã ứng tuyển vào ca Lễ tân',
    time: '10 phút trước',
  },
  {
    id: 2,
    title: 'Sự kiện Summer Fest 2026 đã được duyệt',
    time: '1 giờ trước',
  },
  {
    id: 3,
    title: 'Yêu cầu xác nhận lương từ Trần Thị B',
    time: 'Hôm qua',
  },
];

export default function EORightSidebar() {
  const [calendarDate, setCalendarDate] = useState({ month: new Date().getMonth(), year: new Date().getFullYear() });
  const [selectedDay, setSelectedDay] = useState(new Date().getDate());

  const renderCalendarDays = () => {
    const daysInMonth = new Date(calendarDate.year, calendarDate.month + 1, 0).getDate();
    const firstDay = new Date(calendarDate.year, calendarDate.month, 1).getDay();
    const startWeekdayOffset = firstDay === 0 ? 6 : firstDay - 1;
    const prevMonthDays = new Date(calendarDate.year, calendarDate.month, 0).getDate();
    const cells = [];

    // Các ngày của tháng trước (làm mờ)
    for (let i = 0; i < startWeekdayOffset; i++) {
      cells.push(
        <div key={`empty-${i}`} className="calendar-day-cell dim">
          {prevMonthDays - startWeekdayOffset + i + 1}
        </div>
      );
    }

    // Các ngày của tháng hiện tại
    for (let day = 1; day <= daysInMonth; day++) {
      const isSelected = day === selectedDay;
      const hasEvent = day === 15 || day === 20;

      cells.push(
        <button
          type="button"
          key={`day-${day}`}
          className={`calendar-day-cell ${isSelected ? 'selected' : ''} ${hasEvent ? 'has-event' : ''}`}
          onClick={() => setSelectedDay(day)}
        >
          <span className="day-number">{day}</span>
          {hasEvent && <span className="calendar-event-dot"></span>}
        </button>
      );
    }

    return cells;
  };

  return (
    <aside className="eo-right-sidebar eo-widget-panel">
      {/* Widget 1: Lịch mini tháng & chọn ngày */}
      <div className="eo-widget-card calendar-widget">
        <div className="widget-header">
          <h4 className="widget-title">
            Tháng {calendarDate.month + 1}, {calendarDate.year}
          </h4>
          <div className="calendar-nav-buttons">
            <button
              type="button"
              className="cal-btn"
              title="Tháng trước"
              onClick={() => {
                setCalendarDate((prev) => {
                  if (prev.month === 0) return { month: 11, year: prev.year - 1 };
                  return { ...prev, month: prev.month - 1 };
                });
              }}
            >
              &lt;
            </button>
            <button
              type="button"
              className="cal-btn"
              title="Tháng sau"
              onClick={() => {
                setCalendarDate((prev) => {
                  if (prev.month === 11) return { month: 0, year: prev.year + 1 };
                  return { ...prev, month: prev.month + 1 };
                });
              }}
            >
              &gt;
            </button>
          </div>
        </div>

        {/* Thứ trong tuần */}
        <div className="calendar-weekdays-row">
          <span>T2</span>
          <span>T3</span>
          <span>T4</span>
          <span>T5</span>
          <span>T6</span>
          <span className="weekend">T7</span>
          <span className="weekend">CN</span>
        </div>

        {/* Lưới các ngày trong tháng (7 cột chuẩn) */}
        <div className="calendar-grid">{renderCalendarDays()}</div>
      </div>

      {/* Widget 2: Thông báo và hoạt động gần đây */}
      <div className="eo-widget-card activity-widget">
        <div className="widget-header">
          <h4 className="widget-title">Thông báo gần đây</h4>
          <button
            type="button"
            className="widget-link-btn"
            onClick={() => showToast('Mở trung tâm thông báo', 'info')}
          >
            Tất cả
          </button>
        </div>

        <div className="notification-list">
          {RECENT_NOTIFICATIONS.map((item) => (
            <div key={item.id} className="notification-item">
              <div
                className="notif-avatar"
                style={{ backgroundColor: '#eff6ff', color: '#2563eb' }}
              >
                {item.id}
              </div>
              <div className="notif-body">
                <p className="notif-title">{item.title}</p>
                <span className="notif-time">{item.time}</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </aside>
  );
}
