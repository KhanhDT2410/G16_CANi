import { useState, useMemo } from 'react';

/**
 * Bảng Hộp Thư Tin Nhắn Trực Tiếp (Live Chat Inbox) cho CSKH & Admin.
 * Hiển thị toàn bộ người dùng Role EVENT_ORGANIZER & WORKER kèm nút [Nhắn Tin Trực Tiếp].
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const LiveChatInboxTable = ({ conversations = [], loading, onOpenChat }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');

  const formatTimeAgo = (isoStr) => {
    if (!isoStr) return '—';
    try {
      const diffMins = Math.floor((Date.now() - new Date(isoStr).getTime()) / 60000);
      if (diffMins < 1) return 'Vừa xong';
      if (diffMins < 60) return `${diffMins} phút trước`;
      const diffHours = Math.floor(diffMins / 60);
      if (diffHours < 24) return `${diffHours} giờ trước`;
      return new Date(isoStr).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
    } catch { return '—'; }
  };

  const counts = useMemo(() => ({
    all: conversations.length,
    eo: conversations.filter((c) => c.userRole === 'EVENT_ORGANIZER').length,
    worker: conversations.filter((c) => c.userRole === 'WORKER').length,
    unread: conversations.filter((c) => (c.unreadStaffCount || 0) > 0).length,
  }), [conversations]);

  const filteredList = useMemo(() => {
    return conversations.filter((c) => {
      if (roleFilter === 'EVENT_ORGANIZER' && c.userRole !== 'EVENT_ORGANIZER') return false;
      if (roleFilter === 'WORKER' && c.userRole !== 'WORKER') return false;
      if (roleFilter === 'UNREAD' && (c.unreadStaffCount || 0) <= 0) return false;
      if (!searchTerm.trim()) return true;
      const term = searchTerm.toLowerCase();
      return (
        c.userName?.toLowerCase().includes(term) ||
        c.userPhone?.toLowerCase().includes(term) ||
        c.userEmail?.toLowerCase().includes(term)
      );
    });
  }, [conversations, roleFilter, searchTerm]);

  return (
    <div className="live-chat-inbox-wrapper">
      <div className="tickets-toolbar" style={{ borderBottom: 'none', paddingBottom: 0, flexWrap: 'wrap', gap: '0.75rem' }}>
        <div style={{ display: 'flex', gap: '0.45rem', flexWrap: 'wrap' }}>
          {[
            { id: 'ALL', label: 'Tất Cả', count: counts.all },
            { id: 'EVENT_ORGANIZER', label: 'Nhà Tổ Chức', count: counts.eo },
            { id: 'WORKER', label: 'Nhân Viên', count: counts.worker },
            { id: 'UNREAD', label: 'Chưa Đọc', count: counts.unread },
          ].map((tab) => (
            <button
              key={tab.id}
              type="button"
              className={`ticket-filter-btn ${roleFilter === tab.id ? 'active' : ''}`}
              onClick={() => setRoleFilter(tab.id)}
            >
              <span>{tab.label}</span>
              <span className="ticket-filter-count">{tab.count}</span>
            </button>
          ))}
        </div>

        <div className="tickets-search-box">
          <input
            type="text"
            className="tickets-search-input"
            placeholder="Tìm theo tên, SĐT hoặc email..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
          {searchTerm && (
            <button type="button" className="tickets-search-clear" onClick={() => setSearchTerm('')}>✕</button>
          )}
        </div>
      </div>

      {loading && filteredList.length === 0 ? (
        <div className="ticket-empty-state">Đang tải danh sách người dùng...</div>
      ) : filteredList.length === 0 ? (
        <div className="ticket-empty-state">
          {searchTerm ? 'Không tìm thấy người dùng nào phù hợp.' : 'Chưa có người dùng nào.'}
        </div>
      ) : (
        <div className="tickets-table-wrapper" style={{ marginTop: '1rem' }}>
          <table className="tickets-table">
            <thead>
              <tr>
                <th style={{ minWidth: '180px' }}>Khách Hàng (SĐT / Email)</th>
                <th style={{ width: '130px' }}>Vai Trò</th>
                <th style={{ minWidth: '240px' }}>Tin Nhắn Gần Nhất</th>
                <th style={{ width: '150px' }}>Thời Gian & Trạng Thái</th>
                <th style={{ textAlign: 'left', width: '140px' }}>Thao Tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredList.map((conv) => {
                const hasUnread = (conv.unreadStaffCount || 0) > 0;
                const isEO = conv.userRole === 'EVENT_ORGANIZER';
                const hasChatted = Boolean(conv.lastMessageAt);

                return (
                  <tr key={conv.userId || conv.id} className={hasUnread ? 'chat-row-unread' : ''}>
                    <td>
                      <div className="ticket-customer-cell">
                        <div className="ticket-customer-name" style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                          <span>{conv.userName || 'Người dùng CANi'}</span>
                          {hasUnread && <span className="chat-unread-dot" />}
                        </div>
                        <div className="ticket-customer-contact">
                          <span>SĐT: {conv.userPhone || '—'}</span>
                          <span className="ticket-customer-sep">·</span>
                          <span>{conv.userEmail || '—'}</span>
                        </div>
                      </div>
                    </td>

                    <td>
                      <span
                        className="ticket-badge ticket-badge-medium"
                        style={{
                          background: isEO ? '#eff6ff' : '#f0fdf4',
                          color: isEO ? '#1e40af' : '#166534',
                          borderColor: isEO ? '#bfdbfe' : '#bbf7d0',
                          fontWeight: 600,
                        }}
                      >
                        {isEO ? 'Nhà tổ chức' : 'Nhân viên'}
                      </span>
                    </td>

                    <td style={{ maxWidth: '320px' }}>
                      <div
                        className="ticket-subject-cell"
                        style={{
                          fontWeight: hasUnread ? 700 : 500,
                          color: hasUnread ? '#0f172a' : hasChatted ? '#475569' : '#94a3b8',
                          fontStyle: hasChatted ? 'normal' : 'italic',
                        }}
                      >
                        {conv.lastMessagePreview || 'Chưa có tin nhắn'}
                      </div>
                    </td>

                    <td>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem', whiteSpace: 'nowrap' }}>
                        {hasChatted ? (
                          <>
                            <span style={{ fontSize: '0.84rem', color: '#64748b' }}>{formatTimeAgo(conv.lastMessageAt)}</span>
                            {hasUnread ? (
                              <span className="ticket-badge-sla-warning" style={{ color: '#1d4ed8', background: '#eff6ff', borderColor: '#bfdbfe' }}>
                                {conv.unreadStaffCount} tin mới
                              </span>
                            ) : (
                              <span style={{ fontSize: '0.74rem', color: '#94a3b8' }}>Đã phản hồi</span>
                            )}
                          </>
                        ) : (
                          <span style={{ fontSize: '0.78rem', color: '#94a3b8' }}>Chưa nhắn tin</span>
                        )}
                      </div>
                    </td>

                    <td style={{ textAlign: 'left' }}>
                      <button
                        type="button"
                        className="ticket-btn ticket-btn-chat ticket-btn-sm"
                        onClick={() => onOpenChat(conv)}
                      >
                        Nhắn Tin Trực Tiếp
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default LiveChatInboxTable;
