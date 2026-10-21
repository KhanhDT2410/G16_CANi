import React from 'react';
import { LoginForm } from '../../features/auth';

/**
 * Trang Đăng Nhập (Route Page: /login)
 */
const LoginPage = () => {
  return (
    <div className="auth-container">
      <LoginForm />
    </div>
  );
};

export default LoginPage;
