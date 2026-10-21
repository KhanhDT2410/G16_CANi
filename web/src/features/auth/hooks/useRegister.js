import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';

export const useRegister = () => {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    fullName: '',
    email: '',
    phone: '',
    companyName: '',
    password: '',
    confirmPassword: '',
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

  const toggleShowPassword = () => {
    setShowPassword((prev) => !prev);
  };

  const handleSubmit = async (e) => {
    if (e) e.preventDefault();

    if (!formData.fullName.trim()) {
      setErrorMessage('Vui lòng nhập họ và tên.');
      return;
    }
    if (!formData.email.trim()) {
      setErrorMessage('Vui lòng nhập địa chỉ email.');
      return;
    }
    if (!formData.phone?.trim()) {
      setErrorMessage('Vui lòng nhập số điện thoại liên hệ.');
      return;
    }
    if (!formData.companyName?.trim()) {
      setErrorMessage('Vui lòng nhập tên công ty hoặc tổ chức sự kiện.');
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

    setIsLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      await authApi.register({
        fullName: formData.fullName,
        email: formData.email,
        phone: formData.phone,
        companyName: formData.companyName,
        password: formData.password,
        accountType: 'EVENT_ORGANIZER',
      });

      setSuccessMessage('Đăng ký tài khoản thành công! Đang chuyển hướng đến trang đăng nhập...');
      setTimeout(() => {
        navigate('/login');
      }, 1500);
    } catch (err) {
      setErrorMessage(err.message || 'Đăng ký tài khoản không thành công. Vui lòng thử lại.');
    } finally {
      setIsLoading(false);
    }
  };

  return {
    formData,
    isLoading,
    errorMessage,
    successMessage,
    showPassword,
    handleChange,
    handleSubmit,
    toggleShowPassword,
  };
};
