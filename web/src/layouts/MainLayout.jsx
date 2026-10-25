import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth';
import { ToastContainer } from '../components/common/Toast';
import CaniLogo from '../components/common/CaniLogo';
import './MainLayout.css';

const MainLayout = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, isAuthenticated, logout } = useAuth();

  // Phân quyền thanh điều hướng chuẩn đặc tả SEP490:
  // - Web Portal: Dành riêng cho Event Organizer (EO), Admin, Customer Care.
  // - Worker: Hoạt động độc lập 100% trên Mobile App (CANi Partner App).
  const getNavItems = () => {
    const commonItems = [
      { path: '/mobile-preview', label: '📱 App Mobile Worker' },
    ];

    if (!user) {
      return [
        { path: '/eo', label: 'Ban Tổ Chức (EO)' },
        ...commonItems,
      ];
    }
    switch (user.role) {
      case 'ADMIN':
        return [
          { path: '/admin', label: 'Admin Portal' },
          { path: '/customer-care', label: 'Customer Care' },
          { path: '/eo', label: 'Xem Cổng EO' },
          ...commonItems,
        ];
      case 'CUSTOMER_CARE':
        return [
          { path: '/customer-care', label: 'Customer Care' },
          { path: '/eo', label: 'Xem Cổng EO' },
          ...commonItems,
        ];
      case 'EVENT_ORGANIZER':
      default:
        return [
          { path: '/eo', label: 'Ban Tổ Chức (EO)' },
          ...commonItems,
        ];
    }
  };

  const navItems = getNavItems();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="main-layout">
      <ToastContainer />
      <header className="main-header">
        <div className="main-header-left">
          <Link to="/eo" className="main-logo-group" title="CANi EVENT PLATFORM - Cần Ai, Có CANi">
            <CaniLogo variant="horizontal" height={36} />
          </Link>
          <nav className="main-nav">
            {navItems.map((item) => {
              const isActive = location.pathname === item.path;
              return (
                <Link
                  key={item.path}
                  to={item.path}
                  className={`nav-link ${isActive ? 'active' : ''}`}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>
        </div>

        <div className="main-header-center">
          <div className="header-search-box">
            <span className="header-search-icon">🔍</span>
            <input
              type="text"
              placeholder="Tìm kiếm sự kiện, nhân sự, địa điểm..."
              className="header-search-input"
            />
          </div>
        </div>

        <div className="main-header-right">
          {isAuthenticated && user ? (
            <div className="user-header-group">
              <button type="button" className="header-notif-btn" title="Thông báo mới">
                <span className="notif-icon">🔔</span>
                <span className="notif-badge-dot"></span>
              </button>
              <div className="user-session-info">
                <div className="user-avatar-badge" title={user.fullName || user.username}>
                  {(user.fullName || user.username || 'D').charAt(0).toUpperCase()}
                </div>
                <div className="user-meta">
                  <span className="user-greeting">
                    Xin chào, <strong>{user.fullName || user.username}</strong>
                  </span>
                  <span className="user-role-pill">{user.role}</span>
                </div>
                <button
                  type="button"
                  onClick={handleLogout}
                  className="btn-logout"
                  title="Đăng xuất"
                >
                  Đăng xuất
                </button>
              </div>
            </div>
          ) : (
            <div className="auth-nav-actions">
              <Link to="/login" className="btn-login-link">
                Đăng nhập
              </Link>
              <Link to="/register" className="btn-register-link">
                Đăng ký EO
              </Link>
            </div>
          )}
        </div>
      </header>

      <main className="main-content">
        <Outlet />
      </main>

      <footer className="main-footer">
        <div className="main-footer-inner">
          <span>CANi Event Staffing Platform — Portal Dành Cho Ban Tổ Chức & Quản Trị</span>
          <span className="system-status-indicator">
            <span className="status-dot"></span>
            Hệ thống ổn định
          </span>
        </div>
      </footer>
    </div>
  );
};

export default MainLayout;
