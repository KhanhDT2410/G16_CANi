import { useState } from 'react';
import { CollapseToggleIcon, ChevronDownIcon } from './AdminSidebarIcons';
import { ADMIN_NAV_GROUPS, TICKET_SUB_ITEMS } from './adminSidebar.constants';
import { useStaffChatAlerts } from '../../../features/support-tickets';
import { usePendingAccountAppeals } from '../../../features/account-appeals';
import { useDisputeChatAlerts } from '../../../features/disputes';

export { ADMIN_NAV_GROUPS, TICKET_SUB_ITEMS };

/**
 * Sidebar quản trị đa chức năng với từng màn hình tham số độc lập và menu phiếu hỗ trợ (ADM-01).
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const AdminSidebar = ({
  activeTab,
  onSelectTab,
  activeTicketFilter = 'ALL',
  onSelectTicketFilter,
  collapsed,
  onToggleCollapse,
}) => {
  const [ticketsExpanded, setTicketsExpanded] = useState(true);
  const { totalUnread } = useStaffChatAlerts();
  const { pendingCount } = usePendingAccountAppeals();
  const { unreadDisputes } = useDisputeChatAlerts();

  const renderExpandableItem = (item, isExpanded, onToggleExpand, subItems, activeKey, onSubClick) => {
    const isActive = activeTab === item.id;
    return (
      <div key={item.id} className="sidebar-expandable-item-wrapper">
        <div
          className={`sidebar-nav-item ${isActive ? 'active' : ''}`}
          onClick={() => {
            onSelectTab(item.id);
            if (!collapsed) onToggleExpand();
          }}
        >
          <span className="sidebar-item-icon" style={{ position: 'relative' }}>
            {item.icon}
            {item.id === 'tickets' && totalUnread > 0 && (
              <span className="sidebar-item-dot-badge" title={`${totalUnread} tin nhắn khách mới`} />
            )}
          </span>
          {!collapsed && (
            <>
              <span className="sidebar-item-label">{item.label}</span>
              <button
                type="button"
                className="sidebar-arrow-btn"
                onClick={(e) => {
                  e.stopPropagation();
                  onToggleExpand();
                }}
                title={isExpanded ? 'Thu gọn danh mục con' : 'Mở rộng danh mục con'}
                aria-label={`Mũi tên xổ xuống ${item.label}`}
              >
                <ChevronDownIcon expanded={isExpanded} />
              </button>
            </>
          )}
          {isActive && <span className="sidebar-active-indicator" />}
        </div>

        {!collapsed && isExpanded && (
          <div className="sidebar-submenu animate-fade-in">
            {subItems.map((sub) => {
              const isSubActive = isActive && activeKey === sub.key;
              const isChatSub = sub.key === 'CHAT';
              return (
                <button
                  key={sub.key}
                  type="button"
                  className={`sidebar-submenu-item ${isSubActive ? 'active' : ''}`}
                  onClick={() => onSubClick(sub.key)}
                  title={sub.label}
                >
                  <span className="submenu-bullet" />
                  <span className="submenu-label">{sub.label}</span>
                  {isChatSub && totalUnread > 0 && (
                    <span className="sidebar-sub-badge" title={`${totalUnread} tin nhắn mới`}>
                      {totalUnread > 99 ? '99+' : totalUnread}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        )}
      </div>
    );
  };

  return (
    <aside className={`admin-sidebar ${collapsed ? 'collapsed' : ''}`} aria-label="Menu chức năng quản trị">
      <div className="admin-sidebar-header">
        {!collapsed && (
          <div className="admin-sidebar-brand">
            <span className="sidebar-brand-title">Cổng Quản Trị</span>
          </div>
        )}
        <button
          type="button"
          className="sidebar-collapse-btn"
          onClick={onToggleCollapse}
          title={collapsed ? 'Mở rộng thanh menu' : 'Thu gọn thanh menu'}
          aria-label={collapsed ? 'Mở rộng sidebar' : 'Thu gọn sidebar'}
        >
          <CollapseToggleIcon collapsed={collapsed} />
        </button>
      </div>

      <nav className="admin-sidebar-nav">
        {ADMIN_NAV_GROUPS.map((group) => (
          <div key={group.groupKey} className="sidebar-nav-group">
            {!collapsed ? (
              <div className="sidebar-group-title">{group.groupLabel}</div>
            ) : (
              <div className="sidebar-group-divider" />
            )}

            <div className="sidebar-group-items">
              {group.items.map((item) => {
                if (item.id === 'tickets') {
                  return renderExpandableItem(
                    item,
                    ticketsExpanded,
                    () => setTicketsExpanded((p) => !p),
                    TICKET_SUB_ITEMS,
                    activeTicketFilter,
                    (filter) => {
                      onSelectTab('tickets');
                      if (onSelectTicketFilter) onSelectTicketFilter(filter);
                    }
                  );
                }

                const isActive = activeTab === item.id;
                const isAccountAppeals = item.id === 'account_appeals';
                const isDisputes = item.id === 'disputes';
                const showAppealBadge = isAccountAppeals && pendingCount > 0;
                const showDisputeBadge = isDisputes && unreadDisputes > 0;
                const hasBadge = showAppealBadge || showDisputeBadge;
                const badgeCount = showAppealBadge ? pendingCount : unreadDisputes;

                return (
                  <button
                    key={item.id}
                    type="button"
                    className={`sidebar-nav-item ${isActive ? 'active' : ''}`}
                    onClick={() => onSelectTab(item.id)}
                    title={
                      showAppealBadge
                        ? `${item.label} (${pendingCount} đơn chờ duyệt)`
                        : showDisputeBadge
                        ? `${item.label} (${unreadDisputes} vụ việc có tin mới)`
                        : item.label
                    }
                  >
                    <span className="sidebar-item-icon" style={{ position: 'relative' }}>
                      {item.icon}
                      {hasBadge && (
                        <span
                          className="sidebar-item-dot-badge"
                          title={
                            showAppealBadge
                              ? `${pendingCount} đơn giải trình chờ duyệt`
                              : `${unreadDisputes} vụ việc có tin nhắn mới`
                          }
                        />
                      )}
                    </span>
                    {!collapsed && (
                      <>
                        <span className="sidebar-item-label">{item.label}</span>
                        {hasBadge && (
                          <span
                            className="sidebar-sub-badge"
                            title={
                              showAppealBadge
                                ? `${pendingCount} đơn giải trình đang chờ Admin xét duyệt`
                                : `${unreadDisputes} vụ việc có tin nhắn mới`
                            }
                          >
                            {badgeCount > 99 ? '99+' : badgeCount}
                          </span>
                        )}
                      </>
                    )}
                    {isActive && <span className="sidebar-active-indicator" />}
                  </button>
                );
              })}
            </div>
          </div>
        ))}
      </nav>
    </aside>
  );
};

export default AdminSidebar;
