import { useState } from 'react';
import { showToast } from '../../../components/common/Toast';

const EscalateModal = ({ dispute, onClose, onSubmit }) => {
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!reason.trim()) {
      showToast('Vui lòng nhập lý do kháng nghị lên Quản trị viên', 'warning');
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit(dispute.id, { reason: reason.trim() });
      onClose();
    } catch {
      // Toast lỗi đã được hook xử lý
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="dispute-modal-overlay" onClick={onClose}>
      <div className="dispute-modal" onClick={(e) => e.stopPropagation()}>
        <div className="dispute-modal-header">
          <div>
            <h3>Kháng Nghị Lên Ban Quản Trị Viên (UC-101)</h3>
            <span className="dispute-modal-subtitle">
              Mã đơn: <code>{dispute.id}</code> (BR-54: Trong vòng 7 ngày kể từ quyết định của CSKH)
            </span>
          </div>
          <button type="button" className="dispute-modal-close" onClick={onClose}>&times;</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="dispute-modal-body">
            <div className="dispute-field-group">
              <label>Lý do khiếu nại / Kháng nghị phán quyết *</label>
              <textarea
                className="dispute-textarea"
                rows={4}
                placeholder="Nêu rõ căn cứ hoặc bằng chứng chứng minh kết luận của Chăm sóc khách hàng chưa thỏa đáng..."
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                required
              />
            </div>

            <div className="dispute-escalate-notice">
              Đơn sẽ được chuyển trực tiếp vào hàng đợi thẩm định của Quản trị viên (Admin). Quyết định của Quản trị viên sau bước này sẽ là <strong>phán quyết cuối cùng (BR-68)</strong>.
            </div>
          </div>

          <div className="dispute-modal-footer">
            <button
              type="button"
              className="btn-cancel"
              onClick={onClose}
              disabled={submitting}
            >
              Hủy
            </button>
            <button
              type="submit"
              className="btn-escalate-submit"
              disabled={submitting || !reason.trim()}
            >
              {submitting ? 'Đang gửi...' : 'Gửi kháng nghị lên Admin'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default EscalateModal;
