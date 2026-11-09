import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import CaniLogo from '../../components/common/CaniLogo';
import './LandingPage.css';

export default function LandingPage() {
  const navigate = useNavigate();

  // Kiểm tra token để xác định trạng thái đăng nhập
  const token = localStorage.getItem('cani_token');

  const scrollToSection = (id) => {
    const el = document.getElementById(id);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  };

  const handleHeroCta = () => {
    if (token) {
      navigate('/eo');
    } else {
      navigate('/register');
    }
  };

  return (
    <div className="cani-landing-root">
      {/* =========================================================================
          1. NAVIGATION BAR
          ========================================================================= */}
      <header className="landing-navbar-wrapper">
        <div className="landing-nav-container">
          <div className="landing-nav-brand" onClick={() => scrollToSection('hero')}>
            <CaniLogo height={38} />
          </div>

          <nav>
            <ul className="landing-nav-links">
              <li>
                <span className="landing-nav-link active" onClick={() => scrollToSection('hero')}>
                  Trang chủ
                </span>
              </li>
              <li>
                <span className="landing-nav-link" onClick={() => scrollToSection('features')}>
                  Tính năng
                </span>
              </li>
              <li>
                <span className="landing-nav-link" onClick={() => scrollToSection('workflow')}>
                  Quy trình điều phối
                </span>
              </li>
              <li>
                <span className="landing-nav-link" onClick={() => scrollToSection('about')}>
                  Về chúng tôi
                </span>
              </li>
            </ul>
          </nav>

          <div className="landing-nav-actions">
            {token ? (
              <button
                type="button"
                className="landing-btn-register"
                onClick={() => navigate('/eo')}
              >
                Vào Bảng Điều Khiển
              </button>
            ) : (
              <>
                <Link to="/login" className="landing-btn-login">
                  Đăng nhập
                </Link>
                <Link to="/register" className="landing-btn-register">
                  Đăng ký Ban Tổ Chức
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      {/* =========================================================================
          2. HERO SECTION VỚI HÌNH ẢNH COORDINATOR NHÂN SỰ CANi
          ========================================================================= */}
      <section id="hero" className="landing-hero-section">
        {/* Left Content */}
        <div className="hero-content">
          <div className="hero-pill-badge">
            <span>NỀN TẢNG ĐIỀU PHỐI NHÂN LỰC SỰ KIỆN THEO CA</span>
          </div>

          <h1 className="hero-title">
            Kết nối nhân lực — <br />
            <span className="title-accent">Vận hành sự kiện hiệu quả.</span>
          </h1>

          <p className="hero-subtitle">
            Giải pháp công nghệ toàn diện cho doanh nghiệp, agency và ban tổ chức sự kiện. Kết nối đội ngũ Event Coordinator và Worker chuyên nghiệp chỉ trong tích tắc.
          </p>

          <div className="hero-cta-group">
            <button type="button" className="hero-btn-primary" onClick={handleHeroCta}>
              Tạo sự kiện ngay
            </button>
            <button
              type="button"
              className="hero-btn-secondary"
              onClick={() => scrollToSection('workflow')}
            >
              Xem cách vận hành
            </button>
          </div>

          <ul className="hero-features-list">
            <li className="hero-feature-item">
              <span>Ghép nối ứng viên tự động bằng Matching Radar AI bán kính 15km</span>
            </li>
            <li className="hero-feature-item">
              <span>Điểm danh QR Code động kết hợp bán kính định vị Geofence GPS 200m</span>
            </li>
            <li className="hero-feature-item">
              <span>Quyết toán thù lao Non-Escrow minh bạch, đánh giá 2 chiều chuẩn xác</span>
            </li>
          </ul>
        </div>

        {/* Right Stage: Nhân sự điều phối sự kiện CANi chuẩn như trang Login */}
        <div className="hero-visual-stage">
          <div className="hero-ambient-glow" />

          <div className="hero-coordinator-showcase">
            <div className="coordinator-image-frame">
              <img
                src="/assets/event-coordinator.jpg"
                alt="CANi Event Coordinator"
                className="coordinator-main-photo"
              />
              <div className="coordinator-gradient-overlay" />
            </div>

            {/* Floating Badge 1: Live Events Status (Top Left) */}
            <div className="hero-float-badge badge-top-left">
              <span className="pulse-green-dot" />
              <div>
                <div className="badge-title">Đang điều phối</div>
                <div className="badge-value">120+ Sự kiện active</div>
              </div>
            </div>

            {/* Floating Badge 2: Radar AI Speed (Top Right) */}
            <div className="hero-float-badge badge-top-right">
              <div>
                <div className="badge-title">Matching Radar AI</div>
                <div className="badge-value">&lt; 15 phút lấp đầy ca</div>
              </div>
            </div>

            {/* Floating Card: Coordinator Profile Pill (Bottom Center) */}
            <div className="hero-float-profile-card">
              <div className="profile-details">
                <div className="profile-name">Ban Điều Phối Sự Kiện</div>
                <div className="profile-role">Đội ngũ Coordinator & Worker đã xác thực eKYC</div>
              </div>
              <div className="profile-rating">
                <span>4.9 / 5.0</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* =========================================================================
          3. METRICS COUNTER BAR
          ========================================================================= */}
      <section className="landing-metrics-bar">
        <div className="metrics-card-container">
          <div className="metric-stat-item">
            <div className="metric-stat-info">
              <span className="metric-stat-number">500+</span>
              <span className="metric-stat-label">Sự kiện đã tổ chức</span>
            </div>
          </div>

          <div className="metric-stat-item">
            <div className="metric-stat-info">
              <span className="metric-stat-number">5.000+</span>
              <span className="metric-stat-label">Worker chuyên nghiệp</span>
            </div>
          </div>

          <div className="metric-stat-item">
            <div className="metric-stat-info">
              <span className="metric-stat-number">200+</span>
              <span className="metric-stat-label">Doanh nghiệp & Agency</span>
            </div>
          </div>

          <div className="metric-stat-item">
            <div className="metric-stat-info">
              <span className="metric-stat-number">99.2%</span>
              <span className="metric-stat-label">Tỷ lệ hài lòng hoàn ca</span>
            </div>
          </div>
        </div>
      </section>

      {/* =========================================================================
          4. FEATURES SECTION (4 TRỤ CỘT NỔI BẬT)
          ========================================================================= */}
      <section id="features" className="landing-features-section">
        <div className="section-pill-badge">TÍNH NĂNG VƯỢT TRỘI</div>
        <h2 className="section-title">Tất cả những gì bạn cần cho một sự kiện thành công</h2>
        <p className="section-subtitle">
          Từ tạo sự kiện, tìm người, quản lý ca làm đến thanh toán — CANi đồng hành cùng bạn trong toàn bộ quy trình.
        </p>

        <div className="features-grid">
          <div className="feature-card">
            <h3 className="feature-card-title">Tạo sự kiện linh hoạt</h3>
            <p className="feature-card-desc">
              Dễ dàng tạo nhiều ca (Shift) với khung giờ, vị trí định vị Geofence và vai trò nhân sự tùy chỉnh theo yêu cầu thực tế.
            </p>
          </div>

          <div className="feature-card">
            <h3 className="feature-card-title">Radar AI ghép nối tức thì</h3>
            <p className="feature-card-desc">
              Thuật toán tự động quét bán kính 15km, tính điểm độ tin cậy và tự động gửi lời mời tới các ứng viên phù hợp nhất.
            </p>
          </div>

          <div className="feature-card">
            <h3 className="feature-card-title">Điểm danh Geofence GPS 200m</h3>
            <p className="feature-card-desc">
              Check-in / Check-out thời gian thực bằng mã QR chống gian lận, tự động kích hoạt điều phối thay thế khi có nhân sự hủy ca.
            </p>
          </div>

          <div className="feature-card">
            <h3 className="feature-card-title">Quyết toán minh bạch</h3>
            <p className="feature-card-desc">
              Không giữ cọc lương Non-Escrow, xuất phiếu chi đối soát tự động và hệ thống đánh giá 2 chiều xây dựng uy tín lâu dài.
            </p>
          </div>
        </div>
      </section>

      {/* =========================================================================
          5. WORKFLOW SECTION: QUY TRÌNH ĐIỀU PHỐI 3 BƯỚC
          ========================================================================= */}
      <section id="workflow" className="landing-workflow-section">
        <div className="section-pill-badge">QUY TRÌNH TINH GỌN</div>
        <h2 className="section-title">Vận hành nhân sự sự kiện chỉ trong 3 bước</h2>
        <p className="section-subtitle">
          Tối ưu hóa thời gian chuẩn bị và triệt tiêu rủi ro thiếu người vào giờ chót.
        </p>

        <div className="workflow-steps-grid">
          <div className="workflow-step-card">
            <div className="step-number-tag">01</div>
            <h3 className="step-title">Khởi tạo sự kiện & Lên ca</h3>
            <p className="step-desc">
              Thiết lập tên sự kiện, vị trí địa lý với Reverse Geocoding tự động và định cấu hình ca làm việc, mức thù lao, số lượng nhân sự.
            </p>
          </div>

          <div className="workflow-step-card">
            <div className="step-number-tag">02</div>
            <h3 className="step-title">Radar AI Quét & Khớp ứng viên</h3>
            <p className="step-desc">
              Hệ thống tự động kích hoạt thuật toán ghép nối, gửi thông báo đẩy đến Worker đạt chuẩn trong khu vực và chốt danh sách trong 15 phút.
            </p>
          </div>

          <div className="workflow-step-card">
            <div className="step-number-tag">03</div>
            <h3 className="step-title">Điểm danh QR & Hoàn thành</h3>
            <p className="step-desc">
              Worker quét mã QR tại điểm sự kiện để check-in/out, ban tổ chức duyệt ca và đối soát thù lao trực tiếp an toàn, minh bạch.
            </p>
          </div>
        </div>
      </section>

      {/* =========================================================================
          6. CALL TO ACTION BANNER (THAY THẾ TOÀN BỘ PHẦN BẢNG GIÁ)
          ========================================================================= */}
      <section className="landing-cta-section">
        <div className="cta-container">
          <div className="cta-content">
            <span className="cta-pill">KHỞI ĐỘNG NGAY HÔM NAY</span>
            <h2 className="cta-title">Sẵn sàng tổ chức sự kiện thành công cùng CANi?</h2>
            <p className="cta-subtitle">
              Gia nhập cùng hơn 200+ doanh nghiệp, agency và hàng nghìn Event Worker chuyên nghiệp đang hoạt động trên nền tảng.
            </p>
            <div className="cta-btn-group">
              <button type="button" className="cta-btn-main" onClick={handleHeroCta}>
                Bắt đầu ngay miễn phí
              </button>
              <Link to="/login" className="cta-btn-outline">
                Đăng nhập Cổng Quản lý
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* =========================================================================
          7. FOOTER
          ========================================================================= */}
      <footer id="about" className="landing-footer">
        <div className="footer-container">
          <div>
            <CaniLogo height={32} />
            <p style={{ margin: '0.75rem 0 0', fontSize: '0.85rem', color: '#94a3b8', maxWidth: '380px', lineHeight: 1.5 }}>
              Nền tảng điều phối nhân lực sự kiện ngắn hạn theo ca thế hệ mới. Áp dụng chuẩn kiểm thử IEEE & giải ngân Non-Escrow an toàn.
            </p>
          </div>

          <div style={{ display: 'flex', gap: '2.5rem', fontSize: '0.85rem' }}>
            <div>
              <h5 style={{ color: '#f8fafc', margin: '0 0 0.75rem', fontWeight: 700 }}>Dành cho Ban Tổ Chức (EO)</h5>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                <Link to="/login" style={{ color: '#94a3b8', textDecoration: 'none' }}>Đăng nhập Cổng Quản lý</Link>
                <Link to="/register" style={{ color: '#94a3b8', textDecoration: 'none' }}>Đăng ký Ban Tổ Chức</Link>
                <span onClick={() => scrollToSection('workflow')} style={{ color: '#94a3b8', cursor: 'pointer' }}>Quy trình điều phối</span>
              </div>
            </div>

            <div>
              <h5 style={{ color: '#f8fafc', margin: '0 0 0.75rem', fontWeight: 700 }}>Hỗ trợ & Quy chế</h5>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                <span style={{ color: '#94a3b8' }}>Quy chế giải ngân Non-Escrow</span>
                <span style={{ color: '#94a3b8' }}>Chính sách bảo mật thông tin</span>
                <span style={{ color: '#94a3b8' }}>Hỗ trợ kỹ thuật 24/7</span>
              </div>
            </div>
          </div>
        </div>

        <div className="footer-copy">
          © 2026 CANi Platform — Đồ án Tốt nghiệp Kỹ nghệ Phần mềm SEP490_G16 (FPT University). Mọi quyền được bảo lưu.
        </div>
      </footer>
    </div>
  );
}
