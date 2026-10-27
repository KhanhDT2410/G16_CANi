import React from 'react';
import { MSG157_WARNING } from '../types/rbac.constants';

/**
 * Hộp Thoại Ngoại Lệ Bước 5a: Cảnh Báo Thu Hồi Quyền Nguy Cấp (MSG157 - UC-113)
 */
export const Msg157Modal = ({ isOpen, dialogData, onClose, onConfirm }) => {
  if (!isOpen) return null;

  return (
    <div className="rbac-modal-backdrop" onClick={onClose}>
      <div
        className="rbac-modal-card rbac-confirm-modal animate-scale-in"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="rbac-modal-header">
          <div className="rbac-modal-header-icon critical">🚨</div>
          <div>
            <h3 className="rbac-modal-title">{MSG157_WARNING.title}</h3>
            <p className="rbac-modal-sub">
              Mã cảnh báo: {MSG157_WARNING.code} (UC-113 Bước 5a)
            </p>
          </div>
        </div>
        <p className="rbac-confirm-msg">
          Bạn đang yêu cầu thu hồi quyền hạn nguy cấp{' '}
          <strong>[{dialogData.permissionName}]</strong> khỏi vai trò{' '}
          <strong>{dialogData.roleName}</strong>.
          <br /><br />
          Hành động này có hiệu lực ngay lập tức và có thể làm gián đoạn tức thì quy trình
          nghiệp vụ của toàn bộ tài khoản trực thuộc.
        </p>
        <div className="rbac-modal-footer">
          <button
            type="button"
            className="rbac-btn-secondary"
            onClick={onClose}
          >
            {MSG157_WARNING.cancelBtn}
          </button>
          <button
            type="button"
            className="rbac-btn-danger"
            onClick={onConfirm}
          >
            {MSG157_WARNING.confirmBtn}
          </button>
        </div>
      </div>
    </div>
  );
};

export default Msg157Modal;
