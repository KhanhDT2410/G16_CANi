import React, { useState, useEffect } from 'react';
import { authApi } from '../api/authApi';
import { showToast } from '../../../components/common/Toast';
import './ForgotPasswordModal.css';

export default function ForgotPasswordModal({ isOpen, onClose, onResetSuccess }) {
  const [step, setStep] = useState(1); // 1: Input email, 2: Input OTP & new password, 3: Success
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [countdown, setCountdown] = useState(0);

  // Countdown timer for resending OTP
  useEffect(() => {
    let timer;
    if (countdown > 0) {
      timer = setTimeout(() => setCountdown((prev) => prev - 1), 1000);
    }
    return () => clearTimeout(timer);
  }, [countdown]);

  // Reset modal state when closed or opened
  useEffect(() => {
    if (isOpen) {
      setStep(1);
      setErrorMessage('');
      setOtp('');
      setNewPassword('');
      setConfirmPassword('');
      setCountdown(0);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  // Step 1: Request OTP to email
  const handleRequestOtp = async (e) => {
    if (e) e.preventDefault();
    if (!email.trim()) {
      setErrorMessage('Vui lòng nhập địa chỉ email tài khoản của bạn.');
      return;
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email.trim())) {
      setErrorMessage('Định dạng email không hợp lệ.');
      return;
    }

    setIsLoading(true);
    setErrorMessage('');
    try {
      await authApi.resetPassword({ email: email.trim().toLowerCase() });
      setStep(2);
      setCountdown(60);
      showToast('Đã gửi mã xác thực 6 số đến email của bạn!', 'success');
    } catch (err) {
      setErrorMessage(err.message || 'Không thể gửi mã xác thực. Vui lòng kiểm tra lại email.');
    } finally {
      setIsLoading(false);
    }
  };

  // Resend OTP
  const handleResendOtp = async () => {
    if (countdown > 0 || isLoading) return;
    setIsLoading(true);
    setErrorMessage('');
    try {
      await authApi.resetPassword({ email: email.trim().toLowerCase() });
      setCountdown(60);
      showToast('Đã gửi lại mã OTP mới đến email của bạn!', 'success');
    } catch (err) {
      setErrorMessage(err.message || 'Gửi lại OTP thất bại.');
    } finally {
      setIsLoading(false);
    }
  };

  // Step 2: Confirm OTP & reset password
  const handleConfirmReset = async (e) => {
    if (e) e.preventDefault();
    if (!otp.trim() || otp.trim().length !== 6) {
      setErrorMessage('Mã OTP phải có đúng 6 chữ số.');
      return;
    }
    if (!newPassword) {
      setErrorMessage('Vui lòng nhập mật khẩu mới.');
      return;
    }
    if (newPassword.length < 8) {
      setErrorMessage('Mật khẩu mới phải có tối thiểu 8 ký tự.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setErrorMessage('Mật khẩu xác nhận không khớp.');
      return;
    }

    setIsLoading(true);
    setErrorMessage('');
    try {
      await authApi.confirmResetPassword({
        email: email.trim().toLowerCase(),
        otp: otp.trim(),
        newPassword,
      });
      setStep(3);
      showToast('Đặt lại mật khẩu thành công!', 'success');
      if (onResetSuccess) {
        onResetSuccess(email.trim().toLowerCase());
      }
    } catch (err) {
      setErrorMessage(err.message || 'Xác thực OTP thất bại hoặc mật khẩu không hợp lệ.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="forgot-modal-backdrop" onClick={onClose}>
      <div className="forgot-modal-container" onClick={(e) => e.stopPropagation()}>
        <button
          type="button"
          className="forgot-modal-close"
          onClick={onClose}
          title="Đóng cửa sổ"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>

        {step === 1 && (
          <div>
            <div className="forgot-modal-header">
              <div className="forgot-modal-icon-badge">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                </svg>
              </div>
              <h3 className="forgot-modal-title">Quên Mật Khẩu?</h3>
              <p className="forgot-modal-subtitle">
                Nhập địa chỉ email tài khoản của bạn để nhận mã xác thực OTP khôi phục mật khẩu.
              </p>
            </div>

            <div style={{ textAlign: 'center' }}>
              <span className="forgot-step-badge">
                <span className="dot" /> Bước 1: Nhập Email xác thực
              </span>
            </div>

            {errorMessage && (
              <div className="auth-error-banner" role="alert" style={{ marginBottom: '1rem' }}>
                {errorMessage}
              </div>
            )}

            <form onSubmit={handleRequestOtp} className="auth-form">
              <div className="form-group">
                <label className="form-label" htmlFor="forgot-email">
                  Địa chỉ Email đăng ký
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
                    id="forgot-email"
                    type="email"
                    className="form-input with-icon"
                    placeholder="name@cani.vn"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    disabled={isLoading}
                    autoFocus
                    required
                  />
                </div>
              </div>

              <button
                type="submit"
                className="btn-submit btn-pill-primary"
                disabled={isLoading}
                style={{ marginTop: '0.5rem' }}
              >
                {isLoading ? (
                  <>
                    <span className="spinner" />
                    Đang gửi mã...
                  </>
                ) : (
                  <>
                    <span>Gửi Mã Xác Thực OTP</span>
                    <span className="btn-submit-arrow">→</span>
                  </>
                )}
              </button>
            </form>
          </div>
        )}

        {step === 2 && (
          <div>
            <div className="forgot-modal-header">
              <div className="forgot-modal-icon-badge" style={{ background: '#f0fdf4', color: '#16a34a' }}>
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path>
                  <polyline points="22,6 12,13 2,6"></polyline>
                </svg>
              </div>
              <h3 className="forgot-modal-title">Nhập Mã OTP & Đặt Mật Khẩu</h3>
              <p className="forgot-modal-subtitle">
                Mã xác thực 6 số đã được gửi tới <strong>{email}</strong>
              </p>
            </div>

            <div style={{ textAlign: 'center' }}>
              <span className="forgot-step-badge">
                <span className="dot" /> Bước 2: Thiết lập mật khẩu mới
              </span>
            </div>

            {errorMessage && (
              <div className="auth-error-banner" role="alert" style={{ marginBottom: '1rem' }}>
                {errorMessage}
              </div>
            )}

            <form onSubmit={handleConfirmReset} className="auth-form">
              <div className="form-group">
                <label className="form-label" htmlFor="forgot-otp">
                  Mã OTP 6 số từ Email *
                </label>
                <input
                  id="forgot-otp"
                  type="text"
                  maxLength={6}
                  className="form-input forgot-otp-input"
                  placeholder="••••••"
                  value={otp}
                  onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                  disabled={isLoading}
                  autoFocus
                  required
                />
                <div className="forgot-resend-row">
                  <span style={{ color: '#64748b' }}>Không nhận được mã?</span>
                  <button
                    type="button"
                    className="btn-resend-otp"
                    onClick={handleResendOtp}
                    disabled={countdown > 0 || isLoading}
                  >
                    {countdown > 0 ? `Gửi lại sau (${countdown}s)` : 'Gửi lại mã OTP'}
                  </button>
                </div>
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="forgot-new-password">
                  Mật khẩu mới (Tối thiểu 8 ký tự) *
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
                    id="forgot-new-password"
                    type={showPassword ? 'text' : 'password'}
                    className="form-input with-icon with-eye"
                    placeholder="••••••••"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    disabled={isLoading}
                    required
                  />
                  <button
                    type="button"
                    className="toggle-password-btn"
                    onClick={() => setShowPassword((prev) => !prev)}
                    tabIndex={-1}
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

              <div className="form-group">
                <label className="form-label" htmlFor="forgot-confirm-password">
                  Nhập lại mật khẩu mới *
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
                    id="forgot-confirm-password"
                    type={showPassword ? 'text' : 'password'}
                    className="form-input with-icon"
                    placeholder="••••••••"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    disabled={isLoading}
                    required
                  />
                </div>
              </div>

              <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem' }}>
                <button
                  type="button"
                  className="btn-demo"
                  onClick={() => setStep(1)}
                  disabled={isLoading}
                  style={{ flex: 1 }}
                >
                  ← Quay lại
                </button>
                <button
                  type="submit"
                  className="btn-submit btn-pill-primary"
                  disabled={isLoading}
                  style={{ flex: 2 }}
                >
                  {isLoading ? (
                    <>
                      <span className="spinner" />
                      Đang xử lý...
                    </>
                  ) : (
                    'Cập Nhật Mật Khẩu'
                  )}
                </button>
              </div>
            </form>
          </div>
        )}

        {step === 3 && (
          <div className="forgot-success-box">
            <div className="forgot-success-icon">
              <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                <polyline points="20 6 9 17 4 12"></polyline>
              </svg>
            </div>
            <h3 className="forgot-success-title">Đổi Mật Khẩu Thành Công!</h3>
            <p className="forgot-success-desc">
              Mật khẩu tài khoản của bạn đã được cập nhật an toàn. Vui lòng đăng nhập lại với mật khẩu mới.
            </p>
            <button
              type="button"
              className="btn-submit btn-pill-primary"
              onClick={onClose}
              style={{ width: '100%' }}
            >
              Đăng Nhập Ngay →
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
