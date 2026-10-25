import { useState } from 'react';
import CaniLogo from '../../components/common/CaniLogo';
import { showToast } from '../../components/common/Toast';
import './MobileShowcasePage.css';

/**
 * MobileShowcasePage
 * Cho phép xem trực quan và tương tác với 2 màn hình Mobile Worker App:
 * 1. Màn hình Đăng nhập (Login Screen)
 * 2. Màn hình Bản đồ việc làm Radar (Job Radar Screen)
 */
const MobileShowcasePage = () => {
  const [activeScreen, setActiveScreen] = useState('radar'); // 'login' | 'radar'
  const [isOnline, setIsOnline] = useState(true);
  const [selectedRadius, setSelectedRadius] = useState(15);
  const [radarScanning, setRadarScanning] = useState(false);
  const [bottomTab, setBottomTab] = useState('map'); // 'map', 'jobs', 'notif', 'wallet', 'profile'
  const [showPassword, setShowPassword] = useState(false);

  const handleScanAgain = () => {
    setRadarScanning(true);
    showToast('Đang phát sóng GPS quét tìm các ca làm việc trong bán kính 15km...', 'info');
    setTimeout(() => {
      setRadarScanning(false);
      showToast('Đã quét xong: Tìm thấy 3 ca sự kiện phù hợp gần bạn!', 'success');
    }, 1500);
  };

  return (
    <div className="mobile-showcase-container animate-fade-in-up">
      {/* Top Banner Control */}
      <div className="showcase-toolbar">
        <div className="toolbar-info">
          <h2>📱 CANi Worker Mobile App Simulator</h2>
          <p>Mô phỏng 100% giao diện thực tế ứng dụng Mobile dành cho nhân sự (Worker)</p>
        </div>
        <div className="screen-switcher-pills">
          <button
            type="button"
            className={`pill-btn ${activeScreen === 'login' ? 'active' : ''}`}
            onClick={() => setActiveScreen('login')}
          >
            Màn Hình 1: Đăng Nhập Worker
          </button>
          <button
            type="button"
            className={`pill-btn ${activeScreen === 'radar' ? 'active' : ''}`}
            onClick={() => setActiveScreen('radar')}
          >
            Màn Hình 2: Bản Đồ Việc Làm Radar
          </button>
        </div>
      </div>

      {/* Khung iPhone 16 Pro Frame */}
      <div className="device-stage">
        <div className="iphone-frame">
          {/* Dynamic Island */}
          <div className="iphone-island">
            <div className="island-camera"></div>
            <div className="island-sensor"></div>
          </div>

          {/* Status Bar */}
          <div className="iphone-status-bar">
            <span className="status-time">9:41</span>
            <div className="status-icons">
              <span className="signal-icon">📶</span>
              <span className="wifi-icon">􀙇</span>
              <span className="battery-icon">🔋</span>
            </div>
          </div>

          {/* MÀN HÌNH 1: ĐĂNG NHẬP WORKER */}
          {activeScreen === 'login' && (
            <div className="phone-screen-content login-screen animate-fade-in">
              {/* Header Logo */}
              <div className="phone-login-header">
                <CaniLogo variant="worker" height={44} />
                <p className="phone-login-subtitle">
                  Đăng nhập để nhận ca làm sự kiện linh hoạt
                </p>
              </div>

              {/* Worker Illustration Card */}
              <div className="phone-illustration-card">
                <div className="stage-spotlight left"></div>
                <div className="stage-spotlight right"></div>
                <div className="worker-vector-art">
                  <div className="worker-cap"></div>
                  <div className="worker-face">
                    <div className="worker-eye left"></div>
                    <div className="worker-eye right"></div>
                    <div className="worker-smile"></div>
                  </div>
                  <div className="worker-shirt">
                    <div className="worker-badge-card">
                      <div className="badge-dot"></div>
                    </div>
                  </div>
                  <div className="worker-phone"></div>
                </div>
              </div>

              {/* Form Input Fields */}
              <div className="phone-form">
                <div className="phone-input-group">
                  <label className="phone-label">Email</label>
                  <div className="phone-input-wrapper">
                    <span className="phone-input-icon">✉️</span>
                    <input
                      type="email"
                      className="phone-input"
                      defaultValue="worker@cani.vn"
                      placeholder="worker@cani.vn"
                    />
                  </div>
                </div>

                <div className="phone-input-group">
                  <label className="phone-label">Mật khẩu</label>
                  <div className="phone-input-wrapper">
                    <span className="phone-input-icon">🔒</span>
                    <input
                      type={showPassword ? 'text' : 'password'}
                      className="phone-input"
                      defaultValue="123456"
                      placeholder="••••••••"
                    />
                    <button
                      type="button"
                      className="btn-toggle-eye"
                      onClick={() => setShowPassword(!showPassword)}
                    >
                      {showPassword ? '👁️' : '👁️‍🗨️'}
                    </button>
                  </div>
                </div>

                <button
                  type="button"
                  className="phone-btn-login"
                  onClick={() => {
                    showToast('Đăng nhập Worker thành công! Đang chuyển sang Bản đồ radar...', 'success');
                    setTimeout(() => setActiveScreen('radar'), 600);
                  }}
                >
                  <span>Đăng Nhập</span>
                  <span className="arrow-icon">&rarr;</span>
                </button>

                <div className="phone-divider">
                  <span className="divider-line"></span>
                  <span className="divider-text">Hoặc</span>
                  <span className="divider-line"></span>
                </div>

                <button
                  type="button"
                  className="phone-btn-google"
                  onClick={() => {
                    showToast('Đang kết nối tài khoản Google...', 'info');
                    setTimeout(() => setActiveScreen('radar'), 800);
                  }}
                >
                  <img
                    src="https://www.gstatic.com/images/branding/product/2x/googleg_48dp.png"
                    alt="Google"
                    className="google-g-icon"
                  />
                  <span>Đăng nhập với Google</span>
                </button>

                <p className="phone-register-hint">
                  Chưa có tài khoản Worker? <span className="highlight-link">Đăng ký ngay</span>
                </p>
              </div>
            </div>
          )}

          {/* MÀN HÌNH 2: BẢN ĐỒ VIỆC LÀM RADAR */}
          {activeScreen === 'radar' && (
            <div className="phone-screen-content radar-screen animate-fade-in">
              {/* App Bar */}
              <div className="phone-appbar">
                <div className="user-profile-chip">
                  <div className="avatar-circle">T</div>
                  <div className="user-text">
                    <span className="appbar-title">Bản Đồ Việc Làm</span>
                    <span className="appbar-user-name">Thân Bình Minh</span>
                  </div>
                </div>
                <div className="appbar-actions">
                  <button type="button" className="appbar-btn" title="Lịch sử ca làm">💼</button>
                  <button type="button" className="appbar-btn" title="Chứng chỉ & Điểm">📋</button>
                  <button type="button" className="appbar-btn" title="Làm mới">🔄</button>
                </div>
              </div>

              {/* Status Switch Card */}
              <div className={`radar-status-card ${isOnline ? 'online' : 'offline'}`}>
                <div className="status-dot-pulse"></div>
                <div className="status-text-group">
                  <span className="status-title">
                    {isOnline ? 'CHẾ ĐỘ TÌM VIỆC ĐANG BẬT' : 'CHẾ ĐỘ NGHỈ (OFFLINE)'}
                  </span>
                  <span className="status-subtitle">
                    {isOnline
                      ? `Bạn sẽ nhận được đề xuất việc trong 1km`
                      : 'Bật công tắc để bắt đầu quét việc quanh bạn'}
                  </span>
                </div>
                <label className="toggle-switch">
                  <input
                    type="checkbox"
                    checked={isOnline}
                    onChange={(e) => setIsOnline(e.target.checked)}
                  />
                  <span className="slider"></span>
                </label>
              </div>

              {/* Location Row */}
              <div className="radar-location-bar">
                <span className="pin-icon">📍</span>
                <span className="coords-text">Vị trí GPS thực: 20.9967, 105.5217</span>
                <button
                  type="button"
                  className="btn-update-loc"
                  onClick={() => showToast('Đã định vị lại GPS chính xác!', 'info')}
                >
                  🔄 Cập nhật
                </button>
              </div>

              {/* Distance Radius Pills */}
              <div className="radar-radius-row">
                <span className="radius-lbl">Bán kính:</span>
                <div className="radius-pills">
                  {[5, 10, 15].map((km) => (
                    <button
                      type="button"
                      key={km}
                      className={`radius-pill ${selectedRadius === km ? 'active' : ''}`}
                      onClick={() => setSelectedRadius(km)}
                    >
                      {km}km
                    </button>
                  ))}
                  <button type="button" className="radius-pill map-btn">
                    🗺️ Bản đồ
                  </button>
                </div>
              </div>

              {/* Subtitle count */}
              <div className="radar-sub-count">
                <span>ⓘ Tìm thấy 0 ca làm việc phù hợp xung quanh</span>
              </div>

              {/* Interactive Vector Map View */}
              <div className="radar-map-viewport">
                {/* SVG Vector Map Canvas */}
                <svg className="vector-map-bg" viewBox="0 0 360 380" preserveAspectRatio="none">
                  <defs>
                    <linearGradient id="riverGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                      <stop offset="0%" stopColor="#bae6fd" />
                      <stop offset="100%" stopColor="#7dd3fc" />
                    </linearGradient>
                    <radialGradient id="radarPulseGrad" cx="50%" cy="50%" r="50%">
                      <stop offset="0%" stopColor="#0077FF" stopOpacity="0.25" />
                      <stop offset="65%" stopColor="#38BDF8" stopOpacity="0.18" />
                      <stop offset="100%" stopColor="#0077FF" stopOpacity="0.03" />
                    </radialGradient>
                  </defs>

                  {/* Ground & Blocks */}
                  <rect width="360" height="380" fill="#f8fafc" />
                  <rect x="20" y="30" width="70" height="90" rx="8" fill="#f1f5f9" />
                  <rect x="110" y="20" width="100" height="70" rx="8" fill="#f1f5f9" />
                  <rect x="230" y="40" width="110" height="90" rx="8" fill="#f1f5f9" />
                  <rect x="30" y="250" width="90" height="100" rx="8" fill="#f1f5f9" />
                  <rect x="210" y="270" width="130" height="80" rx="8" fill="#f1f5f9" />

                  {/* River (Sông Sài Gòn uốn lượn mềm) */}
                  <path
                    d="M 330 0 C 290 80, 240 130, 240 200 C 240 280, 290 320, 270 380 L 300 380 C 330 320, 280 270, 270 200 C 270 140, 320 80, 360 0 Z"
                    fill="url(#riverGrad)"
                  />

                  {/* Road Network */}
                  <path d="M 0 150 L 360 150" stroke="#e2e8f0" strokeWidth="8" />
                  <path d="M 175 0 L 175 380" stroke="#e2e8f0" strokeWidth="8" />
                  <path d="M 0 70 L 360 70" stroke="#e2e8f0" strokeWidth="4" />
                  <path d="M 0 240 L 360 240" stroke="#e2e8f0" strokeWidth="4" />
                  <path d="M 90 0 L 90 380" stroke="#e2e8f0" strokeWidth="4" />
                  <path d="M 270 0 L 270 380" stroke="#e2e8f0" strokeWidth="4" />

                  {/* Radar Wave Radius Circles */}
                  <circle
                    cx="175"
                    cy="200"
                    r="68"
                    fill="url(#radarPulseGrad)"
                    stroke="#0077FF"
                    strokeWidth="1.5"
                    strokeDasharray="4 3"
                    className={radarScanning ? 'scanning-circle' : ''}
                  />
                  <circle cx="175" cy="200" r="105" fill="none" stroke="#38BDF8" strokeWidth="1" strokeDasharray="3 3" opacity="0.6" />
                  <circle cx="175" cy="200" r="32" fill="none" stroke="#0077FF" strokeWidth="1" opacity="0.4" />

                  {/* Center Worker Location Dot */}
                  <circle cx="175" cy="200" r="10" fill="#ffffff" filter="drop-shadow(0 2px 4px rgba(0,0,0,0.15))" />
                  <circle cx="175" cy="200" r="6" fill="#0077FF" />
                </svg>

                {/* Map Controls */}
                <div className="map-controls-floating">
                  <button type="button" className="map-btn-circle" title="Vị trí của tôi">🎯</button>
                  <div className="zoom-btn-group">
                    <button type="button" className="map-btn-zoom">+</button>
                    <button type="button" className="map-btn-zoom">&minus;</button>
                  </div>
                </div>

                {/* Floating 'Quét lại' Action Button */}
                <button
                  type="button"
                  className={`btn-scan-floating ${radarScanning ? 'spinning' : ''}`}
                  onClick={handleScanAgain}
                >
                  <span className="scan-icon">🔄</span>
                  <span>Quét lại</span>
                </button>
              </div>

              {/* Bottom Navigation Bar (5 tabs) */}
              <div className="phone-bottom-nav">
                <button
                  type="button"
                  className={`nav-tab-item ${bottomTab === 'map' ? 'active' : ''}`}
                  onClick={() => setBottomTab('map')}
                >
                  <span className="nav-icon">🗺️</span>
                  <span className="nav-lbl">Bản đồ</span>
                </button>

                <button
                  type="button"
                  className={`nav-tab-item ${bottomTab === 'jobs' ? 'active' : ''}`}
                  onClick={() => {
                    setBottomTab('jobs');
                    showToast('Danh sách việc làm phù hợp theo ma trận 4D', 'info');
                  }}
                >
                  <span className="nav-icon">💼</span>
                  <span className="nav-lbl">Việc làm</span>
                </button>

                <button
                  type="button"
                  className={`nav-tab-item ${bottomTab === 'notif' ? 'active' : ''}`}
                  onClick={() => {
                    setBottomTab('notif');
                    showToast('Thông báo mời nhận ca sự kiện mới', 'info');
                  }}
                >
                  <span className="nav-icon">🔔</span>
                  <span className="nav-lbl">Thông báo</span>
                </button>

                <button
                  type="button"
                  className={`nav-tab-item ${bottomTab === 'wallet' ? 'active' : ''}`}
                  onClick={() => {
                    setBottomTab('wallet');
                    showToast('Ví thù lao & Lịch sử nhận lương ngoài', 'info');
                  }}
                >
                  <span className="nav-icon">💳</span>
                  <span className="nav-lbl">Thu nhập</span>
                </button>

                <button
                  type="button"
                  className={`nav-tab-item ${bottomTab === 'profile' ? 'active' : ''}`}
                  onClick={() => {
                    setBottomTab('profile');
                    showToast('Hồ sơ Worker & Điểm uy tín', 'info');
                  }}
                >
                  <span className="nav-icon">👤</span>
                  <span className="nav-lbl">Cá nhân</span>
                </button>
              </div>

              {/* iOS Home Indicator Bar */}
              <div className="iphone-home-indicator"></div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default MobileShowcasePage;
