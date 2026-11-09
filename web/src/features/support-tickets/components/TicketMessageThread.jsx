export const TicketMessageThread = ({ messages, ticketUserId, formatDateTime }) => {
  return (
    <div className="ticket-thread-section">
      <div className="ticket-thread-title">
        Lịch Sử Trao Đổi ({messages?.length || 0})
      </div>
      <div className="ticket-messages-list">
        {(!messages || messages.length === 0) ? (
          <div style={{ textAlign: 'center', color: '#94a3b8', padding: '1rem' }}>
            Chưa có trao đổi nào.
          </div>
        ) : (
          messages.map((msg) => (
            <div
              key={msg.id}
              className={`ticket-message-card ${msg.isInternal ? 'internal' : ''}`}
            >
              <div className="ticket-message-header">
                <span className="ticket-message-sender">
                  {msg.senderId === ticketUserId ? 'Người gửi yêu cầu' : 'Chuyên viên CSKH / Quản trị viên'}
                </span>
                <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                  {msg.isInternal && (
                    <span className="ticket-internal-tag">[GHI CHÚ NỘI BỘ]</span>
                  )}
                  <span>{formatDateTime(msg.createdAt)}</span>
                </div>
              </div>
              <div className="ticket-message-body">{msg.body}</div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};

export default TicketMessageThread;

