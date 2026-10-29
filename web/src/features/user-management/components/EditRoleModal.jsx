import { useState, useEffect } from 'react';
import { USER_ROLES, ROLE_LABELS } from '../types/userManagement.constants';

/**
 * EditRoleModal.jsx
 * Modal thay đổi vai trò người dùng (Worker, EO, CSKH, Admin).
 * Tuân thủ quy định bảo mật và lưu vết lý do thay đổi.
 */

export const EditRoleModal = ({ isOpen, user, onClose, onConfirm }) => {
  const [selectedRole, setSelectedRole] = useState('WORKER');
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (user) {
      setSelectedRole(user.role || 'WORKER');
      setReason('');
    }
  }, [user]);

  if (!isOpen || !user) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!reason.trim()) {
      alert('Vui lòng nhập lý do thay đổi vai trò để lưu vào nhật ký kiểm toán hệ thống.');
      return;
    }
    setSubmitting(true);
    try {
      await onConfirm(user.id, selectedRole, reason.trim());
    } finally {
      setSubmitting(false);
    }
  };

  const roleOptions = [
    {
      value: USER_ROLES.WORKER,
      label: ROLE_LABELS.WORKER,
      desc: 'Nhận ca làm việc, chấm công QR, nhận thanh toán thù lao qua CANi.',
    },
    {
      value: USER_ROLES.EVENT_ORGANIZER,
      label: ROLE_LABELS.EVENT_ORGANIZER,
      desc: 'Tạo và quản lý sự kiện, tuyển dụng nhân sự, khai báo chi trả lương.',
    },
    {
      value: USER_ROLES.CSKH,
      label: ROLE_LABELS.CSKH,
      desc: 'Hỗ trợ người dùng, tiếp nhận & xử lý ticket, hỗ trợ phân giải tranh chấp.',
    },
    {
      value: USER_ROLES.ADMIN,
      label: ROLE_LABELS.ADMIN,
      desc: 'Toàn quyền cấu hình hệ thống, duyệt eKYC, quản lý tài khoản & phân quyền.',
    },
  ];

  return (
    <div className="um-modal-backdrop animate-fade-in" onClick={onClose}>
      <div
        className="um-modal-card animate-scale-in"
        onClick={(e) => e.stopPropagation()}
        style={{ maxWidth: 540 }}
      >
        <div className="um-modal-header">
          <div>
            <h3 className="um-modal-title">Thay Đổi Vai Trò Người Dùng</h3>
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
            <div className="um-form-group">
              <label className="um-form-label">Chọn vai trò mới:</label>
              <div className="um-role-select-list">
                {roleOptions.map((opt) => (
                  <label
                    key={opt.value}
                    className={`um-role-radio-item ${
                      selectedRole === opt.value ? 'selected' : ''
                    }`}
                  >
                    <input
                      type="radio"
                      name="userRole"
                      value={opt.value}
                      checked={selectedRole === opt.value}
                      onChange={() => setSelectedRole(opt.value)}
                    />
                    <div className="um-role-radio-info">
                      <div className="um-role-radio-name">{opt.label}</div>
                      <div className="um-role-radio-desc">{opt.desc}</div>
                    </div>
                  </label>
                ))}
              </div>
            </div>

            {/* Cảnh báo khi cấp quyền Admin */}
            {selectedRole === USER_ROLES.ADMIN && user.role !== USER_ROLES.ADMIN && (
              <div className="um-alert-warning-box">
                ⚠️ <strong>Cảnh báo quyền hạn cao:</strong> Bạn đang phân quyền{' '}
                <strong>Quản Trị Viên (Admin)</strong> cho tài khoản này. Người dùng
                sẽ có quyền truy cập toàn bộ dữ liệu hệ thống CANi.
              </div>
            )}

            <div className="um-form-group" style={{ marginTop: '1rem' }}>
              <label className="um-form-label">
                Lý do thay đổi <span className="text-danger">*</span>:
              </label>
              <textarea
                className="um-form-textarea"
                rows={3}
                placeholder="Nhập lý do điều chỉnh quyền hạn (ví dụ: Tiếp nhận nhân sự CSKH mới, nâng cấp tài khoản doanh nghiệp...)"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                required
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
              Hủy Bỏ
            </button>
            <button
              type="submit"
              className="eo-btn-primary"
              disabled={submitting || selectedRole === user.role}
            >
              {submitting ? 'Đang lưu...' : '✓ Xác Nhận Đổi Vai Trò'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default EditRoleModal;
