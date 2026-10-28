import { showToast } from '../../../components/common/Toast';

export default function EOSidebar({ activeNav, onNavClick }) {
  return (
    <aside className="eo-sidebar">
      <nav className="eo-sidebar-nav">
        <button
          type="button"
          className={`eo-nav-item ${activeNav === 'overview' ? 'active' : ''}`}
          onClick={() => onNavClick('overview')}
        >
          <span className="nav-icon">📊</span>
          <span className="nav-label">Tổng quan</span>
        </button>

        <button
          type="button"
          className={`eo-nav-item ${activeNav === 'events' ? 'active' : ''}`}
          onClick={() => onNavClick('events')}
        >
          <span className="nav-icon">📅</span>
          <span className="nav-label">Quản lý sự kiện</span>
        </button>

        <button
          type="button"
          className={`eo-nav-item ${activeNav === 'recruitment' ? 'active' : ''}`}
          onClick={() => onNavClick('recruitment')}
        >
          <span className="nav-icon">👥</span>
          <span className="nav-label">Tuyển dụng nhân sự</span>
        </button>

        <button
          type="button"
          className={`eo-nav-item ${activeNav === 'payments' ? 'active' : ''}`}
          onClick={() => onNavClick('payments')}
        >
          <span className="nav-icon">💳</span>
          <span className="nav-label">Thanh toán & lương</span>
        </button>

        <button
          type="button"
          className={`eo-nav-item ${activeNav === 'ekyc' ? 'active' : ''}`}
          onClick={() => onNavClick('ekyc')}
        >
          <span className="nav-icon">🛡️</span>
          <span className="nav-label">E-KYC doanh nghiệp</span>
        </button>

        <button
          type="button"
          className={`eo-nav-item ${activeNav === 'analytics' ? 'active' : ''}`}
          onClick={() => onNavClick('analytics')}
        >
          <span className="nav-icon">📈</span>
          <span className="nav-label">Báo cáo & thống kê</span>
        </button>

        <button
          type="button"
          className={`eo-nav-item ${activeNav === 'settings' ? 'active' : ''}`}
          onClick={() => onNavClick('settings')}
        >
          <span className="nav-icon">⚙️</span>
          <span className="nav-label">Cài đặt</span>
        </button>
      </nav>

      {/* Promo Card ở đáy sidebar */}
      <div className="eo-sidebar-promo-card">
        <div className="promo-badge-icon">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect>
            <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path>
          </svg>
        </div>
        <h4 className="promo-title">Tổ chức sự kiện dễ dàng cùng CANi</h4>
        <p className="promo-desc">
          Quản lý nhân sự, tự động hoá chấm công và đối soát thanh toán trực tiếp sau ca.
        </p>
        <button
          type="button"
          className="promo-link-btn"
          onClick={() => showToast('Mở tài liệu hướng dẫn tổ chức sự kiện!', 'info')}
        >
          Tìm hiểu thêm &rarr;
        </button>
      </div>
    </aside>
  );
}
