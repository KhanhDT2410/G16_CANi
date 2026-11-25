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
              <span>Tạo & quản lý sự kiện</span>
            </div>

            <div className="login-feat-pill">
              <span>Điều phối nhân lực theo ca</span>
            </div>

            <div className="login-feat-pill">
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
