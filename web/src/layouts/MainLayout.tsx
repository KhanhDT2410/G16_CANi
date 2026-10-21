import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth';

const MainLayout = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, isAuthenticated, logout } = useAuth();

  const navItems = [
    { path: '/eo', label: '🏢 Event Organizer' },
    { path: '/customer-care', label: '🎧 Customer Care' },
    { path: '/admin', label: '⚙️ Admin' },
  ];

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <header
        style={{
          padding: '1rem 2rem',
          borderBottom: '1px solid #e5e7eb',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '2rem' }}>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 'bold', margin: 0, color: '#2563eb' }}>
            CANi
          </h1>
          <nav style={{ display: 'flex', gap: '0.75rem' }}>
            {navItems.map((item) => (
              <Link
                key={item.path}
                to={item.path}
                style={{
                  textDecoration: 'none',
                  padding: '0.5rem 1rem',
                  borderRadius: '0.375rem',
                  backgroundColor: location.pathname === item.path ? '#3b82f6' : 'transparent',
                  color: location.pathname === item.path ? 'white' : '#374151',
                  fontWeight: location.pathname === item.path ? '600' : 'normal',
                }}
              >
                {item.label}
              </Link>
            ))}
          </nav>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          {isAuthenticated && user ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
              <span style={{ fontSize: '0.875rem', color: '#4b5563' }}>
                Xin chào, <strong>{user.fullName}</strong> ({user.role})
              </span>
              <button
                onClick={handleLogout}
                style={{
                  padding: '0.4rem 0.8rem',
                  borderRadius: '0.375rem',
                  border: '1px solid #d1d5db',
                  backgroundColor: '#ffffff',
                  cursor: 'pointer',
                  fontSize: '0.875rem',
                }}
              >
                Đăng xuất
              </button>
            </div>
          ) : (
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <Link
                to="/login"
                style={{
                  textDecoration: 'none',
                  padding: '0.4rem 0.8rem',
                  borderRadius: '0.375rem',
                  color: '#2563eb',
                  fontWeight: '500',
                }}
              >
                Đăng nhập
              </Link>
              <Link
                to="/register"
                style={{
                  textDecoration: 'none',
                  padding: '0.4rem 0.8rem',
                  borderRadius: '0.375rem',
                  backgroundColor: '#2563eb',
                  color: 'white',
                  fontWeight: '500',
                }}
              >
                Đăng ký EO
              </Link>
            </div>
          )}
        </div>
      </header>

      <main style={{ flex: 1, padding: '2rem' }}>
        <Outlet />
      </main>

      <footer
        style={{
          padding: '1rem 2rem',
          borderTop: '1px solid #e5e7eb',
          textAlign: 'center',
          color: '#6b7280',
          fontSize: '0.875rem',
        }}
      >
        CANi Event Staffing Platform — Portal Dành Cho Ban Tổ Chức & Quản Trị
      </footer>
    </div>
  );
};

export default MainLayout;
