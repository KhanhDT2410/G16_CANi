import React from 'react';

/**
 * Modal Xác Nhận Khôi Phục Phân Quyền Mặc Định (W-40)
 */
export const ResetRoleModal = ({ isOpen, onClose, onConfirm }) => {
  if (!isOpen) return null;

  return (
    <div className="rbac-modal-backdrop" onClick={onClose}>
      <div
        className="rbac-modal-card rbac-confirm-modal animate-scale-in"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="rbac-modal-header">
          <div className="rbac-modal-header-icon warning">⚠️</div>
          <div>
            <h3 className="rbac-modal-title">Xác Nhận Khôi Phục</h3>
            <p className="rbac-modal-sub">Khôi phục danh sách quyền về mặc định</p>
          </div>
        </div>
        <p className="rbac-confirm-msg">
          Bạn có chắc chắn muốn khôi phục toàn bộ phân quyền của vai trò này về
          trạng thái thiết lập ban đầu? Thao tác sẽ có hiệu lực ngay lập tức.
        </p>
        <div className="rbac-modal-footer">
          <button
            type="button"
            className="rbac-btn-secondary"
            onClick={onClose}
          >
            Hủy
          </button>
          <button
            type="button"
            className="rbac-btn-primary"
            onClick={onConfirm}
          >
            Xác Nhận Khôi Phục
          </button>
        </div>
      </div>
    </div>
  );
};

export default ResetRoleModal;
