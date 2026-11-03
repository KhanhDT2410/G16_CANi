import { useState } from 'react';
import {
  TICKET_STATUS_CONFIG,
  TICKET_CATEGORY_CONFIG,
  TICKET_PRIORITY_CONFIG,
} from '../types/ticket.constants';
import TicketMessageThread from './TicketMessageThread';
import { showToast } from '../../../components/common/Toast';

export const TicketDetailModal = ({
  isOpen,
  onClose,
  ticket,
  isStaff,
  onReply,
  onAssign,
  onOpenCloseModal,
  onResolve,
  loading,
}) => {
  const [replyBody, setReplyBody] = useState('');
  const [isInternal, setIsInternal] = useState(false);
  const [submittingReply, setSubmittingReply] = useState(false);

  if (!isOpen || !ticket) return null;

  const handleSendReply = async (e) => {
    e.preventDefault();
    if (!replyBody.trim()) {
      showToast('Vui lòng nhập nội dung tin nhắn', 'warning');
      return;
    }

    setSubmittingReply(true);
    const res = await onReply(ticket.id, {
      body: replyBody.trim(),
      isInternal: isStaff ? isInternal : false,
    }, isStaff);

    setSubmittingReply(false);
    if (res) {
      setReplyBody('');
      setIsInternal(false);
    }
  };

  const statusConfig = TICKET_STATUS_CONFIG[ticket.status] || { label: ticket.status, badgeClass: '' };
  const priorityConfig = TICKET_PRIORITY_CONFIG[ticket.priority] || { label: ticket.priority, badgeClass: '' };
  const categoryConfig = TICKET_CATEGORY_CONFIG[ticket.category] || { label: ticket.category };

  const formatDateTime = (isoStr) => {
    if (!isoStr) return '—';
    return new Date(isoStr).toLocaleString('vi-VN', {
      hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric',
    });
  };

  return (
    <div className="ticket-modal-overlay">
      <div className="ticket-modal ticket-modal-lg">
        <div className="ticket-modal-header">
          <div>
            <h3 style={{ display: 'inline', marginRight: '0.75rem' }}>
              Phiếu: {ticket.referenceNo}
            </h3>
            <span className={`ticket-badge ${statusConfig.badgeClass}`}>
              {statusConfig.label}
            </span>
          </div>
          <button type="button" className="ticket-modal-close-btn" onClick={onClose}>
            &times;
          </button>
        </div>

        <div className="ticket-modal-body">
          {ticket.slaAtRisk && ticket.status !== 'CLOSED' && (
            <div style={{ background: '#fef2f2', border: '1px solid #ef4444', padding: '0.65rem 1rem', borderRadius: '6px', color: '#b91c1c', fontSize: '0.85rem', fontWeight: 600 }}>
              CẢNH BÁO SLA: Phiếu này đang có nguy cơ vi phạm thời gian cam kết xử lý (hạn chót: {formatDateTime(ticket.slaDeadline)}).
            </div>
          )}

          <div className="ticket-info-grid">
            <div className="ticket-info-item">
              <span className="label">Danh mục</span>
              <span className="val">{categoryConfig.label}</span>
            </div>
            <div className="ticket-info-item">
              <span className="label">Mức độ ưu tiên</span>
              <span className="val">
                <span className={`ticket-badge ${priorityConfig.badgeClass}`}>{priorityConfig.label}</span>
              </span>
            </div>
            <div className="ticket-info-item">
              <span className="label">Hạn cam kết SLA</span>
              <span className="val">{formatDateTime(ticket.slaDeadline)}</span>
            </div>
            <div className="ticket-info-item">
              <span className="label">Chuyên viên phụ trách</span>
              <span className="val">{ticket.assignedTo ? ticket.assignedTo.substring(0, 8) + '...' : 'Chưa phân công'}</span>
            </div>
          </div>

          <div>
            <div style={{ fontSize: '0.85rem', fontWeight: 700, color: '#1e293b', marginBottom: '0.35rem' }}>
              Tiêu đề: {ticket.subject}
            </div>
            <div style={{ fontSize: '0.88rem', color: '#475569', lineHeight: 1.5, background: '#f8fafc', padding: '0.75rem 1rem', borderRadius: '6px', border: '1px solid #e2e8f0' }}>
              {ticket.description}
            </div>
          </div>

          <TicketMessageThread
            messages={ticket.messages}
            ticketUserId={ticket.userId}
            formatDateTime={formatDateTime}
          />

          {ticket.canReply && (
            <form onSubmit={handleSendReply} className="ticket-reply-box">
              <label className="ticket-form-label">
                {isStaff ? 'Gửi Phản Hồi Hoặc Ghi Chú' : 'Gửi Phản Hồi'}
              </label>
              <textarea
                className="ticket-form-textarea"
                placeholder={isInternal ? 'Nhập ghi chú nội bộ (chỉ CC/Admin thấy)...' : 'Nhập tin nhắn phản hồi...'}
                value={replyBody}
                onChange={(e) => setReplyBody(e.target.value)}
                rows={3}
                required
              />
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                {isStaff ? (
                  <label className="ticket-checkbox-group">
                    <input
                      type="checkbox"
                      checked={isInternal}
                      onChange={(e) => setIsInternal(e.target.checked)}
                    />
                    Đặt làm ghi chú nội bộ (Người dùng không nhìn thấy)
                  </label>
                ) : <div />}
                <button
                  type="submit"
                  className="ticket-btn ticket-btn-primary ticket-btn-sm"
                  disabled={submittingReply}
                >
                  {submittingReply ? 'Đang gửi...' : 'Gửi Tin Nhắn'}
                </button>
              </div>
            </form>
          )}
        </div>

        <div className="ticket-modal-footer">
          {isStaff && ticket.status === 'OPEN' && (
            <button
              type="button"
              className="ticket-btn ticket-btn-secondary"
              onClick={() => onAssign(ticket.id)}
              disabled={loading}
            >
              Tiếp Nhận Phiếu (Tự Gán)
            </button>
          )}
          {isStaff && ticket.status !== 'CLOSED' && (
            <button
              type="button"
              className="ticket-btn ticket-btn-danger"
              onClick={() => onOpenCloseModal(ticket)}
              disabled={loading}
            >
              Đóng Phiếu Kèm Kết Luận
            </button>
          )}
          {!isStaff && ticket.status !== 'CLOSED' && (
            <button
              type="button"
              className="ticket-btn ticket-btn-success"
              onClick={() => onResolve(ticket.id)}
              disabled={loading}
            >
              Vấn Đề Đã Được Giải Quyết
            </button>
          )}
          <button type="button" className="ticket-btn ticket-btn-secondary" onClick={onClose}>
            Đóng Cửa Sổ
          </button>
        </div>
      </div>
    </div>
  );
};

export default TicketDetailModal;
