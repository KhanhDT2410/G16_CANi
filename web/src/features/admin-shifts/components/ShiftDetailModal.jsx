import React, { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { SHIFT_STATUS_CONFIG, PAYMENT_STATUS_CONFIG } from '../types/adminShift.constants';
import ShiftWorkerTable from './ShiftWorkerTable';
import './ShiftDetailModal.css';

/**
 * Modal Xem Chi Tiết Ca Làm Việc (Căn giữa tuyệt đối qua createPortal).
 * Đầy đủ thông tin đối soát tiến độ Worker & trách nhiệm EO.
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

  const parseTemplateChecklist = (rawChecklist) => {
    if (!rawChecklist) return [];
    try {
      const list = typeof rawChecklist === 'string' ? JSON.parse(rawChecklist) : rawChecklist;
      if (Array.isArray(list)) {
        return list.map((item, idx) => {
          if (typeof item === 'string') return { id: `chk_${idx + 1}`, title: item };
          return { id: item.id || `chk_${idx + 1}`, title: item.title || item.name || `Nhiệm vụ ${idx + 1}` };
        });
      }
    } catch (e) {
      console.warn('Lỗi phân tích checklist template:', e);
    }
    return [];
  };

  const templateChecklist = shift ? parseTemplateChecklist(shift.checklist) : [];
  const statusCfg = shift ? (SHIFT_STATUS_CONFIG[shift.status] || {
    label: shift.status || 'Chưa rõ', bg: '#f1f5f9', color: '#475569',
  }) : null;

  const paymentCfg = shift?.paymentStatus ? (PAYMENT_STATUS_CONFIG[shift.paymentStatus] || {
    label: shift.paymentStatus, bg: '#f1f5f9', color: '#475569',
  }) : null;

  const modalContent = (
    <div className="shift-modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="shift-modal-box" onClick={(e) => e.stopPropagation()}>
        <div className="shift-modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
            <h3>Ca Làm: {shift?.shiftCode || '—'}</h3>
            {statusCfg && (
              <span className="shift-status-pill" style={{ background: statusCfg.bg, color: statusCfg.color }}>
                {statusCfg.label}
              </span>
            )}
            {paymentCfg && (
              <span className="shift-payment-pill" style={{ background: paymentCfg.bg, color: paymentCfg.color }}>
                💳 {paymentCfg.label}
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
              <div className="shift-info-grid">
                <div>
                  <div className="grid-label">Sự Kiện & Vị Trí</div>
                  <div className="grid-main-val">{shift.eventName}</div>
                  <div className="grid-sub-val" style={{ color: '#2563eb' }}>Vị trí: {shift.jobType}</div>
                </div>

                <div>
                  <div className="grid-label">Nhà Tổ Chức (EO)</div>
                  <div className="grid-main-val">{shift.eoName}</div>
                  <div className="grid-sub-val">SĐT: {shift.eoPhone} • {shift.eoEmail}</div>
                  {shift.companyName && <div className="grid-company-val">{shift.companyName}</div>}
                </div>

                <div>
                  <div className="grid-label">Thời Gian & Địa Điểm</div>
                  <div className="grid-main-val" style={{ fontSize: '0.85rem' }}>
                    {formatDateTime(shift.startTime)} — {formatDateTime(shift.endTime)}
                  </div>
                  <div className="grid-sub-val">{shift.venueAddress || '—'}</div>
                </div>

                <div>
                  <div className="grid-label">Nhân Sự & Thù Lao</div>
                  <div className="grid-main-val" style={{ color: '#059669' }}>
                    {Number(shift.hourlyWage || 0).toLocaleString()} đ / giờ
                  </div>
                  <div className="grid-sub-val">
                    Tuyển: <strong>{shift.confirmedWorkers || 0}</strong> / {shift.requiredWorkers || 0} người
                  </div>
                  {shift.dressCode && <div className="grid-dress-val">Đồng phục: {shift.dressCode}</div>}
                </div>
              </div>

              {/* Mô tả & Đầu việc EO đã giao */}
              <div className="shift-task-assignment-section">
                <div className="section-title-sm">Nhiệm Vụ & Đầu Việc EO Đã Giao:</div>
                {shift.description && (
                  <div className="shift-description-box">{shift.description}</div>
                )}
                {templateChecklist.length > 0 ? (
                  <div className="shift-checklist-template-box">
                    <div className="checklist-subhead">Checklist đầu việc chuẩn ({templateChecklist.length} mục):</div>
                    <div className="checklist-chips">
                      {templateChecklist.map((task, idx) => (
                        <div key={task.id} className="checklist-chip-item">
                          <span className="chip-index">{idx + 1}.</span>
                          <span className="chip-title">{task.title}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                ) : (
                  <div className="text-muted-box">Ca làm này không thiết lập danh sách checklist nhiệm vụ.</div>
                )}
              </div>

              {/* Bảng nhân sự & Tiến độ làm việc */}
              <div className="shift-workers-section">
                <div className="section-title-md">
                  <span>Tiến Độ Nhân Viên Nhận Ca ({shift.workers?.length || 0})</span>
                  <span className="sub-helper">Bấm vào thanh tiến độ để kiểm tra chi tiết từng đầu việc</span>
                </div>
                <ShiftWorkerTable workers={shift.workers} templateChecklist={templateChecklist} />
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
