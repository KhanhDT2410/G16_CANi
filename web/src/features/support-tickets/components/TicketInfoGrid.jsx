export const TicketInfoGrid = ({
  categoryLabel,
  priorityLabel,
  priorityBadgeClass,
  slaDeadline,
  assignedTo,
  formatDateTime,
}) => {
  return (
    <div className="ticket-info-grid">
      <div className="ticket-info-item">
        <span className="label">Danh mục</span>
        <span className="val">{categoryLabel}</span>
      </div>
      <div className="ticket-info-item">
        <span className="label">Mức độ ưu tiên</span>
        <span className="val">
          <span className={`ticket-badge ${priorityBadgeClass}`}>{priorityLabel}</span>
        </span>
      </div>
      <div className="ticket-info-item">
        <span className="label">Hạn cam kết SLA</span>
        <span className="val">{formatDateTime(slaDeadline)}</span>
      </div>
      <div className="ticket-info-item">
        <span className="label">Chuyên viên phụ trách</span>
        <span className="val">
          {assignedTo ? assignedTo.substring(0, 8) + '...' : 'Chưa phân công'}
        </span>
      </div>
    </div>
  );
};

export default TicketInfoGrid;

