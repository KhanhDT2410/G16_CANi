import React from 'react';
import { LoginForm } from '../../features/auth';
import CaniLogo from '../../components/common/CaniLogo';
import './LoginPage.css';

/**
 * Trang Đăng Nhập (Route: /login)
 * Thiết kế Split 2-Panel chuẩn Mockup:
 * - Cột trái: Nhận diện thương hiệu, thông điệp giá trị, 3 tính năng cốt lõi & hình ảnh nhân sự điều phối
 * - Cột phải: Khối thẻ đăng nhập nổi hiện đại với đăng nhập Email/Mật khẩu và Google
 */
const LoginPage = () => {
  return (
    <div className="cani-login-split-page">
      {/* 1. CỘT TRÁI: HERO BRANDING & VALUE PROPOSITION */}
      <div className="login-hero-panel">
        <div className="login-hero-backdrop" />

        <div className="login-hero-top">
          <CaniLogo variant="horizontal" height={36} theme="dark" />
        </div>

        <div className="login-hero-content">
          <div className="login-hero-pill">
            <span>Nền tảng điều phối nhân lực sự kiện</span>
          </div>

          <h1 className="login-hero-title">
            Kết nối nhân lực — <br />
            <span>Vận hành sự kiện hiệu quả</span>
          </h1>

          <p className="login-hero-subtitle">
            Giải pháp toàn diện cho doanh nghiệp, agency và đơn vị tổ chức sự kiện.
          </p>

          <div className="login-feature-pills">
            <div className="login-feat-pill">
              <span className="feat-pill-icon-box">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
                  <line x1="16" y1="2" x2="16" y2="6"></line>
                  <line x1="8" y1="2" x2="8" y2="6"></line>
                  <line x1="3" y1="10" x2="21" y2="10"></line>
                </svg>
              </span>
              <span>Tạo & quản lý sự kiện</span>
            </div>

            <div className="login-feat-pill">
              <span className="feat-pill-icon-box">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                  <circle cx="9" cy="7" r="4"></circle>
                  <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                  <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
                </svg>
              </span>
              <span>Điều phối nhân lực theo ca</span>
            </div>

            <div className="login-feat-pill">
              <span className="feat-pill-icon-box">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <line x1="18" y1="20" x2="18" y2="10"></line>
                  <line x1="12" y1="20" x2="12" y2="4"></line>
                  <line x1="6" y1="20" x2="6" y2="14"></line>
                </svg>
              </span>
              <span>Thanh toán & đánh giá minh bạch</span>
            </div>
          </div>
        </div>

        {/* Ảnh nhân sự điều phối sự kiện CANi (Chân dung Coordinator chính thức) */}
        <div className="login-hero-staff-container">
          <img
            src="/assets/event-coordinator.jpg"
            alt="CANi Event Staff Coordinator"
            className="login-staff-image"
          />
        </div>
      </div>

      {/* 2. CỘT PHẢI: FORM ĐĂNG NHẬP FLOATING CARD */}
      <div className="login-form-panel">
        <div className="login-form-wrapper">
          <LoginForm />
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
