import React from 'react';
import { RegisterForm } from '../../features/auth';

/**
 * Trang Đăng Ký Tài Khoản (Route: /register)
 */
const RegisterPage = () => {
  return (
    <div className="auth-container">
      <RegisterForm />
    </div>
  );
};

export default RegisterPage;
