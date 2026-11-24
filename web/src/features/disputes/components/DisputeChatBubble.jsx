/**
 * Hiển thị một bong bóng tin nhắn trong hội thoại đối chất.
 * Hỗ trợ xem ảnh trực tiếp và liên kết tệp đính kèm.
 * Tuân thủ < 200 dòng theo AGENTS.md.
 */
export const DisputeChatBubble = ({ message }) => {
  const isMe = message.isMe;
  const isImage = message.attachmentUrl && (
    /\.(jpg|jpeg|png|webp|gif)/i.test(message.attachmentUrl) ||
    message.attachmentUrl.startsWith('data:image') ||
    message.attachmentUrl.startsWith('blob:')
  );

  return (
    <div className={`dispute-bubble-row ${isMe ? 'msg-outgoing' : 'msg-incoming'}`}>
      <div className="dispute-bubble-wrapper">
        {!isMe && (
          <div className="bubble-sender-meta">
            <span className="bubble-sender-name">{message.senderName || 'Người gửi'}</span>
            <span className="bubble-sender-role">({message.senderRole})</span>
          </div>
        )}
        <div className="dispute-bubble-content">
          <p className="bubble-text">{message.messageText}</p>
          {message.attachmentUrl && (
            <div className="bubble-attachment-box">
              {isImage ? (
                <img
                  src={message.attachmentUrl}
                  alt="Minh chứng"
                  className="bubble-attachment-thumb"
                  onClick={() => window.open(message.attachmentUrl, '_blank')}
                />
              ) : (
                <a href={message.attachmentUrl} target="_blank" rel="noreferrer" className="bubble-attachment-link">
                  Tệp tài liệu đính kèm
                </a>
              )}
            </div>
          )}
        </div>
        <div className="bubble-time-stamp">
          {message.createdAt ? new Date(message.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) : ''}
        </div>
      </div>
    </div>
  );
};

export default DisputeChatBubble;
