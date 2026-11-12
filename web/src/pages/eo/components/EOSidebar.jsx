import React, { useState } from 'react';

export default function EOSidebar({
  activeNav,
  activeEventSubTab = 'list',
  onNavClick,
  onEventSubTabClick,
  isCollapsed = false,
  onToggleCollapse,
}) {
  const [isEventsSubMenuOpen, setIsEventsSubMenuOpen] = useState(true);

  const navItems = [
    {
      key: 'overview',
      label: 'Tổng quan & Báo cáo',
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
        { key: 'recruitment', label: 'Tuyển dụng nhân sự' },
        { key: 'attendance', label: 'Điểm danh & Mã QR' },
        { key: 'cancelled', label: 'Đã hủy / Đã xóa' },
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
      key: 'subscription',
      label: 'Gói dịch vụ',
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
        </svg>
      ),
    },
    {
      key: 'profile',
      label: 'Hồ sơ tổ chức',
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
          <circle cx="12" cy="7" r="4"></circle>
        </svg>
      ),
    },
    {
      key: 'settings',
      label: 'Cài đặt',
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="12" cy="12" r="3"></circle>
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
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
          const isActive = activeNav === item.key || (item.key === 'events' && activeNav === 'recruitment');
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
                <span className="nav-svg-icon">{item.icon}</span>
                {!isCollapsed && (
                  <>
                    <span className="nav-label">{item.label}</span>
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

      {/* Promo Card ở đáy sidebar */}
      {!isCollapsed && (
        <div className="eo-sidebar-promo-card animate-fade-in-up">
          <div className="promo-calendar-3d-box">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
              <line x1="16" y1="2" x2="16" y2="6"></line>
              <line x1="8" y1="2" x2="8" y2="6"></line>
              <line x1="3" y1="10" x2="21" y2="10"></line>
            </svg>
            <div className="promo-calendar-dot" />
          </div>
          <h4 className="promo-card-title">Trợ lý Điều phối AI</h4>
          <p className="promo-card-desc">Tự động quét ứng viên phù hợp bán kính 15km</p>
          <button
            type="button"
            className="promo-card-btn"
            onClick={() => onNavClick('subscription')}
          >
            Xem gói dịch vụ
          </button>
        </div>
      )}
    </aside>
  );
}
