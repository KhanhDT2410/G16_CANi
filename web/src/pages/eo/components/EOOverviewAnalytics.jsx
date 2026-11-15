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
 * EOOverviewAnalytics
 * Khối Thống Kê Tổng Quát & Lịch Sự Kiện Theo Tháng cho Ban Tổ Chức Sự Kiện (EO)
 * - Lịch tháng tương tác: Đánh dấu chấm (dot) các ngày có sự kiện, chuyển tháng, xem nhanh sự kiện.
 * - Cơ cấu vị trí tuyển dụng: Phân bổ số lượng nhân sự theo từng vai trò ca làm.
 * - Bảng quy mô sự kiện: Đã bỏ toàn bộ emoji/icon, thiết kế chuẩn doanh nghiệp.
 */
export default function EOOverviewAnalytics({ events = [], onSelectEvent }) {
  // 1. Lọc danh sách sự kiện chưa hủy
  const activeEvents = useMemo(() => {
    return (events || [])
      .filter((e) => e && e.status !== 'CANCELLED')
      .sort((a, b) => new Date(b.createdAt || b.eventDate || 0) - new Date(a.createdAt || a.eventDate || 0));
  }, [events]);

  // 2. Gom nhóm sự kiện theo ngày 'YYYY-MM-DD' để hiển thị dấu chấm trên lịch
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

  // 3. Quản lý trạng thái Lịch theo tháng
  const [calendarViewDate, setCalendarViewDate] = useState(() => {
    // Mặc định chọn tháng của sự kiện gần nhất hoặc ngày hôm nay
    if (activeEvents.length > 0 && activeEvents[0].eventDate) {
      const d = new Date(activeEvents[0].eventDate);
      if (!isNaN(d.getTime())) return new Date(d.getFullYear(), d.getMonth(), 1);
    }
    const today = new Date();
    return new Date(today.getFullYear(), today.getMonth(), 1);
  });

  const [selectedDateKey, setSelectedDateKey] = useState(() => {
    const todayKey = toDateKey(new Date());
    if (eventsByDate[todayKey]) return todayKey;
    const firstEventDateKey = Object.keys(eventsByDate)[0];
    return firstEventDateKey || todayKey;
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
    setSelectedDateKey(toDateKey(today));
  };

  // Tính toán ma trận các ngày trong tháng (Thứ Hai đến Chủ Nhật)
  const calendarDays = useMemo(() => {
    const firstDay = new Date(year, month, 1);
    // JS getDay(): 0 là CN, 1 là T2... Chuyển sang chuẩn T2 = 0, CN = 6
    const startDayIndex = (firstDay.getDay() + 6) % 7;
    const daysInCurrentMonth = new Date(year, month + 1, 0).getDate();
    const daysInPrevMonth = new Date(year, month, 0).getDate();

    const cells = [];

    // Các ngày cuối tháng trước (mờ)
    for (let i = startDayIndex - 1; i >= 0; i--) {
      cells.push({
        dayNumber: daysInPrevMonth - i,
        isCurrentMonth: false,
        dateKey: toDateKey(new Date(year, month - 1, daysInPrevMonth - i)),
      });
    }

    // Các ngày trong tháng hiện tại
    for (let d = 1; d <= daysInCurrentMonth; d++) {
      cells.push({
        dayNumber: d,
        isCurrentMonth: true,
        dateKey: toDateKey(new Date(year, month, d)),
      });
    }

    // Các ngày đầu tháng sau để đủ khung
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

  // Danh sách sự kiện của ngày được chọn
  const selectedDayEvents = eventsByDate[selectedDateKey] || [];

  // 4. Cơ cấu vị trí tuyển dụng (Job Roles Breakdown)
  const roleBreakdown = useMemo(() => {
    const map = {};
    activeEvents.forEach((evt) => {
      (evt.shifts || []).forEach((sh) => {
        if (sh.status === 'CANCELLED') return;
        const role = sh.jobType || 'Nhân sự tổng hợp';
        if (!map[role]) {
          map[role] = { role, required: 0, confirmed: 0 };
        }
        map[role].required += Number(sh.requiredWorkers) || 0;
        map[role].confirmed += Number(sh.confirmedWorkers) || 0;
      });
    });

    const list = Object.values(map);
    if (list.length === 0) {
      return [
        { role: 'Phục vụ bàn tiệc (F&B)', required: 12, confirmed: 10, color: '#2563eb' },
        { role: 'Lễ tân / PG / PB sự kiện', required: 8, confirmed: 8, color: '#059669' },
        { role: 'Setup & Hậu cần sân khấu', required: 6, confirmed: 4, color: '#d97706' },
        { role: 'Điều phối & Check-in vé', required: 4, confirmed: 3, color: '#7c3aed' },
      ];
    }

    const colors = ['#2563eb', '#059669', '#d97706', '#7c3aed', '#0891b2', '#e11d48'];
    return list.map((item, idx) => ({
      ...item,
      color: colors[idx % colors.length],
      percent: item.required > 0 ? Math.round((item.confirmed / item.required) * 100) : 0,
    }));
  }, [activeEvents]);

  // Format tiền tệ VNĐ
  const formatCurrency = (val) => new Intl.NumberFormat('vi-VN').format(val || 0);

  const todayKey = toDateKey(new Date());

  return (
    <div className="eo-overview-analytics-container animate-fade-in-up" style={{ marginTop: '1.25rem', display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
      
      {/* =========================================================================
          KHỐI 1: GRID 2 CỘT: CƠ CẤU VỊ TRÍ (TRÁI) & LỊCH SỰ KIỆN THEO THÁNG (PHẢI)
          ========================================================================= */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '1.25rem', alignItems: 'stretch' }}>
        
        {/* CỘT TRÁI: Cơ cấu vị trí tuyển dụng */}
        <div className="eo-widget-card" style={{ padding: '1.25rem', display: 'flex', flexDirection: 'column' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.1rem' }}>
            <div>
              <h4 style={{ margin: 0, fontSize: '0.95rem', fontWeight: 700, color: '#0f172a' }}>
                Cơ Cấu Vị Trí & Nhu Cầu Nhân Sự
              </h4>
              <p style={{ margin: '0.2rem 0 0', fontSize: '0.75rem', color: '#64748b' }}>
                Phân bổ số lượng nhân sự theo chuyên môn các ca làm việc
              </p>
            </div>
            <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#2563eb', backgroundColor: '#eff6ff', padding: '3px 10px', borderRadius: 8 }}>
              {roleBreakdown.length} vị trí
            </span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', flex: 1, justifyContent: 'center' }}>
            {roleBreakdown.map((item, idx) => {
              const percent = item.required > 0 ? Math.round((item.confirmed / item.required) * 100) : 0;
              return (
                <div key={idx} style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.82rem' }}>
                    <span style={{ fontWeight: 600, color: '#334155' }}>{item.role}</span>
                    <span style={{ fontWeight: 700, color: item.confirmed >= item.required && item.required > 0 ? '#10b981' : '#2563eb' }}>
                      {item.confirmed}/{item.required} nhân sự ({percent}%)
                    </span>
                  </div>
                  <div style={{ width: '100%', height: '8px', backgroundColor: '#f1f5f9', borderRadius: '4px', overflow: 'hidden' }}>
                    <div
                      style={{
                        width: `${Math.min(100, Math.max(8, percent))}%`,
                        height: '100%',
                        backgroundColor: item.color || '#2563eb',
                        borderRadius: '4px',
                        transition: 'width 0.4s ease-in-out',
                      }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* CỘT PHẢI: Lịch Sự Kiện Theo Tháng (Monthly Event Calendar) */}
        <div className="eo-widget-card" style={{ padding: '1.25rem', display: 'flex', flexDirection: 'column' }}>
          
          {/* Header Lịch: Tháng/Năm & Nút Điều Hướng */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
            <div>
              <h4 style={{ margin: 0, fontSize: '0.95rem', fontWeight: 700, color: '#0f172a' }}>
                Lịch Sự Kiện Tháng {month + 1}/{year}
              </h4>
              <p style={{ margin: '0.2rem 0 0', fontSize: '0.75rem', color: '#64748b' }}>
                Ngày có dấu chấm xanh biểu thị có lịch sự kiện diễn ra
              </p>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
              <button
                type="button"
                onClick={handleCurrentMonth}
                style={{
                  background: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: 6,
                  padding: '3px 8px',
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  color: '#475569',
                }}
              >
                Hôm nay
              </button>
              <button
                type="button"
                onClick={handlePrevMonth}
                style={{
                  background: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: 6,
                  width: 26,
                  height: 26,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  cursor: 'pointer',
                  fontSize: '0.85rem',
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
                  background: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: 6,
                  width: 26,
                  height: 26,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  cursor: 'pointer',
                  fontSize: '0.85rem',
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
              <span key={idx} style={{ fontSize: '0.72rem', fontWeight: 700, color: idx >= 5 ? '#2563eb' : '#64748b' }}>
                {w}
              </span>
            ))}
          </div>

          {/* Lưới Ngày Trong Tháng */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: '4px', textAlign: 'center' }}>
            {calendarDays.map((cell, idx) => {
              const hasEvents = (eventsByDate[cell.dateKey] || []).length > 0;
              const isSelected = cell.dateKey === selectedDateKey;
              const isToday = cell.dateKey === todayKey;

              return (
                <div
                  key={idx}
                  onClick={() => cell.dateKey && setSelectedDateKey(cell.dateKey)}
                  style={{
                    padding: '6px 0',
                    borderRadius: 8,
                    cursor: 'pointer',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    backgroundColor: isSelected
                      ? '#2563eb'
                      : isToday
                      ? '#eff6ff'
                      : hasEvents
                      ? '#f0fdf4'
                      : 'transparent',
                    border: isToday && !isSelected
                      ? '1px solid #93c5fd'
                      : isSelected
                      ? '1px solid #2563eb'
                      : '1px solid transparent',
                    color: isSelected
                      ? '#ffffff'
                      : !cell.isCurrentMonth
                      ? '#cbd5e1'
                      : '#1e293b',
                    transition: 'all 0.15s ease',
                  }}
                  title={hasEvents ? `${cell.dateKey}: Có sự kiện` : cell.dateKey}
                >
                  <span style={{ fontSize: '0.8rem', fontWeight: isToday || hasEvents || isSelected ? 700 : 500, lineHeight: 1 }}>
                    {cell.dayNumber}
                  </span>
                  
                  {/* Dấu chấm tròn (Dot) nếu ngày có sự kiện */}
                  <div style={{ height: '6px', display: 'flex', alignItems: 'center', justifyContent: 'center', marginTop: 3 }}>
                    {hasEvents && (
                      <span
                        style={{
                          width: '5px',
                          height: '5px',
                          borderRadius: '50%',
                          backgroundColor: isSelected ? '#ffffff' : '#10b981',
                          display: 'inline-block',
                        }}
                      />
                    )}
                  </div>
                </div>
              );
            })}
          </div>

          {/* Khung Xem Nhanh Sự Kiện của Ngày Được Chọn */}
          <div
            style={{
              marginTop: '0.85rem',
              padding: '0.75rem 0.85rem',
              backgroundColor: '#f8fafc',
              borderRadius: 8,
              border: '1px solid #e2e8f0',
              fontSize: '0.8rem',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: selectedDayEvents.length > 0 ? 6 : 0 }}>
              <span style={{ fontWeight: 700, color: '#334155' }}>
                Ngày {selectedDateKey ? new Date(selectedDateKey).toLocaleDateString('vi-VN') : '—'}
              </span>
              <span style={{ fontSize: '0.72rem', color: '#64748b' }}>
                {selectedDayEvents.length} sự kiện
              </span>
            </div>

            {selectedDayEvents.length === 0 ? (
              <div style={{ color: '#94a3b8', fontSize: '0.75rem', textAlign: 'center', padding: '0.35rem 0' }}>
                Không có lịch sự kiện diễn ra vào ngày này
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem', marginTop: '0.35rem' }}>
                {selectedDayEvents.map((evt) => (
                  <div
                    key={evt.eventId}
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      backgroundColor: '#ffffff',
                      padding: '6px 10px',
                      borderRadius: 6,
                      border: '1px solid #e2e8f0',
                    }}
                  >
                    <div style={{ minWidth: 0, paddingRight: 8 }}>
                      <div style={{ fontWeight: 700, color: '#0f172a', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {evt.name}
                      </div>
                      <div style={{ fontSize: '0.7rem', color: '#64748b', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {evt.venueAddress || 'Chưa cập nhật địa điểm'}
                      </div>
                    </div>
                    <button
                      type="button"
                      className="eo-btn-primary"
                      style={{ padding: '3px 8px', fontSize: '0.72rem', whiteSpace: 'nowrap', flexShrink: 0 }}
                      onClick={() => onSelectEvent?.(evt)}
                    >
                      Mở &rarr;
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* =========================================================================
          KHỐI 2: BẢNG SO SÁNH QUY MÔ & TIẾN ĐỘ CÁC SỰ KIỆN ĐANG HOẠT ĐỘNG
          (ĐÃ BỎ HOÀN TOÀN TẤT CẢ ICON & EMOJI THEO YÊU CẦU)
          ========================================================================= */}
      <div className="eo-widget-card" style={{ padding: '1.25rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.5rem' }}>
          <div>
            <h4 style={{ margin: 0, fontSize: '0.95rem', fontWeight: 700, color: '#0f172a' }}>
              Quy Mô & Dự Toán Các Sự Kiện Đang Quản Lý
            </h4>
            <p style={{ margin: '0.2rem 0 0', fontSize: '0.75rem', color: '#64748b' }}>
              Theo dõi trực tiếp số ca, tỷ lệ lấp đầy nhân sự và ngân sách dự toán từng sự kiện
            </p>
          </div>
          <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
            Hiển thị <strong>{activeEvents.length}</strong> sự kiện
          </span>
        </div>

        {activeEvents.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '2rem', color: '#94a3b8', fontSize: '0.85rem' }}>
            Chưa có sự kiện nào đang hoạt động.
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="sub-table" style={{ width: '100%', textAlign: 'left', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid #e2e8f0', color: '#64748b', fontSize: '0.78rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                  <th style={{ padding: '10px 12px' }}>Tên Sự Kiện</th>
                  <th style={{ padding: '10px 12px' }}>Thời Gian / Địa Điểm</th>
                  <th style={{ padding: '10px 12px', textAlign: 'center' }}>Số Ca</th>
                  <th style={{ padding: '10px 12px' }}>Tiến Độ Tuyển Dụng</th>
                  <th style={{ padding: '10px 12px', textAlign: 'right' }}>Dự Toán Chi Phí</th>
                  <th style={{ padding: '10px 12px', textAlign: 'center' }}>Thao Tác</th>
                </tr>
              </thead>
              <tbody>
                {activeEvents.slice(0, 5).map((evt) => {
                  const req = evt.totalRequiredWorkers || (evt.shifts || []).reduce((s, sh) => s + (sh.requiredWorkers || 0), 0);
                  const conf = evt.totalConfirmedWorkers || (evt.shifts || []).reduce((s, sh) => s + (sh.confirmedWorkers || 0), 0);
                  const rate = req > 0 ? Math.round((conf / req) * 100) : 0;
                  const shiftsCount = evt.totalShifts || (evt.shifts || []).length || 0;
                  const cost = Number(evt.totalEstimatedCost) || (evt.shifts || []).reduce((s, sh) => {
                    const hours = sh.startTime && sh.endTime ? Math.max(0.5, (new Date(sh.endTime) - new Date(sh.startTime)) / 3600000) : 4;
                    return s + (hours * (sh.requiredWorkers || 1) * (sh.hourlyWage || 50000) + (sh.allowance || 0));
                  }, 0);

                  return (
                    <tr
                      key={evt.eventId}
                      style={{ borderBottom: '1px solid #f1f5f9', fontSize: '0.85rem', transition: 'background 0.2s' }}
                      className="table-row-hover"
                    >
                      <td style={{ padding: '12px', fontWeight: 700, color: '#0f172a' }}>
                        {evt.name || 'Sự kiện không tên'}
                      </td>
                      <td style={{ padding: '12px', color: '#64748b', maxWidth: '240px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        <div>{evt.eventDate ? new Date(evt.eventDate).toLocaleDateString('vi-VN') : 'Chưa định ngày'}</div>
                        <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>{evt.venueAddress || 'Chưa cập nhật địa chỉ'}</div>
                      </td>
                      <td style={{ padding: '12px', textAlign: 'center', fontWeight: 600 }}>
                        <span style={{ backgroundColor: '#f1f5f9', padding: '3px 8px', borderRadius: 6, fontSize: '0.78rem' }}>
                          {shiftsCount} ca
                        </span>
                      </td>
                      <td style={{ padding: '12px', minWidth: '160px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', marginBottom: 4 }}>
                          <span style={{ fontWeight: 600, color: '#334155' }}>{conf}/{req} người</span>
                          <span style={{ fontWeight: 700, color: rate >= 100 ? '#10b981' : '#2563eb' }}>{rate}%</span>
                        </div>
                        <div style={{ width: '100%', height: '6px', backgroundColor: '#f1f5f9', borderRadius: 3, overflow: 'hidden' }}>
                          <div
                            style={{
                              width: `${Math.min(100, Math.max(5, rate))}%`,
                              height: '100%',
                              backgroundColor: rate >= 100 ? '#10b981' : '#2563eb',
                              borderRadius: 3,
                            }}
                          />
                        </div>
                      </td>
                      <td style={{ padding: '12px', textAlign: 'right', fontWeight: 700, color: '#059669' }}>
                        {formatCurrency(cost)} đ
                      </td>
                      <td style={{ padding: '12px', textAlign: 'center' }}>
                        <button
                          type="button"
                          className="eo-btn-secondary"
                          style={{ padding: '4px 10px', fontSize: '0.78rem', borderRadius: 6 }}
                          onClick={() => onSelectEvent?.(evt)}
                        >
                          Chi tiết &rarr;
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

    </div>
  );
}
