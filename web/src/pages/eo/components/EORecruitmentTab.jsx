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

  // Tính toán góc và chu vi Donut SVG (bán kính r = 38, chu vi C ≈ 238.76)
  const radius = 38;
  const circumference = 2 * Math.PI * radius;
  const totalBase = totalPositions || 1;
  const confirmedLen = (totalConfirmed / totalBase) * circumference;
  const missingLen = (totalMissing / totalBase) * circumference;

  return (
    <div className="eo-recruitment-container animate-fade-in-up" style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      {/* 1. HEADER & RECRUITMENT FULFILLMENT OVERVIEW (Biểu Đồ Tròn Donut Tuyển Dụng) */}
      <div className="recruitment-overview-grid">
        {/* Card 1: Tổng ca đang tuyển */}
        <div className="eo-widget-card recruitment-shifts-summary-card">
          <div className="recruitment-card-top">
            <div className="recruitment-icon-box blue">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                <line x1="16" y1="2" x2="16" y2="6"></line>
                <line x1="8" y1="2" x2="8" y2="6"></line>
                <line x1="3" y1="10" x2="21" y2="10"></line>
              </svg>
            </div>
            <div className="recruitment-meta-group">
              <span className="recruitment-meta-title">Tổng ca đang tuyển</span>
              <div className="recruitment-data-row">
                <span className="recruitment-big-number">{allShifts.length}</span>
                <span className="recruitment-badge-sub">ca làm việc</span>
              </div>
            </div>
          </div>
          <div className="recruitment-bottom-sub">
            <span className="recruitment-dot green" />
            <span>Phân bổ trên <strong>{events.length}</strong> sự kiện</span>
          </div>
        </div>

        {/* Card 2: Biểu đồ Donut Tiến độ Lấp đầy Nhân sự */}
        <div className="eo-widget-card recruitment-donut-card">
          <div className="recruitment-donut-header">
            <div className="recruitment-donut-title-group">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                <circle cx="9" cy="7" r="4"></circle>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
              </svg>
              <h4 className="recruitment-donut-title">Tiến độ lấp đầy nhân sự</h4>
            </div>
            {totalMissing > 0 ? (
              <span className="radar-status-pill warning">
                <span className="pulse-dot warning" />
                Cần bù {totalMissing} nhân sự
              </span>
            ) : (
              <span className="radar-status-pill success">
                <span className="pulse-dot success" />
                Đã đạt 100% chỉ tiêu
              </span>
            )}
          </div>

          <div className="recruitment-donut-body">
            {/* Vòng tròn Donut SVG */}
            <div className="recruitment-donut-chart-box">
              <svg viewBox="0 0 100 100" className="recruitment-donut-svg">
                {/* Vòng nền xám */}
                <circle
                  cx="50"
                  cy="50"
                  r={radius}
                  fill="none"
                  stroke="#f1f5f9"
                  strokeWidth="11"
                />

                {/* Cung tròn nhân sự còn thiếu (Đỏ cam #f43f5e) */}
                {missingLen > 0 && (
                  <circle
                    cx="50"
                    cy="50"
                    r={radius}
                    fill="none"
                    stroke="#f43f5e"
                    strokeWidth="11"
                    strokeDasharray={`${missingLen} ${circumference}`}
                    strokeDashoffset={-confirmedLen}
                    transform="rotate(-90 50 50)"
                    strokeLinecap={totalConfirmed === 0 ? 'butt' : 'round'}
                  />
                )}

                {/* Cung tròn nhân sự đã khớp (Xanh ngọc #10b981) */}
                {confirmedLen > 0 && (
                  <circle
                    cx="50"
                    cy="50"
                    r={radius}
                    fill="none"
                    stroke="#10b981"
                    strokeWidth="11"
                    strokeDasharray={`${confirmedLen} ${circumference}`}
                    strokeDashoffset="0"
                    transform="rotate(-90 50 50)"
                    strokeLinecap="round"
                  />
                )}

                {/* Text trung tâm */}
                <text x="50" y="47" textAnchor="middle" dominantBaseline="middle" className="recruitment-donut-num">
                  {overallFillRate}%
                </text>
                <text x="50" y="63" textAnchor="middle" dominantBaseline="middle" className="recruitment-donut-sub">
                  Đã chốt
                </text>
              </svg>
            </div>

            {/* Chi tiết số liệu (Metrics Breakdown) */}
            <div className="recruitment-breakdown-grid">
              <div className="recruitment-breakdown-item target">
                <span className="breakdown-label">
                  <span className="breakdown-dot blue" />
                  Chỉ tiêu ca
                </span>
                <span className="breakdown-val">{totalPositions}</span>
                <span className="breakdown-desc">Nhân sự cần tuyển</span>
              </div>

              <div className="recruitment-breakdown-item confirmed">
                <span className="breakdown-label">
                  <span className="breakdown-dot emerald" />
                  Đã khớp (chốt)
                </span>
                <span className="breakdown-val text-emerald">{totalConfirmed}</span>
                <span className="breakdown-desc">Tỉ lệ: {overallFillRate}%</span>
              </div>

              <div className="recruitment-breakdown-item missing">
                <span className="breakdown-label">
                  <span className="breakdown-dot rose" />
                  Còn thiếu
                </span>
                <span className="breakdown-val text-rose">{totalMissing}</span>
                <span className="breakdown-desc">{totalMissing > 0 ? 'Cần radar matching' : 'Đã đủ ca'}</span>
              </div>
            </div>
          </div>
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

            // Kiểm tra điều kiện Cứu trợ khẩn cấp (Emergency Broadcast):
            // 1. Phải còn thiếu người (!isFull)
            // 2. Ca chưa kết thúc
            // 3. Sát giờ diễn ra ca: trong vòng 4 tiếng trước ca HOẶC ca đang diễn ra mà bị thiếu người (bùng ca/no-show)
            const now = new Date();
            const shiftStart = shift.startTime ? new Date(shift.startTime) : null;
            const shiftEnd = shift.endTime ? new Date(shift.endTime) : null;
            const isNotEnded = shiftEnd ? now < shiftEnd : true;
            const hoursUntilStart = shiftStart ? (shiftStart.getTime() - now.getTime()) / (1000 * 60 * 60) : 999;
            const isNearShift = isNotEnded && (hoursUntilStart <= 4 || (shiftStart && now >= shiftStart));
            const canTriggerEmergency = !isFull && isNearShift;

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

                {/* Các thao tác tuyển dụng & vận hành ca */}
                <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center' }}>
                  {!isFull && (
                    <>
                      {/* Nút Cứu trợ khẩn cấp: Chỉ mở khi GẦN ĐẾN CA (< 4 giờ) mà THIẾU NGƯỜI */}
                      {canTriggerEmergency && (
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
                            display: 'flex',
                            alignItems: 'center',
                            gap: '4px',
                          }}
                          onClick={() => setEmergencyModal({ isOpen: true, shift })}
                          title="Ca sắp diễn ra mà chưa đủ người! Kích hoạt Emergency Broadcast Pool diện rộng."
                        >
                          <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: '#ffffff', display: 'inline-block' }} />
                          Cứu Trợ Khẩn Cấp
                        </button>
                      )}

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
                    title="Mở bảng theo dõi điểm danh trực tiếp & hiển thị mã QR cho Worker quét"
                  >
                    Điểm Danh & Duyệt
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

