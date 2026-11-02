import { useState } from 'react';
import {
  DISPUTE_OUTCOMES,
  DISPUTE_OUTCOME_LABELS,
} from '../types/dispute.constants';

const DisputeDecisionModal = ({ dispute, onClose, onSubmit }) => {
  const isEscalated = dispute?.status === 'ESCALATED';
  const [outcome, setOutcome] = useState(
    isEscalated ? DISPUTE_OUTCOMES.UPHOLD : DISPUTE_OUTCOMES.WORKER_FAULT
  );
  const [rationale, setRationale] = useState('');
  const [penaltyWaiverReason, setPenaltyWaiverReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const rationaleLen = rationale.trim().length;
  const isRationaleValid = rationaleLen >= 100 && rationaleLen <= 5000;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!isRationaleValid) {
      alert('Lý do quyết định phải từ 100 đến 5000 ký tự theo quy định BR-55/BR-68.');
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit(dispute.id, {
        outcome,
        rationale: rationale.trim(),
        penaltyWaiverReason: penaltyWaiverReason.trim() || null,
      });
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
            <h3>
              {isEscalated
                ? 'Phán Quyết Cuối Cùng Của Admin (UC-102)'
                : 'Ban Hành Quyết Định Giải Quyết (UC-100)'}
            </h3>
            <span style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
              Mã đơn: <code>{dispute.id}</code> (Trạng thái hiện tại: <strong>{dispute.status}</strong>)
            </span>
          </div>
          <button type="button" className="dispute-modal-close" onClick={onClose}>✕</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="dispute-modal-body">
            <div className="dispute-field-group">
              <label>Kết quả phán quyết (Outcome) *</label>
              <select
                className="dispute-select"
                value={outcome}
                onChange={(e) => setOutcome(e.target.value)}
              >
                {Object.entries(DISPUTE_OUTCOMES).map(([k, val]) => (
                  <option key={k} value={val}>
                    {DISPUTE_OUTCOME_LABELS[val] || val}
                  </option>
                ))}
              </select>
            </div>

            {outcome === DISPUTE_OUTCOMES.WORKER_FAULT && (
              <div style={{ padding: '0.65rem 0.85rem', background: 'var(--color-warning-bg)', border: '1px solid var(--color-warning-border)', borderRadius: 'var(--radius-md)', fontSize: '0.82rem', color: 'var(--color-warning-text)' }}>
                ⚡ <strong>Quy tắc BR-55:</strong> Kết quả <code>WORKER_FAULT</code> sẽ tự động khấu trừ <strong>−10 điểm RS</strong> của Worker (trừ khi có lý do miễn trừ hợp lệ bên dưới).
              </div>
            )}

            <div className="dispute-field-group">
              <label>Lý do quyết định (Rationale - Tối thiểu 100 ký tự) *</label>
              <textarea
                className="dispute-textarea"
                rows={5}
                placeholder="Nêu rõ căn cứ bằng chứng, đối chiếu nhật ký QR, điều khoản thỏa thuận ca và giải trình của hai bên..."
                value={rationale}
                onChange={(e) => setRationale(e.target.value)}
                required
              />
              <div className={`char-counter ${isRationaleValid ? 'valid' : 'invalid'}`}>
                {rationaleLen} / 100 ký tự tối thiểu {isRationaleValid ? '✓' : '(chưa đủ)'}
              </div>
            </div>

            <div className="dispute-field-group">
              <label>Lý do miễn trừ phạt điểm RS (Nếu có xem xét miễn trừ)</label>
              <input
                type="text"
                className="dispute-input"
                maxLength={500}
                placeholder="Ví dụ: Worker gặp sự cố bất khả kháng về sức khỏe có chứng từ..."
                value={penaltyWaiverReason}
                onChange={(e) => setPenaltyWaiverReason(e.target.value)}
              />
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
                background: isRationaleValid ? 'var(--color-success)' : '#94a3b8',
                borderColor: 'transparent',
                padding: '0.5rem 1.25rem',
              }}
              disabled={submitting || !isRationaleValid}
            >
              {submitting ? 'Đang lưu...' : isEscalated ? 'Ban hành phán quyết cuối' : 'Ghi nhận quyết định'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default DisputeDecisionModal;
