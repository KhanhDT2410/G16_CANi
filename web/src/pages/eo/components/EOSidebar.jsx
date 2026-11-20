import React, { useState } from 'react';

export default function EOSidebar({
  activeNav,
  activeEventSubTab = 'list',
  onNavClick,
  onEventSubTabClick,
  isCollapsed = false,
  onToggleCollapse,
  unreadMessagesCount = 0,
}) {
  const [isEventsSubMenuOpen, setIsEventsSubMenuOpen] = useState(true);

  const navItems = [
    {
      key: 'overview',
      label: 'Tổng quan',
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <rect x="3" y="3" width="7" height="7" rx="1"></rect>
          <rect x="14" y="3" width="7" height="7" rx="1"></rect>
          <rect x="14" y="14" width="7" height="7" rx="1"></rect>
          <rect x="3" y="14" width="7" height="7" rx="1"></rect>
        </svg>
      ),
    },
    {
      key: 'events',
      label: 'Quản lý sự kiện',
      hasSubMenu: true,
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
          <line x1="16" y1="2" x2="16" y2="6"></line>
          <line x1="8" y1="2" x2="8" y2="6"></line>
          <line x1="3" y1="10" x2="21" y2="10"></line>
        </svg>
      ),
      subItems: [
        { key: 'list', label: 'Danh sách sự kiện' },
        { key: 'detail', label: 'Chi tiết sự kiện' },
        { key: 'cancelled', label: 'Sự kiện đã xóa' },
      ],
    },
    {
      key: 'payments',
      label: 'Thanh toán & lương',
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <rect x="1" y="4" width="22" height="16" rx="2" ry="2"></rect>
          <line x1="1" y1="10" x2="23" y2="10"></line>
        </svg>
      ),
    },
    {
      key: 'disputes',
      label: 'Tranh chấp & khiếu nại',
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
          <line x1="12" y1="8" x2="12" y2="12"></line>
          <line x1="12" y1="16" x2="12.01" y2="16"></line>
        </svg>
      ),
    },
    {
      key: 'subscription',
      label: 'Gói dịch vụ',
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
        </svg>
      ),
    },
    {
      key: 'messages',
      label: 'Tin nhắn nhân sự',
      badge: unreadMessagesCount,
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
        </svg>
      ),
    },
    {
      key: 'my-team',
      label: 'Nhân sự quen thuộc',
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
          <circle cx="9" cy="7" r="4"></circle>
          <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
          <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
        </svg>
      ),
    },
  ];

  return (
    <aside className={`eo-sidebar ${isCollapsed ? 'collapsed' : ''}`}>
      <nav className="eo-sidebar-nav">
        {/* Nút thu gọn / mở rộng menu */}
        <div className="eo-sidebar-toggle-header">
          {!isCollapsed && <span className="eo-sidebar-heading">Menu Điều Hướng</span>}
          <button
            type="button"
            className="eo-collapse-btn"
            onClick={onToggleCollapse}
            title={isCollapsed ? 'Mở rộng thanh menu' : 'Thu gọn thanh menu'}
            aria-label={isCollapsed ? 'Mở rộng thanh menu' : 'Thu gọn thanh menu'}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              {isCollapsed ? (
                <path d="M13 17l5-5-5-5M6 17l5-5-5-5" />
              ) : (
                <path d="M11 17l-5-5 5-5M18 17l-5-5 5-5" />
              )}
            </svg>
          </button>
        </div>

        {navItems.map((item) => {
          const isActive = activeNav === item.key;
          return (
            <React.Fragment key={item.key}>
              <button
                type="button"
                className={`eo-nav-item ${isActive ? 'active' : ''}`}
                onClick={() => {
                  onNavClick(item.key);
                  if (item.hasSubMenu) {
                    setIsEventsSubMenuOpen(true);
                  }
                }}
                title={isCollapsed ? item.label : undefined}
              >
                <span className="nav-svg-icon" style={{ position: 'relative' }}>
                  {item.icon}
                  {isCollapsed && item.badge != null && item.badge > 0 && (
                    <span style={{
                      position: 'absolute',
                      top: -2,
                      right: -2,
                      width: 8,
                      height: 8,
                      borderRadius: '50%',
                      background: '#ef4444',
                    }} />
                  )}
                </span>
                {!isCollapsed && (
                  <>
                    <span className="nav-label">{item.label}</span>
                    {item.badge != null && item.badge > 0 && (
                      <span style={{
                        background: '#ef4444',
                        color: '#ffffff',
                        fontSize: '0.68rem',
                        fontWeight: 700,
                        padding: '1px 6px',
                        borderRadius: '9999px',
                        marginLeft: 'auto',
                      }}>
                        +{item.badge}
                      </span>
                    )}
                    {item.hasSubMenu && (
                      <span
                        className="nav-chevron-right"
                        onClick={(e) => {
                          e.stopPropagation();
                          setIsEventsSubMenuOpen((prev) => !prev);
                        }}
                        title={isEventsSubMenuOpen ? 'Thu gọn menu con' : 'Mở rộng menu con'}
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          width: 24,
                          height: 24,
                          borderRadius: 6,
                          transition: 'transform 0.2s ease',
                          transform: isEventsSubMenuOpen ? 'rotate(90deg)' : 'rotate(0deg)',
                          cursor: 'pointer',
                        }}
                      >
                        ›
                      </span>
                    )}
                  </>
                )}
              </button>

              {/* Sub-menu mở rộng của Quản lý sự kiện */}
              {!isCollapsed && item.hasSubMenu && isEventsSubMenuOpen && (
                <div
                  className="eo-nav-submenu"
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '2px',
                    paddingLeft: '2.1rem',
                    marginTop: '2px',
                    marginBottom: '6px',
                  }}
                >
                  {item.subItems.map((sub) => {
                    const isSubActive = isActive && activeEventSubTab === sub.key;
                    return (
                      <button
                        key={sub.key}
                        type="button"
                        onClick={() => {
                          onNavClick('events');
                          onEventSubTabClick?.(sub.key);
                        }}
                        style={{
                          textAlign: 'left',
                          background: isSubActive ? '#eff6ff' : 'transparent',
                          color: isSubActive ? '#2563eb' : '#64748b',
                          fontWeight: isSubActive ? 700 : 500,
                          border: 'none',
                          borderLeft: isSubActive ? '2.5px solid #2563eb' : '2.5px solid #e2e8f0',
                          padding: '0.45rem 0.75rem',
                          fontSize: '0.82rem',
                          borderRadius: '0 6px 6px 0',
                          cursor: 'pointer',
                          transition: 'all 0.15s ease',
                        }}
                      >
                        {sub.label}
                      </button>
                    );
                  })}
                </div>
              )}
            </React.Fragment>
          );
        })}
      </nav>

      {/* Promo Card: Nâng Cấp Gói Dịch Vụ Chuẩn Mockup Ảnh 2 */}
      {!isCollapsed && (
        <div
          className="eo-sidebar-promo-card animate-fade-in-up"
          style={{
            margin: '1rem 0.85rem 0.75rem',
            padding: '1.25rem 1rem 1rem',
            background: '#ffffff',
            borderRadius: '18px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 4px 14px rgba(15, 23, 42, 0.05)',
            textAlign: 'center',
            position: 'relative',
          }}
        >
          {/* Icon Crown & Lotus Badge */}
          <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '0.65rem' }}>
            <img
              src="/assets/eo-crown-badge.png"
              alt="VIP Subscription"
              style={{ width: 56, height: 'auto', display: 'block' }}
            />
          </div>

          <h4
            style={{
              margin: '0 0 0.35rem',
              fontSize: '0.95rem',
              fontWeight: 800,
              color: '#0f172a',
            }}
          >
            Nâng cấp gói dịch vụ
          </h4>

          <p
            style={{
              margin: '0 0 0.85rem',
              fontSize: '0.78rem',
              color: '#64748b',
              lineHeight: 1.45,
            }}
          >
            Mở khóa nhiều tính năng nâng cao và tối ưu vận hành sự kiện.
          </p>

          <button
            type="button"
            onClick={() => onNavClick('subscription')}
            style={{
              width: '100%',
              backgroundColor: '#2563eb',
              color: '#ffffff',
              border: 'none',
              borderRadius: '10px',
              padding: '0.6rem 0.85rem',
              fontSize: '0.82rem',
              fontWeight: 700,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '6px',
              boxShadow: '0 2px 8px rgba(37, 99, 235, 0.25)',
              transition: 'all 0.15s ease',
            }}
            onMouseOver={(e) => (e.currentTarget.style.backgroundColor = '#1d4ed8')}
            onMouseOut={(e) => (e.currentTarget.style.backgroundColor = '#2563eb')}
          >
            Xem các gói dịch vụ &rarr;
          </button>
        </div>
      )}
    </aside>
  );
}
