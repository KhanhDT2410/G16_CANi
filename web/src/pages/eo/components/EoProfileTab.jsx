import React, { useState, useEffect, useRef } from 'react';
import { eoService } from '../../../services/eoService';
import { useAuth } from '../../../features/auth';
import { authApi } from '../../../features/auth/api/authApi';
import { showToast } from '../../../components/common/Toast';
import EOSettingsTab from './EOSettingsTab';
import './EoProfileTab.css';
import '../EoEkycTab.css';

// Hàm che thông tin nhạy cảm
const maskEmail = (email) => {
  if (!email || typeof email !== 'string') return '';
  const atIndex = email.indexOf('@');
  if (atIndex <= 1) return email;
  const name = email.slice(0, atIndex);
  const domain = email.slice(atIndex);
  if (name.length <= 3) {
    return `${name.charAt(0)}****${domain}`;
  }
  return `${name.slice(0, 3)}****${name.slice(-1)}${domain}`;
};

const maskPhone = (phone) => {
  if (!phone || typeof phone !== 'string') return '';
  const digits = phone.trim();
  if (digits.length <= 6) return '****';
  return `${digits.slice(0, 3)}****${digits.slice(-3)}`;
};

export default function EoProfileTab() {
  const { user } = useAuth();
  const fileInputRef = useRef(null);

  const [activeSection, setActiveSection] = useState('PROFILE'); // 'PROFILE' | 'EKYC' | 'SECURITY' | 'BLOCKLIST'
  const [loading, setLoading] = useState(true);
  const [savingProfile, setSavingProfile] = useState(false);
  const [submittingEkyc, setSubmittingEkyc] = useState(false);

  // Ẩn / Hiện thông tin nhạy cảm trên thẻ Header
  const [showSensitive, setShowSensitive] = useState({
    email: false,
    phone: false,
  });

  const toggleSensitive = (field) => {
    setShowSensitive((prev) => ({ ...prev, [field]: !prev[field] }));
  };

  // State Bảo mật & Đổi mật khẩu qua Email OTP
  const [otpStep, setOtpStep] = useState(1);
  const [otpCode, setOtpCode] = useState('');
  const [otpNewPassword, setOtpNewPassword] = useState('');
  const [otpConfirmPassword, setOtpConfirmPassword] = useState('');
  const [otpCountdown, setOtpCountdown] = useState(0);
  const [isSendingOtp, setIsSendingOtp] = useState(false);
  const [isResettingPassword, setIsResettingPassword] = useState(false);
  const [showOtpPassword, setShowOtpPassword] = useState(false);

  useEffect(() => {
    let timer;
    if (otpCountdown > 0) {
      timer = setTimeout(() => setOtpCountdown((prev) => prev - 1), 1000);
    }
    return () => clearTimeout(timer);
  }, [otpCountdown]);

  // Avatar State & Sync
  const [avatarUrl, setAvatarUrl] = useState(() => {
    try {
      return localStorage.getItem('cani_eo_avatar') || user?.avatarUrl || null;
    } catch {
      return user?.avatarUrl || null;
    }
  });

  // Hồ Sơ Nhà Tổ Chức - Khởi tạo từ thông tin người dùng thực tế
  const [profile, setProfile] = useState({
    companyName: '',
    address: '',
    fullName: user?.fullName || user?.username || '',
    phone: user?.phone || '',
    email: user?.email || '',
    ratingAvg: 0,
    ratingCount: 0,
    totalEventsCompleted: 0,
    totalEventsCreated: 0,
    totalConfirmedWorkers: 0,
  });

  // Trạng thái eKYC
  const [ekycData, setEkycData] = useState(null);
  const [cccdNumber, setCccdNumber] = useState('');

  const [files, setFiles] = useState({
    frontImage: null,
    backImage: null,
    selfieImage: null,
  });

  const [previews, setPreviews] = useState({
    frontImage: null,
    backImage: null,
    selfieImage: null,
  });

  // Tải dữ liệu từ Backend (kết hợp đối soát từ getMyEvents để chỉ số luôn chuẩn)
  const loadData = async () => {
    setLoading(true);
    try {
      const [profileRes, ekycRes, eventsRes] = await Promise.allSettled([
        eoService.getEoProfile(),
        eoService.getEkycStatus(),
        eoService.getMyEvents(),
      ]);

      const rawEvents =
        eventsRes.status === 'fulfilled' && Array.isArray(eventsRes.value)
          ? eventsRes.value
          : [];
      const activeNonCancelledEvents = rawEvents.filter((ev) => ev?.status !== 'CANCELLED');
      const livePublishedCount = activeNonCancelledEvents.filter(
        (ev) => ev?.status && ev.status !== 'DRAFT'
      ).length;
      const liveConfirmedWorkers = activeNonCancelledEvents.reduce((sum, ev) => {
        const shiftSum = Array.isArray(ev?.shifts)
          ? ev.shifts
              .filter((s) => s?.status !== 'CANCELLED')
              .reduce((acc, s) => acc + Number(s?.confirmedWorkers || 0), 0)
          : 0;
        return sum + Math.max(Number(ev?.totalConfirmedWorkers || 0), shiftSum);
      }, 0);

      const pVal = profileRes.status === 'fulfilled' ? profileRes.value : null;
      setProfile((prev) => ({
        ...prev,
        companyName: pVal?.companyName || prev.companyName,
        address: pVal?.address || prev.address,
        fullName: pVal?.fullName || prev.fullName,
        phone: pVal?.phone || prev.phone,
        email: pVal?.email || prev.email,
        ratingAvg: pVal?.ratingAvg ?? prev.ratingAvg,
        ratingCount: pVal?.ratingCount ?? prev.ratingCount,
        totalEventsCompleted: Math.max(
          Number(pVal?.totalEventsCompleted || 0),
          livePublishedCount
        ),
        totalEventsCreated: Math.max(
          Number(pVal?.totalEventsCreated || 0),
          activeNonCancelledEvents.length
        ),
        totalConfirmedWorkers: Math.max(
          Number(pVal?.totalConfirmedWorkers || 0),
          liveConfirmedWorkers
        ),
      }));

      if (ekycRes.status === 'fulfilled' && ekycRes.value) {
        setEkycData(ekycRes.value);
      }
    } catch (err) {
      console.warn('Lỗi tải dữ liệu EO Profile/eKYC:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  // Xử lý Thay đổi Ảnh Đại Diện (Avatar Upload)
  const handleAvatarFileSelect = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      showToast('Vui lòng chọn tệp định dạng hình ảnh (PNG, JPG, WEBP)', 'error');
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      showToast('Kích thước ảnh không vượt quá 5MB', 'error');
      return;
    }

    const reader = new FileReader();
    reader.onload = (event) => {
      const base64Url = event.target.result;
      setAvatarUrl(base64Url);
      try {
        localStorage.setItem('cani_eo_avatar', base64Url);
        window.dispatchEvent(new CustomEvent('cani_avatar_updated', { detail: { avatarUrl: base64Url } }));
      } catch (err) {
        console.warn('Lỗi lưu avatar vào localStorage:', err);
      }
      showToast('Cập nhật ảnh đại diện thành công!', 'success');
    };
    reader.readAsDataURL(file);
  };

  const handleRemoveAvatar = () => {
    setAvatarUrl(null);
    try {
      localStorage.removeItem('cani_eo_avatar');
      window.dispatchEvent(new CustomEvent('cani_avatar_updated', { detail: { avatarUrl: null } }));
    } catch (err) {
      console.warn('Lỗi xóa avatar:', err);
    }
    showToast('Đã khôi phục ảnh đại diện mặc định', 'info');
  };

  // Cập nhật Thông Tin Hồ Sơ
  const handleProfileFieldChange = (field, val) => {
    setProfile((prev) => ({ ...prev, [field]: val }));
  };

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setSavingProfile(true);
    try {
      const res = await eoService.updateEoProfile(profile);
      showToast('Lưu thông tin hồ sơ nhà tổ chức thành công!', 'success');
      if (res) {
        setProfile((prev) => ({
          ...prev,
          companyName: res.companyName || prev.companyName,
          address: res.address || prev.address,
          fullName: res.fullName || prev.fullName,
          phone: res.phone || prev.phone,
        }));
      }
    } catch (err) {
      showToast(err.response?.data?.message || err.message || 'Lỗi khi lưu hồ sơ', 'error');
    } finally {
      setSavingProfile(false);
    }
  };

  // eKYC file handlers
  const handleEkycFileChange = (field, e) => {
    const file = e.target.files?.[0];
    if (file) {
      setFiles((prev) => ({ ...prev, [field]: file }));
      setPreviews((prev) => ({ ...prev, [field]: URL.createObjectURL(file) }));
    }
  };

  const handleSubmitEkyc = async (e) => {
    e.preventDefault();
    if (!files.frontImage || !files.backImage || !files.selfieImage) {
      showToast('Vui lòng tải lên đầy đủ ảnh mặt trước, mặt sau CCCD và ảnh selfie đại diện', 'error');
      return;
    }

    const cleanedCccd = (cccdNumber || '').replace(/\D/g, '');
    if (cleanedCccd && cleanedCccd.length !== 12) {
      showToast('Số CCCD phải bao gồm đúng 12 chữ số', 'error');
      return;
    }

    setSubmittingEkyc(true);
    try {
      const formData = new FormData();
      formData.append('frontImage', files.frontImage);
      formData.append('backImage', files.backImage);
      formData.append('selfieImage', files.selfieImage);
      if (cleanedCccd) {
        formData.append('cccdNumber', cleanedCccd);
      }
      if (profile.fullName?.trim()) {
        formData.append('representativeName', profile.fullName.trim());
      }

      const result = await eoService.submitEkyc(formData);
      setEkycData(result);
      if (result?.status === 'REJECTED') {
        showToast(result.routingReason || 'Hồ sơ định danh eKYC không hợp lệ hoặc bị trùng CCCD!', 'error');
      } else if (result?.status === 'PENDING_REVIEW') {
        showToast(result.routingReason || 'Đã nộp hồ sơ eKYC! Đang chờ Admin thẩm định.', 'info');
      } else {
        showToast('Xác thực định danh eKYC thành công!', 'success');
      }
      await loadData();
      try {
        window.dispatchEvent(new CustomEvent('cani_ekyc_updated'));
      } catch (e) {
        console.warn('Lỗi dispatch cani_ekyc_updated:', e);
      }
    } catch (err) {
      showToast(err.response?.data?.message || err.message || 'Lỗi nộp hồ sơ eKYC', 'error');
    } finally {
      setSubmittingEkyc(false);
    }
  };

  // Gửi mã OTP đổi / đặt lại mật khẩu qua Email
  const handleSendOtp = async () => {
    const targetEmail = profile.email || user?.email;
    if (!targetEmail) {
      showToast('Không tìm thấy địa chỉ email tài khoản để nhận mã OTP', 'error');
      return;
    }
    setIsSendingOtp(true);
    try {
      await authApi.resetPassword({ email: targetEmail.trim().toLowerCase() });
      setOtpStep(2);
      setOtpCountdown(60);
      showToast(`Đã gửi mã xác thực 6 số đến email ${targetEmail}!`, 'success');
    } catch (err) {
      showToast(err.message || 'Không thể gửi mã xác thực OTP. Vui lòng thử lại sau.', 'error');
    } finally {
      setIsSendingOtp(false);
    }
  };

  // Xác nhận OTP và thiết lập mật khẩu mới
  const handleConfirmOtpReset = async (e) => {
    e.preventDefault();
    const targetEmail = profile.email || user?.email;
    if (!targetEmail) {
      showToast('Không tìm thấy địa chỉ email tài khoản', 'error');
      return;
    }
    if (!otpCode || otpCode.trim().length !== 6) {
      showToast('Vui lòng nhập đúng mã OTP 6 chữ số', 'error');
      return;
    }
    if (!otpNewPassword || otpNewPassword.length < 8) {
      showToast('Mật khẩu mới phải có tối thiểu 8 ký tự', 'error');
      return;
    }
    if (otpNewPassword !== otpConfirmPassword) {
      showToast('Mật khẩu xác nhận không khớp', 'error');
      return;
    }

    setIsResettingPassword(true);
    try {
      await authApi.confirmResetPassword({
        email: targetEmail.trim().toLowerCase(),
        otp: otpCode.trim(),
        newPassword: otpNewPassword,
      });
      showToast('Thiết lập mật khẩu mới thành công! Bạn có thể sử dụng mật khẩu này để đăng nhập trực tiếp.', 'success');
      setOtpCode('');
      setOtpNewPassword('');
      setOtpConfirmPassword('');
      setOtpStep(1);
    } catch (err) {
      showToast(err.message || 'Không thể đặt lại mật khẩu. Vui lòng kiểm tra lại mã OTP.', 'error');
    } finally {
      setIsResettingPassword(false);
    }
  };

  const ekycStatus = ekycData?.status || 'NOT_SUBMITTED';

  if (loading) {
    return (
      <div style={{ padding: '3rem', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
        <div className="cani-spinner" />
      </div>
    );
  }

  return (
    <div className="eo-profile-container animate-fade-in-up">
      {/* 1. HERO PROFILE CARD */}
      <div className="profile-hero-card is-upgraded">
        <div className="profile-hero-left">
          <div className="profile-avatar-col">
            <div className="profile-avatar-wrapper">
              {avatarUrl ? (
                <img src={avatarUrl} alt="Avatar" className="profile-avatar-img" />
              ) : (
                <div className="profile-avatar-fallback">
                  {(profile.fullName || user?.username || 'D').charAt(0).toUpperCase()}
                </div>
              )}
              <input
                type="file"
                ref={fileInputRef}
                accept="image/*"
                style={{ display: 'none' }}
                onChange={handleAvatarFileSelect}
              />
              <button
                type="button"
                className="profile-avatar-upload-btn"
                title="Thay đổi ảnh đại diện"
                onClick={() => fileInputRef.current?.click()}
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"></path>
                  <circle cx="12" cy="13" r="4"></circle>
                </svg>
              </button>
            </div>
            {avatarUrl && (
              <button
                type="button"
                className="profile-avatar-remove-btn"
                onClick={handleRemoveAvatar}
              >
                Xóa ảnh
              </button>
            )}
          </div>

          <div className="profile-hero-info">
            <div className="profile-name-row">
              <h2 className="profile-org-name">{profile.companyName || 'Chưa cập nhật tên tổ chức'}</h2>
              <span className="profile-role-pill">Ban Tổ Chức Sự Kiện</span>
              {ekycStatus === 'APPROVED' ? (
                <span className="status-pill approved" title="Nhà tổ chức đã được xác thực eKYC">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
                  </svg>
                  Đã Xác Thực eKYC
                </span>
              ) : ekycStatus === 'PENDING_REVIEW' ? (
                <span className="status-pill pending">Chờ Thẩm Định eKYC</span>
              ) : (
                <span className="status-pill none">Chưa Xác Thực eKYC</span>
              )}
            </div>

            <div className="profile-meta-row">
              <span className="profile-meta-item">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                  <circle cx="12" cy="7" r="4"></circle>
                </svg>
                Người đại diện: <strong>{profile.fullName || 'Chưa cập nhật'}</strong>
              </span>
              <span className="profile-meta-item">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path>
                  <polyline points="22,6 12,13 2,6"></polyline>
                </svg>
                <span className="sensitive-masked">
                  {profile.email
                    ? (showSensitive.email ? profile.email : maskEmail(profile.email))
                    : 'Chưa có email'}
                </span>
                {profile.email && (
                  <button
                    type="button"
                    className="btn-toggle-eye"
                    onClick={() => toggleSensitive('email')}
                    title={showSensitive.email ? 'Ẩn email' : 'Hiện email'}
                  >
                    {showSensitive.email ? (
                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
                        <line x1="1" y1="1" x2="23" y2="23"></line>
                      </svg>
                    ) : (
                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                        <circle cx="12" cy="12" r="3"></circle>
                      </svg>
                    )}
                  </button>
                )}
              </span>
              <span className="profile-meta-item">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"></path>
                </svg>
                <span className="sensitive-masked">
                  {profile.phone
                    ? (showSensitive.phone ? profile.phone : maskPhone(profile.phone))
                    : 'Chưa cập nhật SĐT'}
                </span>
                {profile.phone && (
                  <button
                    type="button"
                    className="btn-toggle-eye"
                    onClick={() => toggleSensitive('phone')}
                    title={showSensitive.phone ? 'Ẩn số điện thoại' : 'Hiện số điện thoại'}
                  >
                    {showSensitive.phone ? (
                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
                        <line x1="1" y1="1" x2="23" y2="23"></line>
                      </svg>
                    ) : (
                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                        <circle cx="12" cy="12" r="3"></circle>
                      </svg>
                    )}
                  </button>
                )}
              </span>
            </div>
          </div>
        </div>

        <div className="profile-hero-right">
          <div className="profile-stat-box">
            <div className="profile-stat-value">{profile.totalEventsCompleted ?? 0}</div>
            <div className="profile-stat-label">Sự kiện đã tổ chức</div>
            {profile.totalEventsCreated > (profile.totalEventsCompleted ?? 0) && (
              <div className="profile-stat-sub">
                {profile.totalEventsCreated} tổng tạo (gồm nháp)
              </div>
            )}
          </div>

          <div className="profile-stat-box">
            <div className="profile-stat-value">{profile.totalConfirmedWorkers ?? 0}</div>
            <div className="profile-stat-label">Nhân sự đã chốt ca</div>
          </div>

          <div className="profile-stat-box">
            <div
              className={`profile-stat-value ${
                profile.ratingCount > 0 || ekycStatus === 'APPROVED' ? 'is-verified' : ''
              }`}
            >
              {profile.ratingCount > 0 && Number(profile.ratingAvg) > 0
                ? `${Number(profile.ratingAvg).toFixed(1)} / 5.0`
                : ekycStatus === 'APPROVED'
                ? 'Đã xác thực'
                : 'Chưa đánh giá'}
            </div>
            <div className="profile-stat-label">
              {profile.ratingCount > 0
                ? `Đánh giá (${profile.ratingCount} lượt)`
                : ekycStatus === 'APPROVED'
                ? 'Uy tín eKYC • Chưa có sao'
                : 'Độ uy tín tuyển dụng'}
            </div>
          </div>
        </div>
      </div>

      {/* 2. SUB NAVIGATION PILLS */}
      <nav className="profile-subnav">
        <button
          type="button"
          className={`profile-subnav-btn ${activeSection === 'PROFILE' ? 'active' : ''}`}
          onClick={() => setActiveSection('PROFILE')}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
            <line x1="3" y1="9" x2="21" y2="9"></line>
            <line x1="9" y1="21" x2="9" y2="9"></line>
          </svg>
          1. Thông Tin Nhà Tổ Chức
        </button>

        <button
          type="button"
          className={`profile-subnav-btn ${activeSection === 'EKYC' ? 'active' : ''}`}
          onClick={() => setActiveSection('EKYC')}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
          </svg>
          2. Xác Thực Định Danh eKYC
        </button>

        <button
          type="button"
          className={`profile-subnav-btn ${activeSection === 'SECURITY' ? 'active' : ''}`}
          onClick={() => setActiveSection('SECURITY')}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
            <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
          </svg>
          3. Bảo Mật & Mật Khẩu
        </button>

        <button
          type="button"
          className={`profile-subnav-btn ${activeSection === 'BLOCKLIST' ? 'active' : ''}`}
          onClick={() => setActiveSection('BLOCKLIST')}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"></line>
          </svg>
          4. Danh Sách Chặn
        </button>
      </nav>

      {/* 3. NỘI DUNG TỪNG PHÂN HỆ */}

      {/* SECTION 1: THÔNG TIN NHÀ TỔ CHỨC (GỌN GÀNG, TÍCH XANH TRỰC TIẾP TRÊN TỪNG MỤC) */}
      {activeSection === 'PROFILE' && (() => {
        const checks = [
          { label: 'Đơn vị & Đại diện', done: Boolean(profile.companyName?.trim() && profile.fullName?.trim()) },
          { label: 'Hotline', done: Boolean(profile.phone?.trim()) },
          { label: 'Địa chỉ', done: Boolean(profile.address?.trim()) },
          { label: 'eKYC', done: ekycStatus === 'APPROVED' },
          { label: 'Sự kiện', done: (profile.totalEventsCompleted ?? 0) > 0 },
        ];
        const doneCount = checks.filter((c) => c.done).length;
        const pct = Math.round((doneCount / checks.length) * 100);

        const GreenTick = () => (
          <span className="profile-field-check" title="Đã hoàn thiện">
            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="20 6 9 17 4 12" />
            </svg>
          </span>
        );

        return (
          <div className="profile-card">
            <div className="profile-card-header">
              <div className="profile-card-header-left">
                <h3>Hồ Sơ Nhà Tổ Chức Sự Kiện</h3>
                <p>Thông tin thương hiệu chính thức xuất hiện trên trang tuyển dụng và bảng phân công nhân sự.</p>
              </div>

              <div className="profile-completeness-compact">
                <div className="profile-completeness-head">
                  <span>Hoàn thiện hồ sơ</span>
                  <span className="profile-completeness-pct">{pct}%</span>
                </div>
                <div className="profile-progress-track">
                  <div className="profile-progress-fill" style={{ width: `${pct}%` }} />
                </div>
              </div>
            </div>

            <div className="profile-completeness-tags">
              {checks.map((item, idx) => (
                <span key={idx} className={`profile-status-tag ${item.done ? 'is-done' : ''}`}>
                  {item.done && (
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.8" strokeLinecap="round" strokeLinejoin="round">
                      <polyline points="20 6 9 17 4 12" />
                    </svg>
                  )}
                  <span>{item.label}</span>
                </span>
              ))}
            </div>

            <form onSubmit={handleSaveProfile}>
              <div className="profile-form-grid-2">
                <div className="profile-form-group">
                  <label className="profile-field-label-row">
                    <span>Tên Đơn Vị / Ban Tổ Chức Sự Kiện *</span>
                    {profile.companyName?.trim() && <GreenTick />}
                  </label>
                  <input
                    type="text"
                    required
                    className="profile-input"
                    value={profile.companyName}
                    onChange={(e) => handleProfileFieldChange('companyName', e.target.value)}
                    placeholder="VD: Ban Tổ Chức Festival hoặc Tên Đơn Vị"
                  />
                </div>

                <div className="profile-form-group">
                  <label className="profile-field-label-row">
                    <span>Họ &amp; Tên Trưởng Ban / Người Đại Diện *</span>
                    {profile.fullName?.trim() && <GreenTick />}
                  </label>
                  <input
                    type="text"
                    required
                    className="profile-input"
                    value={profile.fullName}
                    onChange={(e) => handleProfileFieldChange('fullName', e.target.value)}
                    placeholder="VD: Nguyễn Văn A"
                  />
                </div>

                <div className="profile-form-group">
                  <label className="profile-field-label-row">
                    <span>Số Điện Thoại Hotline / Zalo Điều Phối *</span>
                    {profile.phone?.trim() && <GreenTick />}
                  </label>
                  <input
                    type="tel"
                    required
                    className="profile-input"
                    value={profile.phone}
                    onChange={(e) => handleProfileFieldChange('phone', e.target.value)}
                    placeholder="VD: 0901234567"
                  />
                </div>

                <div className="profile-form-group">
                  <label className="profile-field-label-row">
                    <span>Email Tài Khoản &amp; Nhận Thông Báo</span>
                    {profile.email?.trim() && <GreenTick />}
                  </label>
                  <input
                    type="email"
                    readOnly
                    className="profile-input"
                    style={{ backgroundColor: '#f8fafc', color: '#475569' }}
                    value={profile.email}
                    title="Email gắn với tài khoản đăng nhập và nhận mã OTP bảo mật"
                  />
                </div>

                <div className="profile-form-group span-2">
                  <label className="profile-field-label-row">
                    <span>Địa Chỉ Liên Hệ / Văn Phòng *</span>
                    {profile.address?.trim() && <GreenTick />}
                  </label>
                  <input
                    type="text"
                    required
                    className="profile-input"
                    value={profile.address}
                    onChange={(e) => handleProfileFieldChange('address', e.target.value)}
                    placeholder="VD: Số 123 Đường Cầu Giấy, Hà Nội"
                  />
                </div>
              </div>

              <div className="profile-actions-bar">
                <button
                  type="submit"
                  disabled={savingProfile}
                  className="profile-btn-primary"
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"></path>
                    <polyline points="17 21 17 13 7 13 7 21"></polyline>
                    <polyline points="7 3 7 8 15 8"></polyline>
                  </svg>
                  {savingProfile ? 'Đang lưu...' : 'Lưu Thay Đổi Hồ Sơ'}
                </button>
              </div>
            </form>
          </div>
        );
      })()}

      {/* SECTION 2: ĐỊNH DANH eKYC NHÀ TỔ CHỨC */}
      {activeSection === 'EKYC' && (
        <div className="profile-card">
          <div className="profile-card-header">
            <div className="profile-card-header-left">
              <h3>Xác Thực Định Danh Nhà Tổ Chức (eKYC 4.0)</h3>
              <p>Tuân thủ Nghị định 13/2023/NĐ-CP và Luật Căn cước. Nâng cao độ uy tín tuyển dụng và mở khóa tính năng tạo sự kiện.</p>
            </div>

            <div>
              {ekycStatus === 'APPROVED' && (
                <span className="status-pill approved">ĐÃ XÁC THỰC UY TÍN</span>
              )}
              {ekycStatus === 'PENDING_REVIEW' && (
                <span className="status-pill pending">ĐANG CHỜ DUYỆT</span>
              )}
              {ekycStatus === 'REJECTED' && (
                <span className="status-pill none" style={{ color: '#dc2626', borderColor: '#fca5a5' }}>
                  HỒ SƠ BỊ TỪ CHỐI
                </span>
              )}
              {ekycStatus === 'NOT_SUBMITTED' && (
                <span className="status-pill none">CHƯA ĐỊNH DANH</span>
              )}
            </div>
          </div>

          {ekycStatus === 'APPROVED' ? (
            <div style={{ backgroundColor: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: '0.875rem', padding: '1.25rem', marginBottom: '1.5rem' }}>
              <div style={{ fontWeight: 700, color: '#166534', marginBottom: '0.4rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                  <polyline points="20 6 9 17 4 12"></polyline>
                </svg>
                Nhà tổ chức đã hoàn tất thẩm định định danh eKYC điện tử.
              </div>
              <div style={{ fontSize: '0.875rem', color: '#15803d' }}>
                Hồ sơ của bạn được đóng dấu Huy hiệu Nhà Tổ Chức Uy Tín, mở khóa toàn quyền tạo sự kiện và tuyển dụng nhân sự trên CANi.
              </div>

              <div className="info-grid">
                <div className="info-item">
                  <span className="info-label">Họ và tên trên CCCD</span>
                  <span className="info-val">{ekycData.representativeName || '—'}</span>
                </div>
                <div className="info-item">
                  <span className="info-label">Số CCCD (Bảo mật NĐ 13)</span>
                  <span className="info-val">{ekycData.cccdNumberMasked || '—'}</span>
                </div>
                <div className="info-item">
                  <span className="info-label">Ngày sinh & Giới tính</span>
                  <span className="info-val">
                    {[ekycData.dob, ekycData.gender].filter(Boolean).join(' • ') || '—'}
                  </span>
                </div>
                <div className="info-item">
                  <span className="info-label">Quê quán</span>
                  <span className="info-val">{ekycData.originAddress || '—'}</span>
                </div>
                <div className="info-item">
                  <span className="info-label">Nơi thường trú (Trên CCCD)</span>
                  <span className="info-val">{ekycData.residenceAddress || '—'}</span>
                </div>
              </div>
            </div>
          ) : (
            <div>
              {ekycStatus === 'PENDING_REVIEW' && (
                <div style={{ backgroundColor: '#fefce8', border: '1px solid #fef08a', borderRadius: '0.75rem', padding: '1.25rem', marginBottom: '1.5rem' }}>
                  <div style={{ fontWeight: 700, color: '#854d0e', marginBottom: '0.25rem' }}>
                    Hồ sơ eKYC đang chờ Admin / CSKH thẩm định
                  </div>
                  <div style={{ fontSize: '0.875rem', color: '#a16207', marginBottom: '0.75rem' }}>
                    {ekycData?.routingReason || 'Hệ thống đã bóc tách thông tin CCCD của bạn và đang chờ Admin phê duyệt.'}
                  </div>
                  {ekycData && (
                    <div className="info-grid" style={{ marginTop: '0.75rem' }}>
                      <div className="info-item">
                        <span className="info-label">Họ và tên trên CCCD</span>
                        <span className="info-val">{ekycData.representativeName || '—'}</span>
                      </div>
                      <div className="info-item">
                        <span className="info-label">Số CCCD (Bảo mật NĐ 13)</span>
                        <span className="info-val">{ekycData.cccdNumberMasked || '—'}</span>
                      </div>
                      <div className="info-item">
                        <span className="info-label">Ngày sinh & Giới tính</span>
                        <span className="info-val">
                          {[ekycData.dob, ekycData.gender].filter(Boolean).join(' • ') || '—'}
                        </span>
                      </div>
                      <div className="info-item">
                        <span className="info-label">Nơi thường trú (Trên CCCD)</span>
                        <span className="info-val">{ekycData.residenceAddress || '—'}</span>
                      </div>
                    </div>
                  )}
                </div>
              )}

              {ekycStatus === 'REJECTED' && (
                <div style={{ backgroundColor: '#fef2f2', border: '1px solid #fecaca', borderRadius: '0.75rem', padding: '1.25rem', marginBottom: '1.5rem' }}>
                  <div style={{ fontWeight: 700, color: '#991b1b', marginBottom: '0.25rem' }}>
                    Hồ sơ định danh eKYC trước đó chưa được chấp thuận
                  </div>
                  <div style={{ fontSize: '0.875rem', color: '#b91c1c' }}>
                    Lý do: {ekycData.routingReason || 'Ảnh CCCD bị lóa hoặc ảnh selfie không rõ nét.'} Vui lòng tải lại ảnh mới dưới đây.
                  </div>
                </div>
              )}

              <form onSubmit={handleSubmitEkyc}>
                <div className="profile-form-group" style={{ marginBottom: '1rem', maxWidth: '420px' }}>
                  <label>Số Căn Cước Công Dân (12 chữ số trên thẻ) *</label>
                  <input
                    type="text"
                    required
                    maxLength={12}
                    inputMode="numeric"
                    className="profile-input"
                    value={cccdNumber}
                    onChange={(e) => setCccdNumber(e.target.value.replace(/\D/g, '').slice(0, 12))}
                    placeholder="Nhập đúng 12 chữ số CCCD để đối chiếu chống trùng lặp"
                    disabled={submittingEkyc}
                  />
                </div>

                <p style={{ fontSize: '0.9rem', fontWeight: 600, color: '#334155', margin: '0 0 0.75rem' }}>
                  Tải lên hồ sơ xác thực định danh (CCCD & Ảnh chân dung đại diện):
                </p>

                <div className="ekyc-upload-grid">
                  <label className={`upload-box ${previews.frontImage ? 'has-file' : ''}`}>
                    <input
                      type="file"
                      accept="image/*"
                      style={{ display: 'none' }}
                      onChange={(e) => handleEkycFileChange('frontImage', e)}
                      disabled={submittingEkyc}
                    />
                    {previews.frontImage ? (
                      <img src={previews.frontImage} alt="Mặt trước CCCD" className="upload-preview-img" />
                    ) : (
                      <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.75">
                        <rect x="3" y="4" width="18" height="16" rx="2" ry="2"></rect>
                        <line x1="3" y1="10" x2="21" y2="10"></line>
                      </svg>
                    )}
                    <div className="upload-label">Mặt trước CCCD *</div>
                    <div className="upload-hint">Chụp rõ nét, không lóa góc</div>
                  </label>

                  <label className={`upload-box ${previews.backImage ? 'has-file' : ''}`}>
                    <input
                      type="file"
                      accept="image/*"
                      style={{ display: 'none' }}
                      onChange={(e) => handleEkycFileChange('backImage', e)}
                      disabled={submittingEkyc}
                    />
                    {previews.backImage ? (
                      <img src={previews.backImage} alt="Mặt sau CCCD" className="upload-preview-img" />
                    ) : (
                      <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.75">
                        <rect x="3" y="4" width="18" height="16" rx="2" ry="2"></rect>
                        <circle cx="9" cy="14" r="2"></circle>
                      </svg>
                    )}
                    <div className="upload-label">Mặt sau CCCD *</div>
                    <div className="upload-hint">Rõ chip điện tử & mã vạch QR</div>
                  </label>

                  <label className={`upload-box ${previews.selfieImage ? 'has-file' : ''}`}>
                    <input
                      type="file"
                      accept="image/*"
                      style={{ display: 'none' }}
                      onChange={(e) => handleEkycFileChange('selfieImage', e)}
                      disabled={submittingEkyc}
                    />
                    {previews.selfieImage ? (
                      <img src={previews.selfieImage} alt="Selfie" className="upload-preview-img" />
                    ) : (
                      <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.75">
                        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                        <circle cx="12" cy="7" r="4"></circle>
                      </svg>
                    )}
                    <div className="upload-label">Ảnh chân dung đại diện *</div>
                    <div className="upload-hint">Nhìn thẳng, đủ ánh sáng</div>
                  </label>
                </div>

                <div className="profile-actions-bar">
                  <button
                    type="submit"
                    disabled={submittingEkyc}
                    className="profile-btn-primary"
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <polyline points="16 16 12 12 8 16"></polyline>
                      <line x1="12" y1="12" x2="12" y2="21"></line>
                      <path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3"></path>
                    </svg>
                    {submittingEkyc ? 'Đang thẩm định eKYC...' : 'Nộp Hồ Sơ Xác Thực eKYC'}
                  </button>
                </div>
              </form>
            </div>
          )}
        </div>
      )}

      {/* SECTION 3: BẢO MẬT & MẬT KHẨU (CHỈ ĐỔI QUA EMAIL OTP) */}
      {activeSection === 'SECURITY' && (
        <div className="profile-card">
          <div className="profile-card-header">
            <div className="profile-card-header-left">
              <h3>Bảo Mật Tài Khoản & Mật Khẩu</h3>
              <p>Thiết lập hoặc đổi mật khẩu mới thông qua mã xác thực OTP gửi về địa chỉ email của bạn.</p>
            </div>
          </div>

          <div>
            <div style={{
              backgroundColor: '#eff6ff',
              border: '1px solid #bfdbfe',
              borderRadius: '0.875rem',
              padding: '1.25rem',
              marginBottom: '1.5rem',
            }}>
              <div style={{ fontWeight: 700, fontSize: '0.925rem', color: '#1e40af', marginBottom: '0.35rem' }}>
                Đổi hoặc thiết lập mật khẩu qua mã xác thực Email OTP
              </div>
              <p style={{ margin: 0, fontSize: '0.825rem', color: '#1e3a8a', lineHeight: 1.5 }}>
                Hệ thống sẽ gửi mã xác thực gồm 6 chữ số đến email đăng ký để đảm bảo an toàn khi thay đổi mật khẩu cho cả tài khoản thường và tài khoản Google.
              </p>
            </div>

            <form onSubmit={handleConfirmOtpReset}>
              <div className="profile-form-grid-2">
                <div className="profile-form-group span-2">
                  <label>Địa Chỉ Email Nhận Mã OTP</label>
                  <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
                    <input
                      type="text"
                      disabled
                      className="profile-input"
                      style={{ backgroundColor: '#f1f5f9', color: '#334155', flex: '1 1 240px' }}
                      value={showSensitive.email ? (profile.email || user?.email) : maskEmail(profile.email || user?.email)}
                    />
                    <button
                      type="button"
                      disabled={isSendingOtp || otpCountdown > 0}
                      onClick={handleSendOtp}
                      className="profile-btn-primary"
                      style={{ padding: '0.65rem 1.25rem', whiteSpace: 'nowrap', minWidth: '170px' }}
                    >
                      {isSendingOtp ? 'Đang gửi...' : otpCountdown > 0 ? `Gửi lại sau (${otpCountdown}s)` : 'Gửi mã OTP qua Email'}
                    </button>
                  </div>
                </div>

                {otpStep >= 2 && (
                  <>
                    <div className="profile-form-group span-2">
                      <label>Nhập Mã Xác Thực OTP (6 số từ Email) *</label>
                      <input
                        type="text"
                        required
                        maxLength={6}
                        className="profile-input"
                        style={{ fontSize: '1.2rem', letterSpacing: '0.35em', fontWeight: 700, textAlign: 'center' }}
                        value={otpCode}
                        onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
                        placeholder="••••••"
                        autoFocus
                      />
                    </div>

                    <div className="profile-form-group">
                      <label>Mật Khẩu Mới *</label>
                      <div className="profile-input-wrapper">
                        <input
                          type={showOtpPassword ? 'text' : 'password'}
                          required
                          minLength={8}
                          className="profile-input"
                          value={otpNewPassword}
                          onChange={(e) => setOtpNewPassword(e.target.value)}
                          placeholder="Tối thiểu 8 ký tự"
                        />
                        <button
                          type="button"
                          className="profile-input-eye-btn"
                          onClick={() => setShowOtpPassword(!showOtpPassword)}
                          title={showOtpPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                        >
                          {showOtpPassword ? (
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                              <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
                              <line x1="1" y1="1" x2="23" y2="23"></line>
                            </svg>
                          ) : (
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8z"></path>
                              <circle cx="12" cy="12" r="3"></circle>
                            </svg>
                          )}
                        </button>
                      </div>
                    </div>

                    <div className="profile-form-group">
                      <label>Xác Nhận Lại Mật Khẩu Mới *</label>
                      <input
                        type={showOtpPassword ? 'text' : 'password'}
                        required
                        minLength={8}
                        className="profile-input"
                        value={otpConfirmPassword}
                        onChange={(e) => setOtpConfirmPassword(e.target.value)}
                        placeholder="Nhập lại mật khẩu mới"
                      />
                    </div>

                    <div className="profile-actions-bar span-2">
                      <button
                        type="submit"
                        disabled={isResettingPassword}
                        className="profile-btn-primary"
                      >
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <polyline points="20 6 9 17 4 12"></polyline>
                        </svg>
                        {isResettingPassword ? 'Đang xác thực & cập nhật...' : 'Xác Nhận & Cập Nhật Mật Khẩu'}
                      </button>
                    </div>
                  </>
                )}
              </div>
            </form>
          </div>
        </div>
      )}

      {/* SECTION 4: DANH SÁCH CHẶN */}
      {activeSection === 'BLOCKLIST' && (
        <EOSettingsTab />
      )}

    </div>
  );
}

