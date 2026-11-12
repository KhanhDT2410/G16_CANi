import React, { useState } from 'react';
import { useLogin } from '../hooks/useLogin';
import CaniLogo from '../../../components/common/CaniLogo';
import ForgotPasswordModal from './ForgotPasswordModal';
import '../auth.css';

export const LoginForm = () => {
  const [showForgotPassword, setShowForgotPassword] = useState(false);
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
        <CaniLogo variant="full" height={44} className="auth-logo-center" />
        <h2 className="auth-title" style={{ marginTop: '0.85rem' }}>Đăng nhập</h2>
        <p className="auth-subtitle">Cổng quản lý dành cho Ban Tổ Chức Sự Kiện</p>
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
          <div className="input-wrapper has-left-icon">
            <svg
              className="input-field-icon"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="#64748b"
              strokeWidth="2"
            >
              <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path>
              <polyline points="22,6 12,13 2,6"></polyline>
            </svg>
            <input
              id="email"
              name="email"
              type="email"
              className="form-input with-icon"
              placeholder="name@company.com"
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
          <div className="input-wrapper has-left-icon">
            <svg
              className="input-field-icon"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="#64748b"
              strokeWidth="2"
            >
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
              <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
            </svg>
            <input
              id="password"
              name="password"
              type={showPassword ? 'text' : 'password'}
              className="form-input with-icon with-eye"
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
              title={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
            >
              {showPassword ? (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#64748b" strokeWidth="2">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
                  <line x1="1" y1="1" x2="23" y2="23"></line>
                </svg>
              ) : (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#64748b" strokeWidth="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                  <circle cx="12" cy="12" r="3"></circle>
                </svg>
              )}
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
          <button
            type="button"
            className="forgot-link"
            style={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer', font: 'inherit' }}
            onClick={() => setShowForgotPassword(true)}
          >
            Quên mật khẩu?
          </button>
        </div>

        <button type="submit" className="btn-submit btn-pill-primary" disabled={isLoading}>
          {isLoading ? (
            <>
              <span className="spinner" />
              Đang xác thực...
            </>
          ) : (
            <>
              <span className="btn-submit-arrow">→</span>
              <span>Đăng nhập</span>
            </>
          )}
        </button>
      </form>

      <div className="auth-divider">
        <span>Hoặc</span>
      </div>

      <button
        type="button"
        className="btn-google-pill"
        onClick={handleGoogleLogin}
        disabled={isLoading}
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
        Chưa có tài khoản đại diện Ban Tổ Chức? <a href="/register">Đăng ký ngay</a>
      </div>

      <ForgotPasswordModal
        isOpen={showForgotPassword}
        onClose={() => setShowForgotPassword(false)}
        onResetSuccess={(resetEmail) => {
          if (resetEmail && handleChange) {
            handleChange({ target: { name: 'email', value: resetEmail } });
          }
        }}
      />
    </div>
  );
};

export default LoginForm;
