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
          Tiếp Nhận Phiếu (Tự Gán)
        </button>
      )}
      {isStaff && ticket.status !== 'CLOSED' && onOpenTransferModal && (
        <button
          type="button"
          className="ticket-btn ticket-btn-secondary"
          onClick={() => onOpenTransferModal(ticket)}
          disabled={loading}
        >
          Chuyển Phiếu Cho CC Khác
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
  );
};

export default TicketDetailFooter;
