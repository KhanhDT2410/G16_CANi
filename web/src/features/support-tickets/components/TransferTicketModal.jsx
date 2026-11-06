import { useState, useEffect } from 'react';
import { showToast } from '../../../components/common/Toast';

export const TransferTicketModal = ({ isOpen, onClose, onConfirm, ticket, staff = [], loading }) => {
  const [targetCcUserId, setTargetCcUserId] = useState('');

  useEffect(() => {
    if (isOpen) {
      setTargetCcUserId('');
      if (staff.length === 0) {
        showToast('Không có nhân sự Chăm sóc khách hàng nào khả dụng', 'warning');
      }
    }
  }, [isOpen, staff.length]);

  if (!isOpen || !ticket) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!targetCcUserId) {
      showToast('Vui lòng chọn chuyên viên CSKH tiếp nhận phiếu', 'warning');
      return;
    }

    const res = await onConfirm(ticket.id, targetCcUserId);
    if (res) {
      onClose();
    }
  };

  return (
    <div className="ticket-modal-overlay">
      <div className="ticket-modal">
        <div className="ticket-modal-header">
          <h3>Chuyển Phiếu Hỗ Trợ: {ticket.referenceNo}</h3>
          <button type="button" className="ticket-modal-close-btn" onClick={onClose}>
            &times;
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="ticket-modal-body">
            <p style={{ fontSize: '0.88rem', color: '#64748b', margin: 0 }}>
              Chuyển quyền tiếp nhận và xử lý phiếu hỗ trợ này cho chuyên viên khác trong đội Customer Care.
            </p>

            <div className="ticket-form-group">
              <label className="ticket-form-label">
                Chuyên Viên CSKH Tiếp Nhận *
              </label>
              <select
                className="ticket-form-select"
                value={targetCcUserId}
                onChange={(e) => setTargetCcUserId(e.target.value)}
                required
              >
                <option value="">-- Chọn chuyên viên CSKH --</option>
                {staff.map((s) => (
                  <option key={s.userId} value={s.userId}>
                    {s.fullName || s.email || s.userId}
                    {s.assignedCount > 0 ? ` (đang xử lý ${s.assignedCount} phiếu)` : ' (đang rảnh)'}
                  </option>
                ))}
              </select>
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
              {loading ? 'Đang chuyển...' : 'Xác Nhận Chuyển'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default TransferTicketModal;

