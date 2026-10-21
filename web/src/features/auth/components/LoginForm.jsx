import React from 'react';
import { useLogin } from '../hooks/useLogin';
import '../auth.css';

export const LoginForm = () => {
  const {
    formData,
    isLoading,
    errorMessage,
    showPassword,
    handleChange,
    handleSubmit,
    toggleShowPassword,
    handleGoogleLogin,
  } = useLogin();

  return (
    <div className="auth-card">
      <div className="auth-header">
        <div className="auth-logo-badge">C</div>
        <h2 className="auth-title">Đăng Nhập</h2>
        <p className="auth-subtitle">Cổng Quản Trị & Tổ Chức Sự Kiện CANi</p>
      </div>

      {errorMessage && (
        <div className="auth-error-banner" role="alert">
          {errorMessage}
        </div>
      )}

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <div className="form-group">
          <label className="form-label" htmlFor="email">
            Địa chỉ Email
          </label>
          <div className="input-wrapper">
            <input
              id="email"
              name="email"
              type="email"
              className="form-input"
              placeholder="admin@cani.vn hoặc eo@company.com"
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
              tabIndex={-1}
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
            Ghi nhớ phiên đăng nhập
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

      <div className="auth-divider">
        <span>Hoặc</span>
      </div>

      <button
        type="button"
        className="btn-demo"
        onClick={handleGoogleLogin}
        disabled={isLoading}
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '0.75rem',
          backgroundColor: '#ffffff',
          color: '#374151',
          border: '1px solid #d1d5db',
          fontWeight: 500,
          boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
        }}
      >
        <svg width="18" height="18" viewBox="0 0 18 18">
          <path
            fill="#4285F4"
            d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844c-.209 1.125-.843 2.078-1.796 2.717v2.258h2.908c1.702-1.567 2.684-3.874 2.684-6.616z"
          />
          <path
            fill="#34A853"
            d="M9 18c2.43 0 4.467-.806 5.956-2.184l-2.908-2.258c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 0 0 9 18z"
          />
          <path
            fill="#FBBC05"
            d="M3.964 10.707c-.18-.54-.282-1.117-.282-1.707s.102-1.167.282-1.707V4.961H.957A8.996 8.996 0 0 0 0 9c0 1.452.348 2.827.957 4.039l3.007-2.332z"
          />
          <path
            fill="#EA4335"
            d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 0 0 .957 4.961L3.964 7.293C4.672 5.166 6.656 3.58 9 3.58z"
          />
        </svg>
        Đăng nhập với Google
      </button>

      <div className="auth-footer">
        Đại diện Ban Tổ Chức? <a href="/register">Đăng ký tài khoản mới</a>
      </div>
    </div>
  );
};

export default LoginForm;
