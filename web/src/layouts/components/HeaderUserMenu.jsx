import React, { useState, useRef, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { HelpIcon } from './HeaderIcons';
import { NotificationBell } from '../../features/notifications';
import { StaffMessageBell } from '../../features/support-tickets';

export const HeaderUserMenu = ({
  isAuthenticated,
  user,
  userAvatar,
  isAdmin,
  isCustomerCare,
  onUserBlockClick,
  onHelpClick,
  onLogout,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const menuRef = useRef(null);
  const isStaff = isAdmin || isCustomerCare;

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    };
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, []);

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
      <NotificationBell />
      {isStaff && <StaffMessageBell />}

      {!isStaff && (
        <button
          type="button"
          className="header-help-btn"
          title="Trung tâm trợ giúp"
          onClick={onHelpClick}
        >
          <HelpIcon />
        </button>
      )}

      {/* Cụm Profile kết hợp Menu Xổ Xuống (Dropdown) */}
      <div className="user-profile-menu-container" ref={menuRef} style={{ position: 'relative' }}>
        <button
          type="button"
          className={`user-header-compact ${isOpen ? 'active' : ''}`}
          onClick={() => setIsOpen((prev) => !prev)}
          title="Mở menu tài khoản"
          aria-expanded={isOpen}
          aria-haspopup="true"
          style={{
            background: isOpen ? '#f1f5f9' : 'transparent',
            border: 'none',
            display: 'flex',
            alignItems: 'center',
            gap: '0.65rem',
            padding: '0.25rem 0.6rem',
            borderRadius: '10px',
            cursor: 'pointer',
            textAlign: 'left',
          }}
        >
          <div className="user-avatar-badge">
            {userAvatar ? (
              <img
                src={userAvatar}
                alt="Avatar"
                style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover' }}
              />
            ) : (
              (user.fullName || user.username || (isAdmin ? 'Q' : isCustomerCare ? 'C' : 'N')).charAt(0).toUpperCase()
            )}
          </div>
          <div className="user-meta-info">
            <span className="user-meta-name">
              {user.fullName || user.username || (isAdmin ? 'Quản Trị Viên' : isCustomerCare ? 'Chuyên Viên CSKH' : 'Người Dùng')}
            </span>
            <span className="user-meta-email">
              {user.email || 'Chưa cập nhật email'}
            </span>
          </div>

          <svg
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.5"
            style={{
              color: '#64748b',
              transition: 'transform 0.2s ease',
              transform: isOpen ? 'rotate(180deg)' : 'rotate(0deg)',
              marginLeft: '2px',
            }}
          >
            <polyline points="6 9 12 15 18 9"></polyline>
          </svg>
        </button>

        {isOpen && (
          <div
            className="user-profile-dropdown-menu animate-fade-in-down"
            style={{
              position: 'absolute',
              top: 'calc(100% + 8px)',
              right: 0,
              minWidth: '220px',
              backgroundColor: '#ffffff',
              borderRadius: '14px',
              border: '1px solid #e2e8f0',
              boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.05)',
              padding: '6px',
              zIndex: 1000,
            }}
          >
            {/* Mục 1: Thông tin cá nhân */}
            <button
              type="button"
              className="user-dropdown-item"
              onClick={() => {
                setIsOpen(false);
                onUserBlockClick();
              }}
              style={{
                width: '100%',
                display: 'flex',
                alignItems: 'center',
                gap: '10px',
                padding: '0.65rem 0.85rem',
                border: 'none',
                background: 'transparent',
                borderRadius: '8px',
                color: '#1e293b',
                fontSize: '0.875rem',
                fontWeight: 600,
                cursor: 'pointer',
                textAlign: 'left',
                transition: 'background 0.15s ease',
              }}
              onMouseOver={(e) => (e.currentTarget.style.backgroundColor = '#f1f5f9')}
              onMouseOut={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                <circle cx="12" cy="7" r="4"></circle>
              </svg>
              <span>{isAdmin ? 'Bảng điều khiển quản trị' : isCustomerCare ? 'Cổng CSKH' : 'Thông tin cá nhân'}</span>
            </button>

            <div style={{ height: '1px', backgroundColor: '#e2e8f0', margin: '4px 6px' }} />

            {/* Mục 2: Đăng xuất */}
            <button
              type="button"
              className="user-dropdown-item item-logout"
              onClick={() => {
                setIsOpen(false);
                onLogout();
              }}
              style={{
                width: '100%',
                display: 'flex',
                alignItems: 'center',
                gap: '10px',
                padding: '0.65rem 0.85rem',
                border: 'none',
                background: 'transparent',
                borderRadius: '8px',
                color: '#dc2626',
                fontSize: '0.875rem',
                fontWeight: 600,
                cursor: 'pointer',
                textAlign: 'left',
                transition: 'background 0.15s ease',
              }}
              onMouseOver={(e) => (e.currentTarget.style.backgroundColor = '#fef2f2')}
              onMouseOut={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#dc2626" strokeWidth="2">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
                <polyline points="16 17 21 12 16 7"></polyline>
                <line x1="21" y1="12" x2="9" y2="12"></line>
              </svg>
              <span>Đăng xuất</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default HeaderUserMenu;
