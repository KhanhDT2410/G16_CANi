import React, { useState, useMemo } from 'react';
import { showToast } from '../../../components/common/Toast';
import { eoService } from '../../../services/eoService';
import EmergencyBroadcastModal from './EmergencyBroadcastModal';

/**
 * EORecruitmentTab
 * Màn hình Quản lý Tuyển dụng Nhân sự & Điều phối Matching Radar (Clean Enterprise UI)
 */
export default function EORecruitmentTab({
  events = [],
  onOpenAddShift,
  onOpenQr,
  onOpenInvite,
  onOpenAttendance,
  onOpenCreateEvent,
  onReloadEvents,
}) {
  const [selectedEventFilter, setSelectedEventFilter] = useState('ALL');
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [dispatchingShiftId, setDispatchingShiftId] = useState(null);
  const [emergencyModal, setEmergencyModal] = useState({ isOpen: false, shift: null });
  const [activeEmergencies, setActiveEmergencies] = useState({});

  const handleEmergencySuccess = (shiftId, data) => {
    setActiveEmergencies((prev) => ({
      ...prev,
      [shiftId]: data,
    }));
    onReloadEvents?.();
  };

  // Gom toàn bộ ca làm việc (Shifts) từ các sự kiện
  const allShifts = useMemo(() => {
    const list = [];
    events.forEach((evt) => {
      if (Array.isArray(evt.shifts)) {
        evt.shifts.forEach((s) => {
          list.push({
            ...s,
            eventId: evt.eventId,
            eventName: evt.name,
            venueAddress: evt.venueAddress,
            eventStatus: evt.status,
          });
        });
      }
    });
    return list;
  }, [events]);

  // Bộ lọc danh sách ca tuyển dụng
  const filteredShifts = useMemo(() => {
    return allShifts.filter((s) => {
      if (selectedEventFilter !== 'ALL' && s.eventId !== selectedEventFilter) return false;
      if (roleFilter !== 'ALL' && s.jobType !== roleFilter) return false;
      return true;
    });
  }, [allShifts, selectedEventFilter, roleFilter]);

  // Tổng hợp chỉ số tuyển dụng
  const totalPositions = allShifts.reduce((acc, s) => acc + (s.requiredWorkers || 0), 0);
  const totalConfirmed = allShifts.reduce((acc, s) => acc + (s.confirmedWorkers || 0), 0);
  const totalMissing = Math.max(0, totalPositions - totalConfirmed);
  const overallFillRate = totalPositions > 0 ? Math.round((totalConfirmed / totalPositions) * 100) : 0;

  // Kích hoạt lại Matching Radar cho ca cụ thể
  const handleTriggerDispatch = async (shift) => {
    setDispatchingShiftId(shift.shiftId);
    try {
      await eoService.dispatchForShift(shift.shiftId);
      showToast(`Đã kích hoạt quét Matching Radar AI cho ca "${shift.jobType}"!`, 'success');
      onReloadEvents?.();
    } catch (err) {
      showToast('Lỗi khi kích hoạt Matching: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setDispatchingShiftId(null);
    }
  };

  const formatCurrency = (val) => new Intl.NumberFormat('vi-VN').format(val || 0);

  const formatTime = (timeStr) => {
    if (!timeStr) return '--:--';
    try {
      return new Date(timeStr).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return timeStr;
    }
  };

  return (
    <div className="eo-recruitment-container animate-fade-in-up" style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      {/* 1. HEADER & KPI CARDS */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1rem' }}>
        <div className="eo-widget-card" style={{ padding: '1.25rem' }}>
          <span style={{ fontSize: '0.85rem', color: '#64748b', fontWeight: 600 }}>Tổng Ca Đang Tuyển</span>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#0f172a', margin: '0.35rem 0' }}>
            {allShifts.length}
          </div>
          <span style={{ fontSize: '0.75rem', color: '#059669', fontWeight: 600 }}>Trên {events.length} sự kiện</span>
        </div>

        <div className="eo-widget-card" style={{ padding: '1.25rem' }}>
          <span style={{ fontSize: '0.85rem', color: '#64748b', fontWeight: 600 }}>Chỉ Tiêu Cần Tuyển</span>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#2563eb', margin: '0.35rem 0' }}>
            {totalPositions}
          </div>
          <span style={{ fontSize: '0.75rem', color: '#64748b' }}>Tổng nhân sự theo ma trận ca</span>
        </div>

        <div className="eo-widget-card" style={{ padding: '1.25rem' }}>
          <span style={{ fontSize: '0.85rem', color: '#64748b', fontWeight: 600 }}>Nhân Sự Đã Khớp (Chốt)</span>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#059669', margin: '0.35rem 0' }}>
            {totalConfirmed}
          </div>
          <span style={{ fontSize: '0.75rem', color: '#059669', fontWeight: 600 }}>Tỉ lệ lấp đầy: {overallFillRate}%</span>
        </div>

        <div className="eo-widget-card" style={{ padding: '1.25rem' }}>
          <span style={{ fontSize: '0.85rem', color: '#64748b', fontWeight: 600 }}>Nhân Sự Còn Thiếu</span>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: totalMissing > 0 ? '#dc2626' : '#059669', margin: '0.35rem 0' }}>
            {totalMissing}
          </div>
          <span style={{ fontSize: '0.75rem', color: totalMissing > 0 ? '#dc2626' : '#059669' }}>
            {totalMissing > 0 ? 'Cần kích hoạt Matching Radar' : 'Đã đủ toàn bộ nhân sự'}
          </span>
        </div>
      </div>

      {/* 2. THANH CÔNG CỤ LỌC & THAO TÁC */}
      <div className="eo-widget-card" style={{ padding: '1rem 1.25rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'center' }}>
            <span style={{ fontSize: '0.875rem', fontWeight: 700, color: '#1e293b' }}>Lọc theo:</span>
            
            <select
              className="filter-select"
              value={selectedEventFilter}
              onChange={(e) => setSelectedEventFilter(e.target.value)}
              style={{ minWidth: 200 }}
            >
              <option value="ALL">Tất cả sự kiện ({events.length})</option>
              {events.map((evt) => (
                <option key={evt.eventId} value={evt.eventId}>
                  {evt.name} ({evt.shifts?.length || evt.totalShifts || 0} ca)
                </option>
              ))}
            </select>

            <select
              className="filter-select"
              value={roleFilter}
              onChange={(e) => setRoleFilter(e.target.value)}
              style={{ minWidth: 160 }}
            >
              <option value="ALL">Tất cả vị trí</option>
              <option value="Lễ tân / Check-in">Lễ tân / Check-in</option>
              <option value="An ninh / Điều phối">An ninh / Điều phối</option>
              <option value="Hậu cần / Setup">Hậu cần / Setup</option>
              <option value="Phục vụ / Tea-break">Phục vụ / Tea-break</option>
              <option value="PG / PB Sự kiện">PG / PB Sự kiện</option>
              <option value="MC / Hoạt náo">MC / Hoạt náo</option>
            </select>
          </div>

          <div style={{ display: 'flex', gap: '0.5rem' }}>
            {events.length > 0 && (
              <button
                type="button"
                className="eo-btn-primary"
                style={{ fontSize: '0.85rem', padding: '0.5rem 1rem' }}
                onClick={() => onOpenAddShift?.(events[0].eventId)}
              >
                + Thêm Ca Tuyển Dụng
              </button>
            )}
          </div>
        </div>
      </div>

      {/* 3. DANH SÁCH CÁC CA TUYỂN DỤNG & RADAR MATCHING */}
      {filteredShifts.length === 0 ? (
        <div className="eo-widget-card" style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>
          <h4 style={{ margin: '0 0 0.5rem', color: '#1e293b', fontSize: '1.1rem' }}>Chưa có ca làm việc nào cần tuyển</h4>
          <p style={{ margin: '0 0 1.25rem', fontSize: '0.9rem' }}>
            Tạo thêm ca làm việc cho sự kiện để kích hoạt hệ thống tuyển dụng tự động qua Matching Radar AI.
          </p>
          <button
            type="button"
            className="eo-btn-primary"
            onClick={onOpenCreateEvent}
          >
            + Khởi Tạo Sự Kiện Mới
          </button>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {filteredShifts.map((shift) => {
            const confirmed = shift.confirmedWorkers || 0;
            const required = shift.requiredWorkers || 1;
            const percent = Math.min(100, Math.round((confirmed / required) * 100));
            const isFull = confirmed >= required;

            return (
              <div
                key={shift.shiftId}
                className="eo-widget-card"
                style={{
                  padding: '1.25rem',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  gap: '1.25rem',
                  flexWrap: 'wrap',
                  borderLeft: isFull ? '4px solid #10b981' : '4px solid #3b82f6',
                }}
              >
                <div style={{ flex: 1, minWidth: 260 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.35rem', flexWrap: 'wrap' }}>
                    <h4 style={{ margin: 0, fontSize: '1.05rem', fontWeight: 700, color: '#0f172a' }}>
                      {shift.jobType}
                    </h4>
                    <span
                      style={{
                        padding: '2px 8px',
                        borderRadius: 12,
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        backgroundColor: isFull ? '#dcfce7' : '#eff6ff',
                        color: isFull ? '#15803d' : '#1d4ed8',
                      }}
                    >
                      {isFull ? 'Đã Tuyển Đủ' : `Cần tuyển ${required - confirmed} người nữa`}
                    </span>
                    {activeEmergencies[shift.shiftId] && (
                      <span
                        style={{
                          padding: '2px 8px',
                          borderRadius: 12,
                          fontSize: '0.75rem',
                          fontWeight: 700,
                          backgroundColor: '#fee2e2',
                          color: '#b91c1c',
                          border: '1px solid #fecdd3',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '4px',
                        }}
                      >
                        <span
                          style={{
                            width: 6,
                            height: 6,
                            borderRadius: '50%',
                            backgroundColor: '#dc2626',
                            display: 'inline-block',
                          }}
                        />
                        Cứu Trợ Khẩn Cấp (+{formatCurrency(activeEmergencies[shift.shiftId].allowanceBonus)}đ/h • {activeEmergencies[shift.shiftId].expandedRadiusKm}km)
                      </span>
                    )}
                  </div>

                  <div style={{ fontSize: '0.85rem', color: '#64748b', display: 'flex', flexWrap: 'wrap', gap: '0.75rem', marginTop: '0.4rem' }}>
                    <span>Sự kiện: <strong>{shift.eventName}</strong></span>
                    <span>•</span>
                    <span>Thời gian: {formatTime(shift.startTime)} - {formatTime(shift.endTime)}</span>
                    <span>•</span>
                    <span>Thù lao: <strong style={{ color: '#059669' }}>{formatCurrency(shift.hourlyWage)} đ/giờ</strong></span>
                  </div>

                  {/* Tiến độ tuyển dụng */}
                  <div style={{ marginTop: '0.75rem', maxWidth: 360 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', marginBottom: '0.25rem', fontWeight: 600 }}>
                      <span style={{ color: '#475569' }}>Tiến độ chốt nhân sự: {confirmed}/{required} ứng viên</span>
                      <span style={{ color: isFull ? '#10b981' : '#2563eb' }}>{percent}%</span>
                    </div>
                    <div style={{ height: 6, backgroundColor: '#e2e8f0', borderRadius: 3, overflow: 'hidden' }}>
                      <div
                        style={{
                          width: `${percent}%`,
                          height: '100%',
                          backgroundColor: isFull ? '#10b981' : '#2563eb',
                          borderRadius: 3,
                          transition: 'width 0.3s ease',
                        }}
                      />
                    </div>
                  </div>
                </div>

                {/* Các thao tác tuyển dụng */}
                <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center' }}>
                  {!isFull && (
                    <>
                      <button
                        type="button"
                        style={{
                          background: '#dc2626',
                          color: '#ffffff',
                          border: 'none',
                          borderRadius: 6,
                          padding: '0.45rem 0.85rem',
                          fontSize: '0.8rem',
                          fontWeight: 700,
                          cursor: 'pointer',
                          boxShadow: '0 2px 6px rgba(220, 38, 38, 0.25)',
                        }}
                        onClick={() => setEmergencyModal({ isOpen: true, shift })}
                        title="Kích hoạt Emergency Broadcast Pool diện rộng (Section 11 CANi_Flow_v8.txt)"
                      >
                        Cứu Trợ Khẩn Cấp
                      </button>

                      <button
                        type="button"
                        disabled={dispatchingShiftId === shift.shiftId}
                        className="eo-btn-primary"
                        style={{
                          fontSize: '0.8rem',
                          padding: '0.45rem 0.85rem',
                          backgroundColor: '#2563eb',
                        }}
                        onClick={() => handleTriggerDispatch(shift)}
                        title="Quét lại mạng lưới Worker trong bán kính 15km"
                      >
                        {dispatchingShiftId === shift.shiftId ? 'Đang Quét Radar...' : 'Kích Hoạt Radar AI'}
                      </button>
                    </>
                  )}

                  <button
                    type="button"
                    style={{
                      background: '#7c3aed',
                      color: '#ffffff',
                      border: 'none',
                      borderRadius: 6,
                      padding: '0.45rem 0.85rem',
                      fontSize: '0.8rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                    }}
                    onClick={() => onOpenInvite?.(shift, { eventId: shift.eventId, name: shift.eventName })}
                    title="Lấy link & mã mời trực tiếp (Invite Mode)"
                  >
                    Mã Mời
                  </button>

                  <button
                    type="button"
                    style={{
                      background: '#0d9488',
                      color: '#ffffff',
                      border: 'none',
                      borderRadius: 6,
                      padding: '0.45rem 0.85rem',
                      fontSize: '0.8rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                    }}
                    onClick={() => onOpenAttendance?.(shift, { eventId: shift.eventId, name: shift.eventName })}
                    title="Xem danh sách ứng viên, điểm danh và duyệt công"
                  >
                    Điểm Danh & Duyệt
                  </button>

                  <button
                    type="button"
                    style={{
                      background: '#f1f5f9',
                      color: '#334155',
                      border: '1px solid #cbd5e1',
                      borderRadius: 6,
                      padding: '0.45rem 0.75rem',
                      fontSize: '0.8rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                    }}
                    onClick={() => onOpenQr?.(shift, { eventId: shift.eventId, name: shift.eventName })}
                    title="Hiển thị mã QR Check-in/Check-out"
                  >
                    Mã QR
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Modal Cứu Trợ Ca Khẩn Cấp (Emergency Dispatch Pool - Section 11) */}
      <EmergencyBroadcastModal
        isOpen={emergencyModal.isOpen}
        shift={emergencyModal.shift}
        onClose={() => setEmergencyModal({ isOpen: false, shift: null })}
        onSuccess={handleEmergencySuccess}
      />
    </div>
  );
}

