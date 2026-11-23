import { resolveAssetUrl, isImageUrl, getFileExtension, getFileName } from '../utils/resolveAssetUrl';

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
          messages.map((msg) => {
            const hasAttachment = Boolean(msg.attachmentUrl);
            const isImg = hasAttachment && isImageUrl(msg.attachmentUrl);
            const fileName = hasAttachment ? getFileName(msg.attachmentUrl) : '';
            const ext = hasAttachment ? getFileExtension(msg.attachmentUrl) : '';
            const fullHref = hasAttachment ? resolveAssetUrl(msg.attachmentUrl) : '';

            return (
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

                {hasAttachment && (
                  <div className="ticket-message-attachment-box">
                    {isImg ? (
                      <a
                        href={fullHref}
                        target="_blank"
                        rel="noopener noreferrer"
                        title="Bấm để xem ảnh gốc"
                      >
                        <img
                          src={fullHref}
                          alt={fileName}
                          className="ticket-message-attachment-thumb"
                          loading="lazy"
                        />
                      </a>
                    ) : (
                      <div className="ticket-message-doc-row">
                        <span className="ticket-attachment-doc-badge">[{ext}]</span>
                        <span className="ticket-message-doc-name" title={fileName}>
                          {fileName}
                        </span>
                        <a
                          href={fullHref}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="ticket-attachment-view-link"
                        >
                          Tải về / Xem tệp
                        </a>
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};

export default TicketMessageThread;
