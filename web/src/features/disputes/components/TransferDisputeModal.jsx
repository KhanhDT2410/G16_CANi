import { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { showToast } from '../../../components/common/Toast';

const TransferDisputeModal = ({ dispute, staff = [], onClose, onSubmit }) => {
  const [targetCcUserId, setTargetCcUserId] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = prev;
    };
  }, []);

  useEffect(() => {
    if (staff.length === 0) {
      showToast('Không có nhân sự Chăm sóc khách hàng nào khả dụng', 'warning');
    }
  }, [staff.length]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!targetCcUserId) {
      showToast('Vui lòng chọn nhân sự phụ trách tiếp nhận', 'warning');
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit(dispute.id, targetCcUserId);
      onClose();
    } catch {
      // Toast lỗi đã được hook xử lý
    } finally {
      setSubmitting(false);
    }
  };

  const selected = staff.find((s) => s.userId === targetCcUserId);

  return createPortal(
    <div className="dispute-modal-overlay" onClick={onClose}>
      <div className="dispute-modal" onClick={(e) => e.stopPropagation()}>
        <div className="dispute-modal-header">
          <div>
            <h3>Chuyển Tranh Chấp Cho Nhân Sự Khác</h3>
            <span className="dispute-modal-subtitle">
              Mã đơn: <code>{dispute.id}</code> — Tiêu đề: {dispute.title}
            </span>
          </div>
          <button type="button" className="dispute-modal-close" onClick={onClose}>✕</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="dispute-modal-body">
            <div className="dispute-field-group">
              <label>Nhân sự Chăm sóc khách hàng tiếp nhận *</label>
              <select
                className="dispute-select"
                value={targetCcUserId}
                onChange={(e) => setTargetCcUserId(e.target.value)}
                required
              >
                <option value="">-- Chọn nhân sự --</option>
                {staff.map((s) => (
                  <option key={s.userId} value={s.userId}>
                    {s.fullName || s.email || s.userId}
                    {s.assignedCount > 0 ? ` (đang xử lý ${s.assignedCount} đơn)` : ''}
                  </option>
                ))}
              </select>
            </div>

            {selected && (
              <div className="dispute-rule-notice">
                <strong>Thông tin phụ trách:</strong> {selected.fullName || '—'}
                {selected.email ? ` · ${selected.email}` : ''} — hiện đang xử lý{' '}
                <strong>{selected.assignedCount}</strong> đơn chưa đóng.
              </div>
            )}

            <div className="dispute-escalate-notice">
              Sau khi chuyển, tranh chấp chuyển sang trạng thái <strong>Đang điều tra</strong> và
              nhân sự được chọn sẽ nhận được thông báo để tiếp nhận xử lý.
            </div>
          </div>

          <div className="dispute-modal-footer">
            <button type="button" className="btn-cancel" onClick={onClose} disabled={submitting}>
              Hủy
            </button>
            <button type="submit" className="btn-submit" disabled={submitting || !targetCcUserId}>
              {submitting ? 'Đang chuyển...' : 'Xác nhận chuyển đơn'}
            </button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
};

export default TransferDisputeModal;
