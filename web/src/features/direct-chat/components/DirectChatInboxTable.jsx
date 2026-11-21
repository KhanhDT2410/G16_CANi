import React from 'react';
import DirectChatToolbar from './DirectChatToolbar';
import './DirectChat.css';

/**
 * Bảng Hộp Thư Trao Đổi Tin Nhắn Nhân Sự (Direct & Shift Chat Inbox).
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const DirectChatInboxTable = ({
  conversations = [],
  loading = false,
  filterType = 'ALL',
  onFilterChange,
  searchTerm = '',
  onSearchChange,
  counts = {},
  onOpenChat,
  onOpenShiftDetail,
}) => {
  const formatTimeAgo = (isoStr) => {
    if (!isoStr) return '—';
    try {
      const diffMins = Math.floor((Date.now() - new Date(isoStr).getTime()) / 60000);
      if (diffMins < 1) return 'Vừa xong';
      if (diffMins < 60) return `${diffMins} phút trước`;
      const diffHours = Math.floor(diffMins / 60);
      if (diffHours < 24) return `${diffHours} giờ trước`;
      return new Date(isoStr).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' });
    } catch {
      return '—';
    }
  };

  return (
    <div className="direct-chat-wrapper">
      <DirectChatToolbar filterType={filterType} onFilterChange={onFilterChange} searchTerm={searchTerm} onSearchChange={onSearchChange} counts={counts} />

      {loading && conversations.length === 0 ? (
        <div className="direct-chat-empty-state">
          <div className="direct-chat-empty-icon">⏳</div>
          <div className="direct-chat-empty-title">Đang tải danh sách tin nhắn...</div>
        </div>
      ) : conversations.length === 0 ? (
        <div className="direct-chat-empty-state">
          <div className="direct-chat-empty-icon">💬</div>
          <div className="direct-chat-empty-title">{searchTerm ? 'Không tìm thấy cuộc trò chuyện nào phù hợp' : 'Chưa có cuộc trò chuyện nào'}</div>
          <div className="direct-chat-empty-desc">
            {searchTerm
              ? 'Thử thay đổi từ khóa tìm kiếm theo tên nhân sự, số điện thoại hoặc mã ca làm việc.'
              : 'Khi bạn nhắn tin cho nhân sự từ Danh Sách Ca, Đội Ngũ Nhân Sự hoặc khi nhân sự nhắn tin đến, các cuộc trò chuyện sẽ hiển thị đầy đủ tại đây.'}
          </div>
        </div>
      ) : (
        <div className="direct-chat-table-wrapper">
          <table className="direct-chat-table">
            <thead>
              <tr>
                <th style={{ width: '240px' }}>Nhân Sự</th>
                <th style={{ width: '200px' }}>Ngữ Cảnh Trao Đổi</th>
                <th style={{ minWidth: '220px' }}>Tin Nhắn Gần Nhất</th>
                <th style={{ width: '130px' }}>Thời Gian</th>
                <th style={{ width: '120px', textAlign: 'center' }}>Thao Tác</th>
              </tr>
            </thead>
            <tbody>
              {conversations.map((conv) => {
                const hasUnread = (conv.unreadCount || 0) > 0;
                const isShift = conv.type === 'SHIFT';

                return (
                  <tr key={conv.id} className={hasUnread ? 'chat-row-unread' : ''}>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                        <div style={{ width: 36, height: 36, borderRadius: '50%', background: '#e0e7ff', color: '#4338ca', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: '0.85rem' }}>
                          {conv.targetUserName ? conv.targetUserName.charAt(0).toUpperCase() : 'W'}
                        </div>
                        <div>
                          <div style={{ fontWeight: 700, color: '#0f172a', display: 'flex', alignItems: 'center' }}>
                            <span>{conv.targetUserName || 'Nhân sự CANi'}</span>
                            {hasUnread && <span className="direct-unread-badge">+{conv.unreadCount}</span>}
                          </div>
                          <div style={{ fontSize: '0.76rem', color: '#64748b', marginTop: '2px' }}>SĐT: {conv.targetUserPhone || '—'}</div>
                        </div>
                      </div>
                    </td>

                    <td>
                      {isShift && conv.shiftCode ? (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
                          <button
                            type="button"
                            onClick={() => onOpenShiftDetail && onOpenShiftDetail(conv.shiftId)}
                            style={{ background: '#eff6ff', color: '#2563eb', border: '1px solid #bfdbfe', borderRadius: '6px', padding: '2px 8px', fontSize: '0.78rem', fontWeight: 700, cursor: 'pointer', width: 'fit-content', display: 'inline-flex', alignItems: 'center', gap: '4px' }}
                            title="Bấm để xem ca sự kiện"
                          >
                            <span>🏷️ {conv.shiftCode}</span>
                            <span style={{ fontSize: '0.68rem' }}>↗</span>
                          </button>
                          {conv.shiftName && (
                            <span style={{ fontSize: '0.74rem', color: '#475569', maxWidth: '180px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={conv.shiftName}>
                              {conv.shiftName}
                            </span>
                          )}
                        </div>
                      ) : (
                        <span style={{ background: '#f1f5f9', color: '#475569', padding: '3px 8px', borderRadius: '6px', fontSize: '0.76rem', fontWeight: 600 }}>💬 Chat tự do</span>
                      )}
                    </td>

                    <td>
                      <div style={{ fontSize: '0.84rem', color: hasUnread ? '#0f172a' : '#475569', fontWeight: hasUnread ? 700 : 400, maxWidth: '260px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={conv.lastMessagePreview}>
                        {conv.lastMessagePreview || 'Chưa có tin nhắn'}
                      </div>
                    </td>

                    <td style={{ fontSize: '0.78rem', color: '#64748b' }}>{formatTimeAgo(conv.lastMessageAt)}</td>

                    <td style={{ textAlign: 'center' }}>
                      <button
                        type="button"
                        onClick={() => onOpenChat(conv)}
                        style={{ background: '#2563eb', color: '#ffffff', border: 'none', borderRadius: '6px', padding: '5px 12px', fontSize: '0.8rem', fontWeight: 600, cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: '4px' }}
                      >
                        💬 Nhắn Tin
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

export default DirectChatInboxTable;
