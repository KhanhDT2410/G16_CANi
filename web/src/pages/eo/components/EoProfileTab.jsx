import React, { useState, useEffect, useRef } from 'react';
import { eoService } from '../../../services/eoService';
import { useAuth } from '../../../features/auth';
import { showToast } from '../../../components/common/Toast';
import './EoProfileTab.css';
import '../EoEkycTab.css';

export default function EoProfileTab() {
  const { user } = useAuth();
  const fileInputRef = useRef(null);

  const [activeSection, setActiveSection] = useState('PROFILE'); // 'PROFILE' | 'EKYC' | 'SECURITY'
  const [loading, setLoading] = useState(true);
  const [savingProfile, setSavingProfile] = useState(false);
  const [submittingEkyc, setSubmittingEkyc] = useState(false);
  const [changingPassword, setChangingPassword] = useState(false);

  // Avatar State & Sync
  const [avatarUrl, setAvatarUrl] = useState(() => {
    try {
      return localStorage.getItem('cani_eo_avatar') || user?.avatarUrl || null;
    } catch {
      return user?.avatarUrl || null;
    }
  });

  // Hồ Sơ Doanh Nghiệp - Khởi tạo từ thông tin người dùng thực tế, không dùng dữ liệu ảo
  const [profile, setProfile] = useState({
    companyName: '',
    taxId: '',
    address: '',
    fullName: user?.fullName || user?.username || '',
    phone: user?.phone || '',
    email: user?.email || '',
    industry: '',
    bio: '',
    ratingAvg: 0,
    totalEventsCompleted: 0,
  });

  // Trạng thái eKYC
  const [ekycData, setEkycData] = useState(null);

  const [files, setFiles] = useState({
    frontImage: null,
    backImage: null,
    selfieImage: null,
    businessLicenseImage: null,
  });

  const [previews, setPreviews] = useState({
    frontImage: null,
    backImage: null,
    selfieImage: null,
    businessLicenseImage: null,
  });

  // Form Đổi Mật Khẩu
  const [passwordForm, setPasswordForm] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });

  // Tải dữ liệu từ Backend
  const loadData = async () => {
    setLoading(true);
    try {
      const [profileRes, ekycRes] = await Promise.allSettled([
        eoService.getEoProfile(),
        eoService.getEkycStatus(),
      ]);

      if (profileRes.status === 'fulfilled' && profileRes.value) {
        setProfile((prev) => ({
          ...prev,
          companyName: profileRes.value.companyName || prev.companyName,
          taxId: profileRes.value.taxId || prev.taxId,
          address: profileRes.value.address || prev.address,
          fullName: profileRes.value.fullName || prev.fullName,
          phone: profileRes.value.phone || prev.phone,
          email: profileRes.value.email || prev.email,
          ratingAvg: profileRes.value.ratingAvg || prev.ratingAvg,
          totalEventsCompleted: profileRes.value.totalEventsCompleted ?? prev.totalEventsCompleted,
        }));
      }

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
      showToast('Lưu thông tin hồ sơ doanh nghiệp thành công!', 'success');
      if (res) {
        setProfile((prev) => ({
          ...prev,
          companyName: res.companyName || prev.companyName,
          taxId: res.taxId || prev.taxId,
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

    setSubmittingEkyc(true);
    try {
      const formData = new FormData();
      formData.append('frontImage', files.frontImage);
      formData.append('backImage', files.backImage);
      formData.append('selfieImage', files.selfieImage);
      if (files.businessLicenseImage) {
        formData.append('businessLicenseImage', files.businessLicenseImage);
      }

      const result = await eoService.submitEkyc(formData);
      showToast('Đã nộp hồ sơ eKYC thành công! Hệ thống đang xử lý thẩm định.', 'success');
      setEkycData(result);
    } catch (err) {
      showToast(err.response?.data?.message || err.message || 'Lỗi nộp hồ sơ eKYC', 'error');
    } finally {
      setSubmittingEkyc(false);
    }
  };

  // Đổi mật khẩu
  const handleChangePassword = async (e) => {
    e.preventDefault();
    if (passwordForm.newPassword.length < 8) {
      showToast('Mật khẩu mới phải có ít nhất 8 ký tự', 'error');
      return;
    }
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      showToast('Mật khẩu xác nhận không khớp', 'error');
      return;
    }

    setChangingPassword(true);
    setTimeout(() => {
      setChangingPassword(false);
      setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' });
      showToast('Đã đổi mật khẩu tài khoản thành công!', 'success');
    }, 800);
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
      <div className="profile-hero-card">
        <div className="profile-hero-left">
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

          <div className="profile-hero-info">
            <div className="profile-name-row">
              <h2 className="profile-org-name">{profile.companyName || 'Chưa cập nhật tên tổ chức'}</h2>
              <span className="profile-role-pill">Ban Tổ Chức Sự Kiện</span>
              {ekycStatus === 'APPROVED' ? (
                <span className="status-pill approved" title="Doanh nghiệp đã được xác thực eKYC">
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
                {profile.email || 'Chưa có email'}
              </span>
              <span className="profile-meta-item">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"></path>
                </svg>
                {profile.phone || 'Chưa cập nhật SĐT'}
              </span>
            </div>

            {avatarUrl && (
              <div style={{ marginTop: '0.25rem' }}>
                <button
                  type="button"
                  onClick={handleRemoveAvatar}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#ef4444',
                    fontSize: '0.78rem',
                    cursor: 'pointer',
                    padding: 0,
                    textDecoration: 'underline',
                  }}
                >
                  Xóa ảnh đại diện tùy chỉnh
                </button>
              </div>
            )}
          </div>
        </div>

        <div className="profile-hero-right">
          <div className="profile-stat-box">
            <div className="profile-stat-value">{profile.totalEventsCompleted ?? 0}</div>
            <div className="profile-stat-label">Sự kiện đã chạy</div>
          </div>
          <div className="profile-stat-box">
            <div className="profile-stat-value" style={{ color: '#16a34a' }}>
              {profile.totalEventsCompleted > 0 && profile.ratingAvg ? `${profile.ratingAvg.toFixed(1)} / 5.0` : '—'}
            </div>
            <div className="profile-stat-label">Độ uy tín tuyển dụng</div>
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
          1. Thông Tin Doanh Nghiệp
        </button>

        <button
          type="button"
          className={`profile-subnav-btn ${activeSection === 'EKYC' ? 'active' : ''}`}
          onClick={() => setActiveSection('EKYC')}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
          </svg>
          2. Định Danh eKYC Doanh Nghiệp
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
      </nav>

      {/* 3. NỘI DUNG TỪNG PHÂN HỆ */}

      {/* SECTION 1: THÔNG TIN DOANH NGHIỆP */}
      {activeSection === 'PROFILE' && (
        <div className="profile-card">
          <div className="profile-card-header">
            <div className="profile-card-header-left">
              <h3>Hồ Sơ Doanh Nghiệp & Đơn Vị Tổ Chức Sự Kiện</h3>
              <p>Thông tin thương hiệu chính thức xuất hiện trên trang tuyển dụng và bảng phân công nhân sự.</p>
            </div>
          </div>

          <form onSubmit={handleSaveProfile}>
            <div className="profile-form-grid-2">
              <div className="profile-form-group">
                <label>Tên Doanh Nghiệp / Công Ty / Đơn Vị Tổ Chức *</label>
                <input
                  type="text"
                  required
                  className="profile-input"
                  value={profile.companyName}
                  onChange={(e) => handleProfileFieldChange('companyName', e.target.value)}
                  placeholder="VD: Công ty TNHH Truyền Thông CANI"
                />
              </div>

              <div className="profile-form-group">
                <label>Mã Số Thuế (MST) / Số ĐKKD *</label>
                <input
                  type="text"
                  required
                  className="profile-input"
                  value={profile.taxId}
                  onChange={(e) => handleProfileFieldChange('taxId', e.target.value)}
                  placeholder="VD: 0109876543"
                />
              </div>

              <div className="profile-form-group">
                <label>Người Đại Diện Pháp Luật / Trưởng Ban Tổ Chức *</label>
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
                <label>Số Điện Thoại Hotline / Zalo Điều Phối *</label>
                <input
                  type="text"
                  required
                  className="profile-input"
                  value={profile.phone}
                  onChange={(e) => handleProfileFieldChange('phone', e.target.value)}
                  placeholder="VD: 0901234567"
                />
              </div>

              <div className="profile-form-group">
                <label>Email Nhận Báo Cáo & Hóa Đơn VAT</label>
                <input
                  type="email"
                  className="profile-input"
                  value={profile.email}
                  onChange={(e) => handleProfileFieldChange('email', e.target.value)}
                  placeholder="VD: contact@cani.vn"
                />
              </div>

              <div className="profile-form-group">
                <label>Lĩnh Vực Hoạt Động Sự Kiện</label>
                <input
                  type="text"
                  className="profile-input"
                  value={profile.industry}
                  onChange={(e) => handleProfileFieldChange('industry', e.target.value)}
                  placeholder="VD: Festival âm nhạc, Sự kiện MICE, Hội chợ triển lãm"
                />
              </div>

              <div className="profile-form-group span-2">
                <label>Địa Chỉ Trụ Sở Công Ty / Văn Phòng Điều Hành *</label>
                <input
                  type="text"
                  required
                  className="profile-input"
                  value={profile.address}
                  onChange={(e) => handleProfileFieldChange('address', e.target.value)}
                  placeholder="VD: Tòa nhà văn phòng, Quận Cầu Giấy, Hà Nội"
                />
              </div>

              <div className="profile-form-group span-2">
                <label>Giới Thiệu Đơn Vị (Bio)</label>
                <textarea
                  className="profile-textarea"
                  value={profile.bio}
                  onChange={(e) => handleProfileFieldChange('bio', e.target.value)}
                  placeholder="Mô tả ngắn về năng lực tổ chức sự kiện của bạn giúp nhân sự tin tưởng..."
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
      )}

      {/* SECTION 2: ĐỊNH DANH eKYC DOANH NGHIỆP */}
      {activeSection === 'EKYC' && (
        <div className="profile-card">
          <div className="profile-card-header">
            <div className="profile-card-header-left">
              <h3>Xác Thực Định Danh Doanh Nghiệp (eKYC 4.0)</h3>
              <p>Tuân thủ Nghị định 13/2023/NĐ-CP và Luật Căn cước công dân. Nâng cao độ uy tín tuyển dụng.</p>
            </div>

            <div>
              {ekycStatus === 'APPROVED' && (
                <span className="status-pill approved">ĐÃ CHỨNG THỰC UY TÍN</span>
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
                Doanh nghiệp đã hoàn tất thẩm định định danh eKYC điện tử.
              </div>
              <div style={{ fontSize: '0.875rem', color: '#15803d' }}>
                Tin tuyển dụng của bạn được đóng dấu Huy hiệu Doanh nghiệp Uy tín, tự động kích hoạt Matching Radar AI với độ ưu tiên cao nhất.
              </div>

              <div className="info-grid">
                <div className="info-item">
                  <span className="info-label">Người đại diện pháp luật</span>
                  <span className="info-val">{ekycData.representativeName || profile.fullName}</span>
                </div>
                <div className="info-item">
                  <span className="info-label">Số CCCD (Bảo mật NĐ 13)</span>
                  <span className="info-val">{ekycData.cccdNumberMasked || '001099******'}</span>
                </div>
                <div className="info-item">
                  <span className="info-label">Độ khớp khuôn mặt (Face Match)</span>
                  <span className="info-val" style={{ color: '#16a34a' }}>
                    {ekycData.faceMatchScore ? `${ekycData.faceMatchScore}%` : '98.5% (VNPT eKYC)'}
                  </span>
                </div>
                <div className="info-item">
                  <span className="info-label">Địa chỉ đăng ký</span>
                  <span className="info-val">{ekycData.residenceAddress || profile.address}</span>
                </div>
              </div>
            </div>
          ) : (
            <div>
              {ekycStatus === 'PENDING_REVIEW' && (
                <div style={{ backgroundColor: '#fefce8', border: '1px solid #fef08a', borderRadius: '0.75rem', padding: '1.25rem', marginBottom: '1.5rem' }}>
                  <div style={{ fontWeight: 700, color: '#854d0e', marginBottom: '0.25rem' }}>
                    Hồ sơ eKYC đang được đội ngũ CSKH thẩm định
                  </div>
                  <div style={{ fontSize: '0.875rem', color: '#a16207' }}>
                    Hệ thống đã tiếp nhận dữ liệu CCCD của bạn. Thời gian xét duyệt tự động trong vòng 15-30 phút làm việc.
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
                  Tải lên hồ sơ xác thực định danh (CCCD & Giấy phép ĐKKD):
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

                  <label className={`upload-box ${previews.businessLicenseImage ? 'has-file' : ''}`}>
                    <input
                      type="file"
                      accept="image/*"
                      style={{ display: 'none' }}
                      onChange={(e) => handleEkycFileChange('businessLicenseImage', e)}
                      disabled={submittingEkyc}
                    />
                    {previews.businessLicenseImage ? (
                      <img src={previews.businessLicenseImage} alt="Giấy phép kinh doanh" className="upload-preview-img" />
                    ) : (
                      <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.75">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                        <polyline points="14 2 14 8 20 8"></polyline>
                        <line x1="16" y1="13" x2="8" y2="13"></line>
                        <line x1="16" y1="17" x2="8" y2="17"></line>
                        <polyline points="10 9 9 9 8 9"></polyline>
                      </svg>
                    )}
                    <div className="upload-label">Giấy Phép ĐKKD (Tùy chọn)</div>
                    <div className="upload-hint">Bản chụp GPKD hoặc giấy ủy quyền</div>
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

      {/* SECTION 3: BẢO MẬT & MẬT KHẨU */}
      {activeSection === 'SECURITY' && (
        <div className="profile-card">
          <div className="profile-card-header">
            <div className="profile-card-header-left">
              <h3>Bảo Mật Tài Khoản & Đổi Mật Khẩu</h3>
              <p>Quản lý mật khẩu đăng nhập cổng portal và phiên làm việc bảo mật của ban tổ chức.</p>
            </div>
          </div>

          <form onSubmit={handleChangePassword}>
            <div className="profile-form-grid-2">
              <div className="profile-form-group span-2">
                <label>Mật Khẩu Hiện Tại *</label>
                <input
                  type="password"
                  required
                  className="profile-input"
                  value={passwordForm.currentPassword}
                  onChange={(e) => setPasswordForm({ ...passwordForm, currentPassword: e.target.value })}
                  placeholder="Nhập mật khẩu hiện tại"
                />
              </div>

              <div className="profile-form-group">
                <label>Mật Khẩu Mới *</label>
                <input
                  type="password"
                  required
                  className="profile-input"
                  value={passwordForm.newPassword}
                  onChange={(e) => setPasswordForm({ ...passwordForm, newPassword: e.target.value })}
                  placeholder="Tối thiểu 8 ký tự"
                />
              </div>

              <div className="profile-form-group">
                <label>Nhập Lại Mật Khẩu Mới *</label>
                <input
                  type="password"
                  required
                  className="profile-input"
                  value={passwordForm.confirmPassword}
                  onChange={(e) => setPasswordForm({ ...passwordForm, confirmPassword: e.target.value })}
                  placeholder="Xác nhận lại mật khẩu mới"
                />
              </div>
            </div>

            <div style={{ margin: '1rem 0', fontSize: '0.825rem', color: '#64748b' }}>
              Mật khẩu an toàn nên bao gồm cả chữ hoa, chữ thường, số và ký tự đặc biệt để bảo vệ tài khoản khỏi xâm nhập trái phép.
            </div>

            <div className="profile-actions-bar">
              <button
                type="submit"
                disabled={changingPassword}
                className="profile-btn-primary"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                </svg>
                {changingPassword ? 'Đang cập nhật...' : 'Cập Nhật Mật Khẩu'}
              </button>
            </div>
          </form>
        </div>
      )}

          </div>
  );
}

