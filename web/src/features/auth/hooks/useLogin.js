import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';

/**
 * Custom Hook quản lý toàn bộ logic và trạng thái của form đăng nhập
 */
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

  // Xử lý thay đổi input
  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
    // Xóa thông báo lỗi khi người dùng bắt đầu gõ lại
    if (errorMessage) setErrorMessage('');
  };

  // Toggle ẩn/hiện mật khẩu
  const toggleShowPassword = () => {
    setShowPassword((prev) => !prev);
  };

  // Xử lý submit form
  const handleSubmit = async (e) => {
    if (e) e.preventDefault();

    // Validation cơ bản phía client
    if (!formData.email.trim()) {
      setErrorMessage('Vui lòng nhập địa chỉ email hoặc tên đăng nhập.');
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

      // Lưu trữ session nếu đăng nhập thành công
      if (data.token) {
        localStorage.setItem('cani_token', data.token);
      }
      if (data.user) {
        localStorage.setItem('cani_user', JSON.stringify(data.user));
      }

      // Điều hướng theo 5 role chính của hệ thống
      if (data.user?.role === 'ADMIN') navigate('/admin');
      else if (data.user?.role === 'FINANCE') navigate('/finance');
      else if (data.user?.role === 'CSKH') navigate('/cskh');
      else if (data.user?.role === 'WORKER') navigate('/worker');
      else navigate('/eo');
    } catch (err) {
      setErrorMessage(err.message || 'Đăng nhập thất bại.');
    } finally {
      setIsLoading(false);
    }
  };

  // Tính năng tiện ích: Đăng nhập thử nghiệm nhanh khi chưa bật Backend
  const handleQuickDemoLogin = (role = 'EO') => {
    let demoName = 'Event Organizer Demo';
    let demoEmail = 'eo@cani.vn';
    let targetPath = '/eo';

    if (role === 'ADMIN') {
      demoName = 'Admin Quản Trị';
      demoEmail = 'admin@cani.vn';
      targetPath = '/admin';
    } else if (role === 'FINANCE') {
      demoName = 'Trưởng Phòng Tài Chính';
      demoEmail = 'finance@cani.vn';
      targetPath = '/finance';
    } else if (role === 'CSKH') {
      demoName = 'Chuyên Viên CSKH';
      demoEmail = 'cskh@cani.vn';
      targetPath = '/cskh';
    } else if (role === 'WORKER') {
      demoName = 'Nguyễn Văn Worker';
      demoEmail = 'worker@cani.vn';
      targetPath = '/worker';
    }

    const mockUser = {
      id: 'demo-' + role.toLowerCase(),
      name: demoName,
      email: demoEmail,
      role: role,
      reliabilityScore: 100,
      tier: 'STARTER',
    };
    localStorage.setItem('cani_token', 'demo-jwt-token-123456');
    localStorage.setItem('cani_user', JSON.stringify(mockUser));

    navigate(targetPath);
  };

  return {
    formData,
    isLoading,
    errorMessage,
    showPassword,
    handleChange,
    handleSubmit,
    toggleShowPassword,
    handleQuickDemoLogin,
  };
};
