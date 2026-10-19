import React from 'react';
import { useRegister } from '../hooks/useRegister';
import '../auth.css';

/**
 * Component Form Đăng Ký (React JSX)
 * Hỗ trợ tạo tài khoản cho Worker (Nhân sự sự kiện) và Event Organizer (EO)
 */
export const RegisterForm = () => {
  const {
    formData,
    isLoading,
    errorMessage,
    successMessage,
    showPassword,
    handleChange,
    setRole,
    handleSubmit,
    toggleShowPassword,
    handleQuickDemoWorkerRegister,
  } = useRegister();

  const isWorker = formData.role === 'WORKER';

  return (
    <div className="auth-card auth-card-wide">
      <div className="auth-header">
        <div className="auth-logo-badge">C</div>
        <h2 className="auth-title">Đăng Ký Tài Khoản</h2>
        <p className="auth-subtitle">
          {isWorker
            ? 'Tham gia mạng lưới nhân sự sự kiện linh hoạt CANi'
            : 'Đăng ký tài khoản dành cho Nhà tổ chức sự kiện'}
        </p>
      </div>

      {/* Bộ chuyển đổi Vai trò (Role Selector) */}
      <div className="role-selector" role="tablist">
        <button
          type="button"
          className={`role-tab ${isWorker ? 'active' : ''}`}
          onClick={() => setRole('WORKER')}
        >
          <span className="role-tab-icon">👷‍♂️</span>
          <span>Nhân Sự Sự Kiện (Worker)</span>
        </button>
        <button
          type="button"
          className={`role-tab ${!isWorker ? 'active' : ''}`}
          onClick={() => setRole('EVENT_ORGANIZER')}
        >
          <span className="role-tab-icon">🏢</span>
          <span>Nhà Tổ Chức (EO)</span>
        </button>
      </div>

      {/* Hộp quyền lợi khi chọn Worker */}
      {isWorker && (
        <div className="worker-perks-box">
          <div className="worker-perks-title">
            <span>✨ Đặc quyền Worker CANi:</span>
          </div>
          <div>• Khởi đầu với <strong>100 điểm tin cậy</strong> & Hạng kỹ năng <strong>STARTER</strong>.</div>
          <div>• Nhận thông báo ca làm linh hoạt theo giờ, điểm danh mã QR và nhận lương minh bạch.</div>
        </div>
      )}

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
        {/* Họ và tên */}
        <div className="form-group">
          <label className="form-label" htmlFor="fullName">
            Họ và tên của bạn
          </label>
          <input
            id="fullName"
            name="fullName"
            type="text"
            className="form-input"
            placeholder={isWorker ? 'Nguyễn Văn A' : 'Trần Thị Giám Đốc'}
            value={formData.fullName}
            onChange={handleChange}
            autoComplete="name"
            disabled={isLoading}
            required
          />
        </div>

        {/* Email & Số điện thoại (2 cột) */}
        <div className="form-row">
          <div className="form-group">
            <label className="form-label" htmlFor="email">
              Địa chỉ Email
            </label>
            <input
              id="email"
              name="email"
              type="email"
              className="form-input"
              placeholder="nhansu@cani.vn"
              value={formData.email}
              onChange={handleChange}
              autoComplete="email"
              disabled={isLoading}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="phone">
              Số điện thoại
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

        {/* Nếu là EO: Yêu cầu thêm Tên công ty */}
        {!isWorker && (
          <div className="form-group">
            <label className="form-label" htmlFor="companyName">
              Tên công ty / Đơn vị tổ chức
            </label>
            <input
              id="companyName"
              name="companyName"
              type="text"
              className="form-input"
              placeholder="Công ty TNHH Sự Kiện Sáng Tạo Á Châu"
              value={formData.companyName}
              onChange={handleChange}
              disabled={isLoading}
              required
            />
          </div>
        )}

        {/* Mật khẩu & Xác nhận mật khẩu (2 cột) */}
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
                tabIndex="-1"
              >
                {showPassword ? 'Ẩn' : 'Hiện'}
              </button>
            </div>
          </div>
        </div>

        {/* Nút Đăng ký chính */}
        <button type="submit" className="btn-submit" disabled={isLoading}>
          {isLoading ? (
            <>
              <span className="spinner" />
              Đang tạo tài khoản...
            </>
          ) : (
            isWorker ? 'Đăng Ký Làm Nhân Sự Sự Kiện (Worker)' : 'Đăng Ký Tài Khoản Nhà Tổ Chức'
          )}
        </button>
      </form>

      {/* Nút Test Nhanh Luồng Worker */}
      <div className="auth-divider">
        <span>Trải Nghiệm Nhanh</span>
      </div>

      <div className="demo-buttons-group">
        <button
          type="button"
          className="btn-demo"
          onClick={handleQuickDemoWorkerRegister}
          disabled={isLoading}
        >
          🚀 Test Nhanh: Vào Thẳng Portal Worker
        </button>
      </div>

      <div className="auth-footer">
        Đã có tài khoản? <a href="/login">Đăng nhập tại đây</a>
      </div>
    </div>
  );
};

export default RegisterForm;
