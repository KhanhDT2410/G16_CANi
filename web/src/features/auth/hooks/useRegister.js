import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';

/**
 * Custom Hook quản lý trạng thái và logic đăng ký tài khoản (Hỗ trợ Worker và EO)
 */
export const useRegister = () => {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    fullName: '',
    email: '',
    phone: '',
    password: '',
    confirmPassword: '',
    role: 'WORKER', // Mặc định là WORKER theo yêu cầu
    companyName: '',
  });

  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));
    if (errorMessage) setErrorMessage('');
  };

  const setRole = (role) => {
    setFormData((prev) => ({
      ...prev,
      role: role,
    }));
    if (errorMessage) setErrorMessage('');
  };

  const toggleShowPassword = () => {
    setShowPassword((prev) => !prev);
  };

  const handleSubmit = async (e) => {
    if (e) e.preventDefault();

    // Client-side Validation
    if (!formData.fullName.trim()) {
      setErrorMessage('Vui lòng nhập họ và tên.');
      return;
    }
    if (!formData.email.trim()) {
      setErrorMessage('Vui lòng nhập địa chỉ email.');
      return;
    }
    if (!formData.phone.trim()) {
      setErrorMessage('Vui lòng nhập số điện thoại để nhận thông báo ca làm.');
      return;
    }
    if (!formData.password) {
      setErrorMessage('Vui lòng nhập mật khẩu.');
      return;
    }
    if (formData.password.length < 6) {
      setErrorMessage('Mật khẩu phải có độ dài từ 6 ký tự trở lên.');
      return;
    }
    if (formData.password !== formData.confirmPassword) {
      setErrorMessage('Mật khẩu xác nhận không khớp.');
      return;
    }
    if (formData.role === 'EVENT_ORGANIZER' && !formData.companyName.trim()) {
      setErrorMessage('Vui lòng nhập tên công ty hoặc đơn vị tổ chức sự kiện.');
      return;
    }

    setIsLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      await authApi.register({
        fullName: formData.fullName,
        email: formData.email,
        phone: formData.phone,
        password: formData.password,
        role: formData.role,
        companyName: formData.role === 'EVENT_ORGANIZER' ? formData.companyName : null,
      });

      setSuccessMessage('Đăng ký tài khoản thành công! Đang chuyển hướng...');
      setTimeout(() => {
        navigate('/login');
      }, 1500);
    } catch (err) {
      setErrorMessage(err.message || 'Đăng ký thất bại. Vui lòng thử lại.');
    } finally {
      setIsLoading(false);
    }
  };

  // Hỗ trợ test nhanh luồng Worker khi Backend chưa bật
  const handleQuickDemoWorkerRegister = () => {
    const mockWorkerUser = {
      id: 'demo-worker-' + Date.now(),
      name: formData.fullName || 'Nguyễn Văn Nhân Sự (Worker)',
      email: formData.email || 'worker@cani.vn',
      phone: formData.phone || '0987654321',
      role: 'WORKER',
      reliabilityScore: 100,
      tier: 'STARTER',
    };
    localStorage.setItem('cani_token', 'demo-worker-jwt-token');
    localStorage.setItem('cani_user', JSON.stringify(mockWorkerUser));
    navigate('/worker');
  };

  return {
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
  };
};
