import { useState } from 'react';

/**
 * ForceResetPasswordModal.jsx
 * Modal buộc đặt lại mật khẩu cho người dùng (qua email hoặc mật khẩu tạm thời).
 */

export const ForceResetPasswordModal = ({ isOpen, user, onClose, onConfirm }) => {
  const [method, setMethod] = useState('EMAIL'); // 'EMAIL' | 'TEMP_PASSWORD'
  const [tempPassword, setTempPassword] = useState('CANi@2026!Staff');
  const [copied, setCopied] = useState(false);
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (!isOpen || !user) return null;

  const generateRandomPassword = () => {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%';
    let res = 'CANi#';
    for (let i = 0; i < 8; i++) {
      res += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    setTempPassword(res);
    setCopied(false);
  };

  const handleCopy = () => {
    navigator.clipboard.writeText(tempPassword);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await onConfirm(user.id, {
        method,
        tempPassword: method === 'TEMP_PASSWORD' ? tempPassword : null,
        reason: reason.trim() || 'Admin yêu cầu đặt lại mật khẩu định kỳ hoặc bảo mật',
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="um-modal-backdrop animate-fade-in" onClick={onClose}>
      <div
        className="um-modal-card animate-scale-in"
        onClick={(e) => e.stopPropagation()}
        style={{ maxWidth: 500 }}
      >
        <div className="um-modal-header">
          <div>
            <h3 className="um-modal-title">Đặt Lại Mật Khẩu Người Dùng</h3>
            <p className="um-modal-sub">
              Tài khoản: <strong>{user.fullName}</strong> ({user.email})
            </p>
          </div>
          <button type="button" className="um-modal-close" onClick={onClose}>
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="um-modal-body">
            <div className="um-form-group">
              <label className="um-form-label">Chọn phương thức xử lý:</label>
              <div className="um-method-select-grid">
                <div
                  className={`um-method-box ${method === 'EMAIL' ? 'active' : ''}`}
                  onClick={() => setMethod('EMAIL')}
                >
                  <div className="um-method-icon">📧</div>
                  <div className="um-method-title">Gửi Email Khôi Phục</div>
                  <div className="um-method-desc">
                    Hệ thống sẽ gửi link tạo mật khẩu mới an toàn về email chính thức.
                  </div>
                </div>

                <div
                  className={`um-method-box ${method === 'TEMP_PASSWORD' ? 'active' : ''}`}
                  onClick={() => setMethod('TEMP_PASSWORD')}
                >
                  <div className="um-method-icon">🔑</div>
                  <div className="um-method-title">Cấp Mật Khẩu Tạm</div>
                  <div className="um-method-desc">
                    Tạo mật khẩu dùng 1 lần, bắt buộc đổi mật khẩu khi đăng nhập.
                  </div>
                </div>
              </div>
            </div>

            {method === 'TEMP_PASSWORD' && (
              <div className="um-temp-pass-box animate-fade-in">
                <label className="um-form-label">Mật khẩu tạm thời:</label>
                <div className="um-pass-input-row">
                  <input
                    type="text"
                    className="um-pass-input font-mono"
                    value={tempPassword}
                    readOnly
                  />
                  <button
                    type="button"
                    className="um-btn-generate"
                    onClick={generateRandomPassword}
                    title="Tạo chuỗi ngẫu nhiên mới"
                  >
                    🎲 Tạo mới
                  </button>
                  <button
                    type="button"
                    className="um-btn-copy"
                    onClick={handleCopy}
                    title="Sao chép mật khẩu"
                  >
                    {copied ? '✓ Đã chép' : '📋 Copy'}
                  </button>
                </div>
                <div className="um-pass-help">
                  ⚠️ Hãy gửi mật khẩu này qua kênh bảo mật cho người dùng. Mật khẩu
                  hết hạn sau 24 giờ.
                </div>
              </div>
            )}

            <div className="um-form-group" style={{ marginTop: '1rem' }}>
              <label className="um-form-label">Ghi chú lý do thao tác:</label>
              <input
                type="text"
                className="um-search-input"
                placeholder="VD: Người dùng báo mất quyền truy cập, kiểm tra bảo mật..."
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </div>
          </div>

          <div className="um-modal-footer">
            <button
              type="button"
              className="eo-btn-secondary"
              onClick={onClose}
              disabled={submitting}
            >
              Hủy
            </button>
            <button type="submit" className="eo-btn-primary" disabled={submitting}>
              {submitting
                ? 'Đang thực hiện...'
                : method === 'EMAIL'
                ? '✉️ Gửi Email Khôi Phục'
                : '✓ Áp Dụng Mật Khẩu Tạm'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default ForceResetPasswordModal;
