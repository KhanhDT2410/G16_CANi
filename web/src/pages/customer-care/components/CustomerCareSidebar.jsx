import { useState } from 'react';
import {
  TicketIcon,
  FaqIcon,
  DisputeIcon,
  AppealIcon,
  NoShowIcon,
  CollapseToggleIcon,
  ChevronDownIcon,
} from '../../admin/components/AdminSidebarIcons';
import { TICKET_SUB_ITEMS } from '../../admin/components/adminSidebar.constants';

export const CC_NAV_GROUPS = [
  {
    groupKey: 'support',
    groupLabel: 'Tiếp nhận & Hỗ trợ',
    items: [
      { id: 'tickets', label: 'Phiếu Hỗ Trợ Khách Hàng', icon: <TicketIcon />, hasChildren: true },
      { id: 'faqs', label: 'Câu Hỏi Thường Gặp', icon: <FaqIcon /> },
    ],
  },
  {
    groupKey: 'disputes_appeals',
    groupLabel: 'Xử lý khiếu nại & Sự vụ',
    items: [
      { id: 'disputes', label: 'Tranh Chấp & Khiếu Nại', icon: <DisputeIcon /> },
      { id: 'appeals', label: 'Kháng Cáo Điểm Uy Tín', icon: <AppealIcon /> },
      { id: 'noshows', label: 'Xử Lý Vi Phạm Vắng Mặt', icon: <NoShowIcon /> },
    ],
  },
];

/**
 * Sidebar dành cho Cổng Chăm sóc khách hàng (CSKH).
 * Đồng bộ menu con xổ xuống cho Phiếu hỗ trợ khách hàng.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const CustomerCareSidebar = ({
  activeTab,
  onSelectTab,
  activeTicketFilter = 'ALL',
  onSelectTicketFilter,
  collapsed,
  onToggleCollapse,
}) => {
  const [ticketsExpanded, setTicketsExpanded] = useState(true);

  return (
    <aside className={`cc-sidebar ${collapsed ? 'collapsed' : ''}`} aria-label="Menu chức năng chăm sóc khách hàng">
      <div className="cc-sidebar-header">
        {!collapsed && (
          <div className="cc-sidebar-brand">
            <span className="cc-brand-badge">CSKH</span>
            <span className="cc-brand-title">Cổng Chăm Sóc Khách Hàng</span>
          </div>
        )}
        <button
          type="button"
          className="cc-collapse-btn"
          onClick={onToggleCollapse}
          title={collapsed ? 'Mở rộng thanh menu' : 'Thu gọn thanh menu'}
          aria-label={collapsed ? 'Mở rộng sidebar' : 'Thu gọn sidebar'}
        >
          <CollapseToggleIcon collapsed={collapsed} />
        </button>
      </div>

      <nav className="cc-sidebar-nav">
        {CC_NAV_GROUPS.map((group) => (
          <div key={group.groupKey} className="cc-nav-group">
            {!collapsed ? (
              <div className="cc-group-title">{group.groupLabel}</div>
            ) : (
              <div className="cc-group-divider" />
            )}

            <div className="cc-group-items">
              {group.items.map((item) => {
                const isActive = activeTab === item.id;

                if (item.id === 'tickets') {
                  return (
                    <div key={item.id} className="cc-expandable-item-wrapper">
                      <div
                        className={`cc-nav-item ${isActive ? 'active' : ''}`}
                        onClick={() => {
                          onSelectTab('tickets');
                          if (!collapsed) setTicketsExpanded((p) => !p);
                        }}
                      >
                        <span className="cc-item-icon">{item.icon}</span>
                        {!collapsed && (
                          <>
                            <span className="cc-item-label">{item.label}</span>
                            <button
                              type="button"
                              className="cc-arrow-btn"
                              onClick={(e) => {
                                e.stopPropagation();
                                setTicketsExpanded((p) => !p);
                              }}
                              title={ticketsExpanded ? 'Thu gọn danh mục con' : 'Mở rộng danh mục con'}
                              aria-label="Mũi tên xổ xuống phiếu hỗ trợ"
                            >
                              <ChevronDownIcon expanded={ticketsExpanded} />
                            </button>
                          </>
                        )}
                        {isActive && <span className="cc-active-indicator" />}
                      </div>

                      {!collapsed && ticketsExpanded && (
                        <div className="cc-submenu animate-fade-in">
                          {TICKET_SUB_ITEMS.map((sub) => {
                            const isSubActive = isActive && activeTicketFilter === sub.key;
                            return (
                              <button
                                key={sub.key}
                                type="button"
                                className={`cc-submenu-item ${isSubActive ? 'active' : ''}`}
                                onClick={() => {
                                  onSelectTab('tickets');
                                  if (onSelectTicketFilter) onSelectTicketFilter(sub.key);
                                }}
                                title={sub.label}
                              >
                                <span className="cc-submenu-bullet" />
                                <span className="cc-submenu-label">{sub.label}</span>
                              </button>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  );
                }

                return (
                  <button
                    key={item.id}
                    type="button"
                    className={`cc-nav-item ${isActive ? 'active' : ''}`}
                    onClick={() => onSelectTab(item.id)}
                    title={item.label}
                  >
                    <span className="cc-item-icon">{item.icon}</span>
                    {!collapsed && <span className="cc-item-label">{item.label}</span>}
                    {isActive && <span className="cc-active-indicator" />}
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

export default CustomerCareSidebar;
