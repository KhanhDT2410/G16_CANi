import { useState, useEffect } from 'react';
import { SUSPENSION_DURATIONS } from '../types/userManagement.constants';

/**
 * AccountStatusModal.jsx
 * Modal quản lý trạng thái tài khoản: Suspend (Tạm khóa), Ban (Cấm vĩnh viễn),
 * Deactivate (Vô hiệu hóa - Không xóa dữ liệu), và Reactivate (Mở lại).
 * Tuân thủ tuyệt đối quy định UC-112: "Cannot delete records (deactivate only)".
 */

export const AccountStatusModal = ({
  isOpen,
  user,
  actionType,
  onClose,
  onConfirm,
}) => {
  const [currentAction, setCurrentAction] = useState('SUSPEND');
  const [durationDays, setDurationDays] = useState(7);
  const [reason, setReason] = useState('');
  const [confirmCheckbox, setConfirmCheckbox] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setCurrentAction(actionType || 'SUSPEND');
      setDurationDays(7);
      setReason('');
      setConfirmCheckbox(false);
    }
  }, [isOpen, actionType]);

  if (!isOpen || !user) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!reason.trim()) {
      alert('Vui lòng nhập lý do để ghi nhận vào nhật ký kiểm toán quản trị.');
      return;
    }
    setSubmitting(true);
    try {
      await onConfirm(user.id, currentAction, {
        durationDays: currentAction === 'SUSPEND' ? durationDays : null,
        reason: reason.trim(),
      });
    } finally {
      setSubmitting(false);
    }
  };

  const isReactivate = currentAction === 'REACTIVATE';

  return (
    <div className="um-modal-backdrop animate-fade-in" onClick={onClose}>
      <div
        className="um-modal-card animate-scale-in"
        onClick={(e) => e.stopPropagation()}
        style={{ maxWidth: 520 }}
      >
        <div className="um-modal-header">
          <div>
            <h3 className="um-modal-title">
              {isReactivate ? 'Mở Khóa Tài Khoản' : 'Quản Lý Hạn Chế Tài Khoản'}
            </h3>
            <p className="um-modal-sub">
              Tài khoản: <strong>{user.fullName}</strong> ({user.code})
            </p>
          </div>
          <button type="button" className="um-modal-close" onClick={onClose}>
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="um-modal-body">
            {/* Nếu không phải mở khóa, cho phép chọn giữa Suspend / Ban / Deactivate */}
            {!isReactivate && (
              <div className="um-form-group">
                <label className="um-form-label">Chọn hình thức xử lý:</label>
                <div className="um-status-action-tabs">
                  <button
                    type="button"
                    className={`um-status-tab-btn ${
                      currentAction === 'SUSPEND' ? 'active-suspend' : ''
                    }`}
                    onClick={() => setCurrentAction('SUSPEND')}
                  >
                    ⏸️ Tạm Khóa (Suspend)
                  </button>
                  <button
                    type="button"
                    className={`um-status-tab-btn ${
                      currentAction === 'BAN' ? 'active-ban' : ''
                    }`}
                    onClick={() => setCurrentAction('BAN')}
                  >
                    🚫 Cấm Vĩnh Viễn (Ban)
                  </button>
                  <button
                    type="button"
                    className={`um-status-tab-btn ${
                      currentAction === 'DEACTIVATE' ? 'active-deactivate' : ''
                    }`}
                    onClick={() => setCurrentAction('DEACTIVATE')}
                  >
                    🛑 Vô Hiệu Hóa
                  </button>
                </div>
              </div>
            )}

            {/* Thông điệp đặc thù theo hành động */}
            {currentAction === 'SUSPEND' && (
              <div className="um-form-group">
                <label className="um-form-label">Thời hạn tạm khóa:</label>
                <select
                  className="um-select full-width"
                  value={durationDays}
                  onChange={(e) => setDurationDays(Number(e.target.value))}
                >
                  {SUSPENSION_DURATIONS.map((d) => (
                    <option key={d.value} value={d.value}>
                      {d.label}
                    </option>
                  ))}
                </select>
                <div className="um-field-help">
                  Người dùng sẽ không thể đăng nhập hoặc nhận ca cho đến khi hết thời hạn.
                </div>
              </div>
            )}

            {currentAction === 'DEACTIVATE' && (
              <div className="um-deactivate-notice-box">
                <div className="um-notice-title">
                  ℹ️ <strong>Quy Định Bảo Toàn Dữ Liệu (UC-112):</strong>
                </div>
                <p className="um-notice-text">
                  Hệ thống CANi <strong>không cho phép xóa vĩnh viễn (Hard Delete)</strong>{' '}
                  hồ sơ người dùng để bảo toàn tính toàn vẹn của lịch sử ca làm việc,
                  chấm công và thanh toán tài chính.
                </p>
                <p className="um-notice-text" style={{ marginTop: '0.4rem' }}>
                  Hành động này sẽ chuyển trạng thái sang <strong>Đã vô hiệu hóa</strong>{' '}
                  và ngắt mọi quyền truy cập của tài khoản.
                </p>
              </div>
            )}

            {currentAction === 'BAN' && (
              <div className="um-alert-danger-box">
                ⚠️ <strong>Cảnh Báo Cấm Vĩnh Viễn:</strong> Tài khoản này sẽ bị khóa
                triệt để khỏi CANi Platform do vi phạm nghiêm trọng chính sách hoặc có
                hành vi gian lận tài chính.
              </div>
            )}

            {isReactivate && (
              <div className="um-alert-info-box">
                ✓ <strong>Khôi phục tài khoản:</strong> Tài khoản sẽ được chuyển về trạng
                thái <strong>Đang hoạt động (ACTIVE)</strong> và có thể tiếp tục sử dụng
                nền tảng bình thường.
              </div>
            )}

            {/* Lý do thao tác */}
            <div className="um-form-group" style={{ marginTop: '1rem' }}>
              <label className="um-form-label">
                Lý do {isReactivate ? 'mở khóa' : 'áp dụng'}{' '}
                <span className="text-danger">*</span>:
              </label>
              <textarea
                className="um-form-textarea"
                rows={3}
                placeholder={
                  isReactivate
                    ? 'VD: Đã hoàn tất thời gian phạt, giải quyết xong khiếu nại...'
                    : 'VD: No-show 2 ca liên tiếp, có dấu hiệu giả mạo định vị GPS, nợ lương...'
                }
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                required
              />
            </div>

            {/* Checkbox xác nhận cho Ban / Deactivate */}
            {(currentAction === 'BAN' || currentAction === 'DEACTIVATE') && (
              <label className="um-checkbox-label">
                <input
                  type="checkbox"
                  checked={confirmCheckbox}
                  onChange={(e) => setConfirmCheckbox(e.target.checked)}
                />
                <span>Tôi xác nhận áp dụng hình thức xử lý này cho tài khoản.</span>
              </label>
            )}
          </div>

          <div className="um-modal-footer">
            <button
              type="button"
              className="eo-btn-secondary"
              onClick={onClose}
              disabled={submitting}
            >
              Hủy Bỏ
            </button>
            <button
              type="submit"
              className={
                isReactivate
                  ? 'eo-btn-primary'
                  : currentAction === 'BAN'
                  ? 'btn-danger-confirm'
                  : 'eo-btn-primary'
              }
              disabled={
                submitting ||
                ((currentAction === 'BAN' || currentAction === 'DEACTIVATE') &&
                  !confirmCheckbox)
              }
            >
              {submitting
                ? 'Đang lưu...'
                : isReactivate
                ? '✓ Xác Nhận Mở Khóa'
                : currentAction === 'SUSPEND'
                ? '⏸️ Xác Nhận Tạm Khóa'
                : currentAction === 'BAN'
                ? '🚫 Xác Nhận Cấm Vĩnh Viễn'
                : '🛑 Xác Nhận Vô Hiệu Hóa'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default AccountStatusModal;
