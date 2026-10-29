/**
 * Panel tổng quan vận hành Customer Care (CSKH).
 */
const CskhOpsPanel = ({ data }) => {
  if (!data) return null;

  return (
    <div className="adm-panel adm-cskh-panel animate-fade-in-up">
      <div className="adm-panel-header">
        <h3 className="adm-panel-title">
          <span className="adm-panel-icon">🎧</span>
          Vận Hành Customer Care
        </h3>
        <span className="adm-cskh-online">
          <span className="adm-health-dot" style={{ background: 'var(--color-success)' }} />
          {data.cskhOnline}/{data.cskhTotal} nhân viên online
        </span>
      </div>
      <div className="adm-cskh-grid">
        <div className="adm-cskh-stat">
          <div className="adm-cskh-stat-value danger">{data.ticketsOpen}</div>
          <div className="adm-cskh-stat-label">Ticket mở</div>
        </div>
        <div className="adm-cskh-stat">
          <div className="adm-cskh-stat-value warning">{data.ticketsInProgress}</div>
          <div className="adm-cskh-stat-label">Đang xử lý</div>
        </div>
        <div className="adm-cskh-stat">
          <div className="adm-cskh-stat-value success">{data.ticketsClosed}</div>
          <div className="adm-cskh-stat-label">Đã đóng</div>
        </div>
        <div className="adm-cskh-stat">
          <div className="adm-cskh-stat-value info">{data.avgResponseTime}</div>
          <div className="adm-cskh-stat-label">Phản hồi TB</div>
        </div>
        <div className="adm-cskh-stat">
          <div className="adm-cskh-stat-value info">{data.avgResolutionTime}</div>
          <div className="adm-cskh-stat-label">Xử lý TB</div>
        </div>
      </div>
    </div>
  );
};

export default CskhOpsPanel;
