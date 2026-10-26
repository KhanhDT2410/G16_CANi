import { useState } from 'react';

const UserStatusModal = ({ user, submitting, onClose, onConfirm }) => {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

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

  return (
    <div className="user-modal-overlay" onClick={onClose}>
      <div
        className="user-modal-box"
        style={{ maxWidth: '480px' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="user-modal-header">
          <h3 style={{ color: isCurrentlyLocked ? '#16a34a' : '#dc2626' }}>
            {actionTitle}
          </h3>
          <button type="button" className="btn-modal-close" onClick={onClose}>
            &times;
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <p style={{ fontSize: '0.9rem', color: '#374151', margin: '0 0 1rem 0' }}>
            Bạn đang thực hiện {actionTitle.toLowerCase()} cho người dùng{' '}
            <strong>{user.fullName}</strong> ({user.email}).
          </p>

          <div style={{ marginBottom: '1rem' }}>
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

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
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
};

export default UserStatusModal;
