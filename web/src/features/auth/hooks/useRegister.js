import { useState, useEffect } from 'react';
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
    otpCode: '',
  });

  const [isLoading, setIsLoading] = useState(false);
  const [isSendingOtp, setIsSendingOtp] = useState(false);
  const [otpSent, setOtpSent] = useState(false);
  const [countdown, setCountdown] = useState(0);

  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  useEffect(() => {
    let timer;
    if (countdown > 0) {
      timer = setTimeout(() => setCountdown((prev) => prev - 1), 1000);
    }
    return () => clearTimeout(timer);
  }, [countdown]);

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

  const handleSendOtp = async () => {
    if (!formData.email?.trim()) {
      setErrorMessage('Vui lòng nhập địa chỉ email doanh nghiệp trước khi lấy mã OTP.');
      return;
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(formData.email.trim())) {
      setErrorMessage('Định dạng email không hợp lệ.');
      return;
    }

    setIsSendingOtp(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      await authApi.sendOtp(formData.email.trim(), 'REGISTER');
      setOtpSent(true);
      setCountdown(60);
      setSuccessMessage('Mã xác thực 6 số đã được gửi tới email của bạn. Vui lòng kiểm tra hộp thư.');
    } catch (err) {
      setErrorMessage(err.message || 'Không thể gửi mã OTP. Vui lòng thử lại sau.');
    } finally {
      setIsSendingOtp(false);
    }
  };

  const handleGoogleLogin = async () => {
    setIsLoading(true);
    setErrorMessage('');
    try {
      await authApi.loginWithGoogle();
    } catch (err) {
      setErrorMessage(err.message || 'Không thể đăng ký / đăng nhập bằng Google.');
      setIsLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    if (e) e.preventDefault();

    if (!formData.fullName.trim()) {
      setErrorMessage('Vui lòng nhập họ và tên người đại diện.');
      return;
    }
    if (!formData.email.trim()) {
      setErrorMessage('Vui lòng nhập địa chỉ email doanh nghiệp.');
      return;
    }
    if (!formData.phone?.trim()) {
      setErrorMessage('Vui lòng nhập số điện thoại liên hệ.');
      return;
    }
    if (!formData.companyName?.trim()) {
      setErrorMessage('Vui lòng nhập tên công ty hoặc đơn vị tổ chức sự kiện.');
      return;
    }
    if (!formData.otpCode?.trim() || formData.otpCode.trim().length !== 6) {
      setErrorMessage('Vui lòng nhập chính xác mã xác thực OTP 6 số đã gửi về email.');
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
        fullName: formData.fullName.trim(),
        email: formData.email.trim(),
        phone: formData.phone.trim(),
        companyName: formData.companyName.trim(),
        password: formData.password,
        otpCode: formData.otpCode.trim(),
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
    isSendingOtp,
    otpSent,
    countdown,
    errorMessage,
    successMessage,
    showPassword,
    handleChange,
    handleSendOtp,
    handleGoogleLogin,
    handleSubmit,
    toggleShowPassword,
  };
};
