import React from 'react';
import { useRegister } from '../hooks/useRegister';
import '../auth.css';

export const RegisterForm = () => {
  const {
    formData,
    isLoading,
    errorMessage,
    successMessage,
    showPassword,
    handleChange,
    handleSubmit,
    toggleShowPassword,
  } = useRegister();

  return (
    <div className="auth-card auth-card-wide">
      <div className="auth-header">
        <div className="auth-logo-badge">C</div>
        <h2 className="auth-title">Đăng Ký Nhà Tổ Chức</h2>
        <p className="auth-subtitle">
          Tạo tài khoản dành riêng cho Ban Tổ Chức & Doanh Nghiệp Sự Kiện
        </p>
      </div>

      <div className="worker-perks-box" style={{ backgroundColor: '#f0fdf4', borderColor: '#bbf7d0', color: '#166534' }}>
        <div className="worker-perks-title" style={{ color: '#15803d' }}>
          <span>Quyền lợi Nhà Tổ Chức (Event Organizer):</span>
        </div>
        <div>• Đăng tải không giới hạn ca làm, tuyển chọn nhân sự sự kiện linh hoạt và chất lượng cao.</div>
        <div>• Tự động khớp ca, quản lý điểm danh QR thời gian thực và thanh toán tự động, minh bạch.</div>
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
          <div className="form-group">
            <label className="form-label" htmlFor="email">
              Địa chỉ Email doanh nghiệp
            </label>
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
          </div>

          <div className="form-group">
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
        </div>

        <div className="form-group">
          <label className="form-label" htmlFor="companyName">
            Tên công ty / Đơn vị tổ chức sự kiện
          </label>
          <input
            id="companyName"
            name="companyName"
            type="text"
            className="form-input"
            placeholder="Công ty Cổ phần Truyền thông & Sự kiện Á Châu"
            value={formData.companyName}
            onChange={handleChange}
            disabled={isLoading}
            required
          />
        </div>

        <div className="form-row">
          <div className="form-group">
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

          <div className="form-group">
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
              Đang tạo tài khoản Nhà Tổ Chức...
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
