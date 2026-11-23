import { useState } from 'react';
import { useDisputeChatAlerts } from '../../../features/disputes';

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
  const { unreadDisputes } = useDisputeChatAlerts();

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
        { key: 'cancelled', label: 'Sự kiện đã hủy' },
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
      badge: unreadDisputes > 0 ? unreadDisputes : null,
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
    {
      key: 'support',
      label: 'Trợ giúp & FAQ',
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="12" cy="12" r="10"></circle>
          <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"></path>
          <line x1="12" y1="17" x2="12.01" y2="17"></line>
        </svg>
      ),
    },
    {
      key: 'profile',
      label: 'Hồ sơ & Định danh',
      icon: (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
          <circle cx="12" cy="7" r="4"></circle>
        </svg>
      ),
    },
  ];

  return (
    <aside className={`eo-sidebar ${isCollapsed ? 'collapsed' : ''}`}>
      <nav className="eo-sidebar-nav" aria-label="Điều hướng ban tổ chức">
        <div className="eo-sidebar-toggle-header">
          {!isCollapsed && <span className="eo-sidebar-heading">Ban tổ chức</span>}
          <button
            type="button"
            className="eo-collapse-btn"
            onClick={onToggleCollapse}
            aria-label={isCollapsed ? 'Mở rộng thanh điều hướng' : 'Thu gọn thanh điều hướng'}
            aria-expanded={!isCollapsed}
          >
            <span aria-hidden="true">{isCollapsed ? '»' : '«'}</span>
          </button>
        </div>

        {navItems.map((item) => {
          const isActive = activeNav === item.key;
          const showSubmenu = !isCollapsed && item.hasSubMenu && isActive && isEventsSubMenuOpen;
          return (
            <div className="eo-nav-group" key={item.key}>
              <div className="eo-nav-row">
                <button
                  type="button"
                  className={`eo-nav-item ${isActive ? 'active' : ''}`}
                  onClick={() => {
                    onNavClick(item.key);
                    if (item.hasSubMenu) setIsEventsSubMenuOpen(true);
                  }}
                  aria-current={isActive ? 'page' : undefined}
                  aria-label={item.badge > 0 ? `${item.label}, ${item.badge} chưa đọc` : item.label}
                  title={isCollapsed ? item.label : undefined}
                >
                  <span className="nav-svg-icon" aria-hidden="true">{item.icon}</span>
                  <span className="nav-label">{item.label}</span>
                  {item.badge > 0 && <span className="eo-nav-badge" aria-hidden="true">{item.badge}</span>}
                </button>

                {!isCollapsed && item.hasSubMenu && isActive && (
                  <button
                    type="button"
                    className="eo-nav-submenu-toggle"
                    onClick={() => setIsEventsSubMenuOpen((open) => !open)}
                    aria-expanded={showSubmenu}
                    aria-controls="eo-event-nav"
                    aria-label={showSubmenu ? 'Thu gọn mục sự kiện' : 'Mở rộng mục sự kiện'}
                  >
                    <span aria-hidden="true">{showSubmenu ? '−' : '+'}</span>
                  </button>
                )}
              </div>

              {!isCollapsed && item.hasSubMenu && isActive && (
                <div className="eo-nav-submenu" id="eo-event-nav" hidden={!showSubmenu}>
                  {item.subItems.map((sub) => (
                    <button
                      key={sub.key}
                      type="button"
                      aria-current={activeEventSubTab === sub.key ? 'page' : undefined}
                      onClick={() => onEventSubTabClick(sub.key)}
                    >
                      {sub.label}
                    </button>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </nav>
    </aside>
  );
}
