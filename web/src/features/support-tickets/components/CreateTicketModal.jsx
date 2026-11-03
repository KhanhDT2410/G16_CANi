import { useState } from 'react';
import { TICKET_CATEGORIES, TICKET_CATEGORY_CONFIG } from '../types/ticket.constants';
import { showToast } from '../../../components/common/Toast';

export const CreateTicketModal = ({ isOpen, onClose, onSubmit, loading }) => {
  const [category, setCategory] = useState(TICKET_CATEGORIES.ACCOUNT);
  const [subject, setSubject] = useState('');
  const [description, setDescription] = useState('');
  const [attachmentUrl, setAttachmentUrl] = useState('');

  if (!isOpen) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!subject.trim()) {
      showToast('Vui lòng nhập tiêu đề yêu cầu hỗ trợ', 'warning');
      return;
    }
    if (description.trim().length < 30) {
      showToast('Mô tả chi tiết phải từ 30 ký tự trở lên (quy định MSG151)', 'warning');
      return;
    }

    const payload = {
      category,
      subject: subject.trim(),
      description: description.trim(),
      attachments: attachmentUrl.trim() ? [attachmentUrl.trim()] : [],
    };

    const res = await onSubmit(payload);
    if (res) {
      onClose();
    }
  };

  return (
    <div className="ticket-modal-overlay">
      <div className="ticket-modal">
        <div className="ticket-modal-header">
          <h3>Gửi Yêu Cầu Hỗ Trợ Mới</h3>
          <button type="button" className="ticket-modal-close-btn" onClick={onClose}>
            &times;
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="ticket-modal-body">
            <div className="ticket-form-group">
              <label className="ticket-form-label">Danh Mục Hỗ Trợ *</label>
              <select
                className="ticket-form-select"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
              >
                {Object.values(TICKET_CATEGORIES).map((cat) => (
                  <option key={cat} value={cat}>
                    {TICKET_CATEGORY_CONFIG[cat]?.label || cat}
                  </option>
                ))}
              </select>
            </div>

            <div className="ticket-form-group">
              <label className="ticket-form-label">Tiêu Đề Phiếu *</label>
              <input
                type="text"
                className="ticket-form-input"
                placeholder="VD: Không thể hoàn tất đối soát ca làm việc ngày 30/10"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                maxLength={200}
                required
              />
            </div>

            <div className="ticket-form-group">
              <label className="ticket-form-label">
                Mô Tả Chi Tiết Sự Cố * (Tối thiểu 30 ký tự)
              </label>
              <textarea
                className="ticket-form-textarea"
                placeholder="Mô tả cụ thể vấn đề bạn đang gặp phải, mã ca làm việc, thời gian phát sinh..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={5}
                required
              />
              <span style={{ fontSize: '0.75rem', color: description.trim().length < 30 ? '#ef4444' : '#64748b' }}>
                Đã nhập: {description.trim().length}/30 ký tự tối thiểu
              </span>
            </div>

            <div className="ticket-form-group">
              <label className="ticket-form-label">Đường Dẫn Ảnh Đính Kèm (Tùy chọn)</label>
              <input
                type="url"
                className="ticket-form-input"
                placeholder="https://storage.cani.vn/evidence.jpg"
                value={attachmentUrl}
                onChange={(e) => setAttachmentUrl(e.target.value)}
              />
            </div>
          </div>

          <div className="ticket-modal-footer">
            <button
              type="button"
              className="ticket-btn ticket-btn-secondary"
              onClick={onClose}
              disabled={loading}
            >
              Hủy
            </button>
            <button
              type="submit"
              className="ticket-btn ticket-btn-primary"
              disabled={loading}
            >
              {loading ? 'Đang gửi...' : 'Gửi Yêu Cầu'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CreateTicketModal;

