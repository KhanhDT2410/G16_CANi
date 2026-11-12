export const TicketInfoGrid = ({
  categoryLabel,
  priorityLabel,
  priorityBadgeClass,
  slaDeadline,
  assignedTo,
  formatDateTime,
  customerName,
  customerPhone,
  customerEmail,
}) => {
  return (
    <div className="ticket-info-grid">
      {customerName && (
        <div className="ticket-info-item" style={{ gridColumn: '1 / -1' }}>
          <span className="label">Khách hàng</span>
          <span className="val" style={{ fontWeight: 700, color: '#0f172a' }}>
            {customerName}
            {(customerPhone || customerEmail) && (
              <span style={{ fontWeight: 400, color: '#64748b', marginLeft: '0.5rem' }}>
                (SĐT: {customerPhone || '—'} · Email: {customerEmail || '—'})
              </span>
            )}
          </span>
        </div>
      )}
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
          {assignedTo ? (assignedTo.length > 10 ? assignedTo.substring(0, 8) + '...' : assignedTo) : 'Chưa phân công'}
        </span>
      </div>
    </div>
  );
};

export default TicketInfoGrid;
