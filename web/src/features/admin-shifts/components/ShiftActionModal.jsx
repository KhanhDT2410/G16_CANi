import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';

/**
 * Modal Xác Nhận Hành Động Can Thiệp Ca Làm Việc Của Quản Trị Viên.
 * Căn giữa tuyệt đối trên toàn màn hình qua createPortal & CSS fixed flex.
 * Hoàn toàn không sử dụng icon/emoji theo quy chuẩn dự án.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const ShiftActionModal = ({
  isOpen,
  actionType, // 'CANCEL' | 'SUSPEND' | 'WARN'
  shiftCode,
  loading,
  onConfirm,
  onClose,
}) => {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (isOpen) {
      setReason('');
      setError('');
    }
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && !loading) onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, loading, onClose]);

  if (!isOpen || typeof document === 'undefined') return null;

  const getActionConfig = () => {
    switch (actionType) {
      case 'CANCEL':
        return {
          title: 'Hủy Ca Cưỡng Chế',
          desc: `Bạn đang thực hiện hủy cưỡng chế ca [${shiftCode || '—'}]. Tất cả nhân viên đã nhận ca sẽ được giải phóng lịch và Nhà tổ chức sẽ nhận thông báo hủy.`,
          placeholder: 'Nhập lý do hủy cưỡng chế (bắt buộc, tối thiểu 5 ký tự)...',
          confirmLabel: 'Xác Nhận Hủy Ca',
          btnClass: 'ticket-btn ticket-btn-danger',
        };
      case 'SUSPEND':
        return {
          title: 'Tạm Đình Chỉ Ca Làm Việc',
          desc: `Bạn đang tạm đình chỉ ca [${shiftCode || '—'}]. Ca làm việc sẽ bị tạm dừng tuyển dụng và điều phối để rà soát vi phạm.`,
          placeholder: 'Nhập lý do tạm đình chỉ (bắt buộc, tối thiểu 5 ký tự)...',
          confirmLabel: 'Xác Nhận Tạm Đình Chỉ',
          btnClass: 'ticket-btn ticket-btn-warning',
        };
      case 'WARN':
      default:
        return {
          title: 'Gửi Cảnh Báo Tới Nhà Tổ Chức',
          desc: `Gửi thông báo cảnh báo/nhắc nhở trực tiếp tới Nhà tổ chức của ca [${shiftCode || '—'}].`,
          placeholder: 'Nhập nội dung cảnh báo hoặc yêu cầu chấn chỉnh (bắt buộc, tối thiểu 5 ký tự)...',
          confirmLabel: 'Gửi Cảnh Báo',
          btnClass: 'ticket-btn ticket-btn-primary',
        };
    }
  };

  const config = getActionConfig();

  const handleSubmit = (e) => {
    e.preventDefault();
    const trimmed = reason.trim();
    if (!trimmed || trimmed.length < 5) {
      setError('Vui lòng nhập lý do tối thiểu 5 ký tự.');
      return;
    }
    setError('');
    onConfirm(trimmed);
  };

  const modalElement = (
    <div
      className="shift-action-modal-overlay"
      onClick={!loading ? onClose : undefined}
      role="dialog"
      aria-modal="true"
    >
      <div className="shift-action-modal-box" onClick={(e) => e.stopPropagation()}>
        <div className="shift-action-modal-header">
          <h4>{config.title}</h4>
          <button
            type="button"
            className="shift-modal-close-btn"
            onClick={onClose}
            disabled={loading}
            aria-label="Đóng cửa sổ"
          >
            &times;
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="shift-action-modal-body">
            <p className="shift-action-desc">{config.desc}</p>

            <div className="shift-action-field">
              <label htmlFor="shift-action-reason" className="shift-action-label">
                Lý Do / Nội Dung Can Thiệp:
              </label>
              <textarea
                id="shift-action-reason"
                className={`shift-action-textarea ${error ? 'has-error' : ''}`}
                rows={4}
                value={reason}
                onChange={(e) => {
                  setReason(e.target.value);
                  if (error) setError('');
                }}
                placeholder={config.placeholder}
                disabled={loading}
                autoFocus
              />
              {error && <div className="shift-action-error">{error}</div>}
            </div>
          </div>

          <div className="shift-action-modal-footer">
            <button
              type="button"
              className="ticket-btn ticket-btn-secondary"
              onClick={onClose}
              disabled={loading}
            >
              Hủy Bỏ
            </button>
            <button
              type="submit"
              className={config.btnClass}
              disabled={loading}
            >
              {loading ? 'Đang Xử Lý...' : config.confirmLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  );

  return createPortal(modalElement, document.body);
};

export default ShiftActionModal;
