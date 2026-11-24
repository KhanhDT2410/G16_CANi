/**
 * Từng dòng hội thoại trong popover thông báo tin nhắn của Admin & CSKH.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const StaffMessageItem = ({ conv, onSelect, formatTimeAgo }) => {
  const hasUnread = (conv.unreadStaffCount || 0) > 0;
  const isEO = conv.userRole === 'EVENT_ORGANIZER';

  return (
    <div
      className={`notif-item ${hasUnread ? 'unread' : 'read'}`}
      onClick={() => onSelect(conv)}
      style={{ cursor: 'pointer', display: 'flex', alignItems: 'flex-start', gap: '0.75rem', padding: '0.75rem 1rem' }}
    >
      <div
        style={{
          width: '38px',
          height: '38px',
          borderRadius: '50%',
          background: isEO ? '#dbeafe' : '#dcfce7',
          color: isEO ? '#1d4ed8' : '#15803d',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontWeight: 700,
          fontSize: '0.85rem',
          flexShrink: 0,
        }}
      >
        {conv.userName ? conv.userName.charAt(0).toUpperCase() : 'U'}
      </div>

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '0.5rem' }}>
          <span style={{ fontWeight: hasUnread ? 700 : 600, fontSize: '0.88rem', color: '#0f172a', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {conv.userName || 'Khách hàng'}
          </span>
          <span style={{ fontSize: '0.72rem', color: '#94a3b8', flexShrink: 0 }}>
            {formatTimeAgo(conv.lastMessageAt)}
          </span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', marginTop: '0.15rem' }}>
          <span
            style={{
              fontFamily: "var(--font-family, 'Be Vietnam Pro', -apple-system, sans-serif)",
              fontSize: '0.75rem',
              fontWeight: 500,
              padding: '1px 6px',
              borderRadius: '4px',
              background: isEO ? '#fffbeb' : '#eff6ff',
              border: `1px solid ${isEO ? '#fde68a' : '#bfdbfe'}`,
              color: isEO ? '#b45309' : '#1d4ed8',
            }}
          >
            {isEO ? 'Nhà tổ chức' : 'Người lao động'}
          </span>
          <span style={{ fontSize: '0.74rem', color: '#94a3b8' }}>
            {conv.userPhone || ''}
          </span>
        </div>

        <p
          style={{
            margin: '0.3rem 0 0 0',
            fontSize: '0.8rem',
            color: hasUnread ? '#1e293b' : '#64748b',
            fontWeight: hasUnread ? 600 : 400,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {conv.lastMessagePreview || 'Bắt đầu cuộc hội thoại...'}
        </p>
      </div>

      {hasUnread && (
        <span
          style={{
            width: '8px',
            height: '8px',
            borderRadius: '50%',
            background: '#2563eb',
            flexShrink: 0,
            marginTop: '0.5rem',
          }}
        />
      )}
    </div>
  );
};

export default StaffMessageItem;
