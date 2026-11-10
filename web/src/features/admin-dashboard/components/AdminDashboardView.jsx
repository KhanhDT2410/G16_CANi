import { useAdminDashboard } from '../hooks/useAdminDashboard';
import MetricsCards from './MetricsCards';
import RecentActivitiesList from './RecentActivitiesList';
import './AdminDashboard.css';

/**
 * Component chính cho Bảng điều khiển Quản trị & Phân tích số liệu (UC-86)
 */
const AdminDashboardView = ({ onNavigateTab }) => {
  const { metrics, activities, loading, refreshing, error, refresh } = useAdminDashboard();

  if (loading) {
    return (
      <div className="dashboard-container">
        <div className="skeleton" style={{ height: '56px', borderRadius: '12px' }} />
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1.25rem' }}>
          <div className="skeleton" style={{ height: '220px', borderRadius: '12px' }} />
          <div className="skeleton" style={{ height: '220px', borderRadius: '12px' }} />
          <div className="skeleton" style={{ height: '220px', borderRadius: '12px' }} />
        </div>
        <div className="skeleton" style={{ height: '350px', borderRadius: '12px' }} />
      </div>
    );
  }

  if (error && !metrics) {
    return (
      <div className="dashboard-container">
        <div className="dashboard-error-state">
          <h3 className="dashboard-error-title">Không thể tải Bảng điều khiển</h3>
          <p className="dashboard-error-desc">{error}</p>
          <button
            type="button"
            className="dashboard-refresh-btn"
            onClick={refresh}
          >
            <span>Thử lại</span>
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard-container animate-fade-in-up">
      {/* Thanh công cụ / Tiêu đề */}
      <div className="dashboard-toolbar">
        <div className="dashboard-toolbar-title">
          <span>Tổng Quan Vận Hành Nền Tảng</span>
        </div>
        <button
          type="button"
          className="dashboard-refresh-btn"
          onClick={refresh}
          disabled={refreshing}
        >
          <span>{refreshing ? 'Đang cập nhật...' : 'Làm mới'}</span>
        </button>
      </div>

      {/* Các khối thẻ chỉ số (User, Verifications, Operations) */}
      <MetricsCards metrics={metrics} onNavigateTab={onNavigateTab} />

      {/* Danh sách 10 hoạt động quản trị gần nhất */}
      <RecentActivitiesList activities={activities} />
    </div>
  );
};

export default AdminDashboardView;
