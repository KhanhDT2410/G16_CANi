import { useState, useMemo } from 'react';

/**
 * Bảng Hộp Thư Tin Nhắn Trực Tiếp (Live Chat Inbox) cho CSKH & Admin.
 * Hiển thị toàn bộ người dùng Role EVENT_ORGANIZER & WORKER kèm Mã Ca Làm Việc và nút [Nhắn Tin Trực Tiếp].
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const LiveChatInboxTable = ({
  conversations = [],
  loading,
  onOpenChat,
  onOpenShiftDetail,
}) => {
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
        c.userEmail?.toLowerCase().includes(term) ||
        c.currentShiftCode?.toLowerCase().includes(term)
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
            placeholder="Tìm theo tên, SĐT, email hoặc mã ca..."
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
                <th style={{ width: '220px' }}>Khách Hàng</th>
                <th style={{ width: '120px', textAlign: 'center' }}>Vai Trò</th>
                <th style={{ minWidth: '180px', textAlign: 'center' }}>Mã Ca Làm</th>
                <th style={{ minWidth: '220px' }}>Tin Nhắn Gần Nhất</th>
                <th style={{ width: '140px' }}>Thời Gian & Trạng Thái</th>
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
                    {/* Cột 1: Khách Hàng (SĐT trên, Email dưới) */}
                    <td>
                      <div className="ticket-customer-cell">
                        <div className="ticket-customer-name" style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontWeight: 700, color: '#0f172a' }}>
                          <span>{conv.userName || 'Người dùng CANi'}</span>
                          {hasUnread && <span className="chat-unread-dot" />}
                        </div>
                        <div style={{ fontSize: '0.78rem', color: '#334155', marginTop: '2px', fontWeight: 600 }}>
                          SĐT: {conv.userPhone || '—'}
                        </div>
                        <div style={{ fontSize: '0.74rem', color: '#94a3b8', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: '200px' }} title={conv.userEmail}>
                          {conv.userEmail || '—'}
                        </div>
                      </div>
                    </td>

                    {/* Cột 2: Vai Trò (Kéo sát lại) */}
                    <td style={{ width: '120px', textAlign: 'center' }}>
                      <span
                        className="ticket-badge ticket-badge-medium"
                        style={{
                          background: isEO ? '#eff6ff' : '#f0fdf4',
                          color: isEO ? '#1e40af' : '#166534',
                          borderColor: isEO ? '#bfdbfe' : '#bbf7d0',
                          fontWeight: 600,
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {isEO ? 'Nhà tổ chức' : 'Nhân viên'}
                      </span>
                    </td>

                    {/* Cột 3: Mã Ca Làm (Căn giữa cân đối, không bị sát Vai Trò) */}
                    <td style={{ textAlign: 'center', padding: '0.75rem 1rem' }}>
                      {conv.currentShiftCode ? (
                        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '3px' }}>
                          <button
                            type="button"
                            onClick={() => onOpenShiftDetail && onOpenShiftDetail(conv.currentShiftId)}
                            style={{
                              background: '#eff6ff',
                              color: '#2563eb',
                              border: '1px solid #bfdbfe',
                              borderRadius: '6px',
                              padding: '3px 10px',
                              fontSize: '0.8rem',
                              fontWeight: 700,
                              cursor: 'pointer',
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '4px',
                              width: 'fit-content',
                              transition: 'all 0.15s',
                              boxShadow: '0 1px 2px rgba(37, 99, 235, 0.08)',
                            }}
                            title="Bấm để xem chi tiết ca làm việc"
                          >
                            <span>🏷️ {conv.currentShiftCode}</span>
                            <span style={{ fontSize: '0.7rem' }}>↗</span>
                          </button>
                          {conv.currentShiftName && (
                            <div
                              style={{
                                fontSize: '0.74rem',
                                color: '#64748b',
                                maxWidth: '190px',
                                whiteSpace: 'nowrap',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                                textAlign: 'center',
                              }}
                              title={conv.currentShiftName}
                            >
                              {conv.currentShiftName}
                            </div>
                          )}
                        </div>
                      ) : (
                        <span style={{ color: '#94a3b8', fontSize: '0.85rem' }}>—</span>
                      )}
                    </td>

                    {/* Cột 4: Tin Nhắn Gần Nhất (Giữ nguyên, cắt gọn 1 dòng) */}
                    <td style={{ maxWidth: '240px' }}>
                      <div
                        className="ticket-subject-cell"
                        style={{
                          fontWeight: hasUnread ? 700 : 500,
                          color: hasUnread ? '#0f172a' : hasChatted ? '#475569' : '#94a3b8',
                          fontStyle: hasChatted ? 'normal' : 'italic',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                        }}
                        title={conv.lastMessagePreview}
                      >
                        {conv.lastMessagePreview || 'Chưa có tin nhắn'}
                      </div>
                    </td>

                    {/* Cột 5: Thời Gian & Trạng Thái */}
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

                    {/* Cột 6: Thao Tác */}
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
