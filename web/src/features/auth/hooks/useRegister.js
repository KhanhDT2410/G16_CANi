import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';

export const useRegister = () => {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    fullName: '',
    email: '',
    phone: '',
    organizerType: 'ENTERPRISE', // 'ENTERPRISE' | 'INDIVIDUAL'
    companyName: '',
    taxId: '',
    password: '',
    confirmPassword: '',
    otpCode: '',
  });

  const [fieldErrors, setFieldErrors] = useState({
    fullName: '',
    email: '',
    phone: '',
    companyName: '',
    taxId: '',
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
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));

    // Tự động xóa lỗi inline của trường đang nhập
    if (fieldErrors[name]) {
      setFieldErrors((prev) => ({
        ...prev,
        [name]: '',
      }));
    }

    // Nếu sửa mật khẩu chính, kiểm tra lại khớp với confirmPassword nếu confirmPassword đã nhập
    if (name === 'password' && formData.confirmPassword) {
      if (value !== formData.confirmPassword) {
        setFieldErrors((prev) => ({
          ...prev,
          confirmPassword: 'Mật khẩu xác nhận không khớp với mật khẩu đã nhập.',
        }));
      } else {
        setFieldErrors((prev) => ({
          ...prev,
          confirmPassword: '',
        }));
      }
    }

    if (errorMessage) setErrorMessage('');
  };

  const toggleShowPassword = () => {
    setShowPassword((prev) => !prev);
  };

  const toggleShowConfirmPassword = () => {
    setShowConfirmPassword((prev) => !prev);
  };

  const handleSendOtp = async () => {
    if (!formData.email?.trim()) {
      setFieldErrors((prev) => ({
        ...prev,
        email: 'Vui lòng nhập địa chỉ email trước khi nhận mã OTP.',
      }));
      setErrorMessage('Vui lòng nhập địa chỉ email trước khi nhận mã OTP.');
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(formData.email.trim())) {
      setFieldErrors((prev) => ({
        ...prev,
        email: 'Định dạng email không hợp lệ (Ví dụ: name@company.com).',
      }));
      setErrorMessage('Định dạng email không hợp lệ.');
      return;
    }

    setIsSendingOtp(true);
    setErrorMessage('');
    setSuccessMessage('');
    setFieldErrors((prev) => ({ ...prev, email: '', otpCode: '' }));

    try {
      await authApi.sendOtp(formData.email.trim(), 'REGISTER');
      setOtpSent(true);
      setCountdown(60);
      setSuccessMessage(`Mã xác thực OTP 6 số đã được gửi tới email ${formData.email.trim()}. Vui lòng kiểm tra hộp thư đến (hoặc mục Thư rác/Spam).`);
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

    const newErrors = {};

    if (!formData.fullName?.trim()) {
      newErrors.fullName = 'Họ và tên không được để trống.';
    }

    if (!formData.email?.trim()) {
      newErrors.email = 'Địa chỉ email không được để trống.';
    } else {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(formData.email.trim())) {
        newErrors.email = 'Định dạng email không hợp lệ.';
      }
    }

    if (!formData.phone?.trim()) {
      newErrors.phone = 'Số điện thoại không được để trống.';
    }

    if (formData.organizerType === 'ENTERPRISE') {
      if (!formData.companyName?.trim()) {
        newErrors.companyName = 'Hộ kinh doanh / Doanh nghiệp bắt buộc phải nhập Tên đơn vị / thương hiệu.';
      }
      if (!formData.taxId?.trim()) {
        newErrors.taxId = 'Hộ kinh doanh / Doanh nghiệp bắt buộc phải nhập Mã số thuế / ĐKKD.';
      }
    }

    // OTP verification validation
    if (!formData.otpCode?.trim()) {
      newErrors.otpCode = 'Vui lòng nhập mã OTP 6 số đã gửi về email.';
    } else if (formData.otpCode.trim().length !== 6) {
      newErrors.otpCode = 'Mã OTP phải có đúng 6 chữ số.';
    }

    // Password validation - Dòng 1
    const pwdError = validatePasswordFormat(formData.password);
    if (pwdError) {
      newErrors.password = pwdError;
    }

    // Confirm Password validation - Dòng 2
    const confirmPwdError = validateConfirmPassword(formData.confirmPassword, formData.password);
    if (confirmPwdError) {
      newErrors.confirmPassword = confirmPwdError;
    }

    setFieldErrors(newErrors);

    if (Object.keys(newErrors).length > 0) {
      setErrorMessage('Vui lòng kiểm tra và sửa các thông tin chưa chính xác bên dưới.');
      return;
    }

    setIsLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      const finalCompanyName = formData.organizerType === 'ENTERPRISE'
        ? formData.companyName.trim()
        : (formData.companyName?.trim() || formData.fullName.trim());

      await authApi.register({
        fullName: formData.fullName.trim(),
        email: formData.email.trim(),
        phone: formData.phone.trim(),
        organizerType: formData.organizerType,
        companyName: finalCompanyName,
        taxId: formData.taxId?.trim() || null,
        password: formData.password,
        otpCode: formData.otpCode.trim(),
        accountType: 'EVENT_ORGANIZER',
      });

      setSuccessMessage('Đăng ký tài khoản thành công! Lưu ý: Bạn cần hoàn tất định danh eKYC thì mới có thể tạo sự kiện và tuyển dụng nhân sự trên CANi.');
      setTimeout(() => {
        navigate('/login');
      }, 2500);
    } catch (err) {
      setErrorMessage(err.message || 'Đăng ký tài khoản không thành công. Vui lòng thử lại.');
    } finally {
      setIsLoading(false);
    }
  };

  return {
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
    handleGoogleLogin,
    handleSubmit,
    toggleShowPassword,
    toggleShowConfirmPassword,
  };
};
