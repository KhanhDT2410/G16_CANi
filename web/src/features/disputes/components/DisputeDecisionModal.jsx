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

  const handleQuickFill = () => {
    setRationale(
      'Qua quá trình đối chiếu toàn diện giữa nhật ký điểm danh QR, sao kê ngân hàng và tường trình của hai bên, xác nhận sự việc có vi phạm thỏa thuận ca làm việc. Quyết định được ban hành theo quy chuẩn giải quyết tranh chấp CANi (lý do trên 100 ký tự).'
    );
  };

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
      // Đã toast lỗi
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
              Đơn: <code>{dispute.id}</code> (Trạng thái: <strong>{dispute.status}</strong>)
            </span>
          </div>
          <button type="button" className="dispute-modal-close" onClick={onClose}>✕</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="dispute-modal-body">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
                Ràng buộc: Lý do quyết định bắt buộc từ 100 đến 5000 ký tự.
              </span>
              <button type="button" className="btn-quick-fill" onClick={handleQuickFill}>
                ⚡ Điền lý do mẫu (≥100 ký tự)
              </button>
            </div>

            <div className="dispute-field-group">
              <label>Kết quả xử lý (Outcome) *</label>
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
                placeholder="Nêu rõ căn cứ bằng chứng, điều khoản thỏa thuận ca, kết quả đối chiếu giữa hai bên..."
                value={rationale}
                onChange={(e) => setRationale(e.target.value)}
                required
              />
              <div className={`char-counter ${isRationaleValid ? 'valid' : 'invalid'}`}>
                {rationaleLen} / 100 ký tự tối thiểu {isRationaleValid ? '✓' : '(chưa đủ)'}
              </div>
            </div>

            <div className="dispute-field-group">
              <label>Lý do miễn phạt điểm RS (Tùy chọn - nếu áp dụng miễn trừ)</label>
              <input
                type="text"
                className="dispute-input"
                maxLength={500}
                placeholder="Ví dụ: Worker gặp sự cố bất khả kháng về y tế có xác nhận của trạm y tế..."
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

