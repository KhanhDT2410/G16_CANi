import React, { useState, useMemo } from 'react';
import { showToast } from '../../../components/common/Toast';
import ShiftQrAndAttendancePanel from './ShiftQrAndAttendancePanel';

/**
 * EventShiftsDrawer
 * Ngăn kéo hiển thị danh sách Ca làm việc thuộc một Sự kiện:
 * - Ca đang hoạt động (ACTIVE) xếp trên đầu, Ca nháp (DRAFT) xếp ở dưới.
 * - Ca đã hủy (CANCELLED) được tách hẳn sang khu vực riêng ở dưới cùng.
 * - Gộp Điểm danh & Mã QR trượt mở ngay bên dưới (không dùng popup nhỏ).
 */
const EventShiftsDrawer = ({
  event,
  onOpenDetail,
  onAddShift,
  onOpenQr,
  onOpenInvite,
  onOpenAttendance,
  onEditShift,
  onCancelShift,
  onReloadEvents,
  onOpenPaymentDeclaration,
}) => {
  const [selectedShiftForPanel, setSelectedShiftForPanel] = useState(null);
  const [showCancelledShifts, setShowCancelledShifts] = useState(false);

  const shifts = event?.shifts || [];

  // Phân loại & sắp xếp: Ca đang hoạt động lên đầu, Ca nháp xuống dưới; Ca đã hủy tách riêng
  const { activeAndDraftShifts, cancelledShifts } = useMemo(() => {
    const activeDraft = [];
    const cancelled = [];

    shifts.forEach((s) => {
      if (s.status === 'CANCELLED') {
        cancelled.push(s);
      } else {
        activeDraft.push(s);
      }
    });

    activeDraft.sort((a, b) => {
      const rankA = a.status === 'ACTIVE' ? 1 : 2;
      const rankB = b.status === 'ACTIVE' ? 1 : 2;
      if (rankA !== rankB) return rankA - rankB;
      return new Date(a.startTime || 0) - new Date(b.startTime || 0);
    });

    return { activeAndDraftShifts: activeDraft, cancelledShifts: cancelled };
  }, [shifts]);

  if (!event) return null;

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

  const handleToggleQrAttendance = (shift) => {
    setSelectedShiftForPanel((prev) => (prev?.shiftId === shift.shiftId ? null : shift));
  };

  const renderShiftRow = (shift) => {
    const statusMeta = getShiftStatusMeta(shift.status);
    const isPanelOpen = selectedShiftForPanel?.shiftId === shift.shiftId;

    return (
      <div key={shift.shiftId} style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
        <div
          style={{
            background: isPanelOpen ? '#eff6ff' : '#f8fafc',
            border: isPanelOpen ? '1.5px solid #2563eb' : '1px solid #e2e8f0',
            borderRadius: 10,
            padding: '0.85rem 1.1rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            flexWrap: 'wrap',
            transition: 'all 0.2s',
          }}
        >
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontWeight: 700, fontSize: '0.95rem', color: '#1e293b' }}>
                {shift.jobType}
              </span>
              <span
                style={{
                  padding: '2px 8px',
                  borderRadius: 10,
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  background: statusMeta.bg,
                  color: statusMeta.color,
                }}
              >
                {statusMeta.label}
              </span>
              {shift.dressCode && (
                <span style={{ fontSize: '0.75rem', color: '#64748b', background: '#e2e8f0', padding: '2px 6px', borderRadius: 4 }}>
                  Đồng phục: {shift.dressCode}
                </span>
              )}
            </div>
            <div style={{ fontSize: '0.82rem', color: '#64748b', marginTop: 4 }}>
              {formatTime(shift.startTime, '08:00')} - {formatTime(shift.endTime, '12:00')} •{' '}
              <strong>
                {shift.confirmedWorkers || 0}/{shift.requiredWorkers || 0}
              </strong>{' '}
              Nhân sự •{' '}
              <strong style={{ color: '#059669' }}>
                {Number(shift.hourlyWage || 50000).toLocaleString()} đ/h
              </strong>
            </div>
          </div>

          {/* Thao tác trên từng ca */}
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {shift.status !== 'CANCELLED' && (
              <button
                type="button"
                onClick={() => handleToggleQrAttendance(shift)}
                style={{
                  background: isPanelOpen ? '#1e40af' : '#2563eb',
                  color: '#ffffff',
                  border: 'none',
                  borderRadius: 6,
                  padding: '6px 12px',
                  fontSize: '0.8rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 5,
                }}
                title="Mở bảng Điểm danh & Mã QR trực tiếp ngay bên dưới"
              >
                <span>{isPanelOpen ? '▾ Đóng Điểm Danh & QR' : '▸ Điểm Danh & Mã QR'}</span>
              </button>
            )}

            {shift.status !== 'CANCELLED' && (
              <button
                type="button"
                onClick={() => onOpenInvite && onOpenInvite(shift, event)}
                style={{
                  background: '#7c3aed',
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
                title="Lấy link & mã mời trực tiếp cho nhân sự"
              >
                Mã Mời
              </button>
            )}

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
              Sửa
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
                Hủy
              </button>
            )}
          </div>
        </div>

        {/* Trượt mở ngay bên dưới ca đang chọn: Mã QR Động + Bảng Điểm Danh Trực Tiếp */}
        {isPanelOpen && (
          <ShiftQrAndAttendancePanel
            shift={{ ...shift, eventName: event.name }}
            event={event}
            onSelectShift={(nextShift) => setSelectedShiftForPanel(nextShift)}
            onClose={() => setSelectedShiftForPanel(null)}
            onReloadEvents={onReloadEvents}
            onOpenPaymentDeclaration={onOpenPaymentDeclaration}
          />
        )}
      </div>
    );
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
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.5rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' }}>
          <h4 style={{ margin: 0, fontSize: '0.95rem', color: '#1e293b' }}>
            Danh Sách Ca Làm Việc
          </h4>
          <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
            ({activeAndDraftShifts.length} ca hoạt động/nháp)
          </span>
          {event.eventId && (
            <button
              type="button"
              style={{
                fontSize: '0.72rem',
                color: '#64748b',
                background: '#f1f5f9',
                border: '1px solid #e2e8f0',
                padding: '2px 8px',
                borderRadius: '6px',
                fontFamily: 'monospace',
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '4px',
              }}
              title={`Nhấp để sao chép mã sự kiện: ${event.eventId}`}
              onClick={() => {
                if (navigator.clipboard) {
                  navigator.clipboard.writeText(event.eventId);
                  showToast('Đã sao chép mã sự kiện', 'success');
                }
              }}
            >
              Mã: {event.eventId.length > 12 ? `${event.eventId.substring(0, 8)}...` : event.eventId}
            </button>
          )}
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          {onOpenDetail && (
            <button
              type="button"
              style={{
                background: '#f8fafc',
                color: '#2563eb',
                border: '1px solid #bfdbfe',
                borderRadius: 6,
                padding: '5px 12px',
                fontSize: '0.8rem',
                fontWeight: 600,
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.35rem',
              }}
              onClick={() => onOpenDetail(event)}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                <circle cx="12" cy="12" r="3"></circle>
              </svg>
              Xem chi tiết sự kiện & nhân sự
            </button>
          )}
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
      </div>

      {activeAndDraftShifts.length === 0 ? (
        <div style={{ padding: '1.5rem', textAlign: 'center', background: '#f8fafc', borderRadius: 8, color: '#64748b', fontSize: '0.85rem' }}>
          Chưa có ca làm việc nào đang hoạt động. Hãy nhấn <strong>+ Thêm Ca Mới</strong> để lên lịch ca.
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          {activeAndDraftShifts.map((shift) => renderShiftRow(shift))}
        </div>
      )}

      {/* Khu vực riêng biệt dành cho Ca đã hủy / đã xóa */}
      {cancelledShifts.length > 0 && (
        <div style={{ marginTop: '1rem', paddingTop: '0.75rem', borderTop: '1px dashed #e2e8f0' }}>
          <button
            type="button"
            onClick={() => setShowCancelledShifts((prev) => !prev)}
            style={{
              background: '#fef2f2',
              color: '#b91c1c',
              border: '1px solid #fecaca',
              borderRadius: 8,
              padding: '6px 12px',
              fontSize: '0.8rem',
              fontWeight: 600,
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
            }}
          >
            <span>{showCancelledShifts ? '▾ Ẩn ca đã hủy' : `▸ Xem ${cancelledShifts.length} ca đã hủy / đã xóa (Tách riêng)`}</span>
          </button>

          {showCancelledShifts && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.65rem', marginTop: '0.75rem', opacity: 0.85 }}>
              {cancelledShifts.map((shift) => renderShiftRow(shift))}
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default EventShiftsDrawer;
