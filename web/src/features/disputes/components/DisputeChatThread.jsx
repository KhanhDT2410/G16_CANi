import { useState } from 'react';
import { DISPUTE_EVIDENCE_LABELS } from '../types/dispute.constants';

/**
 * Khung dòng hội thoại đối chất và gửi bằng chứng giữa các bên (ADM-03).
 * Hỗ trợ gửi tin nhắn, hình ảnh và tệp đính kèm.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const DisputeChatThread = ({ dispute, onSendMessage, isSending }) => {
  const [message, setMessage] = useState('');
  const [attachmentMode, setAttachmentMode] = useState('NONE'); // 'NONE' | 'IMAGE' | 'DOCUMENT'
  const [attachmentUrl, setAttachmentUrl] = useState('');
  const [attachmentName, setAttachmentName] = useState('');

  if (!dispute) return null;

  const handleSend = async (e) => {
    e.preventDefault();
    const trimmedMsg = message.trim();
    if (!trimmedMsg && !attachmentUrl.trim()) return;

    const fileType = attachmentMode === 'IMAGE' ? 'IMAGE' : attachmentMode === 'DOCUMENT' ? 'DOCUMENT' : 'DOCUMENT';
    const payload = {
      fileType,
      fileUrl: attachmentUrl.trim() || 'https://cani.vn/evidence/note',
      label: trimmedMsg || attachmentName || 'Bằng chứng bổ sung',
    };

    await onSendMessage(dispute.id, payload);
    setMessage('');
    setAttachmentUrl('');
    setAttachmentName('');
    setAttachmentMode('NONE');
  };

  const getPartyBadge = (partyId) => {
    if (!partyId) return { text: 'Người tham gia', cls: 'party-neutral' };
    if (partyId === dispute.complainantId) return { text: 'Bên khiếu nại', cls: 'party-complainant' };
    if (partyId === dispute.respondentId) return { text: 'Bên bị đơn', cls: 'party-respondent' };
    return { text: 'Trọng tài CSKH / Admin', cls: 'party-arbitrator' };
  };

  return (
    <div className="dispute-chat-thread">
      {/* Header dòng thời gian */}
      <div className="dispute-chat-header">
        <span className="chat-header-title">Lịch Sử Trao Đổi & Bằng Chứng Đối Chất</span>
        <span className="chat-header-count">{(dispute.evidences?.length || 0) + 1} lượt trao đổi</span>
      </div>

      {/* Danh sách tin nhắn / sự kiện */}
      <div className="dispute-chat-messages">
        {/* 1. Tin nhắn khởi tạo tranh chấp ban đầu */}
        <div className="dispute-msg-card msg-complainant">
          <div className="dispute-msg-meta">
            <span className="party-badge party-complainant">Bên khiếu nại</span>
            <span className="msg-party-id"><code>{dispute.complainantId?.substring(0, 8)}...</code></span>
            <span className="msg-timestamp">{new Date(dispute.createdAt).toLocaleString('vi-VN')}</span>
            <span className="msg-type-tag">Đơn khởi tạo</span>
          </div>
          <div className="dispute-msg-content">
            <div className="msg-title-highlight">{dispute.title}</div>
            <div className="msg-body-text">{dispute.description}</div>
          </div>
        </div>

        {/* 2. Các lượt trao đổi & nộp bằng chứng của các bên */}
        {dispute.evidences?.map((ev) => {
          const party = getPartyBadge(ev.party);
          const isImage = ev.fileType === 'IMAGE' || (ev.fileUrl && ev.fileUrl.match(/\.(jpg|jpeg|png|webp|gif)/i));

          return (
            <div key={ev.id} className={`dispute-msg-card ${party.cls}`}>
              <div className="dispute-msg-meta">
                <span className={`party-badge ${party.cls}`}>{party.text}</span>
                <span className="msg-party-id"><code>{ev.party?.substring(0, 8)}...</code></span>
                <span className="msg-timestamp">{new Date(ev.createdAt).toLocaleString('vi-VN')}</span>
                <span className="msg-type-tag">{DISPUTE_EVIDENCE_LABELS[ev.fileType] || ev.fileType}</span>
              </div>
              <div className="dispute-msg-content">
                {ev.label && <div className="msg-body-text">{ev.label}</div>}

                {/* Khối hiển thị ảnh nếu là hình ảnh đính kèm */}
                {isImage && ev.fileUrl && (
                  <div className="dispute-attachment-image-box">
                    <img
                      src={ev.fileUrl}
                      alt="Bằng chứng"
                      className="dispute-attachment-thumb"
                      onError={(e) => { e.target.style.display = 'none'; }}
                    />
                    <a href={ev.fileUrl} target="_blank" rel="noreferrer" className="attachment-view-link">
                      Xem ảnh kích thước đầy đủ
                    </a>
                  </div>
                )}

                {/* Khối hiển thị tệp tài liệu nếu không phải ảnh */}
                {!isImage && ev.fileUrl && (
                  <div className="dispute-attachment-file-box">
                    <a href={ev.fileUrl} target="_blank" rel="noreferrer" className="dispute-file-btn">
                      Tải tệp tài liệu đính kèm
                    </a>
                  </div>
                )}
              </div>
            </div>
          );
        })}

        {/* 3. Phán quyết kết luận của CSKH (nếu có) */}
        {dispute.outcome && (
          <div className="dispute-msg-card msg-decision">
            <div className="dispute-msg-meta">
              <span className="party-badge party-arbitrator">Phán quyết của Trọng tài CSKH</span>
              <span className="msg-timestamp">{new Date(dispute.updatedAt || dispute.createdAt).toLocaleString('vi-VN')}</span>
            </div>
            <div className="dispute-msg-content">
              <div className="decision-outcome-text">Kết luận: <strong>{dispute.outcome}</strong></div>
              <div className="msg-body-text">{dispute.decisionRationale}</div>
              {dispute.rsPenaltyApplied && (
                <div className="rs-penalty-note">Đã tự động áp dụng khấu trừ -10 điểm Uy Tín (RS)</div>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Khung nhập tin nhắn trao đổi & đính kèm ảnh/tệp */}
      <form onSubmit={handleSend} className="dispute-chat-input-bar">
        {attachmentMode === 'IMAGE' && (
          <div className="chat-attachment-field">
            <span>Nhập đường dẫn ảnh:</span>
            <input type="url" placeholder="https://.../anh-chup-bien-ban.png" value={attachmentUrl} onChange={(e) => setAttachmentUrl(e.target.value)} required />
            <button type="button" onClick={() => setAttachmentMode('NONE')}>✕</button>
          </div>
        )}

        {attachmentMode === 'DOCUMENT' && (
          <div className="chat-attachment-field">
            <span>Nhập đường dẫn tệp tài liệu:</span>
            <input type="url" placeholder="https://.../bien-ban-su-co.pdf" value={attachmentUrl} onChange={(e) => setAttachmentUrl(e.target.value)} required />
            <button type="button" onClick={() => setAttachmentMode('NONE')}>✕</button>
          </div>
        )}

        <div className="chat-input-row">
          <textarea
            className="chat-textarea"
            rows={2}
            placeholder="Nhập nội dung trao đổi, giải trình hoặc yêu cầu bổ sung chứng cứ giữa các bên..."
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            disabled={isSending}
          />
          <div className="chat-input-actions">
            <div className="chat-attach-buttons">
              <button type="button" className={`btn-attach ${attachmentMode === 'IMAGE' ? 'active' : ''}`} onClick={() => setAttachmentMode(attachmentMode === 'IMAGE' ? 'NONE' : 'IMAGE')} title="Đính kèm hình ảnh bằng chứng">
                Ảnh
              </button>
              <button type="button" className={`btn-attach ${attachmentMode === 'DOCUMENT' ? 'active' : ''}`} onClick={() => setAttachmentMode(attachmentMode === 'DOCUMENT' ? 'NONE' : 'DOCUMENT')} title="Đính kèm tệp văn bản hoặc PDF">
                Tệp tin
              </button>
            </div>
            <button type="submit" className="btn-send-message" disabled={isSending || (!message.trim() && !attachmentUrl.trim())}>
              {isSending ? 'Đang gửi...' : 'Gửi phản hồi'}
            </button>
          </div>
        </div>
      </form>
    </div>
  );
};

export default DisputeChatThread;
