export const TicketDetailFooter = ({
  ticket,
  isStaff,
  loading,
  onAssign,
  onOpenTransferModal,
  onOpenCloseModal,
  onResolve,
  onClose,
}) => {
  return (
    <div className="ticket-modal-footer">
      {isStaff && ticket.status === 'OPEN' && (
        <button
          type="button"
          className="ticket-btn ticket-btn-secondary"
          onClick={() => onAssign(ticket.id)}
          disabled={loading}
        >
          Tiếp nhận xử lý
        </button>
      )}
      {isStaff && ticket.status !== 'CLOSED' && onOpenTransferModal && (
        <button
          type="button"
          className="ticket-btn ticket-btn-secondary"
          onClick={() => onOpenTransferModal(ticket)}
          disabled={loading}
        >
          Chuyển chuyên viên khác
        </button>
      )}
      {isStaff && ticket.status !== 'CLOSED' && (
        <button
          type="button"
          className="ticket-btn ticket-btn-danger"
          onClick={() => onOpenCloseModal(ticket)}
          disabled={loading}
        >
          Đóng phiếu hỗ trợ
        </button>
      )}
      {!isStaff && ticket.status !== 'CLOSED' && (
        <button
          type="button"
          className="ticket-btn ticket-btn-success"
          onClick={() => onResolve(ticket.id)}
          disabled={loading}
        >
          Xác nhận đã giải quyết
        </button>
      )}
      <button type="button" className="ticket-btn ticket-btn-secondary" onClick={onClose}>
        Đóng
      </button>
    </div>
  );
};

export default TicketDetailFooter;
