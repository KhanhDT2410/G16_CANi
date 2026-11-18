import { useState, useEffect } from 'react';
import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth';
import { ToastContainer } from '../components/common/Toast';
import CaniLogo from '../components/common/CaniLogo';
import { SupportChatWidget } from '../features/support-tickets';
import { SearchIcon } from './components/HeaderIcons';
import HeaderUserMenu from './components/HeaderUserMenu';
import './MainLayout.css';

const MainLayout = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, isAuthenticated, logout } = useAuth();

  const isAdmin = location.pathname.startsWith('/admin') || user?.role === 'ADMIN';
  const isCustomerCare = location.pathname.startsWith('/customer-care') || user?.role === 'CUSTOMER_CARE';
  const isStaff = isAdmin || isCustomerCare;

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

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const handleUserBlockClick = () => {
    if (isAdmin) {
      navigate('/admin');
      window.dispatchEvent(new CustomEvent('cani_admin_navigate_home'));
    } else if (isCustomerCare) {
      navigate('/customer-care');
    } else {
      navigate('/eo?tab=profile');
      window.dispatchEvent(new CustomEvent('cani_switch_tab', { detail: { tab: 'profile' } }));
    }
  };

  return (
    <div className="main-layout">
      <ToastContainer />
      <header className="main-header">
        <div className="main-header-left">
          <Link
            to={isAdmin ? '/admin' : isCustomerCare ? '/customer-care' : '/eo'}
            className="main-logo-group"
            title="CANi EVENT PLATFORM - Cần Ai, Có CANi"
            onClick={() => {
              if (isAdmin) {
                window.dispatchEvent(new CustomEvent('cani_admin_navigate_home'));
              }
            }}
          >
            <CaniLogo variant="horizontal" height={36} />
          </Link>
        </div>

        {!isStaff && (
          <div className="main-header-center">
            <div className="header-search-box">
              <SearchIcon />
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
                  className="header-search-clear-btn"
                  title="Xóa tìm kiếm"
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
                >
                  ×
                </button>
              )}
            </div>
          </div>
        )}

        <div className="main-header-right">
          <HeaderUserMenu
            isAuthenticated={isAuthenticated}
            user={user}
            userAvatar={userAvatar}
            isAdmin={isAdmin}
            isCustomerCare={isCustomerCare}
            onUserBlockClick={handleUserBlockClick}
            onLogout={handleLogout}
          />
        </div>
      </header>

      <main className="main-content">
        <Outlet />
      </main>

      <footer className="main-footer">
        <div className="main-footer-inner">
          <span>CANi Event Staffing Platform — Portal Dành Cho Ban Tổ Chức & Quản Trị</span>
        </div>
      </footer>

      {/* Bong bóng chat hỗ trợ trực tuyến toàn sàn - chỉ hiển thị cho Người dùng / EO, ẩn trên Admin và CSKH */}
      {!isStaff && <SupportChatWidget />}
    </div>
  );
};

export default MainLayout;
