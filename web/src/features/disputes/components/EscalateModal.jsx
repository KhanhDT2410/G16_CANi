import { useState } from 'react';

const EscalateModal = ({ dispute, onClose, onSubmit }) => {
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!reason.trim()) {
      alert('Vui lòng nhập lý do kháng nghị lên Quản trị viên');
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit(dispute.id, { reason: reason.trim() });
      onClose();
    } catch {
      // Đã hiển thị toast lỗi
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
            <span style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
              Mã đơn: <code>{dispute.id}</code> (BR-54: Trong vòng 7 ngày kể từ quyết định của CSKH)
            </span>
          </div>
          <button type="button" className="dispute-modal-close" onClick={onClose}>✕</button>
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

            <div style={{ padding: '0.65rem 0.85rem', background: '#fae8ff', border: '1px solid #f0abfc', borderRadius: 'var(--radius-md)', fontSize: '0.82rem', color: '#86198f' }}>
              ℹ️ Đơn sẽ được chuyển trực tiếp vào hàng đợi thẩm định của Quản trị viên (Admin). Quyết định của Quản trị viên sau bước này sẽ là <strong>phán quyết cuối cùng (BR-68)</strong>.
            </div>
          </div>

          <div className="dispute-modal-footer">
            <button
              type="button"
              className="btn-quick-fill"
              style={{ background: 'var(--color-bg-subtle)' }}
              onClick={onClose}
              disabled={submitting}
            >
              Hủy
            </button>
            <button
              type="submit"
              className="btn-quick-fill"
              style={{
                color: '#fff',
                background: '#86198f',
                borderColor: 'transparent',
                padding: '0.5rem 1.25rem',
              }}
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
