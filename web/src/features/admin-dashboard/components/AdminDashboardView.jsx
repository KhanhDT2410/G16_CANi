import { useAdminDashboard } from '../hooks/useAdminDashboard';
import MetricsCards from './MetricsCards';
import RecentActivitiesList from './RecentActivitiesList';
import './AdminDashboard.css';

/**
 * Component chính cho Bảng điều khiển Quản trị & Phân tích số liệu (UC-86)
 */
const AdminDashboardView = ({ onNavigateTab }) => {
  const { metrics, activities, loading, refreshing, error, isDemoFallback, refresh } = useAdminDashboard();

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
        <div style={{ textAlign: 'center', padding: '3rem 1rem', background: '#fff', borderRadius: '0.75rem', border: '1px solid #fee2e2' }}>
          <div style={{ fontSize: '2.5rem', marginBottom: '0.75rem' }}>⚠️</div>
          <h3 style={{ color: '#dc2626', margin: '0 0 0.5rem 0' }}>Không thể tải Bảng điều khiển</h3>
          <p style={{ color: '#6b7280', margin: '0 0 1.25rem 0' }}>{error}</p>
          <button
            type="button"
            className="dashboard-refresh-btn"
            onClick={refresh}
            style={{ margin: '0 auto' }}
          >
            Thử lại
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
          <span>📊</span>
          <span>Tổng Quan Vận Hành Nền Tảng CANi</span>
          {isDemoFallback && (
            <span
              style={{
                fontSize: '0.75rem',
                fontWeight: 600,
                padding: '0.2rem 0.5rem',
                background: '#fef3c7',
                color: '#b45309',
                borderRadius: '0.375rem',
                marginLeft: '0.5rem',
              }}
              title="Backend đang tắt hoặc chưa đăng nhập Admin - Hiển thị dữ liệu mẫu để bạn test giao diện"
            >
              ⚡ Dữ liệu Demo (Offline)
            </span>
          )}
        </div>
        <button
          type="button"
          className="dashboard-refresh-btn"
          onClick={refresh}
          disabled={refreshing}
        >
          <span>{refreshing ? '🔄 Đang làm mới...' : '🔄 Làm mới dữ liệu'}</span>
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
