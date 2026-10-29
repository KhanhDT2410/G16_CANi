import React from 'react';

/**
 * EventShiftsDrawer
 * Ngăn kéo hiển thị danh sách Ca làm việc thuộc một Sự kiện kèm thao tác QR, Điểm danh, Sửa, Hủy
 */
const EventShiftsDrawer = ({
  event,
  onAddShift,
  onOpenQr,
  onOpenAttendance,
  onEditShift,
  onCancelShift,
}) => {
  if (!event) return null;

  const shifts = event.shifts || [];

  const formatTime = (iso, fallback) => {
    if (!iso) return fallback;
    try {
      return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return fallback;
    }
  };

  const getShiftStatusMeta = (status) => {
    if (status === 'ACTIVE') {
      return { label: 'Đang tuyển', bg: '#dcfce7', color: '#15803d' };
    }
    if (status === 'CANCELLED') {
      return { label: 'Đã hủy', bg: '#fee2e2', color: '#b91c1c' };
    }
    return { label: 'Bản nháp', bg: '#fef3c7', color: '#b45309' };
  };

  return (
    <div
      className="event-shifts-drawer"
      style={{
        width: '100%',
        marginTop: '1.2rem',
        paddingTop: '1.2rem',
        borderTop: '1px dashed #cbd5e1',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h4 style={{ margin: 0, fontSize: '0.95rem', color: '#1e293b', display: 'flex', alignItems: 'center', gap: 6 }}>
          <span>📅 Danh Sách Ca Làm Việc Thuộc Sự Kiện</span>
          <span style={{ fontSize: '0.8rem', color: '#64748b', fontWeight: 'normal' }}>
            ({shifts.length} ca)
          </span>
        </h4>
        <button
          type="button"
          style={{
            background: '#eff6ff',
            color: '#1d4ed8',
            border: '1px solid #bfdbfe',
            borderRadius: 6,
            padding: '5px 12px',
            fontSize: '0.8rem',
            fontWeight: 600,
            cursor: 'pointer',
          }}
          onClick={() => onAddShift(event.eventId)}
        >
          + Thêm Ca Mới
        </button>
      </div>

      {shifts.length === 0 ? (
        <div style={{ padding: '1.5rem', textAlign: 'center', background: '#f8fafc', borderRadius: 8, color: '#64748b', fontSize: '0.85rem' }}>
          Chưa có ca làm việc nào được tạo. Hãy nhấn <strong>+ Thêm Ca Mới</strong> để lên lịch ca.
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          {shifts.map((shift) => {
            const statusMeta = getShiftStatusMeta(shift.status);
            return (
              <div
                key={shift.shiftId}
                style={{
                  background: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: 10,
                  padding: '0.85rem 1.1rem',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 12,
                  flexWrap: 'wrap',
                }}
              >
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontWeight: 700, fontSize: '0.95rem', color: '#1e293b' }}>
                      {shift.jobType}
                    </span>
                    <span style={{
                      padding: '2px 8px',
                      borderRadius: 10,
                      fontSize: '0.75rem',
                      fontWeight: 600,
                      background: statusMeta.bg,
                      color: statusMeta.color,
                    }}>
                      {statusMeta.label}
                    </span>
                    {shift.dressCode && (
                      <span style={{ fontSize: '0.75rem', color: '#64748b', background: '#e2e8f0', padding: '2px 6px', borderRadius: 4 }}>
                        👔 {shift.dressCode}
                      </span>
                    )}
                  </div>
                  <div style={{ fontSize: '0.82rem', color: '#64748b', marginTop: 4 }}>
                    🕒 {formatTime(shift.startTime, '08:00')} - {formatTime(shift.endTime, '12:00')} • 👥 <strong>{shift.confirmedWorkers || 0}/{shift.requiredWorkers || 0}</strong> Nhân sự • 💰 <strong style={{ color: '#059669' }}>{Number(shift.hourlyWage || 50000).toLocaleString()} đ/h</strong>
                  </div>
                </div>

                {/* Thao tác trên từng ca */}
                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                  <button
                    type="button"
                    onClick={() => onOpenQr(shift, event)}
                    style={{
                      background: '#2563eb',
                      color: '#ffffff',
                      border: 'none',
                      borderRadius: 6,
                      padding: '6px 12px',
                      fontSize: '0.8rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 4,
                    }}
                    title="Hiển thị mã QR Check-in / Check-out cho Worker quét"
                  >
                    📱 Mã QR
                  </button>

                  <button
                    type="button"
                    onClick={() => onOpenAttendance(shift, event)}
                    style={{
                      background: '#0d9488',
                      color: '#ffffff',
                      border: 'none',
                      borderRadius: 6,
                      padding: '6px 12px',
                      fontSize: '0.8rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 4,
                    }}
                    title="Theo dõi điểm danh, duyệt công và đánh giá Worker"
                  >
                    📋 Điểm Danh
                  </button>

                  <button
                    type="button"
                    onClick={() => onEditShift(shift)}
                    style={{
                      background: '#ffffff',
                      color: '#475569',
                      border: '1px solid #cbd5e1',
                      borderRadius: 6,
                      padding: '6px 10px',
                      fontSize: '0.8rem',
                      cursor: 'pointer',
                    }}
                    title="Chỉnh sửa thông tin ca này"
                  >
                    ✏️ Sửa
                  </button>

                  {shift.status !== 'CANCELLED' && (
                    <button
                      type="button"
                      onClick={() => onCancelShift(shift)}
                      style={{
                        background: '#ffffff',
                        color: '#ef4444',
                        border: '1px solid #fca5a5',
                        borderRadius: 6,
                        padding: '6px 10px',
                        fontSize: '0.8rem',
                        cursor: 'pointer',
                      }}
                      title="Hủy ca làm việc này"
                    >
                      ❌ Hủy
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default EventShiftsDrawer;

