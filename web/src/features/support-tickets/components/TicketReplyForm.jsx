import { useState } from 'react';
import TicketAttachmentUpload from './TicketAttachmentUpload';
import { showToast } from '../../../components/common/Toast';

export const TicketReplyForm = ({ isStaff, onSubmit, submitting }) => {
  const [replyBody, setReplyBody] = useState('');
  const [isInternal, setIsInternal] = useState(false);
  const [attachments, setAttachments] = useState([]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!replyBody.trim()) {
      showToast('Vui lòng nhập nội dung tin nhắn', 'warning');
      return;
    }

    const firstAttachment = attachments[0];
    const attachmentUrl = firstAttachment
      ? (typeof firstAttachment === 'string' ? firstAttachment : firstAttachment.url)
      : null;

    const success = await onSubmit({
      body: replyBody.trim(),
      isInternal: isStaff ? isInternal : false,
      attachmentUrl,
    });

    if (success) {
      setReplyBody('');
      setIsInternal(false);
      setAttachments([]);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="ticket-reply-box">
      <label className="ticket-form-label">
        {isStaff ? 'Gửi Phản Hồi Hoặc Ghi Chú' : 'Gửi Phản Hồi Cho Ban Quản Trị'}
      </label>
      <textarea
        className="ticket-form-textarea"
        placeholder={isInternal ? 'Nhập ghi chú nội bộ...' : 'Nhập tin nhắn phản hồi sự cố...'}
        value={replyBody}
        onChange={(e) => setReplyBody(e.target.value)}
        rows={3}
        required
      />

      <div style={{ marginTop: '0.5rem', marginBottom: '0.75rem' }}>
        <TicketAttachmentUpload
          attachments={attachments}
          onChange={setAttachments}
          disabled={submitting}
          maxFiles={2}
        />
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        {isStaff ? (
          <label className="ticket-checkbox-group">
            <input
              type="checkbox"
              checked={isInternal}
              onChange={(e) => setIsInternal(e.target.checked)}
            />
            Đặt làm ghi chú nội bộ
          </label>
        ) : <div />}
        <button
          type="submit"
          className="ticket-btn ticket-btn-primary ticket-btn-sm"
          disabled={submitting}
        >
          {submitting ? 'Đang gửi...' : 'Gửi Tin Nhắn'}
        </button>
      </div>
    </form>
  );
};

export default TicketReplyForm;
