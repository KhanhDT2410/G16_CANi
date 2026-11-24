import { useState, useRef, useEffect } from 'react';
import { disputeApi } from '../api/disputeApi';
import DisputeChatBubble from './DisputeChatBubble';

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
  const [selectedFile, setSelectedFile] = useState(null);
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef(null);
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

  const handleFileSelect = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > 10 * 1024 * 1024) {
      alert('Dung lượng tệp không được vượt quá 10MB');
      return;
    }
    setSelectedFile(file);
    setShowAttachInput(false);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (isClosed || isSending || isUploading) return;
    const trimmed = text.trim();
    if (!trimmed && !selectedFile && !attachmentUrl.trim()) return;

    let finalAttachmentUrl = attachmentUrl.trim() || null;
    if (selectedFile) {
      setIsUploading(true);
      try {
        finalAttachmentUrl = await disputeApi.uploadDisputeAttachment(partyChat?.disputeId || 'evidence', selectedFile);
      } catch (err) {
        console.error('Lỗi tải tệp lên:', err);
        finalAttachmentUrl = URL.createObjectURL(selectedFile);
      } finally {
        setIsUploading(false);
      }
    }

    onSendMessage({
      targetParty: targetParty || partyChat?.party || 'WORKER',
      messageText: trimmed || (selectedFile ? `Đã gửi tệp: ${selectedFile.name}` : 'Đính kèm tài liệu đối soát'),
      attachmentUrl: finalAttachmentUrl,
    });

    setText('');
    setSelectedFile(null);
    setAttachmentUrl('');
    if (fileInputRef.current) fileInputRef.current.value = '';
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
          messages.map((m) => (
            <DisputeChatBubble key={m.id || m.createdAt} message={m} />
          ))
        )}
      </div>

      {isClosed ? (
        <div className="pane-closed-bar">
          <span>Vụ việc đã được hoàn tất xử lý. Kênh đối chất đã được lưu trữ ở chế độ chỉ đọc.</span>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="dispute-single-pane-footer">
          {!isArbitrator && (
            <input
              type="file"
              ref={fileInputRef}
              accept="image/*,.pdf,.doc,.docx"
              style={{ display: 'none' }}
              onChange={handleFileSelect}
            />
          )}

          {!isArbitrator && selectedFile && (
            <div className="pane-selected-file-chip">
              <span className="file-name">{selectedFile.name} ({(selectedFile.size / 1024).toFixed(0)} KB)</span>
              <button
                type="button"
                onClick={() => {
                  setSelectedFile(null);
                  if (fileInputRef.current) fileInputRef.current.value = '';
                }}
              >
                ✕
              </button>
            </div>
          )}

          <div className="pane-input-bar">
            {!isArbitrator && (
              <button
                type="button"
                className={`btn-pane-attach ${selectedFile ? 'active' : ''}`}
                onClick={() => fileInputRef.current?.click()}
                title="Chọn ảnh hoặc tài liệu từ thiết bị"
              >
                Đính kèm
              </button>
            )}

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
              disabled={isSending || isUploading}
            />

            <button
              type="submit"
              className="btn-pane-send"
              disabled={isSending || isUploading || (!text.trim() && !selectedFile && (isArbitrator || !attachmentUrl.trim()))}
            >
              {isSending || isUploading ? 'Đang gửi...' : 'Gửi'}
            </button>
          </div>
        </form>
      )}
    </div>
  );
};

export default DisputeSingleChatPane;
