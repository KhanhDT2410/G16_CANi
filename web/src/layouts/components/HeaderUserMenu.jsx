import { Link } from 'react-router-dom';
import { BellIcon, HelpIcon, LogoutIcon } from './HeaderIcons';

export const HeaderUserMenu = ({
  isAuthenticated,
  user,
  userAvatar,
  isAdmin,
  onUserBlockClick,
  onLogout,
}) => {
  if (!isAuthenticated || !user) {
    return (
      <div className="auth-nav-actions">
        <Link to="/login" className="btn-login-link">
          Đăng nhập
        </Link>
        <Link to="/register" className="btn-register-link">
          Đăng ký EO
        </Link>
      </div>
    );
  }

  return (
    <div className="user-header-group">
      <button type="button" className="header-notif-btn" title="Thông báo mới">
        <BellIcon />
        <span className="notif-badge-dot" />
      </button>

      {!isAdmin && (
        <button
          type="button"
          className="header-help-btn"
          title="Trung tâm trợ giúp"
          onClick={() => window.dispatchEvent(new CustomEvent('cani_switch_tab', { detail: { tab: 'settings' } }))}
        >
          <HelpIcon />
        </button>
      )}

      <div
        className="user-header-compact"
        onClick={onUserBlockClick}
        title={isAdmin ? 'Trở về Bảng Điều Khiển Quản Trị' : `Hồ sơ: ${user.fullName || user.username || 'EO'}`}
        style={{ cursor: 'pointer' }}
      >
        <div className="user-avatar-badge">
          {userAvatar ? (
            <img
              src={userAvatar}
              alt="Avatar"
              style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover' }}
            />
          ) : (
            (user.fullName || user.username || (isAdmin ? 'Q' : 'D')).charAt(0).toUpperCase()
          )}
        </div>
        <div className="user-meta-info">
          <span className="user-meta-name">
            {user.fullName || user.username || (isAdmin ? 'Quản Trị Viên Hệ Thống' : 'Duy Hưng Phạm')}
          </span>
          <span className="user-meta-email">
            {user.email || (isAdmin ? 'admin@cani.vn' : 'hung.company@gmail.com')}
          </span>
        </div>
        {!isAdmin && <span className="user-meta-arrow">▾</span>}
      </div>

      <button
        type="button"
        onClick={onLogout}
        className="btn-logout-icon"
        title="Đăng xuất"
      >
        <LogoutIcon />
      </button>
    </div>
  );
};

export default HeaderUserMenu;

