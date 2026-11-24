import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { resolveShiftStatus, PAYMENT_STATUS_CONFIG } from '../types/adminShift.constants';
import { parseTemplateChecklist } from '../utils/shiftModalHelpers';
import ShiftInfoGrid from './ShiftInfoGrid';
import ShiftWorkerTable from './ShiftWorkerTable';
import ShiftActionModal from './ShiftActionModal';
import './ShiftDetailModal.css';

/**
 * Modal Xem Chi Tiết Ca Làm Việc (Căn giữa tuyệt đối qua createPortal).
 * Hiển thị đầy đủ quyền can thiệp của Quản trị viên (Cảnh báo EO, Tạm đình chỉ, Hủy ca).
 * Không sử dụng icon/emoji theo quy chuẩn dự án.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const ShiftDetailModal = ({
  shift, loading, actionLoading, onClose, onCancelShift, onSuspendShift, onWarnEo,
}) => {
  const [activeAction, setActiveAction] = useState(null);

  useEffect(() => {
    if (!shift && !loading) return;
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && !activeAction) onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    const orig = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = orig;
    };
  }, [shift, loading, onClose, activeAction]);

  if ((!shift && !loading) || typeof document === 'undefined') return null;

  const templateChecklist = shift ? parseTemplateChecklist(shift.checklist) : [];
  const statusCfg = shift ? resolveShiftStatus(shift) : null;
  const paymentCfg = shift?.paymentStatus ? (PAYMENT_STATUS_CONFIG[shift.paymentStatus] || {
    label: shift.paymentStatus, bg: '#f1f5f9', color: '#475569',
  }) : null;

  const isFinished = statusCfg?.label === 'Hoàn Thành' || shift?.status === 'COMPLETED';
  const canSuspend = shift && !isFinished && shift.status !== 'CANCELLED' && shift.status !== 'SUSPENDED';
  const canCancel = shift && !isFinished && shift.status !== 'CANCELLED';

  const handleActionConfirm = async (reason) => {
    if (!shift) return;
    let ok = false;
    if (activeAction === 'CANCEL' && onCancelShift) ok = await onCancelShift(shift.shiftId, reason);
    else if (activeAction === 'SUSPEND' && onSuspendShift) ok = await onSuspendShift(shift.shiftId, reason);
    else if (activeAction === 'WARN' && onWarnEo) ok = await onWarnEo(shift.shiftId, reason);
    if (ok) setActiveAction(null);
  };

  const modalContent = (
    <div className="shift-modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="shift-modal-box shift-modal-box-wide" onClick={(e) => e.stopPropagation()}>
        <div className="shift-modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
            <h3>Ca Làm: {shift?.shiftCode || '—'}</h3>
            {statusCfg && (
              <span className="shift-status-badge" style={{ background: statusCfg.bg, color: statusCfg.color, borderColor: statusCfg.border || '#cbd5e1' }}>
                {statusCfg.label}
              </span>
            )}
            {paymentCfg && (
              <span className="shift-payment-badge" style={{ background: paymentCfg.bg, color: paymentCfg.color, borderColor: paymentCfg.border || '#cbd5e1' }}>
                {paymentCfg.label}
              </span>
            )}
          </div>
          <button type="button" className="shift-modal-close-btn" onClick={onClose} aria-label="Đóng cửa sổ">✕</button>
        </div>

        <div className="shift-modal-body">
          {loading ? (
            <div className="ticket-empty-state">Đang tải dữ liệu chi tiết ca làm...</div>
          ) : shift ? (
            <>
              {shift.status === 'SUSPENDED' && shift.adminNote && (
                <div className="shift-intervention-box box-suspended">
                  <div className="shift-intervention-title">Ca làm việc đang bị Tạm đình chỉ:</div>
                  <div className="shift-intervention-reason">{shift.adminNote}</div>
                </div>
              )}
              {shift.status === 'CANCELLED' && shift.cancellationReason && (
                <div className="shift-intervention-box box-cancelled">
                  <div className="shift-intervention-title">Lý do hủy ca từ Ban quản trị:</div>
                  <div className="shift-intervention-reason">{shift.cancellationReason}</div>
                </div>
              )}

              <ShiftInfoGrid shift={shift} />

              <div className="shift-task-assignment-section">
                <div className="section-title-sm">Nhiệm Vụ & Đầu Việc EO Đã Giao:</div>
                {shift.description && <div className="shift-description-box">{shift.description}</div>}
                {templateChecklist.length > 0 ? (
                  <div className="shift-checklist-template-box">
                    <div className="checklist-subhead">Checklist đầu việc chuẩn ({templateChecklist.length} mục):</div>
                    <div className="checklist-chips">
                      {templateChecklist.map((task, idx) => (
                        <div key={task.id} className="checklist-chip-item">
                          <span className="chip-index">{idx + 1}.</span>
                          <span className="chip-title">{task.title}</span>
                          {task.isRequiredEvidence && (
                            <span style={{ fontSize: '0.7rem', color: '#b45309', background: '#fef3c7', padding: '1px 5px', borderRadius: 3, fontWeight: 600 }}>
                              Cần ảnh
                            </span>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                ) : (
                  <div className="text-muted-box">Ca làm này không thiết lập danh sách checklist nhiệm vụ.</div>
                )}
              </div>

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
          <button
            type="button"
            className="ticket-btn ticket-btn-secondary"
            onClick={() => setActiveAction('WARN')}
            disabled={actionLoading}
            title="Gửi cảnh báo hoặc yêu cầu chấn chỉnh trực tiếp tới Nhà tổ chức"
          >
            Cảnh Báo EO
          </button>
          <button
            type="button"
            className="ticket-btn ticket-btn-warning"
            onClick={() => canSuspend && setActiveAction('SUSPEND')}
            disabled={actionLoading || !canSuspend}
            style={{ opacity: canSuspend ? 1 : 0.45, cursor: canSuspend ? 'pointer' : 'not-allowed' }}
            title={isFinished ? 'Ca làm việc đã kết thúc hoàn thành, không thể đình chỉ' : shift?.status === 'CANCELLED' ? 'Ca làm việc đã bị hủy trước đó, không thể đình chỉ' : 'Tạm đình chỉ ca làm việc này'}
          >
            Tạm Đình Chỉ Ca
          </button>
          <button
            type="button"
            className="ticket-btn ticket-btn-danger"
            onClick={() => canCancel && setActiveAction('CANCEL')}
            disabled={actionLoading || !canCancel}
            style={{ opacity: canCancel ? 1 : 0.45, cursor: canCancel ? 'pointer' : 'not-allowed' }}
            title={isFinished ? 'Ca làm việc đã kết thúc hoàn thành, không thể hủy' : shift?.status === 'CANCELLED' ? 'Ca làm việc đã bị hủy trước đó' : 'Hủy ca làm việc cưỡng chế'}
          >
            Hủy Ca Cưỡng Chế
          </button>
          <button type="button" className="ticket-btn ticket-btn-secondary" onClick={onClose} disabled={actionLoading}>
            Đóng
          </button>
        </div>
      </div>

      <ShiftActionModal
        isOpen={Boolean(activeAction)}
        actionType={activeAction}
        shiftCode={shift?.shiftCode}
        loading={actionLoading}
        onClose={() => setActiveAction(null)}
        onConfirm={handleActionConfirm}
      />
    </div>
  );

  return createPortal(modalContent, document.body);
};

export default ShiftDetailModal;
