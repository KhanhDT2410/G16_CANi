import React from 'react';
import { Link } from 'react-router-dom';
import { useRegister } from '../hooks/useRegister';
import CaniLogo from '../../../components/common/CaniLogo';
import '../auth.css';

export const RegisterForm = () => {
  const {
    step,
    formData,
    fieldErrors,
    isLoading,
    isSendingOtp,
    countdown,
    errorMessage,
    successMessage,
    showPassword,
    showConfirmPassword,
    handleChange,
    handleSendOtp,
    handleProceedToStep2,
    handleBackToStep1,
    handleGoogleLogin,
    handleSubmit,
    toggleShowPassword,
    toggleShowConfirmPassword,
  } = useRegister();

  return (
    <div className="auth-card auth-card-wide">
      <div className="auth-header" style={{ marginBottom: '1.25rem' }}>
        <CaniLogo variant="full" height={52} className="auth-logo-center" />
        <h2 className="auth-title" style={{ marginTop: '0.85rem' }}>Đăng Ký Nhà Tổ Chức</h2>
        <p className="auth-subtitle">
          Tạo tài khoản dành riêng cho Nhà Tổ Chức Sự Kiện trên CANi Web Portal
        </p>
      </div>

      {/* Thanh chỉ báo quy trình 2 bước */}
      <div className="register-stepper">
        <div className={`register-step ${step === 1 ? 'active' : 'done'}`}>
          <span className="register-step-badge">{step > 1 ? '✓' : '1'}</span>
          <span className="register-step-label">Điền thông tin</span>
        </div>
        <div className={`register-step-line ${step === 2 ? 'done' : ''}`} />
        <div className={`register-step ${step === 2 ? 'active' : ''}`}>
          <span className="register-step-badge">2</span>
          <span className="register-step-label">Xác thực OTP</span>
        </div>
      </div>

      {errorMessage && (
        <div className="auth-error-banner" role="alert" style={{ marginBottom: '1rem' }}>
          {errorMessage}
        </div>
      )}

      {successMessage && (
        <div className="auth-success-banner" role="status">
          {successMessage}
        </div>
      )}

      {step === 1 ? (
        <>
          <div
            className="worker-perks-box"
            style={{ backgroundColor: '#fefce8', borderColor: '#fde047', color: '#854d0e', marginBottom: '1.15rem' }}
          >
            <div className="worker-perks-title" style={{ color: '#a16207' }}>
              <span>Quy định tạo sự kiện & tuyển dụng:</span>
            </div>
            <div style={{ fontSize: '0.825rem', marginTop: '0.15rem' }}>
              Sau khi đăng ký, bạn cần <strong>hoàn tất định danh eKYC</strong> để tạo sự kiện và tuyển dụng nhân sự.
            </div>
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
            Đăng ký nhanh bằng Google (Dành cho EO)
          </button>

          <div className="auth-divider" style={{ margin: '1rem 0' }}>
            <span>Hoặc điền thông tin đăng ký qua Email</span>
          </div>

          <form className="auth-form" onSubmit={handleProceedToStep2} noValidate>
            {/* Họ và tên */}
            <div className="form-group">
              <label className="form-label" htmlFor="fullName">
                Họ và tên người tổ chức sự kiện *
              </label>
              <input
                id="fullName"
                name="fullName"
                type="text"
                className={`form-input ${fieldErrors.fullName ? 'input-error' : ''}`}
                placeholder="Nguyễn Văn A"
                value={formData.fullName}
                onChange={handleChange}
                autoComplete="name"
                disabled={isSendingOtp}
                required
              />
              {fieldErrors.fullName && (
                <div className="field-error-text">⚠️ {fieldErrors.fullName}</div>
              )}
            </div>

            {/* Tên đơn vị / nhóm tổ chức */}
            <div className="form-group">
              <label className="form-label" htmlFor="companyName">
                Tên đơn vị / nhóm tổ chức sự kiện (Tùy chọn)
              </label>
              <input
                id="companyName"
                name="companyName"
                type="text"
                className="form-input"
                placeholder="CLB Sự Kiện Trẻ / Nhóm điều phối tự do..."
                value={formData.companyName}
                onChange={handleChange}
                disabled={isSendingOtp}
              />
            </div>

            {/* Email liên hệ (Full width thoáng rõ) */}
            <div className="form-group">
              <label className="form-label" htmlFor="email">
                Địa chỉ Email liên hệ *
              </label>
              <input
                id="email"
                name="email"
                type="email"
                className={`form-input ${fieldErrors.email ? 'input-error' : ''}`}
                placeholder="contact@company.com"
                value={formData.email}
                onChange={handleChange}
                autoComplete="email"
                disabled={isSendingOtp}
                required
              />
              {fieldErrors.email ? (
                <div className="field-error-text">⚠️ {fieldErrors.email}</div>
              ) : (
                <div className="field-hint-text">
                  Hệ thống sẽ gửi mã OTP 6 chữ số về email này ở bước tiếp theo.
                </div>
              )}
            </div>

            {/* Số điện thoại */}
            <div className="form-group">
              <label className="form-label" htmlFor="phone">
                Số điện thoại liên hệ *
              </label>
              <input
                id="phone"
                name="phone"
                type="tel"
                className={`form-input ${fieldErrors.phone ? 'input-error' : ''}`}
                placeholder="0912345678"
                value={formData.phone}
                onChange={handleChange}
                autoComplete="tel"
                disabled={isSendingOtp}
                required
              />
              {fieldErrors.phone && (
                <div className="field-error-text">⚠️ {fieldErrors.phone}</div>
              )}
            </div>

            {/* Mật khẩu */}
            <div className="form-group">
              <label className="form-label" htmlFor="password">
                Mật khẩu đăng nhập *
              </label>
              <div className="input-wrapper">
                <input
                  id="password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  className={`form-input ${fieldErrors.password ? 'input-error' : ''}`}
                  placeholder="Tối thiểu 8 ký tự, có 1 chữ in hoa (A-Z)"
                  value={formData.password}
                  onChange={handleChange}
                  autoComplete="new-password"
                  disabled={isSendingOtp}
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
              {fieldErrors.password ? (
                <div className="field-error-text">⚠️ {fieldErrors.password}</div>
              ) : (
                <div className="field-hint-text">
                  Mật khẩu bắt buộc có tối thiểu 8 ký tự và chứa ít nhất 1 chữ cái in hoa (A-Z).
                </div>
              )}
            </div>

            {/* Xác nhận lại mật khẩu */}
            <div className="form-group">
              <label className="form-label" htmlFor="confirmPassword">
                Xác nhận lại mật khẩu *
              </label>
              <div className="input-wrapper">
                <input
                  id="confirmPassword"
                  name="confirmPassword"
                  type={showConfirmPassword ? 'text' : 'password'}
                  className={`form-input ${fieldErrors.confirmPassword ? 'input-error' : ''}`}
                  placeholder="Nhập lại chính xác mật khẩu đã tạo ở trên"
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  autoComplete="new-password"
                  disabled={isSendingOtp}
                  required
                />
                <button
                  type="button"
                  className="toggle-password-btn"
                  onClick={toggleShowConfirmPassword}
                  tabIndex={-1}
                >
                  {showConfirmPassword ? 'Ẩn' : 'Hiện'}
                </button>
              </div>
              {fieldErrors.confirmPassword ? (
                <div className="field-error-text">⚠️ {fieldErrors.confirmPassword}</div>
              ) : (
                <div className="field-hint-text">
                  Nhập lại mật khẩu để đảm bảo bạn không gõ nhầm ký tự.
                </div>
              )}
            </div>

            <button type="submit" className="btn-submit" disabled={isSendingOtp}>
              {isSendingOtp ? (
                <>
                  <span className="spinner" />
                  Đang gửi mã OTP xác thực...
                </>
              ) : (
                'Tiếp tục & Gửi mã OTP →'
              )}
            </button>
          </form>
        </>
      ) : (
        /* BƯỚC 2: XÁC THỰC MÃ OTP EMAIL */
        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="otp-step-summary">
            <div style={{ fontWeight: 700, color: '#0f172a', marginBottom: '0.25rem' }}>
              Kiểm tra hộp thư Email của bạn
            </div>
            <div style={{ fontSize: '0.85rem', color: '#475569', lineHeight: 1.45 }}>
              Mã xác thực 6 chữ số đã được gửi tới{' '}
              <strong style={{ color: '#2563eb' }}>{formData.email}</strong>. Vui lòng nhập mã bên dưới để hoàn tất đăng ký.
            </div>
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="otpCode" style={{ textAlign: 'center' }}>
              Mã xác thực OTP (6 chữ số) *
            </label>
            <input
              id="otpCode"
              name="otpCode"
              type="text"
              inputMode="numeric"
              maxLength={6}
              autoFocus
              className={`form-input otp-input-large ${fieldErrors.otpCode ? 'input-error' : ''}`}
              placeholder="● ● ● ● ● ●"
              value={formData.otpCode}
              onChange={handleChange}
              disabled={isLoading}
              required
            />
            {fieldErrors.otpCode && (
              <div className="field-error-text" style={{ justifyContent: 'center' }}>
                ⚠️ {fieldErrors.otpCode}
              </div>
            )}
          </div>

          <div className="otp-resend-row">
            <span>Bạn chưa nhận được mã?</span>
            <button
              type="button"
              className="otp-resend-btn"
              onClick={handleSendOtp}
              disabled={isLoading || isSendingOtp || countdown > 0}
            >
              {isSendingOtp
                ? 'Đang gửi lại...'
                : countdown > 0
                  ? `Gửi lại sau ${countdown}s`
                  : 'Gửi lại mã OTP'}
            </button>
          </div>

          <div className="register-step2-actions">
            <button
              type="button"
              className="btn-demo"
              onClick={handleBackToStep1}
              disabled={isLoading}
              style={{ padding: '0.8rem 1rem', fontWeight: 600 }}
            >
              ← Sửa thông tin
            </button>
            <button
              type="submit"
              className="btn-submit"
              disabled={isLoading}
              style={{ flex: 2 }}
            >
              {isLoading ? (
                <>
                  <span className="spinner" />
                  Đang tạo tài khoản...
                </>
              ) : (
                'Xác nhận OTP & Đăng ký'
              )}
            </button>
          </div>
        </form>
      )}

      <div className="auth-footer" style={{ marginTop: '1.25rem', paddingTop: '1rem', borderTop: '1px solid #f3f4f6' }}>
        <div>
          Bạn là <strong>Nhân sự sự kiện</strong>? Hãy tải ứng dụng <strong>CANi Mobile (Android APK)</strong> trên điện thoại để nhận ca làm linh hoạt.
        </div>
        <div style={{ marginTop: '0.5rem' }}>
          Đã có tài khoản? <Link to="/login">Đăng nhập tại đây</Link>
        </div>
      </div>
    </div>
  );
};

export default RegisterForm;
