import { useAdminDashboard } from '../hooks/useAdminDashboard';
import SystemHealthPanel from './SystemHealthPanel';
import ActiveEventsTable from './ActiveEventsTable';
import RecentDisputesTable from './RecentDisputesTable';
import CskhOpsPanel from './CskhOpsPanel';
import '../adminDashboard.css';

/**
 * W-38: Admin System Dashboard – Screen
 *
 * Hiển thị tổng quan nền tảng cho Admin:
 * - Metric cards: người dùng, sự kiện, tranh chấp, doanh thu
 * - Sức khỏe hệ thống (service status)
 * - Sự kiện đang hoạt động
 * - Tranh chấp gần đây (escalated → Admin)
 * - Vận hành CSKH
 */

const MetricCard = ({ icon, label, value, change, unit, color }) => {
  const isPositive = change >= 0;
  return (
    <div className="adm-metric-card animate-fade-in-up">
      <div className="adm-metric-icon" style={{ color }}>
        {icon}
      </div>
      <div className="adm-metric-body">
        <div className="adm-metric-label">{label}</div>
        <div className="adm-metric-value" style={{ color }}>
          {typeof value === 'number'
            ? value.toLocaleString('vi-VN')
            : value}
          {unit && <span className="adm-metric-unit">{unit}</span>}
        </div>
        {change !== undefined && change !== null && (
          <div
            className={`adm-metric-change ${
              isPositive ? 'positive' : 'negative'
            }`}
          >
            {isPositive ? '▲' : '▼'} {Math.abs(change)}% so với tuần trước
          </div>
        )}
      </div>
    </div>
  );
};

const DashboardSkeleton = () => (
  <div className="adm-skeleton-wrap">
    <div className="adm-metrics-grid">
      {[1, 2, 3, 4].map((i) => (
        <div key={i} className="skeleton" style={{ height: 120, borderRadius: 12 }} />
      ))}
    </div>
    <div className="adm-grid-2col" style={{ marginTop: '1.5rem' }}>
      <div className="skeleton" style={{ height: 280, borderRadius: 12 }} />
      <div className="skeleton" style={{ height: 280, borderRadius: 12 }} />
    </div>
    <div className="skeleton" style={{ height: 300, borderRadius: 12, marginTop: '1.5rem' }} />
  </div>
);

const AdminDashboard = () => {
  const {
    metrics,
    systemHealth,
    activeEvents,
    recentDisputes,
    cskhOps,
    loading,
    lastRefresh,
    refresh,
  } = useAdminDashboard();

  if (loading || !metrics) {
    return (
      <div className="adm-dashboard">
        <div className="adm-dash-header">
          <div>
            <h1 className="adm-dash-title">
              Admin System Dashboard
              <span className="adm-dash-badge">ADMIN</span>
            </h1>
            <p className="adm-dash-subtitle">Đang tải dữ liệu...</p>
          </div>
        </div>
        <DashboardSkeleton />
      </div>
    );
  }

  return (
    <div className="adm-dashboard">
      {/* Header */}
      <div className="adm-dash-header">
        <div>
          <h1 className="adm-dash-title">
            Admin System Dashboard
            <span className="adm-dash-badge">ADMIN</span>
          </h1>
          <p className="adm-dash-subtitle">
            Giám sát nền tảng, người dùng, sự kiện, tranh chấp &amp; vận hành
            Customer Care
          </p>
        </div>
        <div className="adm-dash-actions">
          {lastRefresh && (
            <span className="adm-last-refresh">
              Cập nhật lúc{' '}
              {lastRefresh.toLocaleTimeString('vi-VN', {
                hour: '2-digit',
                minute: '2-digit',
              })}
            </span>
          )}
          <button
            type="button"
            className="adm-refresh-btn"
            onClick={refresh}
            title="Làm mới dữ liệu"
          >
            🔄 Làm mới
          </button>
        </div>
      </div>

      {/* KPI Metric Cards */}
      <div className="adm-metrics-grid">
        <MetricCard
          icon="👥"
          label="Người Dùng Hoạt Động"
          value={metrics.totalActiveUsers}
          change={metrics.activeUsersChange}
          color="var(--color-primary)"
        />
        <MetricCard
          icon="📅"
          label="Sự Kiện Đang Chạy"
          value={metrics.totalActiveEvents}
          change={metrics.activeEventsChange}
          color="#0284c7"
        />
        <MetricCard
          icon="⚠️"
          label="Tranh Chấp Mở"
          value={metrics.openDisputes}
          change={metrics.openDisputesChange}
          color="var(--color-danger)"
        />
        <MetricCard
          icon="💰"
          label="Doanh Thu Tháng"
          value={metrics.totalRevenue}
          change={metrics.revenueChange}
          unit="đ"
          color="var(--color-success)"
        />
      </div>

      {/* Secondary metrics row */}
      <div className="adm-secondary-metrics">
        <div className="adm-sec-metric">
          <span className="adm-sec-label">Tổng Worker</span>
          <span className="adm-sec-value">
            {metrics.totalWorkers.toLocaleString('vi-VN')}
          </span>
        </div>
        <div className="adm-sec-divider" />
        <div className="adm-sec-metric">
          <span className="adm-sec-label">Tổng EO</span>
          <span className="adm-sec-value">{metrics.totalEOs}</span>
        </div>
        <div className="adm-sec-divider" />
        <div className="adm-sec-metric">
          <span className="adm-sec-label">Phân Công Hôm Nay</span>
          <span className="adm-sec-value">{metrics.totalAssignmentsToday}</span>
        </div>
        <div className="adm-sec-divider" />
        <div className="adm-sec-metric">
          <span className="adm-sec-label">Match Score TB</span>
          <span className="adm-sec-value">{metrics.avgMatchScore}%</span>
        </div>
        <div className="adm-sec-divider" />
        <div className="adm-sec-metric">
          <span className="adm-sec-label">Tỷ Lệ No-Show</span>
          <span className="adm-sec-value adm-sec-danger">
            {metrics.noShowRate}%
          </span>
        </div>
        <div className="adm-sec-divider" />
        <div className="adm-sec-metric">
          <span className="adm-sec-label">CSKH Resolution</span>
          <span className="adm-sec-value adm-sec-success">
            {metrics.cskhResolutionRate}%
          </span>
        </div>
      </div>

      {/* System Health + CSKH Ops */}
      <div className="adm-grid-2col">
        <SystemHealthPanel services={systemHealth} />
        <CskhOpsPanel data={cskhOps} />
      </div>

      {/* Active Events */}
      <ActiveEventsTable events={activeEvents} />

      {/* Recent Disputes */}
      <RecentDisputesTable disputes={recentDisputes} />
    </div>
  );
};

export default AdminDashboard;
