import React, { useState, useMemo } from 'react';

// Chuyển đổi Date thành chuỗi 'YYYY-MM-DD' đồng nhất
const toDateKey = (dateInput) => {
  if (!dateInput) return '';
  try {
    const d = new Date(dateInput);
    if (isNaN(d.getTime())) return '';
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  } catch {
    return '';
  }
};

/**
 * EORightSidebar
 * Cột tiện ích bên phải tích hợp Lịch Hoạt Động & Sự Kiện Hợp Nhất
 * - Bảng màu chuẩn Ocean Blue + Neutral Slate (loại bỏ tím/coral)
 * - Đồng bộ chọn ngày và hiển thị sự kiện tương ứng
 */
export default function EORightSidebar({
  events = [],
  onOpenQr,
  onSelectEvent,
  onViewAllEvents,
}) {
  // 1. Lọc danh sách sự kiện chưa hủy
  const activeEvents = useMemo(() => {
    return (events || [])
      .filter((e) => e && e.status !== 'CANCELLED')
      .sort((a, b) => new Date(a.eventDate || a.createdAt || 0) - new Date(b.eventDate || b.createdAt || 0));
  }, [events]);

  // 2. Gom nhóm sự kiện theo ngày 'YYYY-MM-DD' để hiển thị chấm trên lịch
  const eventsByDate = useMemo(() => {
    const map = {};
    activeEvents.forEach((evt) => {
      const dateKeys = new Set();
      if (evt.eventDate) {
        const k = toDateKey(evt.eventDate);
        if (k) dateKeys.add(k);
      }
      (evt.shifts || []).forEach((sh) => {
        if (sh.startTime) {
          const k = toDateKey(sh.startTime);
          if (k) dateKeys.add(k);
        }
      });
      dateKeys.forEach((k) => {
        if (!map[k]) map[k] = [];
        if (!map[k].some((item) => item.eventId === evt.eventId)) {
          map[k].push(evt);
        }
      });
    });
    return map;
  }, [activeEvents]);

  // 3. Quản lý trạng thái xem lịch
  const [calendarViewDate, setCalendarViewDate] = useState(() => {
    if (activeEvents.length > 0 && activeEvents[0].eventDate) {
      const d = new Date(activeEvents[0].eventDate);
      if (!isNaN(d.getTime())) return new Date(d.getFullYear(), d.getMonth(), 1);
    }
    const today = new Date();
    return new Date(today.getFullYear(), today.getMonth(), 1);
  });

  const todayKey = toDateKey(new Date());

  const [selectedDateKey, setSelectedDateKey] = useState(() => {
    if (eventsByDate[todayKey]) return todayKey;
    const firstEventKey = Object.keys(eventsByDate)[0];
    return firstEventKey || todayKey;
  });

  const year = calendarViewDate.getFullYear();
  const month = calendarViewDate.getMonth(); // 0 - 11

  const handlePrevMonth = () => {
    setCalendarViewDate(new Date(year, month - 1, 1));
  };

  const handleNextMonth = () => {
    setCalendarViewDate(new Date(year, month + 1, 1));
  };

  const handleCurrentMonth = () => {
    const today = new Date();
    setCalendarViewDate(new Date(today.getFullYear(), today.getMonth(), 1));
    setSelectedDateKey(todayKey);
  };

  // Tính ma trận các ngày trong tháng (T2 - CN)
  const calendarDays = useMemo(() => {
    const firstDay = new Date(year, month, 1);
    const startDayIndex = (firstDay.getDay() + 6) % 7;
    const daysInCurrentMonth = new Date(year, month + 1, 0).getDate();
    const daysInPrevMonth = new Date(year, month, 0).getDate();

    const cells = [];

    // Các ngày cuối tháng trước
    for (let i = startDayIndex - 1; i >= 0; i--) {
      cells.push({
        dayNumber: daysInPrevMonth - i,
        isCurrentMonth: false,
        dateKey: toDateKey(new Date(year, month - 1, daysInPrevMonth - i)),
      });
    }

    // Các ngày tháng hiện tại
    for (let d = 1; d <= daysInCurrentMonth; d++) {
      cells.push({
        dayNumber: d,
        isCurrentMonth: true,
        dateKey: toDateKey(new Date(year, month, d)),
      });
    }

    // Các ngày đầu tháng sau
    const totalCells = cells.length > 35 ? 42 : 35;
    const remaining = totalCells - cells.length;
    for (let d = 1; d <= remaining; d++) {
      cells.push({
        dayNumber: d,
        isCurrentMonth: false,
        dateKey: toDateKey(new Date(year, month + 1, d)),
      });
    }

    return cells;
  }, [year, month]);

  // Sự kiện của ngày được chọn
  const selectedDayEvents = eventsByDate[selectedDateKey] || [];

  // Danh sách sự kiện sắp tới (dùng làm fallback khi ngày chọn không có sự kiện)
  const upcomingEvents = useMemo(() => {
    return activeEvents.slice(0, 3);
  }, [activeEvents]);

  // Hàm điều hướng trực tiếp vào sự kiện (ưu tiên onSelectEvent, fallback onOpenQr)
  const handleOpenEvent = (evt) => {
    if (onSelectEvent) {
      onSelectEvent(evt);
    } else if (onOpenQr) {
      onOpenQr(evt);
    } else if (onViewAllEvents) {
      onViewAllEvents();
    }
  };

  // Format ngày để hiển thị tiêu đề
  const formatDisplayDate = (key) => {
    if (!key) return '';
    try {
      const parts = key.split('-');
      if (parts.length === 3) {
        return `${parts[2]}/${parts[1]}/${parts[0]}`;
      }
    } catch {
      // ignore
    }
    return key;
  };

  return (
    <aside className="eo-right-sidebar eo-widget-panel">
      {/* =========================================================================
          WIDGET: LỊCH HOẠT ĐỘNG & SỰ KIỆN HỢP NHẤT (CALENDAR + UPCOMING SCHEDULE)
          ========================================================================= */}
      <div className="eo-widget-card" style={{ padding: '1.15rem' }}>
        {/* Header Lịch & Nút Điều Hướng Tháng */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.85rem' }}>
          <div>
            <h4 style={{ margin: 0, fontSize: '0.9rem', fontWeight: 700, color: 'var(--color-text-main)', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <span style={{ width: 8, height: 8, borderRadius: '50%', backgroundColor: 'var(--color-primary-active)', display: 'inline-block' }} />
              Tháng {month + 1}/{year}
            </h4>
            <span style={{ fontSize: '0.7rem', color: '#64748b' }}>
              Lịch hoạt động & sự kiện
            </span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
            <button
              type="button"
              onClick={handleCurrentMonth}
              style={{
                background: 'var(--color-primary-50)',
                border: '1px solid var(--color-primary-200)',
                borderRadius: 6,
                padding: '2px 7px',
                fontSize: '0.7rem',
                fontWeight: 600,
                cursor: 'pointer',
                color: 'var(--color-primary-active)',
              }}
              title="Về tháng hiện tại"
            >
              Hôm nay
            </button>
            <button
              type="button"
              onClick={handlePrevMonth}
              style={{
                background: '#ffffff',
                border: '1px solid #e2e8f0',
                borderRadius: 6,
                width: 24,
                height: 24,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: 'pointer',
                fontSize: '0.8rem',
                color: '#475569',
              }}
              title="Tháng trước"
            >
              &lsaquo;
            </button>
            <button
              type="button"
              onClick={handleNextMonth}
              style={{
                background: '#ffffff',
                border: '1px solid #e2e8f0',
                borderRadius: 6,
                width: 24,
                height: 24,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: 'pointer',
                fontSize: '0.8rem',
                color: '#475569',
              }}
              title="Tháng sau"
            >
              &rsaquo;
            </button>
          </div>
        </div>

        {/* Hàng Tiêu Đề Thứ Trong Tuần (T2 - CN) */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', textAlign: 'center', marginBottom: '0.35rem' }}>
          {['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'].map((w, idx) => (
            <span
              key={idx}
              style={{
                fontSize: '0.68rem',
                fontWeight: 700,
                color: idx >= 5 ? 'var(--color-primary-active)' : '#94a3b8',
              }}
            >
              {w}
            </span>
          ))}
        </div>

        {/* Lưới Ngày Trong Tháng */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: '3px', textAlign: 'center' }}>
          {calendarDays.map((cell, idx) => {
            const hasEvents = (eventsByDate[cell.dateKey] || []).length > 0;
            const isSelected = cell.dateKey === selectedDateKey;
            const isToday = cell.dateKey === todayKey;

            return (
              <div
                key={idx}
                onClick={() => cell.dateKey && setSelectedDateKey(cell.dateKey)}
                style={{
                  padding: '4px 0',
                  borderRadius: 7,
                  cursor: 'pointer',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  minHeight: '34px',
                  backgroundColor: isSelected
                    ? '#2563eb'
                    : isToday
                    ? '#eff6ff'
                    : 'transparent',
                  border: isToday && !isSelected
                    ? '1px solid #bfdbfe'
                    : isSelected
                    ? '1px solid #2563eb'
                    : '1px solid transparent',
                  color: isSelected
                    ? '#ffffff'
                    : !cell.isCurrentMonth
                    ? '#cbd5e1'
                    : isToday
                    ? '#1d4ed8'
                    : '#1e293b',
                  transition: 'all 0.15s ease',
                  boxShadow: isSelected ? '0 2px 6px rgba(37, 99, 235, 0.25)' : 'none',
                }}
                title={hasEvents ? `${cell.dateKey}: Có sự kiện` : cell.dateKey}
              >
                <span style={{ fontSize: '0.75rem', fontWeight: isToday || isSelected || hasEvents ? 700 : 500, lineHeight: 1 }}>
                  {cell.dayNumber}
                </span>

                {/* Dấu chấm chỉ báo ngày có sự kiện */}
                <div style={{ height: '4px', display: 'flex', alignItems: 'center', justifyContent: 'center', marginTop: 2 }}>
                  {hasEvents && (
                    <span
                      style={{
                        width: '4px',
                        height: '4px',
                        borderRadius: '50%',
                        backgroundColor: isSelected ? '#ffffff' : '#2563eb',
                        display: 'inline-block',
                      }}
                    />
                  )}
                </div>
              </div>
            );
          })}
        </div>

        {/* Khung Hiển Thị Danh Sách Sự Kiện Tương Ứng (Tích Hợp Bên Dưới Lịch) */}
        <div style={{ marginTop: '0.85rem', paddingTop: '0.75rem', borderTop: '1px solid #f1f5f9' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#334155' }}>
              {selectedDayEvents.length > 0
                ? `Lịch ngày ${formatDisplayDate(selectedDateKey)}`
                : 'Sự kiện sắp tới'}
            </span>
            <span style={{ fontSize: '0.68rem', color: 'var(--color-primary-active)', fontWeight: 600 }}>
              {selectedDayEvents.length > 0 ? `${selectedDayEvents.length} sự kiện` : `${upcomingEvents.length} sắp tới`}
            </span>
          </div>

          {/* Danh sách các sự kiện */}
          {selectedDayEvents.length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.45rem' }}>
              {selectedDayEvents.map((evt) => (
                <div
                  key={evt.eventId}
                  onClick={() => handleOpenEvent(evt)}
                  className="upcoming-event-item"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '0.45rem 0.6rem',
                    borderRadius: 8,
                    backgroundColor: '#eff6ff',
                    border: '1px solid #dbeafe',
                    cursor: 'pointer',
                    transition: 'all 0.15s ease',
                  }}
                  title="Nhấn để mở chi tiết sự kiện"
                >
                  <div style={{ minWidth: 0, paddingRight: '0.5rem' }}>
                    <div style={{ fontSize: '0.78rem', fontWeight: 700, color: 'var(--color-text-main)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {evt.name || 'Sự kiện không tên'}
                    </div>
                    <div style={{ fontSize: '0.68rem', color: '#64748b', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {evt.venueAddress || 'Chưa cập nhật địa điểm'}
                    </div>
                  </div>
                  <span style={{ color: 'var(--color-primary-active)', fontWeight: 700, fontSize: '0.85rem', flexShrink: 0 }}>
                    &rarr;
                  </span>
                </div>
              ))}
            </div>
          ) : upcomingEvents.length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.45rem' }}>
              <div style={{ fontSize: '0.68rem', color: '#94a3b8', marginBottom: 2 }}>
                Ngày này chưa có lịch, xem sự kiện gần nhất:
              </div>
              {upcomingEvents.map((evt) => {
                let dayStr = '--';
                let monthStr = 'Thg --';
                if (evt.eventDate) {
                  try {
                    const d = new Date(evt.eventDate);
                    if (!isNaN(d.getTime())) {
                      dayStr = String(d.getDate()).padStart(2, '0');
                      monthStr = `Thg ${d.getMonth() + 1}`;
                    }
                  } catch {
                    // ignore
                  }
                }
                return (
                  <div
                    key={evt.eventId}
                    onClick={() => handleOpenEvent(evt)}
                    className="upcoming-event-item"
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.5rem',
                      padding: '0.4rem 0.55rem',
                      borderRadius: 8,
                      backgroundColor: '#f8fafc',
                      border: '1px solid #f1f5f9',
                      cursor: 'pointer',
                      transition: 'all 0.15s ease',
                    }}
                    title="Nhấn để mở chi tiết sự kiện"
                  >
                    <div
                      style={{
                        width: 34,
                        height: 34,
                        borderRadius: 6,
                        backgroundColor: '#ffffff',
                        border: '1px solid #e2e8f0',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0,
                      }}
                    >
                      <span style={{ fontSize: '0.75rem', fontWeight: 800, color: '#0f172a', lineHeight: 1 }}>{dayStr}</span>
                      <span style={{ fontSize: '0.55rem', color: '#64748b', fontWeight: 600 }}>{monthStr}</span>
                    </div>
                    <div style={{ minWidth: 0, flex: 1 }}>
                      <div style={{ fontSize: '0.78rem', fontWeight: 700, color: '#0f172a', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {evt.name || 'Sự kiện không tên'}
                      </div>
                      <div style={{ fontSize: '0.68rem', color: '#64748b', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {evt.venueAddress || 'Chưa cập nhật địa điểm'}
                      </div>
                    </div>
                    <span style={{ color: '#94a3b8', fontSize: '0.8rem', flexShrink: 0 }}>&rarr;</span>
                  </div>
                );
              })}
            </div>
          ) : (
            <div style={{ textAlign: 'center', padding: '0.75rem 0', color: '#94a3b8', fontSize: '0.75rem' }}>
              Chưa có lịch sự kiện sắp tới
            </div>
          )}
        </div>
      </div>
    </aside>
  );
}
