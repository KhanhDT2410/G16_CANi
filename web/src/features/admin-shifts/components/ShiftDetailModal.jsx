import React, { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { SHIFT_STATUS_CONFIG, WORKER_ASSIGNMENT_STATUS_CONFIG } from '../types/adminShift.constants';
import './ShiftDetailModal.css';

/**
 * Modal Xem Chi Tiết Ca Làm Việc (Căn giữa tuyệt đối qua createPortal).
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const ShiftDetailModal = ({ shift, loading, onClose }) => {
  useEffect(() => {
    if (!shift && !loading) return;
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = originalOverflow;
    };
  }, [shift, loading, onClose]);

  if ((!shift && !loading) || typeof document === 'undefined') return null;

  const formatDateTime = (isoStr) => {
    if (!isoStr) return '—';
    try {
      return new Date(isoStr).toLocaleString('vi-VN', {
        hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric',
      });
    } catch {
      return '—';
    }
  };

  const statusCfg = shift ? (SHIFT_STATUS_CONFIG[shift.status] || {
    label: shift.status || 'Chưa rõ',
    bg: '#f1f5f9',
    color: '#475569',
  }) : null;

  const modalContent = (
    <div className="shift-modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="shift-modal-box" onClick={(e) => e.stopPropagation()}>
        <div className="shift-modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <h3>Ca Làm: {shift?.shiftCode || '—'}</h3>
            {statusCfg && (
              <span
                style={{
                  padding: '3px 10px',
                  borderRadius: 12,
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  background: statusCfg.bg,
                  color: statusCfg.color,
                }}
              >
                {statusCfg.label}
              </span>
            )}
          </div>
          <button type="button" className="shift-modal-close-btn" onClick={onClose} aria-label="Đóng cửa sổ">
            &times;
          </button>
        </div>

        <div className="shift-modal-body">
          {loading ? (
            <div className="ticket-empty-state">Đang tải dữ liệu chi tiết ca làm...</div>
          ) : shift ? (
            <>
              {/* Lưới thông tin Sự kiện & EO */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                  gap: '1rem',
                  background: '#f8fafc',
                  padding: '1rem',
                  borderRadius: 8,
                  border: '1px solid #e2e8f0',
                }}
              >
                <div>
                  <div style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600, textTransform: 'uppercase' }}>
                    Sự Kiện & Vị Trí
                  </div>
                  <div style={{ fontWeight: 700, color: '#0f172a', fontSize: '0.95rem', marginTop: 2 }}>
                    {shift.eventName}
                  </div>
                  <div style={{ fontSize: '0.85rem', color: '#2563eb', fontWeight: 600 }}>
                    Vị trí: {shift.jobType}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600, textTransform: 'uppercase' }}>
                    Nhà Tổ Chức (EO)
                  </div>
                  <div style={{ fontWeight: 700, color: '#0f172a', fontSize: '0.92rem', marginTop: 2 }}>
                    {shift.eoName}
                  </div>
                  <div style={{ fontSize: '0.82rem', color: '#475569' }}>
                    SĐT: {shift.eoPhone} • {shift.eoEmail}
                  </div>
                  {shift.companyName && (
                    <div style={{ fontSize: '0.78rem', color: '#64748b' }}>{shift.companyName}</div>
                  )}
                </div>

                <div>
                  <div style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600, textTransform: 'uppercase' }}>
                    Thời Gian & Địa Điểm
                  </div>
                  <div style={{ fontWeight: 600, color: '#1e293b', fontSize: '0.85rem', marginTop: 2 }}>
                    {formatDateTime(shift.startTime)} — {formatDateTime(shift.endTime)}
                  </div>
                  <div style={{ fontSize: '0.82rem', color: '#475569', marginTop: 2 }}>
                    {shift.venueAddress || '—'}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600, textTransform: 'uppercase' }}>
                    Nhân Sự & Thù Lao
                  </div>
                  <div style={{ fontWeight: 700, color: '#059669', fontSize: '0.95rem', marginTop: 2 }}>
                    {Number(shift.hourlyWage || 0).toLocaleString()} đ / giờ
                  </div>
                  <div style={{ fontSize: '0.82rem', color: '#475569' }}>
                    Tuyển: <strong>{shift.confirmedWorkers || 0}</strong> / {shift.requiredWorkers || 0} người
                  </div>
                  {shift.dressCode && (
                    <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: 2 }}>
                      Đồng phục: {shift.dressCode}
                    </div>
                  )}
                </div>
              </div>

              {shift.description && (
                <div>
                  <div style={{ fontSize: '0.8rem', fontWeight: 700, color: '#334155', marginBottom: 4 }}>
                    Mô Tả Công Việc:
                  </div>
                  <div style={{ fontSize: '0.85rem', color: '#475569', background: '#ffffff', padding: '0.75rem', borderRadius: 6, border: '1px solid #e2e8f0', whiteSpace: 'pre-wrap' }}>
                    {shift.description}
                  </div>
                </div>
              )}

              {/* Danh sách Worker */}
              <div>
                <div style={{ fontSize: '0.9rem', fontWeight: 700, color: '#0f172a', marginBottom: '0.5rem' }}>
                  Danh Sách Nhân Viên Đã Nhận Ca ({shift.workers?.length || 0})
                </div>

                {(!shift.workers || shift.workers.length === 0) ? (
                  <div style={{ textAlign: 'center', padding: '1.25rem', color: '#94a3b8', background: '#f8fafc', borderRadius: 8, border: '1px solid #e2e8f0' }}>
                    Chưa có nhân viên nào được duyệt vào ca này.
                  </div>
                ) : (
                  <div style={{ border: '1px solid #e2e8f0', borderRadius: 8, overflow: 'hidden' }}>
                    <table className="tickets-table" style={{ margin: 0 }}>
                      <thead>
                        <tr>
                          <th>Nhân Viên</th>
                          <th>Số Điện Thoại</th>
                          <th>Trạng Thái Nhận Việc</th>
                          <th>Giờ Điểm Danh</th>
                        </tr>
                      </thead>
                      <tbody>
                        {shift.workers.map((w) => {
                          const wStatus = WORKER_ASSIGNMENT_STATUS_CONFIG[w.status] || {
                            label: w.status, color: '#475569', bg: '#f1f5f9',
                          };
                          return (
                            <tr key={w.assignmentId}>
                              <td>
                                <div style={{ fontWeight: 600, color: '#1e293b' }}>
                                  {w.workerName}
                                  {w.isGroupLeader && (
                                    <span style={{ marginLeft: 6, fontSize: '0.7rem', background: '#fef3c7', color: '#d97706', padding: '1px 6px', borderRadius: 4, fontWeight: 700 }}>
                                      Trưởng nhóm
                                    </span>
                                  )}
                                </div>
                                <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>{w.workerEmail}</div>
                              </td>
                              <td>{w.workerPhone || '—'}</td>
                              <td>
                                <span
                                  style={{
                                    padding: '2px 8px',
                                    borderRadius: 10,
                                    fontSize: '0.72rem',
                                    fontWeight: 600,
                                    background: wStatus.bg,
                                    color: wStatus.color,
                                  }}
                                >
                                  {wStatus.label}
                                </span>
                              </td>
                              <td style={{ fontSize: '0.8rem' }}>
                                <div>Vào: {w.checkinTime ? formatDateTime(w.checkinTime) : 'Chưa điểm danh'}</div>
                                {w.checkoutTime && <div>Ra: {formatDateTime(w.checkoutTime)}</div>}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </>
          ) : null}
        </div>

        <div className="shift-modal-footer">
          <button type="button" className="ticket-btn ticket-btn-secondary" onClick={onClose}>
            Đóng
          </button>
        </div>
      </div>
    </div>
  );

  return createPortal(modalContent, document.body);
};

export default ShiftDetailModal;
