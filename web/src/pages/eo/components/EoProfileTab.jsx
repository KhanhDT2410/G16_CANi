import React, { useState, useEffect, useRef } from 'react';
import { eoService } from '../../../services/eoService';
import { useAuth } from '../../../features/auth';
import { authApi } from '../../../features/auth/api/authApi';
import { showToast } from '../../../components/common/Toast';
import EOSettingsTab from './EOSettingsTab';
import EoReviewsSection from './EoReviewsSection';
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

  // Trạng thái eKYC & Nghị định 13/2023 consent
  const [ekycData, setEkycData] = useState(null);
  const [consentPolicy, setConsentPolicy] = useState({
    required: true,
    noticeVersion: '2026.11-nd13-v1',
    title: 'Thông báo xử lý dữ liệu cá nhân & sinh trắc học (Nghị định 13/2023/NĐ-CP)',
    summary: 'CANi chỉ dùng ảnh CCCD và chân dung selfie để xác thực danh tính qua VNPT eKYC, kiểm tra độ tuổi tối thiểu và phòng chống trùng lặp tài khoản.',
  });
  const [consentAccepted, setConsentAccepted] = useState(false);

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
      const [profileRes, ekycRes, eventsRes, consentRes] = await Promise.allSettled([
        eoService.getEoProfile(),
        eoService.getEkycStatus(),
        eoService.getMyEvents(),
        eoService.getEkycConsentPolicy(),
      ]);
      if (consentRes.status === 'fulfilled' && consentRes.value) {
        setConsentPolicy(consentRes.value);
      }

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
    if (consentPolicy?.required !== false && !consentAccepted) {
      showToast('Vui lòng đọc và xác nhận đồng ý xử lý dữ liệu cá nhân & sinh trắc học (Nghị định 13/2023/NĐ-CP).', 'error');
      return;
    }

    setSubmittingEkyc(true);
    try {
      const formData = new FormData();
      formData.append('frontImage', files.frontImage);
      formData.append('backImage', files.backImage);
      formData.append('selfieImage', files.selfieImage);
      formData.append('consentAccepted', String(Boolean(consentAccepted)));
      if (consentPolicy?.noticeVersion) {
        formData.append('consentNoticeVersion', consentPolicy.noticeVersion);
      }
      if (profile.fullName?.trim()) {
        formData.append('representativeName', profile.fullName.trim());
      }

      const result = await eoService.submitEkyc(formData);
      setEkycData(result);
      if (result?.status === 'APPROVED') {
        showToast('Xác thực định danh eKYC thành công!', 'success');
      } else if (result?.status === 'PROCESSING' || result?.status === 'PENDING') {
        showToast(result.routingReason || 'Hồ sơ eKYC đang được xử lý.', 'info');
      } else if (result?.status === 'TECHNICAL_FAILURE') {
        showToast(result.routingReason || 'Lỗi kết nối VNPT eKYC tạm thời. Hồ sơ chưa bị từ chối, vui lòng thử lại.', 'error');
      } else {
        showToast(result?.routingReason || 'Hồ sơ định danh eKYC chưa đạt yêu cầu. Vui lòng chụp lại rõ nét và nộp lại.', 'error');
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
                <span style={{ fontSize: '0.68rem', fontWeight: 700 }}>Ảnh</span>
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
                  Đã Xác Thực eKYC
                </span>
              ) : ekycStatus === 'PENDING_REVIEW' ? (
                <span className="status-pill pending">Cần Xác Thực Lại eKYC</span>
              ) : (
                <span className="status-pill none">Chưa Xác Thực eKYC</span>
              )}
            </div>

            <div className="profile-meta-row">
              <span className="profile-meta-item">
                Người đại diện: <strong>{profile.fullName || 'Chưa cập nhật'}</strong>
              </span>
              <span className="profile-meta-item">
                Email: <span className="sensitive-masked">
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
                SĐT: <span className="sensitive-masked">
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

          <div
            className="profile-stat-box"
            onClick={() => setActiveSection('REVIEWS')}
            style={{ cursor: 'pointer' }}
            title="Nhấp để xem chi tiết nhận xét & đánh giá từ nhân sự"
          >
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
          1. Thông Tin Nhà Tổ Chức
        </button>

        <button
          type="button"
          className={`profile-subnav-btn ${activeSection === 'EKYC' ? 'active' : ''}`}
          onClick={() => setActiveSection('EKYC')}
        >
          2. Xác Thực Định Danh eKYC
        </button>

        <button
          type="button"
          className={`profile-subnav-btn ${activeSection === 'REVIEWS' ? 'active' : ''}`}
          onClick={() => setActiveSection('REVIEWS')}
        >
          3. Đánh Giá Từ Nhân Sự {profile.ratingCount > 0 ? `(${profile.ratingCount})` : ''}
        </button>

        <button
          type="button"
          className={`profile-subnav-btn ${activeSection === 'SECURITY' ? 'active' : ''}`}
          onClick={() => setActiveSection('SECURITY')}
        >
          4. Bảo Mật & Mật Khẩu
        </button>

        <button
          type="button"
          className={`profile-subnav-btn ${activeSection === 'BLOCKLIST' ? 'active' : ''}`}
          onClick={() => setActiveSection('BLOCKLIST')}
        >
          5. Danh Sách Chặn
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
          <span className="profile-field-check" title="Đã hoàn thiện" style={{ fontSize: '0.7rem', fontWeight: 700 }}>
            Đủ
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
                <span className="status-pill pending">CẦN XÁC THỰC LẠI</span>
              )}
              {(ekycStatus === 'PROCESSING' || ekycStatus === 'PENDING') && (
                <span className="status-pill pending">ĐANG XÁC MINH</span>
              )}
              {ekycStatus === 'TECHNICAL_FAILURE' && (
                <span className="status-pill none" style={{ color: '#c2410c', borderColor: '#fdba74', backgroundColor: '#fff7ed' }}>
                  LỖI KỸ THUẬT — THỬ LẠI
                </span>
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
              <div style={{ fontWeight: 700, color: '#166534', marginBottom: '0.4rem' }}>
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
                  <span className="info-label">Độ tuổi quy định</span>
                  <span className="info-val">
                    {ekycData.meetsMinimumAge === true
                      ? 'Đạt độ tuổi quy định'
                      : ekycData.meetsMinimumAge === false
                        ? 'Chưa đạt độ tuổi tối thiểu'
                        : 'Đã ghi nhận xác minh'}
                  </span>
                </div>
                {(ekycData.dob || ekycData.gender) && (
                  <div className="info-item">
                    <span className="info-label">Thông tin bổ sung (dữ liệu cũ)</span>
                    <span className="info-val">
                      {[ekycData.dob, ekycData.gender].filter(Boolean).join(' • ')}
                    </span>
                  </div>
                )}
                {ekycData.residenceAddress && (
                  <div className="info-item">
                    <span className="info-label">Nơi thường trú (dữ liệu cũ)</span>
                    <span className="info-val">{ekycData.residenceAddress}</span>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div>
              {ekycStatus === 'PENDING_REVIEW' && (
                <div style={{ backgroundColor: '#fefce8', border: '1px solid #fef08a', borderRadius: '0.75rem', padding: '1.25rem', marginBottom: '1.5rem' }}>
                  <div style={{ fontWeight: 700, color: '#854d0e', marginBottom: '0.25rem' }}>
                    {ekycData?.imagesExpired || ekycData?.imagesAvailable === false
                      ? 'Hồ sơ eKYC cũ đã hết hạn lưu trữ ảnh — Vui lòng nộp lại'
                      : 'Hồ sơ eKYC cũ chưa hoàn tất xác thực tự động'}
                  </div>
                  <div style={{ fontSize: '0.875rem', color: '#a16207', marginBottom: '0.75rem' }}>
                    {(ekycData?.imagesExpired || ekycData?.imagesAvailable === false)
                      ? (ekycData?.imagesUnavailableReason || 'Ảnh xác minh trước đó đã hết hạn lưu trữ tạm thời. Bạn có thể chụp và gửi lại hồ sơ mới ngay bên dưới.')
                      : (ekycData?.routingReason || 'Hệ thống đã chuyển sang xác thực tự động qua VNPT eKYC. Vui lòng chụp lại ảnh rõ nét và nộp lại bên dưới.')}
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
                    </div>
                  )}
                </div>
              )}

              {ekycStatus === 'TECHNICAL_FAILURE' && (
                <div style={{ backgroundColor: '#fff7ed', border: '1px solid #fed7aa', borderRadius: '0.75rem', padding: '1.25rem', marginBottom: '1.5rem' }}>
                  <div style={{ fontWeight: 700, color: '#c2410c', marginBottom: '0.25rem' }}>
                    Lỗi kỹ thuật tạm thời từ cổng VNPT eKYC — Bạn có thể thử lại
                  </div>
                  <div style={{ fontSize: '0.875rem', color: '#9a3412' }}>
                    {ekycData?.routingReason || 'Không thể kết nối tới cổng VNPT eKYC. Hồ sơ của bạn không bị từ chối — vui lòng gửi lại.'}
                  </div>
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
                    {previews.frontImage && (
                      <img src={previews.frontImage} alt="Mặt trước CCCD" className="upload-preview-img" />
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
                    {previews.backImage && (
                      <img src={previews.backImage} alt="Mặt sau CCCD" className="upload-preview-img" />
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
                    {previews.selfieImage && (
                      <img src={previews.selfieImage} alt="Selfie" className="upload-preview-img" />
                    )}
                    <div className="upload-label">Ảnh chân dung đại diện *</div>
                    <div className="upload-hint">Nhìn thẳng, đủ ánh sáng</div>
                  </label>
                </div>

                <div
                  style={{
                    marginTop: '1rem',
                    padding: '0.9rem 1rem',
                    borderRadius: '0.75rem',
                    backgroundColor: '#f8fafc',
                    border: '1px solid #e2e8f0',
                  }}
                >
                  <div style={{ fontWeight: 700, fontSize: '0.85rem', color: '#0f172a', marginBottom: '0.35rem' }}>
                    {consentPolicy?.title}
                  </div>
                  <div style={{ fontSize: '0.8rem', color: '#475569', lineHeight: 1.45, marginBottom: '0.6rem' }}>
                    {consentPolicy?.summary}
                  </div>
                  <label style={{ display: 'flex', alignItems: 'flex-start', gap: '0.55rem', cursor: 'pointer', fontSize: '0.84rem', color: '#1e293b', fontWeight: 600 }}>
                    <input
                      type="checkbox"
                      checked={consentAccepted}
                      onChange={(e) => setConsentAccepted(e.target.checked)}
                      disabled={submittingEkyc}
                      style={{ marginTop: '0.2rem' }}
                    />
                    <span>
                      Tôi đồng ý cho CANi thu thập và xử lý ảnh CCCD, ảnh chân dung sinh trắc học qua VNPT eKYC theo thông báo phiên bản {consentPolicy?.noticeVersion}.
                    </span>
                  </label>
                </div>

                <div className="profile-actions-bar">
                  <button
                    type="submit"
                    disabled={submittingEkyc || (consentPolicy?.required !== false && !consentAccepted)}
                    className="profile-btn-primary"
                  >
                    {submittingEkyc ? 'Đang thẩm định eKYC...' : 'Nộp Hồ Sơ Xác Thực eKYC'}
                  </button>
                </div>
              </form>
            </div>
          )}
        </div>
      )}

      {/* SECTION 3: ĐÁNH GIÁ TỪ NHÂN SỰ */}
      {activeSection === 'REVIEWS' && (
        <EoReviewsSection />
      )}

      {/* SECTION 4: BẢO MẬT & MẬT KHẨU (CHỈ ĐỔI QUA EMAIL OTP) */}
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

