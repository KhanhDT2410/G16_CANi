import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';

export const useLogin = () => {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    email: '',
    password: '',
    rememberMe: false,
  });

  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
    if (errorMessage) setErrorMessage('');
  };

  const toggleShowPassword = () => {
    setShowPassword((prev) => !prev);
  };

  const handleSubmit = async (e) => {
    if (e) e.preventDefault();

    if (!formData.email.trim()) {
      setErrorMessage('Vui lòng nhập địa chỉ email.');
      return;
    }
    if (!formData.password) {
      setErrorMessage('Vui lòng nhập mật khẩu.');
      return;
    }

    setIsLoading(true);
    setErrorMessage('');

    try {
      const data = await authApi.login({
        email: formData.email,
        password: formData.password,
      });

      if (data.user && data.user.role === 'WORKER') {
        await authApi.logout();
        setErrorMessage('Tài khoản Nhân sự (Worker) vui lòng đăng nhập trên ứng dụng CANi Mobile!');
        return;
      }

      if (data.token) {
        localStorage.setItem('cani_token', data.token);
      }
      if (data.refreshToken) {
        localStorage.setItem('cani_refresh_token', data.refreshToken);
      }
      if (data.user) {
        localStorage.setItem('cani_user', JSON.stringify(data.user));
      }

      switch (data.user?.role) {
        case 'ADMIN':
          navigate('/admin');
          break;
        case 'CUSTOMER_CARE':
          navigate('/customer-care');
          break;
        case 'EVENT_ORGANIZER':
        default:
          navigate('/eo');
          break;
      }
    } catch (err) {
      setErrorMessage(err.message || 'Đăng nhập không thành công.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleGoogleLogin = async () => {
    setIsLoading(true);
    setErrorMessage('');
    try {
      await authApi.loginWithGoogle();
    } catch (err) {
      setErrorMessage(err.message || 'Không thể đăng nhập bằng Google.');
      setIsLoading(false);
    }
  };

  return {
    formData,
    isLoading,
    errorMessage,
    showPassword,
    handleChange,
    handleSubmit,
    toggleShowPassword,
    handleGoogleLogin,
  };
};
