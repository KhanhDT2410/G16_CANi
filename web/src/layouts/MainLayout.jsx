import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth';
import './MainLayout.css';

const MainLayout = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, isAuthenticated, logout } = useAuth();

  const navItems = [
    { path: '/eo', label: 'Event Organizer' },
    { path: '/customer-care', label: 'Customer Care' },
    { path: '/admin', label: 'Admin' },
  ];

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="main-layout">
      <header className="main-header">
        <div className="main-header-left">
          <h1 className="main-logo">CANi</h1>
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

        <div className="main-header-right">
          {isAuthenticated && user ? (
            <div className="user-session-info">
              <span className="user-greeting">
                Xin chào, <strong>{user.fullName}</strong> ({user.role})
              </span>
              <button
                type="button"
                onClick={handleLogout}
                className="btn-logout"
              >
                Đăng xuất
              </button>
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
        CANi Event Staffing Platform — Portal Dành Cho Ban Tổ Chức & Quản Trị
      </footer>
    </div>
  );
};

export default MainLayout;
