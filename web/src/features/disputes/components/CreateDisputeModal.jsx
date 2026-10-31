import { useState } from 'react';
import { DISPUTE_TYPES, DISPUTE_TYPE_LABELS } from '../types/dispute.constants';

const CreateDisputeModal = ({ onClose, onSubmit }) => {
  const [assignmentId, setAssignmentId] = useState('');
  const [type, setType] = useState(DISPUTE_TYPES.PAYMENT);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const descLength = description.trim().length;
  const isDescValid = descLength >= 50 && descLength <= 3000;

  const handleQuickFill = () => {
    setTitle('Chưa nhận tiền công và phụ cấp ca trực lễ tân');
    setDescription(
      'Tôi đã hoàn thành đầy đủ ca làm việc theo phân công nhưng đã quá 24h nhà tổ chức sự kiện vẫn chưa thanh toán thù lao và không phản hồi tin nhắn đối soát.'
    );
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!assignmentId.trim()) {
      alert('Vui lòng nhập ID ca làm việc (Assignment ID)');
      return;
    }
    if (!title.trim()) {
      alert('Vui lòng nhập tiêu đề tranh chấp');
      return;
    }
    if (!isDescValid) {
      alert('Mô tả tranh chấp phải từ 50 đến 3000 ký tự (theo quy định BR-52)');
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit({
        assignmentId: assignmentId.trim(),
        type,
        title: title.trim(),
        description: description.trim(),
      });
      onClose();
    } catch {
      // Đã xử lý toast ở hook
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="dispute-modal-overlay" onClick={onClose}>
      <div className="dispute-modal" onClick={(e) => e.stopPropagation()}>
        <div className="dispute-modal-header">
          <h3>Nộp Đơn Tranh Chấp Mới (UC-96)</h3>
          <button type="button" className="dispute-modal-close" onClick={onClose}>✕</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="dispute-modal-body">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
                Quy định BR-52: Cửa sổ nộp trong 14 ngày, mô tả ≥ 50 ký tự.
              </span>
              <button type="button" className="btn-quick-fill" onClick={handleQuickFill}>
                ⚡ Điền nhanh mẫu test
              </button>
            </div>

            <div className="dispute-field-group">
              <label>ID Ca làm việc (Assignment UUID) *</label>
              <input
                type="text"
                className="dispute-input"
                placeholder="Ví dụ: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"
                value={assignmentId}
                onChange={(e) => setAssignmentId(e.target.value)}
                required
              />
            </div>

            <div className="dispute-field-group">
              <label>Danh mục tranh chấp *</label>
              <select
                className="dispute-select"
                value={type}
                onChange={(e) => setType(e.target.value)}
              >
                {Object.entries(DISPUTE_TYPES).map(([key, val]) => (
                  <option key={key} value={val}>
                    {DISPUTE_TYPE_LABELS[val] || val}
                  </option>
                ))}
              </select>
            </div>

            <div className="dispute-field-group">
              <label>Tiêu đề tranh chấp *</label>
              <input
                type="text"
                className="dispute-input"
                maxLength={200}
                placeholder="Tóm tắt vấn đề (tối đa 200 ký tự)"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                required
              />
            </div>

            <div className="dispute-field-group">
              <label>Mô tả chi tiết tình huống (Tối thiểu 50 ký tự) *</label>
              <textarea
                className="dispute-textarea"
                rows={4}
                placeholder="Mô tả cụ thể diễn biến, thời gian, số tiền tranh chấp hoặc thỏa thuận bị vi phạm..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                required
              />
              <div className={`char-counter ${isDescValid ? 'valid' : 'invalid'}`}>
                {descLength} / 50 ký tự tối thiểu {isDescValid ? '✓' : '(chưa đủ)'}
              </div>
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
                background: isDescValid ? 'var(--color-primary)' : '#94a3b8',
                borderColor: 'transparent',
                padding: '0.5rem 1.25rem',
              }}
              disabled={submitting || !isDescValid}
            >
              {submitting ? 'Đang gửi...' : 'Nộp đơn khiếu nại'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CreateDisputeModal;

