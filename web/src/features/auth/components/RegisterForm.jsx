import React from 'react';
import { useRegister } from '../hooks/useRegister';
import CaniLogo from '../../../components/common/CaniLogo';
import '../auth.css';

export const RegisterForm = () => {
  const {
    formData,
    isLoading,
    isSendingOtp,
    otpSent,
    countdown,
    errorMessage,
    successMessage,
    showPassword,
    handleChange,
    handleSendOtp,
    handleGoogleLogin,
    handleSubmit,
    toggleShowPassword,
  } = useRegister();

  return (
    <div className="auth-card auth-card-wide">
      <div className="auth-header">
        <CaniLogo variant="full" height={52} className="auth-logo-center" />
        <h2 className="auth-title" style={{ marginTop: '0.85rem' }}>Đăng Ký Nhà Tổ Chức</h2>
        <p className="auth-subtitle">
          Tạo tài khoản dành riêng cho Ban Tổ Chức & Doanh Nghiệp Sự Kiện
        </p>
      </div>

      <div className="worker-perks-box" style={{ backgroundColor: '#f0fdf4', borderColor: '#bbf7d0', color: '#166534', marginBottom: '1.25rem' }}>
        <div className="worker-perks-title" style={{ color: '#15803d' }}>
          <span>Quyền lợi Nhà Tổ Chức (Event Organizer):</span>
        </div>
        <div>• Đăng tải không giới hạn ca làm, tuyển chọn nhân sự sự kiện chất lượng cao.</div>
        <div>• Tự động khớp ca, quản lý điểm danh QR thời gian thực và thanh toán tự động, minh bạch.</div>
      </div>

      {/* Đăng ký nhanh bằng Google */}
      <button
        type="button"
        className="btn-demo"
        onClick={handleGoogleLogin}
        disabled={isLoading || isSendingOtp}
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '0.75rem',
          backgroundColor: '#ffffff',
          color: '#374151',
          border: '1px solid #d1d5db',
          fontWeight: 600,
          boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
          padding: '0.75rem 1rem',
          borderRadius: '0.5rem',
          width: '100%',
          cursor: 'pointer',
          marginBottom: '1rem',
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
        Đăng ký bằng Google (Dành cho EO)
      </button>

      <div className="auth-divider" style={{ margin: '1rem 0' }}>
        <span>Hoặc đăng ký bằng Email & Xác thực OTP</span>
      </div>

      {errorMessage && (
        <div className="auth-error-banner" role="alert">
          {errorMessage}
        </div>
      )}

      {successMessage && (
        <div className="auth-success-banner" role="status">
          {successMessage}
        </div>
      )}

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <div className="form-group">
          <label className="form-label" htmlFor="fullName">
            Họ và tên người đại diện
          </label>
          <input
            id="fullName"
            name="fullName"
            type="text"
            className="form-input"
            placeholder="Nguyễn Văn Đại Diện"
            value={formData.fullName}
            onChange={handleChange}
            autoComplete="name"
            disabled={isLoading}
            required
          />
        </div>

        <div className="form-row">
          <div className="form-group" style={{ flex: 1 }}>
            <label className="form-label" htmlFor="email">
              Địa chỉ Email doanh nghiệp
            </label>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <input
                id="email"
                name="email"
                type="email"
                className="form-input"
                placeholder="contact@company.com"
                value={formData.email}
                onChange={handleChange}
                autoComplete="email"
                disabled={isLoading}
                required
              />
              <button
                type="button"
                onClick={handleSendOtp}
                disabled={isLoading || isSendingOtp || countdown > 0}
                style={{
                  whiteSpace: 'nowrap',
                  padding: '0 0.85rem',
                  backgroundColor: countdown > 0 ? '#9ca3af' : '#2563eb',
                  color: '#ffffff',
                  border: 'none',
                  borderRadius: '0.5rem',
                  fontSize: '0.8125rem',
                  fontWeight: 600,
                  cursor: countdown > 0 ? 'not-allowed' : 'pointer',
                  transition: 'background-color 0.2s',
                }}
              >
                {isSendingOtp ? 'Đang gửi...' : countdown > 0 ? `Gửi lại (${countdown}s)` : 'Gửi mã OTP'}
              </button>
            </div>
          </div>

          <div className="form-group" style={{ flex: 1 }}>
            <label className="form-label" htmlFor="otpCode">
              Mã xác thực OTP (6 chữ số)
            </label>
            <input
              id="otpCode"
              name="otpCode"
              type="text"
              maxLength={6}
              className="form-input"
              placeholder="123456"
              value={formData.otpCode}
              onChange={handleChange}
              disabled={isLoading}
              style={{
                letterSpacing: '0.2em',
                fontWeight: 700,
                textAlign: 'center',
                backgroundColor: otpSent ? '#f0fdf4' : '#ffffff',
                borderColor: otpSent ? '#22c55e' : '#d1d5db',
              }}
              required
            />
          </div>
        </div>

        <div className="form-row">
          <div className="form-group" style={{ flex: 1 }}>
            <label className="form-label" htmlFor="phone">
              Số điện thoại liên hệ
            </label>
            <input
              id="phone"
              name="phone"
              type="tel"
              className="form-input"
              placeholder="0912345678"
              value={formData.phone}
              onChange={handleChange}
              autoComplete="tel"
              disabled={isLoading}
              required
            />
          </div>

          <div className="form-group" style={{ flex: 1 }}>
            <label className="form-label" htmlFor="companyName">
              Tên công ty / Đơn vị tổ chức sự kiện
            </label>
            <input
              id="companyName"
              name="companyName"
              type="text"
              className="form-input"
              placeholder="Công ty Sự kiện Á Châu"
              value={formData.companyName}
              onChange={handleChange}
              disabled={isLoading}
              required
            />
          </div>
        </div>

        <div className="form-row">
          <div className="form-group" style={{ flex: 1 }}>
            <label className="form-label" htmlFor="password">
              Mật khẩu (tối thiểu 6 ký tự)
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
                autoComplete="new-password"
                disabled={isLoading}
                required
              />
            </div>
          </div>

          <div className="form-group" style={{ flex: 1 }}>
            <label className="form-label" htmlFor="confirmPassword">
              Xác nhận lại mật khẩu
            </label>
            <div className="input-wrapper">
              <input
                id="confirmPassword"
                name="confirmPassword"
                type={showPassword ? 'text' : 'password'}
                className="form-input"
                placeholder="••••••••"
                value={formData.confirmPassword}
                onChange={handleChange}
                autoComplete="new-password"
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
        </div>

        <button type="submit" className="btn-submit" disabled={isLoading}>
          {isLoading ? (
            <>
              <span className="spinner" />
              Đang xác thực OTP & Tạo tài khoản Nhà Tổ Chức...
            </>
          ) : (
            'Đăng Ký Tài Khoản Nhà Tổ Chức'
          )}
        </button>
      </form>

      <div className="auth-footer" style={{ marginTop: '1.25rem', paddingTop: '1rem', borderTop: '1px solid #f3f4f6' }}>
        <div>
          Bạn là <strong>Nhân sự sự kiện (Worker)</strong>? Hãy tải ứng dụng <strong>CANi Mobile</strong> trên điện thoại để nhận ca làm linh hoạt.
        </div>
        <div style={{ marginTop: '0.5rem' }}>
          Đã có tài khoản? <a href="/login">Đăng nhập tại đây</a>
        </div>
      </div>
    </div>
  );
};

export default RegisterForm;
