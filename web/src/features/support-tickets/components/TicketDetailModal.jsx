import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import {
  TICKET_STATUS_CONFIG,
  TICKET_CATEGORY_CONFIG,
  TICKET_PRIORITY_CONFIG,
} from '../types/ticket.constants';
import TicketInfoGrid from './TicketInfoGrid';
import TicketAttachmentsView from './TicketAttachmentsView';
import TicketMessageThread from './TicketMessageThread';
import TicketReplyForm from './TicketReplyForm';
import TicketDetailFooter from './TicketDetailFooter';

export const TicketDetailModal = ({
  isOpen,
  onClose,
  ticket,
  isStaff,
  onReply,
  onAssign,
  onOpenTransferModal,
  onOpenCloseModal,
  onResolve,
  loading,
}) => {
  const [submittingReply, setSubmittingReply] = useState(false);

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

  if (!isOpen || !ticket || typeof document === 'undefined') return null;

  const handleSendReply = async (payload) => {
    setSubmittingReply(true);
    const res = await onReply(ticket.id, payload, isStaff);
    setSubmittingReply(false);
    return Boolean(res);
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

  const modalNode = (
    <div className="ticket-modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="ticket-modal ticket-modal-lg" onClick={(e) => e.stopPropagation()}>
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

          <TicketInfoGrid
            categoryLabel={categoryConfig.label}
            priorityLabel={priorityConfig.label}
            priorityBadgeClass={priorityConfig.badgeClass}
            slaDeadline={ticket.slaDeadline}
            assignedTo={ticket.assignedTo}
            formatDateTime={formatDateTime}
            customerName={ticket.userName}
            customerPhone={ticket.userPhone}
            customerEmail={ticket.userEmail}
          />

          <div>
            <div style={{ fontSize: '0.85rem', fontWeight: 700, color: '#1e293b', marginBottom: '0.35rem' }}>
              Tiêu đề: {ticket.subject}
            </div>
            <div style={{ fontSize: '0.88rem', color: '#475569', lineHeight: 1.5, background: '#f8fafc', padding: '0.75rem 1rem', borderRadius: '6px', border: '1px solid #e2e8f0' }}>
              {ticket.description}
            </div>
          </div>

          <TicketAttachmentsView attachments={ticket.attachments} />

          <TicketMessageThread
            messages={ticket.messages}
            ticketUserId={ticket.userId}
            formatDateTime={formatDateTime}
          />

          {ticket.canReply && (
            <TicketReplyForm
              isStaff={isStaff}
              onSubmit={handleSendReply}
              submitting={submittingReply}
            />
          )}
        </div>

        <TicketDetailFooter
          ticket={ticket}
          isStaff={isStaff}
          loading={loading}
          onAssign={onAssign}
          onOpenTransferModal={onOpenTransferModal}
          onOpenCloseModal={onOpenCloseModal}
          onResolve={onResolve}
          onClose={onClose}
        />
      </div>
    </div>
  );

  return createPortal(modalNode, document.body);
};

export default TicketDetailModal;
