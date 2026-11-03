export const TicketStatsBar = ({ stats }) => {
  return (
    <div className="tickets-stats-bar">
      <div className="ticket-stat-card">
        <span className="ticket-stat-label">Tổng Phiếu Cần Xử Lý</span>
        <span className="ticket-stat-value">{stats.total}</span>
      </div>
      <div className="ticket-stat-card">
        <span className="ticket-stat-label">Chờ Tiếp Nhận (Mới)</span>
        <span className="ticket-stat-value">{stats.openCount}</span>
      </div>
      <div className="ticket-stat-card">
        <span className="ticket-stat-label">Đang Xử Lý</span>
        <span className="ticket-stat-value">{stats.inProgressCount}</span>
      </div>
      <div className="ticket-stat-card risk-card">
        <span className="ticket-stat-label">SLA Nguy Cơ (&lt;24h)</span>
        <span className="ticket-stat-value">{stats.riskCount}</span>
      </div>
    </div>
  );
};

export default TicketStatsBar;

