import { SERVICE_STATUS } from '../types/adminDashboard.constants';

/**
 * Panel hiển thị trạng thái sức khỏe các dịch vụ hệ thống.
 */
const StatusDot = ({ status }) => {
  const colorMap = {
    [SERVICE_STATUS.HEALTHY]: 'var(--color-success)',
    [SERVICE_STATUS.DEGRADED]: 'var(--color-warning)',
    [SERVICE_STATUS.DOWN]: 'var(--color-danger)',
  };
  return (
    <span
      className="adm-health-dot"
      style={{ background: colorMap[status] || '#94a3b8' }}
    />
  );
};

const SystemHealthPanel = ({ services = [] }) => {
  const healthyCount = services.filter(
    (s) => s.status === SERVICE_STATUS.HEALTHY
  ).length;

  return (
    <div className="adm-panel adm-health-panel animate-fade-in-up">
      <div className="adm-panel-header">
        <h3 className="adm-panel-title">
          <span className="adm-panel-icon">🖥️</span>
          Sức Khỏe Hệ Thống
        </h3>
        <span className="adm-health-summary">
          {healthyCount}/{services.length} dịch vụ hoạt động
        </span>
      </div>
      <div className="adm-health-list">
        {services.map((svc) => (
          <div key={svc.name} className="adm-health-row">
            <div className="adm-health-name">
              <StatusDot status={svc.status} />
              <span>{svc.name}</span>
            </div>
            <div className="adm-health-stats">
              <span className="adm-health-uptime">
                {svc.uptime}% uptime
              </span>
              <span
                className={`adm-health-latency ${
                  svc.latency > 500 ? 'warning' : ''
                }`}
              >
                {svc.latency}ms
              </span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default SystemHealthPanel;
