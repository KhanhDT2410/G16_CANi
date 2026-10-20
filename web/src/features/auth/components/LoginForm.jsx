import React from 'react';
import { useLogin } from '../hooks/useLogin';
import '../auth.css';

/**
 * Component Form Đăng Nhập thuần túy (React JSX)
 * Áp dụng triết lý Clean Architecture: Chỉ đảm nhiệm hiển thị UI và nhận tương tác,
 * toàn bộ logic điều phối và gọi API được đóng gói trong hook useLogin.
 */
export const LoginForm = () => {
  const {
    formData,
    isLoading,
    errorMessage,
    showPassword,
    handleChange,
    handleSubmit,
    toggleShowPassword,
    handleQuickDemoLogin,
  } = useLogin();

  return (
    <div className="auth-card">
      <div className="auth-header">
        <div className="auth-logo-badge">C</div>
        <h2 className="auth-title">Đăng Nhập</h2>
        <p className="auth-subtitle">Nền tảng Quản lý & Tuyển dụng Nhân sự Sự kiện CANi</p>
      </div>

      {errorMessage && (
        <div className="auth-error-banner" role="alert">
          {errorMessage}
        </div>
      )}

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <div className="form-group">
          <label className="form-label" htmlFor="email">
            Email / Tên đăng nhập
          </label>
          <div className="input-wrapper">
            <input
              id="email"
              name="email"
              type="email"
              className="form-input"
              placeholder="nhansu@cani.vn hoặc eo@cani.vn"
              value={formData.email}
              onChange={handleChange}
              autoComplete="username"
              disabled={isLoading}
              required
            />
          </div>
        </div>

        <div className="form-group">
          <label className="form-label" htmlFor="password">
            Mật khẩu
          </label>
          <div className="input-wrapper">
            <input
              id="password"
              name="password"
              type={showPassword ? 'text' : 'password'}
              className="form-input"
              placeholder="••••••••"
              value={formData.password}
              onChange={handleChange}
              autoComplete="current-password"
              disabled={isLoading}
              required
            />
            <button
              type="button"
              className="toggle-password-btn"
              onClick={toggleShowPassword}
              tabIndex="-1"
            >
              {showPassword ? 'Ẩn' : 'Hiện'}
            </button>
          </div>
        </div>

        <div className="form-options">
          <label className="remember-label">
            <input
              type="checkbox"
              name="rememberMe"
              checked={formData.rememberMe}
              onChange={handleChange}
              disabled={isLoading}
            />
            Ghi nhớ đăng nhập
          </label>
          <a href="#forgot" className="forgot-link" onClick={(e) => e.preventDefault()}>
            Quên mật khẩu?
          </a>
        </div>

        <button type="submit" className="btn-submit" disabled={isLoading}>
          {isLoading ? (
            <>
              <span className="spinner" />
              Đang xác thực...
            </>
          ) : (
            'Đăng nhập'
          )}
        </button>
      </form>

      {/* Phần hỗ trợ test nhanh giao diện cho nhóm khi Backend chưa khởi động */}
      <div className="auth-divider">
        <span>Hoặc Test Nhanh Màn Hình</span>
      </div>

      <div className="demo-buttons-group" style={{ flexDirection: 'column', gap: '0.5rem' }}>
        <button
          type="button"
          className="btn-demo"
          onClick={() => handleQuickDemoLogin('WORKER')}
          disabled={isLoading}
          style={{ backgroundColor: '#eff6ff', borderColor: '#bfdbfe', color: '#1d4ed8', fontWeight: '600' }}
        >
          👷‍♂️ Vào vai Nhân Sự Sự Kiện (Worker)
        </button>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.5rem', width: '100%' }}>
          <button
            type="button"
            className="btn-demo"
            onClick={() => handleQuickDemoLogin('EO')}
            disabled={isLoading}
          >
            🏢 Ban Tổ Chức (EO)
          </button>
          <button
            type="button"
            className="btn-demo"
            onClick={() => handleQuickDemoLogin('ADMIN')}
            disabled={isLoading}
          >
            ⚙️ Quản Trị (Admin)
          </button>
          <button
            type="button"
            className="btn-demo"
            onClick={() => handleQuickDemoLogin('CSKH')}
            disabled={isLoading}
            style={{ backgroundColor: '#fffbeb', borderColor: '#fef3c7', color: '#b45309' }}
          >
            🎧 CSKH (Support)
          </button>
          <button
            type="button"
            className="btn-demo"
            onClick={() => handleQuickDemoLogin('FINANCE')}
            disabled={isLoading}
            style={{ backgroundColor: '#ecfdf5', borderColor: '#d1fae5', color: '#047857' }}
          >
            💰 Tài Chính (Finance)
          </button>
        </div>
      </div>

      <div className="auth-footer">
        Chưa có tài khoản sự kiện? <a href="/register">Đăng ký ngay tại đây</a>
      </div>
    </div>
  );
};

export default LoginForm;
