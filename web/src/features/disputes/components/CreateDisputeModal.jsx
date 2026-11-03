import { useState } from 'react';
import { showToast } from '../../../components/common/Toast';
import { DISPUTE_TYPES, DISPUTE_TYPE_LABELS } from '../types/dispute.constants';

const CreateDisputeModal = ({ onClose, onSubmit }) => {
  const [assignmentId, setAssignmentId] = useState('');
  const [type, setType] = useState(DISPUTE_TYPES.PAYMENT);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const descLength = description.trim().length;
  const isDescValid = descLength >= 50 && descLength <= 3000;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!assignmentId.trim()) {
      showToast('Vui lòng nhập ID ca làm việc (Assignment ID)', 'warning');
      return;
    }
    if (!title.trim()) {
      showToast('Vui lòng nhập tiêu đề tranh chấp', 'warning');
      return;
    }
    if (!isDescValid) {
      showToast('Mô tả tranh chấp phải từ 50 đến 3000 ký tự theo quy định BR-52', 'warning');
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
      // Toast lỗi đã được hook xử lý
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="dispute-modal-overlay" onClick={onClose}>
      <div className="dispute-modal" onClick={(e) => e.stopPropagation()}>
        <div className="dispute-modal-header">
          <h3>Nộp Đơn Tranh Chấp & Khiếu Nại (UC-96)</h3>
          <button type="button" className="dispute-modal-close" onClick={onClose}>&times;</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="dispute-modal-body">
            <div className="dispute-modal-notice">
              Quy tắc BR-52: Đơn chỉ được nộp trong vòng 14 ngày kể từ khi ca kết thúc, mô tả tối thiểu 50 ký tự.
            </div>

            <div className="dispute-field-group">
              <label>Mã Ca Làm Việc (Assignment ID) *</label>
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
                placeholder="Tóm tắt nội dung khiếu nại (tối đa 200 ký tự)"
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
                {descLength} / 50 ký tự tối thiểu {isDescValid ? '(Đủ điều kiện)' : '(Chưa đủ)'}
              </div>
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
              className="btn-submit"
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
