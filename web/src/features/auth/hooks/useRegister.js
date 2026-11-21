import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_REGEX = /^(\+84|0)[0-9]{8,10}$/;

export const useRegister = () => {
  const navigate = useNavigate();

  // step = 1: Điền thông tin tài khoản | step = 2: Nhập & Xác thực OTP
  const [step, setStep] = useState(1);

  const [formData, setFormData] = useState({
    fullName: '',
    email: '',
    phone: '',
    organizerType: 'INDIVIDUAL',
    companyName: '',
    password: '',
    confirmPassword: '',
    otpCode: '',
  });

  const [fieldErrors, setFieldErrors] = useState({
    fullName: '',
    email: '',
    phone: '',
    companyName: '',
    otpCode: '',
    password: '',
    confirmPassword: '',
  });

  const [isLoading, setIsLoading] = useState(false);
  const [isSendingOtp, setIsSendingOtp] = useState(false);
  const [otpSent, setOtpSent] = useState(false);
  const [countdown, setCountdown] = useState(0);

  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  useEffect(() => {
    let timer;
    if (countdown > 0) {
      timer = setTimeout(() => setCountdown((prev) => prev - 1), 1000);
    }
    return () => clearTimeout(timer);
  }, [countdown]);

  // Kiểm tra định dạng mật khẩu theo quy chuẩn Backend (@Size(min=8), @Pattern uppercase)
  const validatePasswordFormat = (pwd) => {
    if (!pwd || !pwd.trim()) {
      return 'Mật khẩu không được để trống.';
    }
    const hasMinLength = pwd.length >= 8;
    const hasUppercase = /[A-Z]/.test(pwd);

    if (!hasMinLength && !hasUppercase) {
      return 'Mật khẩu phải có tối thiểu 8 ký tự và chứa ít nhất 1 chữ cái in hoa (A-Z).';
    }
    if (!hasMinLength) {
      return 'Mật khẩu phải có tối thiểu 8 ký tự.';
    }
    if (!hasUppercase) {
      return 'Mật khẩu phải chứa ít nhất 1 chữ cái in hoa (A-Z).';
    }
    return '';
  };

  const validateConfirmPassword = (confirmPwd, pwd) => {
    if (!confirmPwd || !confirmPwd.trim()) {
      return 'Vui lòng nhập lại mật khẩu xác nhận.';
    }
    if (confirmPwd !== pwd) {
      return 'Mật khẩu xác nhận không khớp với mật khẩu đã nhập.';
    }
    return '';
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    const nextValue = name === 'otpCode' ? value.replace(/\D/g, '').slice(0, 6) : value;

    setFormData((prev) => ({
      ...prev,
      [name]: nextValue,
    }));

    if (fieldErrors[name]) {
      setFieldErrors((prev) => ({
        ...prev,
        [name]: '',
      }));
    }

    if (name === 'password' && formData.confirmPassword) {
      setFieldErrors((prev) => ({
        ...prev,
        confirmPassword:
          nextValue !== formData.confirmPassword
            ? 'Mật khẩu xác nhận không khớp với mật khẩu đã nhập.'
            : '',
      }));
    }

    if (errorMessage) setErrorMessage('');
  };

  const toggleShowPassword = () => setShowPassword((prev) => !prev);
  const toggleShowConfirmPassword = () => setShowConfirmPassword((prev) => !prev);

  // Kiểm tra thông tin Bước 1
  const validateStep1 = () => {
    const newErrors = {};

    if (!formData.fullName?.trim()) {
      newErrors.fullName = 'Họ và tên không được để trống.';
    }

    const cleanEmail = formData.email?.trim() || '';
    if (!cleanEmail) {
      newErrors.email = 'Địa chỉ email không được để trống.';
    } else if (!EMAIL_REGEX.test(cleanEmail)) {
      newErrors.email = 'Định dạng email không hợp lệ (Ví dụ: name@company.com).';
    }

    const cleanPhone = formData.phone?.trim().replace(/\s+/g, '') || '';
    if (!cleanPhone) {
      newErrors.phone = 'Số điện thoại không được để trống.';
    } else if (!PHONE_REGEX.test(cleanPhone)) {
      newErrors.phone = 'Số điện thoại không hợp lệ (VD: 0912345678 hoặc +84912345678).';
    }

    const pwdError = validatePasswordFormat(formData.password);
    if (pwdError) {
      newErrors.password = pwdError;
    }

    const confirmPwdError = validateConfirmPassword(formData.confirmPassword, formData.password);
    if (confirmPwdError) {
      newErrors.confirmPassword = confirmPwdError;
    }

    setFieldErrors((prev) => ({
      ...prev,
      fullName: newErrors.fullName || '',
      email: newErrors.email || '',
      phone: newErrors.phone || '',
      password: newErrors.password || '',
      confirmPassword: newErrors.confirmPassword || '',
    }));

    if (Object.keys(newErrors).length > 0) {
      setErrorMessage('Vui lòng kiểm tra và hoàn thiện đầy đủ thông tin ở Bước 1.');
      return false;
    }
    return true;
  };

  const handleSendOtp = async () => {
    const cleanEmail = formData.email?.trim() || '';
    if (!cleanEmail || !EMAIL_REGEX.test(cleanEmail)) {
      setFieldErrors((prev) => ({
        ...prev,
        email: 'Vui lòng nhập đúng định dạng email trước khi nhận mã OTP.',
      }));
      setErrorMessage('Định dạng email không hợp lệ.');
      return false;
    }

    setIsSendingOtp(true);
    setErrorMessage('');
    setSuccessMessage('');
    setFieldErrors((prev) => ({ ...prev, email: '', otpCode: '' }));

    try {
      await authApi.sendOtp(cleanEmail, 'REGISTER');
      setOtpSent(true);
      setCountdown(60);
      setSuccessMessage(
        `Mã xác thực OTP 6 chữ số đã được gửi tới email ${cleanEmail}. Vui lòng kiểm tra hộp thư đến (hoặc Spam).`
      );
      return true;
    } catch (err) {
      setErrorMessage(err.message || 'Không thể gửi mã OTP. Vui lòng thử lại sau.');
      return false;
    } finally {
      setIsSendingOtp(false);
    }
  };

  // Chuyển từ Bước 1 (Thông tin) -> Bước 2 (Nhập OTP)
  const handleProceedToStep2 = async (e) => {
    if (e) e.preventDefault();
    if (!validateStep1()) return;

    const sent = await handleSendOtp();
    if (sent) {
      setStep(2);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const handleBackToStep1 = () => {
    setStep(1);
    setErrorMessage('');
    setSuccessMessage('');
    setFieldErrors((prev) => ({ ...prev, otpCode: '' }));
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

  // Bước 2: Xác nhận OTP & Tạo tài khoản
  const handleSubmit = async (e) => {
    if (e) e.preventDefault();

    if (step === 1) {
      await handleProceedToStep2();
      return;
    }

    const cleanOtp = formData.otpCode?.trim() || '';
    if (!cleanOtp) {
      setFieldErrors((prev) => ({ ...prev, otpCode: 'Vui lòng nhập mã OTP 6 chữ số đã gửi về email.' }));
      setErrorMessage('Vui lòng nhập mã OTP để hoàn tất đăng ký.');
      return;
    }
    if (cleanOtp.length !== 6) {
      setFieldErrors((prev) => ({ ...prev, otpCode: 'Mã OTP phải gồm đúng 6 chữ số.' }));
      setErrorMessage('Mã OTP không hợp lệ.');
      return;
    }

    setIsLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      const finalCompanyName = formData.companyName?.trim() || formData.fullName.trim();

      await authApi.register({
        fullName: formData.fullName.trim(),
        email: formData.email.trim(),
        phone: formData.phone.trim().replace(/\s+/g, ''),
        organizerType: 'INDIVIDUAL',
        companyName: finalCompanyName,
        password: formData.password,
        otpCode: cleanOtp,
        accountType: 'EVENT_ORGANIZER',
      });

      setSuccessMessage(
        'Đăng ký tài khoản thành công! Lưu ý: Bạn cần hoàn tất định danh eKYC sau khi đăng nhập để tạo sự kiện và tuyển dụng nhân sự.'
      );
      setTimeout(() => {
        navigate('/login');
      }, 2200);
    } catch (err) {
      setErrorMessage(err.message || 'Xác thực OTP hoặc đăng ký không thành công. Vui lòng thử lại.');
    } finally {
      setIsLoading(false);
    }
  };

  return {
    step,
    formData,
    fieldErrors,
    isLoading,
    isSendingOtp,
    otpSent,
    countdown,
    errorMessage,
    successMessage,
    showPassword,
    showConfirmPassword,
    handleChange,
    handleSendOtp,
    handleProceedToStep2,
    handleBackToStep1,
    handleGoogleLogin,
    handleSubmit,
    toggleShowPassword,
    toggleShowConfirmPassword,
  };
};
