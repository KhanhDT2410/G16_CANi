import { useState, useRef, useEffect } from 'react';

/**
 * Khung chat đơn với từng bên (Nhân sự hoặc Nhà tổ chức).
 * Thiết kế chuẩn mực, chuyên nghiệp, không biểu tượng cảm xúc linh tinh.
 * Tuân thủ < 200 dòng theo AGENTS.md.
 */
export const DisputeSingleChatPane = ({
  partyChat,
  targetParty,
  partyRolePrefix,
  partyName,
  partyTitle,
  partyRoleLabel,
  onSendMessage,
  isSending = false,
  isArbitrator = false,
  isClosed = false,
  placeholder,
}) => {
  const [text, setText] = useState('');
  const [showAttachInput, setShowAttachInput] = useState(false);
  const [attachmentUrl, setAttachmentUrl] = useState('');
  const bodyRef = useRef(null);
  const isAtBottomRef = useRef(true);
  const prevCountRef = useRef(0);

  const displayPrefix = partyRolePrefix || (partyTitle?.includes(':') ? partyTitle.split(':')[0].trim() : null);
  const displayName = partyName || (partyTitle?.includes(':') ? partyTitle.split(':').slice(1).join(':').trim() : partyTitle || 'Hội thoại');

  const scrollToBottom = (smooth = true) => {
    const el = bodyRef.current;
    if (!el) return;
    el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'instant' });
  };

  const handleScroll = () => {
    const el = bodyRef.current;
    if (el) isAtBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight <= 50;
  };

  useEffect(() => {
    const count = partyChat?.messages?.length || 0;
    if (count > prevCountRef.current && (isAtBottomRef.current || prevCountRef.current === 0)) {
      scrollToBottom(prevCountRef.current > 0);
    }
    prevCountRef.current = count;
  }, [partyChat?.messages?.length]);

  const handleSubmit = (e) => {
    e.preventDefault();
    if (isClosed || isSending) return;
    const trimmed = text.trim();
    if (!trimmed && !attachmentUrl.trim()) return;

    onSendMessage({
      targetParty: targetParty || partyChat?.party || 'WORKER',
      messageText: trimmed || 'Đính kèm tài liệu đối soát',
      attachmentUrl: attachmentUrl.trim() || null,
    });

    setText('');
    setAttachmentUrl('');
    setShowAttachInput(false);
    setTimeout(() => scrollToBottom(true), 50);
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    }
  };

  const messages = partyChat?.messages || [];

  return (
    <div className="dispute-single-pane">
      <div className="dispute-single-pane-header">
        <div className="pane-header-info">
          {displayPrefix && <span className="pane-party-prefix">{displayPrefix}:</span>}
          <span className="pane-party-title">{displayName}</span>
        </div>
        {partyRoleLabel && (
          <div className="pane-header-role-right">
            <span className="pane-role-badge">{partyRoleLabel}</span>
          </div>
        )}
      </div>

      <div ref={bodyRef} onScroll={handleScroll} className="dispute-single-pane-body">
        {messages.length === 0 ? (
          <div className="pane-empty-notice">
            <span>Chưa có tin nhắn nào trong kênh này. Hãy bắt đầu trao đổi đối chất.</span>
          </div>
        ) : (
          messages.map((m) => {
            const isMe = m.isMe;
            return (
              <div key={m.id || m.createdAt} className={`dispute-bubble-row ${isMe ? 'msg-outgoing' : 'msg-incoming'}`}>
                <div className="dispute-bubble-wrapper">
                  {!isMe && (
                    <div className="bubble-sender-meta">
                      <span className="bubble-sender-name">{m.senderName || 'Người gửi'}</span>
                      <span className="bubble-sender-role">({m.senderRole})</span>
                    </div>
                  )}
                  <div className="dispute-bubble-content">
                    <p className="bubble-text">{m.messageText}</p>
                    {m.attachmentUrl && (
                      <div className="bubble-attachment-box">
                        <a href={m.attachmentUrl} target="_blank" rel="noreferrer" className="bubble-attachment-link">
                          Tài liệu đính kèm
                        </a>
                      </div>
                    )}
                  </div>
                  <div className="bubble-time-stamp">
                    {m.createdAt ? new Date(m.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) : ''}
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>

      {isClosed ? (
        <div className="pane-closed-bar">
          <span>Vụ việc đã có phán quyết cuối cùng (FINAL_RESOLVED). Kênh đối chất đã được đóng.</span>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="dispute-single-pane-footer">
          {showAttachInput && (
            <div className="pane-attachment-input-row">
              <input
                type="url"
                className="pane-attachment-input"
                placeholder="Nhập đường dẫn URL tài liệu minh chứng..."
                value={attachmentUrl}
                onChange={(e) => setAttachmentUrl(e.target.value)}
              />
              <button
                type="button"
                className="btn-cancel-attach"
                onClick={() => { setShowAttachInput(false); setAttachmentUrl(''); }}
              >
                Hủy
              </button>
            </div>
          )}

          <div className="pane-input-bar">
            <button
              type="button"
              className={`btn-pane-attach ${showAttachInput ? 'active' : ''}`}
              onClick={() => setShowAttachInput((prev) => !prev)}
              title="Đính kèm tài liệu"
            >
              Đính kèm
            </button>

            <textarea
              className="pane-textarea"
              rows={2}
              maxLength={2000}
              placeholder={
                placeholder ||
                (isArbitrator
                  ? 'Nhập chỉ đạo hoặc yêu cầu đối soát gửi cho bên này...'
                  : 'Nhập ý kiến giải trình hoặc phản hồi...')
              }
              value={text}
              onChange={(e) => setText(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={isSending}
            />

            <button
              type="submit"
              className="btn-pane-send"
              disabled={isSending || (!text.trim() && !attachmentUrl.trim())}
            >
              {isSending ? 'Đang gửi...' : 'Gửi'}
            </button>
          </div>
        </form>
      )}
    </div>
  );
};

export default DisputeSingleChatPane;
