import { useState, useEffect } from 'react';
import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth';
import { ToastContainer } from '../components/common/Toast';
import CaniLogo from '../components/common/CaniLogo';
import './MainLayout.css';

const MainLayout = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, isAuthenticated, logout } = useAuth();

  const [userAvatar, setUserAvatar] = useState(() => {
    try {
      return localStorage.getItem('cani_eo_avatar') || user?.avatarUrl || null;
    } catch {
      return user?.avatarUrl || null;
    }
  });

  const [headerSearch, setHeaderSearch] = useState(() => {
    try {
      return new URLSearchParams(location.search).get('q') || '';
    } catch {
      return '';
    }
  });

  const handleSearchChange = (e) => {
    const val = e.target.value;
    setHeaderSearch(val);
    window.dispatchEvent(new CustomEvent('cani_global_search', { detail: { query: val } }));
  };

  const handleSearchKeyDown = (e) => {
    if (e.key === 'Enter') {
      window.dispatchEvent(new CustomEvent('cani_global_search', { detail: { query: headerSearch } }));
      if (!location.pathname.startsWith('/eo')) {
        navigate(`/eo?q=${encodeURIComponent(headerSearch)}`);
      }
    }
  };

  const handleClearSearch = () => {
    setHeaderSearch('');
    window.dispatchEvent(new CustomEvent('cani_global_search', { detail: { query: '' } }));
  };

  useEffect(() => {
    const handleAvatarUpdate = (e) => {
      const newAvatar = e.detail?.avatarUrl || localStorage.getItem('cani_eo_avatar');
      setUserAvatar(newAvatar);
    };
    window.addEventListener('cani_avatar_updated', handleAvatarUpdate);
    return () => window.removeEventListener('cani_avatar_updated', handleAvatarUpdate);
  }, []);

  // Phân quyền thanh điều hướng chuẩn đặc tả SEP490:
  // - Web Portal: Dành riêng cho Event Organizer (EO), Admin, Customer Care.
  // - Worker: Hoạt động độc lập 100% trên Mobile App (CANi Partner App).
  const getNavItems = () => {
    const commonItems = [
      { path: '/mobile-preview', label: 'App Mobile Worker' },
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
          { path: '/support', label: 'Trung Tâm Hỗ Trợ' },
          ...commonItems,
        ];
    }
  };

  const navItems = getNavItems();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const handleOpenProfile = () => {
    navigate('/eo?tab=profile');
    window.dispatchEvent(new CustomEvent('cani_switch_tab', { detail: { tab: 'profile' } }));
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
            <svg
              className="header-search-svg"
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <circle cx="11" cy="11" r="8"></circle>
              <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
            </svg>
            <input
              type="text"
              placeholder="Tìm kiếm sự kiện, nhân sự, địa điểm..."
              className="header-search-input"
              value={headerSearch}
              onChange={handleSearchChange}
              onKeyDown={handleSearchKeyDown}
            />
            {headerSearch && (
              <button
                type="button"
                onClick={handleClearSearch}
                style={{
                  background: 'transparent',
                  border: 'none',
                  fontSize: '1rem',
                  color: '#94a3b8',
                  cursor: 'pointer',
                  padding: '0 4px',
                  display: 'flex',
                  alignItems: 'center',
                }}
                title="Xóa tìm kiếm"
              >
                ×
              </button>
            )}
          </div>
        </div>

        <div className="main-header-right">
          {isAuthenticated && user ? (
            <div className="user-header-group">
              <button type="button" className="header-notif-btn" title="Thông báo mới">
                <svg
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
                  <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
                </svg>
                <span className="notif-badge-dot"></span>
              </button>

              <div className="user-header-compact">
                <button
                  type="button"
                  className="user-avatar-btn"
                  onClick={handleOpenProfile}
                  title={`Xem hồ sơ: ${user.fullName || user.username || 'EO'}`}
                >
                  {userAvatar ? (
                    <img src={userAvatar} alt="Avatar" className="user-avatar-img" />
                  ) : (
                    <div className="user-avatar-badge">
                      {(user.fullName || user.username || 'D').charAt(0).toUpperCase()}
                    </div>
                  )}
                </button>

                <button
                  type="button"
                  onClick={handleLogout}
                  className="btn-logout-icon"
                  title="Đăng xuất"
                >
                  <svg
                    width="18"
                    height="18"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
                    <polyline points="16 17 21 12 16 7"></polyline>
                    <line x1="21" y1="12" x2="9" y2="12"></line>
                  </svg>
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
