import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import CaniLogo from '../../components/common/CaniLogo';
import './LandingPage.css';

export default function LandingPage() {
  const navigate = useNavigate();

  // Kiểm tra token để xác định trạng thái đăng nhập
  const token = localStorage.getItem('accessToken');

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
                <span className="landing-nav-link" onClick={() => scrollToSection('pricing')}>
                  Bảng giá Gói cước
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
                Vào Bảng Điều Khiển →
              </button>
            ) : (
              <>
                <Link to="/login" className="landing-btn-login">
                  Đăng nhập
                </Link>
                <Link to="/register" className="landing-btn-register">
                  Đăng ký
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      {/* =========================================================================
          2. HERO SECTION
          ========================================================================= */}
      <section id="hero" className="landing-hero-section">
        {/* Left Content */}
        <div className="hero-content">
          <div className="hero-pill-badge">
            <span className="hero-star-icon">✦</span>
            <span>NỀN TẢNG ĐIỀU PHỐI NHÂN LỰC NGẮN HẠN THEO CA</span>
          </div>

          <h1 className="hero-title">
            Cần người cho sự kiện? <br />
            <span className="title-accent">Có CANi ngay.</span>
          </h1>

          <p className="hero-subtitle">
            Kết nối doanh nghiệp, agency và đơn vị tổ chức sự kiện với đội ngũ Worker chất lượng, sẵn sàng theo ca. Điều phối nhanh chóng, quản lý dễ dàng, vận hành hiệu quả.
          </p>

          <div className="hero-cta-group">
            <button type="button" className="hero-btn-primary" onClick={handleHeroCta}>
              <span>Tạo sự kiện ngay</span>
              <span>→</span>
            </button>
            <button
              type="button"
              className="hero-btn-secondary"
              onClick={() => scrollToSection('features')}
            >
              <span>▶</span>
              <span>Tìm hiểu thêm</span>
            </button>
          </div>

          <ul className="hero-features-list">
            <li className="hero-feature-item">
              <span className="hero-check-icon">✓</span>
              <span>Tìm người phù hợp tự động bằng AI Matching</span>
            </li>
            <li className="hero-feature-item">
              <span className="hero-check-icon">✓</span>
              <span>Quản lý ca làm toàn diện & Geofence GPS 200m</span>
            </li>
            <li className="hero-feature-item">
              <span className="hero-check-icon">✓</span>
              <span>Minh bạch - Nhanh chóng - Quyết toán Non-Escrow an toàn</span>
            </li>
          </ul>
        </div>

        {/* Right Graphic Composition Matching Mockup */}
        <div className="hero-visual-stage">
          <div className="hero-ambient-glow" />

          <div className="hero-graphic-card">
            {/* Coordinator Backdrop Card */}
            <div className="hero-staff-backdrop">
              <img
                src="https://images.unsplash.com/photo-1511578314322-379afb476865?auto=format&fit=crop&w=700&q=80"
                alt="Event Coordinator CANi"
                className="hero-staff-image"
              />
            </div>

            {/* Floating Card 1: Sự kiện 120+ (Top Left) */}
            <div className="float-card-events">
              <div className="float-card-header">
                <span className="float-card-icon">📅</span>
                <span className="float-card-title">Sự kiện</span>
                <span className="float-pill-green">↑ 28%</span>
              </div>
              <div className="float-card-metric">120+</div>
              <div className="float-mini-bars">
                <div className="mini-bar" style={{ height: '8px' }} />
                <div className="mini-bar" style={{ height: '14px' }} />
                <div className="mini-bar" style={{ height: '10px' }} />
                <div className="mini-bar" style={{ height: '16px', background: '#2563eb' }} />
                <div className="mini-bar" style={{ height: '12px' }} />
              </div>
            </div>

            {/* Floating Card 2: Worker Phù Hợp (Top Right) */}
            <div className="float-card-workers">
              <div className="float-workers-label">Worker phù hợp</div>
              <div className="float-avatar-stack">
                <div className="float-avatar" style={{ backgroundImage: 'url(https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=100&q=80)' }} />
                <div className="float-avatar" style={{ backgroundImage: 'url(https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=100&q=80)' }} />
                <div className="float-avatar" style={{ backgroundImage: 'url(https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=100&q=80)' }} />
                <div className="float-avatar-more">+12</div>
              </div>
            </div>

            {/* Floating Card 3: Shift Status Pills (Bottom Left) */}
            <div className="float-card-shifts">
              <div className="shift-status-row">
                <span>Setup sự kiện</span>
                <span className="shift-status-tag">+ 10/10</span>
              </div>
              <div className="shift-status-row">
                <span>Vận hành & Lễ tân</span>
                <span className="shift-status-tag">+ 18/20</span>
              </div>
              <div className="shift-status-row">
                <span>Teardown tháo dỡ</span>
                <span className="shift-status-tag">+ 8/8</span>
              </div>
            </div>

            {/* Floating Card 4: Sleek Smartphone Mockup (Right) */}
            <div className="hero-phone-mockup">
              <div className="phone-screen">
                <div className="phone-top-bar">
                  <span className="phone-brand">CANi</span>
                  <span>🔔</span>
                </div>

                <div className="phone-user-greeting">
                  <div className="phone-user-title">Xin chào, Duy Hưng!</div>
                  <div className="phone-user-sub">Sẵn sàng làm sự kiện hôm nay?</div>
                </div>

                <div className="phone-search-box">
                  <span>🔍</span>
                  <span>Tìm kiếm ca sự kiện...</span>
                </div>

                <div className="phone-chips-row">
                  <span className="phone-chip active">Gần đây</span>
                  <span className="phone-chip">Phù hợp</span>
                  <span className="phone-chip">Lương cao</span>
                </div>

                <div className="phone-event-card">
                  <div className="phone-event-banner">
                    <div className="phone-event-name">Summer Fest 2026</div>
                  </div>
                  <div className="phone-event-body">
                    <div className="phone-event-meta">
                      <span>📍 Quận 7, TP.HCM</span>
                      <span>📅 20/10/2026 (14:00 - 22:00)</span>
                    </div>
                    <div className="phone-event-wage">300.000 đ/ca</div>
                    <div className="phone-event-tags">
                      <span className="phone-tag-badge">Setup</span>
                      <span className="phone-tag-badge">Toàn thời gian</span>
                    </div>
                  </div>
                </div>
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
            <div className="metric-stat-icon">📅</div>
            <div className="metric-stat-info">
              <span className="metric-stat-number">500+</span>
              <span className="metric-stat-label">Sự kiện đã được tổ chức</span>
            </div>
          </div>

          <div className="metric-stat-item">
            <div className="metric-stat-icon">👥</div>
            <div className="metric-stat-info">
              <span className="metric-stat-number">5.000+</span>
              <span className="metric-stat-label">Worker đã tham gia</span>
            </div>
          </div>

          <div className="metric-stat-item">
            <div className="metric-stat-icon">🏢</div>
            <div className="metric-stat-info">
              <span className="metric-stat-number">200+</span>
              <span className="metric-stat-label">Doanh nghiệp tin tưởng</span>
            </div>
          </div>

          <div className="metric-stat-item">
            <div className="metric-stat-icon">⭐</div>
            <div className="metric-stat-info">
              <span className="metric-stat-number">4.8/5</span>
              <span className="metric-stat-label">Đánh giá từ khách hàng</span>
            </div>
          </div>
        </div>
      </section>

      {/* =========================================================================
          4. FEATURES SECTION (4 KHỐI NỔI BẬT TỪ MOCKUP)
          ========================================================================= */}
      <section id="features" className="landing-features-section">
        <div className="section-pill-badge">TÍNH NĂNG NỔI BẬT</div>
        <h2 className="section-title">Tất cả những gì bạn cần cho một sự kiện thành công</h2>
        <p className="section-subtitle">
          Từ tạo sự kiện, tìm người, quản lý ca làm đến thanh toán — CANi đồng hành cùng bạn trong toàn bộ quy trình.
        </p>

        <div className="features-grid">
          <div className="feature-card">
            <div className="feature-card-icon icon-purple">📅</div>
            <h3 className="feature-card-title">Tạo sự kiện linh hoạt</h3>
            <p className="feature-card-desc">
              Dễ dàng tạo nhiều ca (Shift) với thời gian, địa điểm, loại công việc tùy chỉnh. Quản lý hạn mức ngân sách thông minh.
            </p>
          </div>

          <div className="feature-card">
            <div className="feature-card-icon icon-green">👥</div>
            <h3 className="feature-card-title">Tự động tìm Worker phù hợp</h3>
            <p className="feature-card-desc">
              Sử dụng thuật toán Matching Radar AI quét bán kính 15km, tự động chấm điểm độ tin cậy và ghép nối ứng viên trong 15 phút.
            </p>
          </div>

          <div className="feature-card">
            <div className="feature-card-icon icon-orange">📋</div>
            <h3 className="feature-card-title">Quản lý ca làm toàn diện</h3>
            <p className="feature-card-desc">
              Theo dõi nhận ca thời gian thực, điểm danh QR code kết hợp Geofence GPS 200m, xử lý hủy ca và điều phối khẩn cấp tức thì.
            </p>
          </div>

          <div className="feature-card">
            <div className="feature-card-icon icon-blue">💳</div>
            <h3 className="feature-card-title">Thanh toán & đánh giá</h3>
            <p className="feature-card-desc">
              Minh bạch thu nhập, lịch sử đối soát và đánh giá 2 chiều giúp xây dựng đội ngũ nhân sự và ban tổ chức đáng tin cậy.
            </p>
          </div>
        </div>
      </section>



      {/* =========================================================================
          6. DÀNH CHO DOANH NGHIỆP & BẢNG GIÁ GÓI CƯỚC (#pricing, #enterprise)
          ========================================================================= */}
      <section id="pricing" className="landing-features-section" style={{ background: '#ffffff', borderTop: '1px solid #f1f5f9' }}>
        <div id="enterprise" className="section-pill-badge">BẢNG GIÁ & GÓI DỊCH VỤ</div>
        <h2 className="section-title">Minh bạch chi phí, tối ưu ngân sách tổ chức</h2>
        <p className="section-subtitle">
          Áp dụng mô hình Gói Cước Quota theo sự kiện. Không thu phí lẻ theo đầu người, không giữ cọc lương Non-Escrow.
        </p>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '1.5rem', maxWidth: '1080px', margin: '0 auto', textAlign: 'left' }}>
          {/* Gói Chào Mừng */}
          <div style={{ background: '#f8fafc', border: '2px solid #bfdbfe', borderRadius: '20px', padding: '2rem', display: 'flex', flexDirection: 'column' }}>
            <span style={{ background: '#dbeafe', color: '#1e40af', fontSize: '0.75rem', fontWeight: 800, padding: '0.25rem 0.65rem', borderRadius: '999px', width: 'fit-content', marginBottom: '0.75rem' }}>
              🎁 ƯU ĐÃI CHÀO MỪNG
            </span>
            <h3 style={{ fontSize: '1.25rem', fontWeight: 800, margin: '0 0 0.5rem', color: '#0f172a' }}>Gói Trải Nghiệm</h3>
            <div style={{ fontSize: '2rem', fontWeight: 800, color: '#2563eb', margin: '0.5rem 0' }}>0 VNĐ</div>
            <p style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '1.25rem' }}>Dành riêng cho Nhà tổ chức sự kiện đăng ký mới.</p>
            <ul style={{ listStyle: 'none', padding: 0, margin: '0 0 1.5rem', display: 'flex', flexDirection: 'column', gap: '0.5rem', fontSize: '0.85rem', color: '#334155' }}>
              <li>✓ Tặng 1 Sự kiện trải nghiệm miễn phí</li>
              <li>✓ Mở full Matching Radar AI 15km</li>
              <li>✓ Không giới hạn số lượng ca làm việc</li>
            </ul>
            <Link to="/register" className="landing-btn-login" style={{ textAlign: 'center', justifyContent: 'center', marginTop: 'auto' }}>
              Đăng ký nhận ưu đãi
            </Link>
          </div>

          {/* Gói Matching Plus */}
          <div style={{ background: '#ffffff', border: '2px solid #2563eb', borderRadius: '20px', padding: '2rem', display: 'flex', flexDirection: 'column', position: 'relative', boxShadow: '0 12px 30px rgba(37,99,235,0.12)' }}>
            <span style={{ position: 'absolute', top: '-12px', right: '20px', background: '#2563eb', color: '#ffffff', fontSize: '0.75rem', fontWeight: 800, padding: '0.25rem 0.75rem', borderRadius: '999px' }}>
              KHUYÊN DÙNG
            </span>
            <h3 style={{ fontSize: '1.25rem', fontWeight: 800, margin: '0 0 0.5rem', color: '#0f172a' }}>Matching Radar AI</h3>
            <div style={{ fontSize: '2rem', fontWeight: 800, color: '#0f172a', margin: '0.5rem 0' }}>
              80.000 <small style={{ fontSize: '1rem', color: '#64748b' }}>VNĐ</small>
            </div>
            <p style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '1.25rem' }}>Quét và tự động đề xuất Worker phù hợp nhất.</p>
            <ul style={{ listStyle: 'none', padding: 0, margin: '0 0 1.5rem', display: 'flex', flexDirection: 'column', gap: '0.5rem', fontSize: '0.85rem', color: '#334155' }}>
              <li>✓ Hạn mức 3 Sự kiện tổ chức</li>
              <li>✓ Bắn thông báo đẩy FCM đến Worker quanh 15km</li>
              <li>✓ Điểm danh QR & Geofence GPS 200m</li>
              <li>✓ Điều phối thay thế tức thì khi có hủy ca</li>
            </ul>
            <Link to="/register" className="landing-btn-register" style={{ textAlign: 'center', justifyContent: 'center', marginTop: 'auto' }}>
              Bắt đầu ngay
            </Link>
          </div>

          {/* Gói Doanh Nghiệp */}
          <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '20px', padding: '2rem', display: 'flex', flexDirection: 'column' }}>
            <h3 style={{ fontSize: '1.25rem', fontWeight: 800, margin: '0 0 0.5rem', color: '#0f172a' }}>Enterprise Pro</h3>
            <div style={{ fontSize: '2rem', fontWeight: 800, color: '#0f172a', margin: '0.5rem 0' }}>
              Liên hệ
            </div>
            <p style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '1.25rem' }}>Dành cho các Agency và tập đoàn sự kiện lớn.</p>
            <ul style={{ listStyle: 'none', padding: 0, margin: '0 0 1.5rem', display: 'flex', flexDirection: 'column', gap: '0.5rem', fontSize: '0.85rem', color: '#334155' }}>
              <li>✓ Không giới hạn số lượng sự kiện</li>
              <li>✓ Quản lý phân quyền đa cấp bậc</li>
              <li>✓ Hỗ trợ đối soát thanh toán riêng biệt</li>
            </ul>
            <Link to="/login" className="landing-btn-login" style={{ textAlign: 'center', justifyContent: 'center', marginTop: 'auto' }}>
              Tư vấn doanh nghiệp
            </Link>
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
              <h5 style={{ color: '#f8fafc', margin: '0 0 0.75rem', fontWeight: 700 }}>Dành cho Nhà Tổ Chức (EO)</h5>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                <Link to="/login" style={{ color: '#94a3b8', textDecoration: 'none' }}>Đăng nhập Cổng Quản lý</Link>
                <Link to="/register" style={{ color: '#94a3b8', textDecoration: 'none' }}>Đăng ký Doanh nghiệp</Link>
                <span onClick={() => scrollToSection('pricing')} style={{ color: '#94a3b8', cursor: 'pointer' }}>Bảng giá Gói cước</span>
              </div>
            </div>

            <div>
              <h5 style={{ color: '#f8fafc', margin: '0 0 0.75rem', fontWeight: 700 }}>Hỗ trợ & Quy định</h5>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                <span onClick={() => scrollToSection('pricing')} style={{ color: '#94a3b8', cursor: 'pointer' }}>Chính sách Gói cước</span>
                <span style={{ color: '#94a3b8' }}>Quy chế giải ngân Non-Escrow</span>
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

