import { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';

export const RsAppealReviewModal = ({ appeal, isOpen, onClose, onSubmit, submitting }) => {
  const [approved, setApproved] = useState(true);
  const [decisionNote, setDecisionNote] = useState('');
  const [validationError, setValidationError] = useState('');

  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = prevOverflow;
    };
  }, [isOpen, onClose]);

  if (!isOpen || !appeal || typeof document === 'undefined') return null;

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!decisionNote.trim() || decisionNote.trim().length < 5) {
      setValidationError('Ghi chú quyết định phải có ít nhất 5 ký tự');
      return;
    }
    setValidationError('');
    onSubmit({ approved, decisionNote: decisionNote.trim() });
  };

  const modalNode = (
    <div className="appeal-modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="appeal-modal-content" onClick={(e) => e.stopPropagation()}>
        <form onSubmit={handleSubmit}>
          <div className="appeal-modal-header">
            <div>
              <h3 className="appeal-modal-title">Thẩm Định Kháng Cáo Điểm RS</h3>
              <p className="appeal-modal-sub">
                Nhân sự: {appeal.workerName} | Mức phạt: -{appeal.penaltyAmount} RS
              </p>
            </div>
            <button type="button" className="btn-modal-close" onClick={onClose}>✕</button>
          </div>

          <div className="appeal-modal-body">
            <div className="review-choice-group">
              <label className="review-choice-label">Quyết định thẩm định:</label>
              <div className="review-choice-btns">
                <button
                  type="button"
                  className={`btn-choice approve ${approved ? 'active' : ''}`}
                  onClick={() => setApproved(true)}
                >
                  Chấp Thuận Kháng Cáo
                </button>
                <button
                  type="button"
                  className={`btn-choice reject ${!approved ? 'active' : ''}`}
                  onClick={() => setApproved(false)}
                >
                  Từ Chối Kháng Cáo
                </button>
              </div>
            </div>

            <div className={`review-hint-box ${approved ? 'approve' : 'reject'}`}>
              {approved ? (
                <span>
                  Hệ thống sẽ <strong>khôi phục +{appeal.penaltyAmount} điểm uy tín</strong> cho nhân sự,
                  ghi nhận vào nhật ký kiểm toán và tự động gửi thông báo kết quả.
                </span>
              ) : (
                <span>
                  Đơn kháng cáo sẽ bị <strong>từ chối</strong>. Điểm uy tín của nhân sự giữ nguyên và
                  lý do từ chối sẽ được gửi trực tiếp đến nhân sự.
                </span>
              )}
            </div>

            <div className="form-group">
              <label className="form-label">
                Ghi chú giải trình quyết định <span className="req">*</span>:
              </label>
              <textarea
                className="form-textarea"
                rows={4}
                placeholder="Nhập lý do phê duyệt hoặc lý do từ chối (bắt buộc tối thiểu 5 ký tự)..."
                value={decisionNote}
                onChange={(e) => setDecisionNote(e.target.value)}
              />
              {validationError && <p className="form-err">{validationError}</p>}
            </div>
          </div>

          <div className="appeal-modal-footer">
            <button type="button" className="btn-secondary" onClick={onClose} disabled={submitting}>
              Hủy
            </button>
            <button type="submit" className={`btn-primary ${approved ? 'btn-approve-submit' : 'btn-reject-submit'}`} disabled={submitting}>
              {submitting ? 'Đang Xử Lý...' : approved ? 'Xác Nhận Khôi Phục Điểm' : 'Xác Nhận Từ Chối'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );

  return createPortal(modalNode, document.body);
};

