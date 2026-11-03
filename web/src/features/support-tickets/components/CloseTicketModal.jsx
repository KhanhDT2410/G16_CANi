import { useState } from 'react';
import { showToast } from '../../../components/common/Toast';

export const CloseTicketModal = ({ isOpen, onClose, onConfirm, ticket, loading }) => {
  const [rationale, setRationale] = useState('');

  if (!isOpen || !ticket) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (rationale.trim().length < 30) {
      showToast('Lý do đóng phiếu phải từ 30 ký tự trở lên để lưu biên bản xử lý', 'warning');
      return;
    }

    const res = await onConfirm(ticket.id, rationale.trim());
    if (res) {
      setRationale('');
      onClose();
    }
  };

  return (
    <div className="ticket-modal-overlay">
      <div className="ticket-modal">
        <div className="ticket-modal-header">
          <h3>Đóng Phiếu Hỗ Trợ: {ticket.referenceNo}</h3>
          <button type="button" className="ticket-modal-close-btn" onClick={onClose}>
            &times;
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="ticket-modal-body">
            <p style={{ fontSize: '0.88rem', color: '#64748b', margin: 0 }}>
              Sau khi đóng, phiếu hỗ trợ sẽ chuyển sang trạng thái <strong>ĐÃ ĐÓNG</strong> và hệ thống
              sẽ gửi thông báo kèm kết luận xử lý đến người dùng.
            </p>

            <div className="ticket-form-group">
              <label className="ticket-form-label">
                Kết Luận & Lý Do Đóng Phiếu * (Tối thiểu 30 ký tự)
              </label>
              <textarea
                className="ticket-form-textarea"
                placeholder="Nhập chi tiết biện pháp đã xử lý, phương án khắc phục hoặc hướng dẫn cho người dùng..."
                value={rationale}
                onChange={(e) => setRationale(e.target.value)}
                rows={5}
                required
              />
              <span style={{ fontSize: '0.75rem', color: rationale.trim().length < 30 ? '#ef4444' : '#64748b' }}>
                Đã nhập: {rationale.trim().length}/30 ký tự tối thiểu
              </span>
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
              className="ticket-btn ticket-btn-danger"
              disabled={loading}
            >
              {loading ? 'Đang đóng phiếu...' : 'Xác Nhận Đóng Phiếu'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CloseTicketModal;

