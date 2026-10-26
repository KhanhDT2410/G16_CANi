import { ROLE_LABELS, STATUS_CONFIG } from '../types/userManagement.constants';

/**
 * UserProfileDrawer.jsx
 * Slide-over Drawer xem hồ sơ chi tiết người dùng chuẩn DESIGN.md.
 */

export const UserProfileDrawer = ({
  isOpen,
  user,
  onClose,
  onEditRole,
  onResetPassword,
  onOpenStatusModal,
}) => {
  if (!isOpen || !user) return null;

  const statusInfo = STATUS_CONFIG[user.status] || STATUS_CONFIG.ACTIVE;

  return (
    <div className="um-drawer-overlay animate-fade-in" onClick={onClose}>
      <div
        className="um-drawer-panel animate-slide-in-right"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Drawer Header */}
        <div className="um-drawer-header">
          <div className="um-drawer-title-wrap">
            <h2 className="um-drawer-title">Hồ Sơ Chi Tiết Người Dùng</h2>
            <span className="um-drawer-code">{user.code}</span>
          </div>
          <button
            type="button"
            className="um-drawer-close-btn"
            onClick={onClose}
            title="Đóng bảng chi tiết"
          >
            ✕
          </button>
        </div>

        {/* Drawer Content */}
        <div className="um-drawer-content">
          {/* Hero Profile Block */}
          <div className="um-profile-hero">
            <div className="um-hero-avatar-box">
              {user.avatar ? (
                <img src={user.avatar} alt={user.fullName} />
              ) : (
                <div className="um-hero-avatar-fallback">
                  {user.fullName?.charAt(0) || 'U'}
                </div>
              )}
              {user.ekycStatus === 'VERIFIED' && (
                <span className="um-hero-verified-badge" title="Tài khoản tích xanh">
                  ✓
                </span>
              )}
            </div>

            <div className="um-hero-info">
              <h3 className="um-hero-name">{user.fullName}</h3>
              <div className="um-hero-role-status">
                <span className="um-hero-role-pill">
                  {ROLE_LABELS[user.role] || user.role}
                </span>
                <span className={`um-status-pill ${statusInfo.badgeClass}`}>
                  <span
                    className="um-status-dot"
                    style={{ backgroundColor: statusInfo.dotColor }}
                  />
                  {statusInfo.label}
                </span>
              </div>
            </div>
          </div>

          {/* AI Risk Flag Alert Box if present */}
          {user.aiFlag && (
            <div className={`um-drawer-alert ${user.aiFlag.severity?.toLowerCase()}`}>
              <div className="um-alert-header">
                <span className="um-alert-icon">⚠️</span>
                <strong>Cảnh Báo Giám Sát AI ({user.aiFlag.type}):</strong>
              </div>
              <p className="um-alert-body">{user.aiFlag.reason}</p>
            </div>
          )}

          {/* Performance & Reliability Grid */}
          <div className="um-drawer-section">
            <h4 className="um-section-heading">Chỉ Số Uy Tín &amp; Vận Hành</h4>
            <div className="um-stats-2col">
              <div className="um-stat-box">
                <div className="um-stat-num" style={{ color: '#5B86E5' }}>
                  {user.reliabilityScore || 0}
                  <span style={{ fontSize: '0.9rem', color: '#64748b' }}>/100</span>
                </div>
                <div className="um-stat-lbl">Điểm Uy Tín (Reliability)</div>
              </div>
              <div className="um-stat-box">
                <div className="um-stat-num" style={{ color: '#f59e0b' }}>
                  {user.ratingAvg > 0 ? `${user.ratingAvg} ★` : 'Chưa có'}
                </div>
                <div className="um-stat-lbl">Đánh Giá Trung Bình</div>
              </div>
              <div className="um-stat-box">
                <div className="um-stat-num" style={{ color: '#10b981' }}>
                  {user.role === 'WORKER'
                    ? user.shiftsCompleted || 0
                    : user.eventsOrganized || 0}
                </div>
                <div className="um-stat-lbl">
                  {user.role === 'WORKER' ? 'Ca Làm Hoàn Thành' : 'Sự Kiện Đã Tổ Chức'}
                </div>
              </div>
              <div className="um-stat-box">
                <div
                  className="um-stat-num"
                  style={{ color: (user.noShowCount || 0) > 0 ? '#ef4444' : '#10b981' }}
                >
                  {user.noShowCount || 0}
                </div>
                <div className="um-stat-lbl">Lần Bỏ Ca (No-Show)</div>
              </div>
            </div>
          </div>

          {/* Personal & Identification Details */}
          <div className="um-drawer-section">
            <h4 className="um-section-heading">Thông Tin Định Danh &amp; Liên Hệ</h4>
            <div className="um-detail-list">
              <div className="um-detail-item">
                <span className="um-detail-label">Email:</span>
                <span className="um-detail-val">{user.email}</span>
              </div>
              <div className="um-detail-item">
                <span className="um-detail-label">Số điện thoại:</span>
                <span className="um-detail-val">{user.phone}</span>
              </div>
              <div className="um-detail-item">
                <span className="um-detail-label">Số CCCD (Masked):</span>
                <span className="um-detail-val font-mono">
                  {user.cccdMasked || 'Chưa cung cấp'}
                </span>
              </div>
              <div className="um-detail-item">
                <span className="um-detail-label">Ngày sinh:</span>
                <span className="um-detail-val">{user.dob || '—'}</span>
              </div>
              <div className="um-detail-item">
                <span className="um-detail-label">Địa chỉ:</span>
                <span className="um-detail-val">{user.address || '—'}</span>
              </div>
              {user.companyName && (
                <>
                  <div className="um-detail-item">
                    <span className="um-detail-label">Doanh nghiệp:</span>
                    <span className="um-detail-val">{user.companyName}</span>
                  </div>
                  <div className="um-detail-item">
                    <span className="um-detail-label">Mã số thuế:</span>
                    <span className="um-detail-val font-mono">{user.taxCode}</span>
                  </div>
                </>
              )}
            </div>
          </div>

          {/* System Audit Information */}
          <div className="um-drawer-section">
            <h4 className="um-section-heading">Nhật Ký Hệ Thống &amp; Ghi Chú</h4>
            <div className="um-detail-list">
              <div className="um-detail-item">
                <span className="um-detail-label">Ngày tạo tài khoản:</span>
                <span className="um-detail-val">{user.createdAt}</span>
              </div>
              <div className="um-detail-item">
                <span className="um-detail-label">Đăng nhập gần nhất:</span>
                <span className="um-detail-val">{user.lastLoginAt}</span>
              </div>
              {user.suspendReason && (
                <div className="um-detail-item highlight-warn">
                  <span className="um-detail-label">Lý do tạm khóa:</span>
                  <span className="um-detail-val">{user.suspendReason}</span>
                </div>
              )}
              {user.bannedReason && (
                <div className="um-detail-item highlight-danger">
                  <span className="um-detail-label">Lý do cấm:</span>
                  <span className="um-detail-val">{user.bannedReason}</span>
                </div>
              )}
              {user.deactivatedReason && (
                <div className="um-detail-item highlight-subtle">
                  <span className="um-detail-label">Lý do vô hiệu hóa:</span>
                  <span className="um-detail-val">{user.deactivatedReason}</span>
                </div>
              )}
            </div>
            {user.notes && <div className="um-notes-box">📝 {user.notes}</div>}
          </div>
        </div>

        {/* Drawer Actions Footer */}
        <div className="um-drawer-footer">
          <button
            type="button"
            className="eo-btn-secondary"
            onClick={() => onResetPassword(user)}
          >
            🔑 Đặt Lại Mật Khẩu
          </button>
          <button
            type="button"
            className="eo-btn-secondary"
            onClick={() => onEditRole(user)}
          >
            ✏️ Đổi Vai Trò
          </button>
          <button
            type="button"
            className="eo-btn-primary"
            onClick={() => onOpenStatusModal(user, user.status === 'ACTIVE' ? 'SUSPEND' : 'REACTIVATE')}
          >
            {user.status === 'ACTIVE' ? '⚙️ Quản Lý Khóa / Hạn Chế' : '▶️ Mở Lại Tài Khoản'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default UserProfileDrawer;
