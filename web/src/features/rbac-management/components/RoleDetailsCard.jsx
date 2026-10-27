import React from 'react';

/**
 * Thẻ Chi Tiết Vai Trò Đang Chọn (W-40)
 * Hiển thị tóm tắt, chỉ số người dùng trực thuộc và các hành động quản trị vai trò.
 */
export const RoleDetailsCard = ({
  role,
  onResetRole,
  onDeleteRole,
}) => {
  if (!role) return null;

  return (
    <div className="rbac-role-card" style={{ borderLeftColor: role.color }}>
      <div className="rbac-role-card-header">
        <div className="rbac-role-card-info">
          <div className="rbac-role-badge-row">
            <span
              className="rbac-role-color-pill"
              style={{ backgroundColor: role.color }}
            >
              {role.code}
            </span>
            {role.isSystem ? (
              <span className="rbac-badge-system">
                {role.isSuperAdmin ? 'Super Admin (Toàn quyền bảo mật BR-69)' : 'Vai trò Hệ thống (Bảo vệ cốt lõi)'}
              </span>
            ) : (
              <span className="rbac-badge-custom">Vai trò Tùy chỉnh (AF-01)</span>
            )}
            <span className="rbac-badge-users">
              👥 <strong>{role.userCount}</strong> tài khoản đảm nhiệm
            </span>
          </div>

          <h2 className="rbac-role-card-title">{role.name}</h2>
          <p className="rbac-role-card-desc">{role.description}</p>
        </div>

        <div className="rbac-role-card-actions">
          {role.isSystem ? (
            <button
              type="button"
              className="rbac-btn-reset"
              onClick={() => onResetRole(role.id)}
              title="Khôi phục phân quyền gốc của vai trò hệ thống này"
            >
              ↺ Khôi Phục Mặc Định
            </button>
          ) : (
            <div className="rbac-custom-actions">
              <button
                type="button"
                className="rbac-btn-reset"
                onClick={() => onResetRole(role.id)}
                title="Khôi phục cấu hình ban đầu"
              >
                ↺ Khôi Phục
              </button>
              <button
                type="button"
                className="rbac-btn-delete"
                onClick={() => onDeleteRole(role.id)}
                title="Xóa vai trò tùy chỉnh"
              >
                🗑 Xóa Vai Trò
              </button>
            </div>
          )}
        </div>
      </div>

      <div className="rbac-role-card-footer">
        <div className="rbac-role-meta-item">
          <span>Tổng quyền đang cấp:</span>
          <strong>{role.permissions.length} quyền hạn</strong>
        </div>
        <div className="rbac-role-meta-item">
          <span>Chỉnh sửa gần nhất:</span>
          <strong>{role.lastModifiedAt || 'Chưa ghi nhận'}</strong>
          {role.lastModifiedBy && <em> (bởi {role.lastModifiedBy})</em>}
        </div>
        <div className="rbac-immediate-notice">
          ⚡ <em>Hiệu lực tức thời: Mọi thay đổi áp dụng ngay cho tất cả tài khoản thuộc vai trò này.</em>
        </div>
      </div>
    </div>
  );
};

export default RoleDetailsCard;
