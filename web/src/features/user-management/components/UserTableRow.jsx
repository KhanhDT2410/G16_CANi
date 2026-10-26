import { ROLE_LABELS, STATUS_CONFIG } from '../types/userManagement.constants';

/**
 * UserTableRow.jsx
 * Đại diện 1 hàng dữ liệu người dùng trong bảng W-39.
 */

export const UserTableRow = ({
  user,
  onViewProfile,
  onEditRole,
  onResetPassword,
  onOpenStatusModal,
}) => {
  const statusInfo = STATUS_CONFIG[user.status] || STATUS_CONFIG.ACTIVE;
  const isSuspendedOrBanned = user.status === 'SUSPENDED' || user.status === 'BANNED' || user.status === 'DEACTIVATED';

  // Role badge class
  const getRoleBadgeClass = (role) => {
    switch (role) {
      case 'ADMIN':
        return 'um-role-admin';
      case 'CSKH':
        return 'um-role-cskh';
      case 'EVENT_ORGANIZER':
        return 'um-role-eo';
      default:
        return 'um-role-worker';
    }
  };

  return (
    <tr className="um-table-row">
      {/* 1. Người dùng & Định danh */}
      <td className="um-td-user">
        <div className="um-user-cell">
          <div className="um-avatar-wrap">
            {user.avatar ? (
              <img src={user.avatar} alt={user.fullName} className="um-avatar-img" />
            ) : (
              <div className="um-avatar-fallback">
                {user.fullName?.charAt(0) || 'U'}
              </div>
            )}
            {user.ekycStatus === 'VERIFIED' && (
              <span className="um-verified-tick" title="Đã xác thực eKYC CCCD">
                ✓
              </span>
            )}
          </div>
          <div className="um-user-info">
            <div className="um-user-name-row">
              <span className="um-user-name" onClick={() => onViewProfile(user)}>
                {user.fullName}
              </span>
              {user.aiFlag && (
                <span
                  className={`um-flag-badge ${user.aiFlag.severity?.toLowerCase()}`}
                  title={user.aiFlag.reason}
                >
                  ⚠️ AI Flag
                </span>
              )}
            </div>
            <div className="um-user-code-sub">
              <span className="um-code-tag">{user.code}</span>
              {user.companyName && (
                <span className="um-company-name">• {user.companyName}</span>
              )}
            </div>
          </div>
        </div>
      </td>

      {/* 2. Thông tin liên hệ */}
      <td className="um-td-contact">
        <div className="um-contact-email">{user.email}</div>
        <div className="um-contact-phone">📞 {user.phone}</div>
      </td>

      {/* 3. Vai trò */}
      <td className="um-td-role">
        <span className={`um-role-badge ${getRoleBadgeClass(user.role)}`}>
          {ROLE_LABELS[user.role] || user.role}
        </span>
      </td>

      {/* 4. Điểm uy tín & Hiệu suất */}
      <td className="um-td-reliability">
        <div className="um-rel-score-wrap">
          <div className="um-rel-bar-bg">
            <div
              className={`um-rel-bar-fill ${
                user.reliabilityScore >= 85
                  ? 'high'
                  : user.reliabilityScore >= 60
                  ? 'medium'
                  : 'low'
              }`}
              style={{ width: `${Math.min(user.reliabilityScore || 0, 100)}%` }}
            />
          </div>
          <div className="um-rel-score-text">
            <strong>{user.reliabilityScore || 0}</strong>/100
            {user.ratingAvg > 0 && (
              <span className="um-rating-star"> ⭐ {user.ratingAvg}</span>
            )}
          </div>
        </div>
        <div className="um-shifts-stat">
          {user.role === 'WORKER'
            ? `${user.shiftsCompleted || 0} ca • ${user.noShowCount || 0} No-show`
            : user.role === 'EVENT_ORGANIZER'
            ? `${user.eventsOrganized || 0} sự kiện đã tạo`
            : `Hoạt động tích cực`}
        </div>
      </td>

      {/* 5. Trạng thái */}
      <td className="um-td-status">
        <span className={`um-status-pill ${statusInfo.badgeClass}`}>
          <span
            className="um-status-dot"
            style={{ backgroundColor: statusInfo.dotColor }}
          />
          {statusInfo.label}
        </span>
        {user.status === 'SUSPENDED' && user.suspendedUntil && (
          <div className="um-status-subnote">Hết hạn: {user.suspendedUntil}</div>
        )}
      </td>

      {/* 6. Thao tác quản trị */}
      <td className="um-td-actions">
        <div className="um-action-btn-group">
          {/* Xem hồ sơ */}
          <button
            type="button"
            className="um-act-btn um-act-view"
            onClick={() => onViewProfile(user)}
            title="Xem hồ sơ chi tiết"
          >
            👁️ Xem
          </button>

          {/* Đổi vai trò */}
          <button
            type="button"
            className="um-act-btn um-act-role"
            onClick={() => onEditRole(user)}
            title="Thay đổi vai trò người dùng"
          >
            ✏️ Vai trò
          </button>

          {/* Buộc đặt lại mật khẩu */}
          <button
            type="button"
            className="um-act-btn um-act-key"
            onClick={() => onResetPassword(user)}
            title="Đặt lại mật khẩu"
          >
            🔑 Reset MK
          </button>

          {/* Trạng thái: Nếu đang bị khóa/cấm thì hiển thị Mở lại, nếu đang hoạt động thì hiển thị menu quản lý */}
          {isSuspendedOrBanned ? (
            <button
              type="button"
              className="um-act-btn um-act-reactivate"
              onClick={() => onOpenStatusModal(user, 'REACTIVATE')}
              title="Khôi phục tài khoản về trạng thái hoạt động"
            >
              ▶️ Mở Lại
            </button>
          ) : (
            <div className="um-dropdown-action-wrap">
              <button
                type="button"
                className="um-act-btn um-act-danger"
                onClick={() => onOpenStatusModal(user, 'SUSPEND')}
                title="Tạm khóa / Hạn chế tài khoản"
              >
                ⚙️ Khóa/Hạn chế
              </button>
            </div>
          )}
        </div>
      </td>
    </tr>
  );
};

export default UserTableRow;
