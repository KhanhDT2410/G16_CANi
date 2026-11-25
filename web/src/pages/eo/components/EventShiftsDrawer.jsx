import React, { useState, useMemo } from 'react';
import { showToast } from '../../../components/common/Toast';
import { showConfirm } from '../../../components/common/PromptModal';
import { eoService } from '../../../services/eoService';
import ShiftQrAndAttendancePanel from './ShiftQrAndAttendancePanel';
import { getDynamicShiftStatus, getShiftStaffingSummary } from '../utils/eventHelpers';

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
  onOpenShiftWorkspace,
  onAddShift,
  onOpenQr,
  onOpenInvite,
  onOpenAttendance,
  onEditShift,
  onCancelShift,
  onReloadEvents,
  onOpenPaymentDeclaration,
  skillTaxonomy = [],
}) => {
  const [selectedShiftForPanel, setSelectedShiftForPanel] = useState(null);
  const [showCancelledShifts, setShowCancelledShifts] = useState(false);

  // Phân loại & sắp xếp: Ca đang hoạt động lên đầu, Ca nháp xuống dưới; Ca đã hủy tách riêng
  const { activeAndDraftShifts, cancelledShifts } = useMemo(() => {
    const list = event?.shifts || [];
    const activeDraft = [];
    const cancelled = [];

    list.forEach((s) => {
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
  }, [event?.shifts]);

  // Map skillId -> tên kỹ năng (taxonomy) để hiển thị badge requiredSkills
  const skillNameMap = useMemo(() => {
    const m = {};
    (Array.isArray(skillTaxonomy) ? skillTaxonomy : []).forEach((sk) => {
      if (sk && sk.skillId) m[sk.skillId] = sk.name;
    });
    return m;
  }, [skillTaxonomy]);

  if (!event) return null;

  const formatTime = (iso, fallback) => {
    if (!iso) return fallback;
    try {
      return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return fallback;
    }
  };

  const hasCustomArrival = (shift) =>
    shift.arrivalTime && shift.startTime &&
    new Date(shift.arrivalTime).getTime() !== new Date(shift.startTime).getTime();

  const getShiftStatusMeta = (shiftObj) => {
    const dyn = getDynamicShiftStatus(shiftObj, event?.status);
    return {
      label: dyn.label,
      bg: dyn.bg,
      color: dyn.color,
    };
  };

  const handleToggleQrAttendance = (shift) => {
    setSelectedShiftForPanel((prev) => (prev?.shiftId === shift.shiftId ? null : shift));
  };

  const renderShiftRow = (shift) => {
    const statusMeta = getShiftStatusMeta(shift);
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
              {(shift.performedSkillName || skillNameMap[shift.performedSkillId]) && (
                <span
                  style={{ fontSize: '0.75rem', color: '#047857', background: '#ecfdf5', padding: '2px 6px', borderRadius: 4, fontWeight: 700 }}
                  title="Kỹ năng thực hiện chính để cộng +20 SP/ca"
                >
                  Chính: {shift.performedSkillName || skillNameMap[shift.performedSkillId]}
                </span>
              )}
              {Array.isArray(shift.skillRequirements) && shift.skillRequirements.length > 0 ? (
                <span
                  style={{ fontSize: '0.75rem', color: '#1d4ed8', background: '#dbeafe', padding: '2px 6px', borderRadius: 4 }}
                  title="Yêu cầu kỹ năng của ca làm"
                >
                  {shift.skillRequirements
                    .map((r) => `${r.requirementType === 'PREFERRED' ? 'Ưu tiên' : 'Bắt buộc'}: ${r.skillName || skillNameMap[r.skillId] || 'Kỹ năng'}`)
                    .join(', ')}
                </span>
              ) : (
                Array.isArray(shift.requiredSkills) &&
                shift.requiredSkills.length > 0 && (
                  <span
                    style={{ fontSize: '0.75rem', color: '#1d4ed8', background: '#dbeafe', padding: '2px 6px', borderRadius: 4 }}
                    title="Kỹ năng bắt buộc (matching lọc cứng theo kỹ năng)"
                  >
                    Kỹ năng: {(shift.requiredSkills.map((id) => skillNameMap[id]).filter(Boolean).join(', ') || `${shift.requiredSkills.length} kỹ năng`)}
                  </span>
                )
              )}
            </div>
            <div style={{ fontSize: '0.82rem', color: '#64748b', marginTop: 4 }}>
              {formatTime(shift.startTime, '08:00')} - {formatTime(shift.endTime, '12:00')}
              {hasCustomArrival(shift) && ` • Có mặt từ ${formatTime(shift.arrivalTime, '')}`} •{' '}
              <strong>
                {shift.confirmedWorkers || 0}/{shift.requiredWorkers || 0}
              </strong>{' '}
              Nhân sự •{' '}
              <strong style={{ color: '#059669' }}>
                {Number(shift.hourlyWage || 50000).toLocaleString()} đ/h
              </strong>
            </div>
            {shift.status === 'SUSPENDED' && shift.adminNote && (
              <div style={{ fontSize: '0.8rem', color: '#b45309', background: '#fffbeb', border: '1px solid #fde68a', padding: '4px 8px', borderRadius: 4, marginTop: 4 }}>
                Lưu ý từ Ban quản trị: {shift.adminNote}
              </div>
            )}
            {shift.status === 'CANCELLED' && shift.cancellationReason && (
              <div style={{ fontSize: '0.8rem', color: '#dc2626', background: '#fef2f2', border: '1px solid #fecaca', padding: '4px 8px', borderRadius: 4, marginTop: 4 }}>
                Lý do hủy ca: {shift.cancellationReason}
              </div>
            )}
          </div>

          {/* Thao tác trên từng ca */}
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {shift.status !== 'CANCELLED' && onOpenShiftWorkspace && (
              <button
                type="button"
                onClick={() => onOpenShiftWorkspace(shift, event)}
                style={{
                  background: '#059669',
                  color: '#ffffff',
                  border: 'none',
                  borderRadius: 6,
                  padding: '6px 12px',
                  fontSize: '0.8rem',
                  fontWeight: 700,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 5,
                }}
                title="Mở trang quản lý riêng của ca làm việc này"
              >
                Quản lý ca
              </button>
            )}

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
                title="Mở bảng điểm danh & mã QR trực tiếp ngay bên dưới"
              >
                <span>{isPanelOpen ? 'Đóng Điểm Danh & QR' : 'Điểm Danh & Mã QR'}</span>
              </button>
            )}

            {(() => {
              const summary = getShiftStaffingSummary(shift, event);
              if (summary.isDraft || summary.isCancelled || summary.isCompleted) return null;
              if (summary.isEmergency) {
                return (
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '0.35rem',
                      padding: '0.4rem 0.75rem',
                      borderRadius: 6,
                      fontSize: '0.78rem',
                      fontWeight: 700,
                      backgroundColor: '#fee2e2',
                      color: '#b91c1c',
                      border: '1px solid #fecdd3',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: '#dc2626' }} />
                    Đang điều phối khẩn cấp
                  </span>
                );
              }
              if (summary.nearStartUnderstaffed && !summary.isInProgress && !summary.isInviteMode) {
                return (
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '0.35rem',
                      padding: '0.4rem 0.75rem',
                      borderRadius: 6,
                      fontSize: '0.78rem',
                      fontWeight: 700,
                      backgroundColor: '#fff1f2',
                      color: '#be123c',
                      border: '1px solid #fecdd3',
                      whiteSpace: 'nowrap',
                    }}
                    title="Hệ thống tự động đề xuất mở rộng tìm kiếm khi cận giờ"
                  >
                    <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: '#e11d48' }} />
                    Cận giờ · Tự động đề xuất
                  </span>
                );
              }
              return null;
            })()}

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
                onClick={() => {
                  window.dispatchEvent(
                    new CustomEvent('cani_open_direct_chat', {
                      detail: {
                        shiftId: shift.shiftId,
                      },
                    })
                  );
                }}
                style={{
                  background: '#eff6ff',
                  color: '#2563eb',
                  border: '1px solid #bfdbfe',
                  borderRadius: 6,
                  padding: '6px 10px',
                  fontSize: '0.8rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                }}
                title="Mở khung trao đổi tin nhắn trực tiếp với nhân sự trong ca"
              >
                Chat Ca
              </button>
            )}

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
            skillTaxonomy={skillTaxonomy}
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
