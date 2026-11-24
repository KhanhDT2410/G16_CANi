import { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';

const UserStatusModal = ({ user, submitting, onClose, onConfirm }) => {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (!user) return;
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
  }, [user, onClose]);

  if (!user) return null;

  const isCurrentlyLocked = user.status === 'LOCKED';
  const targetStatus = isCurrentlyLocked ? 'ACTIVE' : 'LOCKED';
  const actionTitle = isCurrentlyLocked ? 'Mở Khóa Tài Khoản' : 'Khóa Tài Khoản';
  const confirmBtnColor = isCurrentlyLocked ? '#16a34a' : '#dc2626';

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!reason.trim()) {
      setError('Vui lòng nhập lý do thay đổi trạng thái.');
      return;
    }
    setError('');
    onConfirm(targetStatus, reason.trim());
  };

  const modalNode = (
    <div className="user-modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div
        className="user-modal-box"
        style={{ maxWidth: '480px' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="user-modal-header">
          <h3 style={{ color: isCurrentlyLocked ? '#16a34a' : '#dc2626' }}>
            {actionTitle}
          </h3>
          <button type="button" className="btn-modal-close" onClick={onClose} aria-label="Đóng cửa sổ">
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
          <div className="user-modal-body">
            <p style={{ fontSize: '0.9rem', color: '#374151', margin: '0 0 1rem 0' }}>
              Bạn đang thực hiện {actionTitle.toLowerCase()} cho người dùng{' '}
              <strong>{user.fullName}</strong> ({user.email}).
            </p>

            <div style={{ marginBottom: '0.5rem' }}>
              <label
                htmlFor="status-reason-input"
                style={{
                  display: 'block',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  color: '#374151',
                  marginBottom: '0.35rem',
                }}
              >
                Lý do thay đổi <span style={{ color: '#dc2626' }}>*</span>
              </label>
              <textarea
                id="status-reason-input"
                rows={3}
                className="user-filter-input"
                style={{ width: '100%', resize: 'vertical' }}
                placeholder={
                  isCurrentlyLocked
                    ? 'Ví dụ: Đã giải trình thỏa đáng và hoàn tất bồi thường vi phạm...'
                    : 'Ví dụ: Hủy ca liên tiếp không báo trước, vi phạm quy định nền tảng...'
                }
                value={reason}
                onChange={(e) => {
                  setReason(e.target.value);
                  if (error) setError('');
                }}
              />
              {error && (
                <span style={{ color: '#dc2626', fontSize: '0.8rem', display: 'block', marginTop: '0.25rem' }}>
                  {error}
                </span>
              )}
            </div>
          </div>

          <div className="user-modal-footer">
            <button
              type="button"
              className="btn-filter-reset"
              onClick={onClose}
              disabled={submitting}
            >
              Hủy
            </button>
            <button
              type="submit"
              className="btn-admin-approve"
              style={{ background: confirmBtnColor }}
              disabled={submitting}
            >
              {submitting ? 'Đang xử lý...' : `Xác nhận ${actionTitle}`}
            </button>
          </div>
        </form>
      </div>
    </div>
  );

  return typeof document !== 'undefined' ? createPortal(modalNode, document.body) : modalNode;
};

export default UserStatusModal;
