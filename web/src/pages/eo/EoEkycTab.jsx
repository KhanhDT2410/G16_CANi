import React, { useState, useEffect } from 'react';
import { eoService } from '../../services/eoService';
import { showToast } from '../../components/common/Toast';
import './EoEkycTab.css';

export const EoEkycTab = () => {
  const [loading, setLoading] = useState(true);
  const [savingProfile, setSavingProfile] = useState(false);
  const [submittingEkyc, setSubmittingEkyc] = useState(false);
  
  const [profile, setProfile] = useState({
    companyName: '',
    taxId: '',
    address: '',
    fullName: '',
    phone: '',
    email: '',
  });

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

  const loadData = async () => {
    setLoading(true);
    try {
      const [profileRes, ekycRes] = await Promise.allSettled([
        eoService.getEoProfile(),
        eoService.getEkycStatus(),
      ]);

      if (profileRes.status === 'fulfilled' && profileRes.value) {
        setProfile({
          companyName: profileRes.value.companyName || '',
          taxId: profileRes.value.taxId || '',
          address: profileRes.value.address || '',
          fullName: profileRes.value.fullName || '',
          phone: profileRes.value.phone || '',
          email: profileRes.value.email || '',
        });
      }

      if (ekycRes.status === 'fulfilled' && ekycRes.value) {
        setEkycData(ekycRes.value);
      }
    } catch (err) {
      console.error('Lỗi tải dữ liệu EO Profile/eKYC:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleProfileChange = (field, val) => {
    setProfile((prev) => ({ ...prev, [field]: val }));
  };

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setSavingProfile(true);
    try {
      const res = await eoService.updateEoProfile(profile);
      showToast('Cập nhật thông tin hồ sơ doanh nghiệp thành công!', 'success');
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

  const handleFileChange = (field, e) => {
    const file = e.target.files?.[0];
    if (file) {
      setFiles((prev) => ({ ...prev, [field]: file }));
      setPreviews((prev) => ({ ...prev, [field]: URL.createObjectURL(file) }));
    }
  };

  const handleSubmitEkyc = async (e) => {
    e.preventDefault();
    if (!files.frontImage || !files.backImage || !files.selfieImage) {
      showToast('Vui lòng tải lên đầy đủ ảnh mặt trước, mặt sau CCCD và ảnh chân dung selfie', 'error');
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
      showToast(err.response?.data?.message || err.message || 'Lỗi khi nộp hồ sơ eKYC. Vui lòng thử lại.', 'error');
    } finally {
      setSubmittingEkyc(false);
    }
  };

  if (loading) {
    return (
      <div className="eo-ekyc-container">
        <div className="eo-ekyc-card" style={{ textAlign: 'center', padding: '3rem' }}>
          <div className="spinner" style={{ margin: '0 auto 1rem' }} />
          <p style={{ color: '#6b7280' }}>Đang kiểm tra hồ sơ định danh doanh nghiệp...</p>
        </div>
      </div>
    );
  }

  const status = ekycData?.status || 'NOT_SUBMITTED';

  return (
    <div className="eo-ekyc-container animate-fade-in-up">
      {/* PHẦN 1: HỒ SƠ DOANH NGHIỆP / TỔ CHỨC */}
      <div className="eo-ekyc-card" style={{ marginBottom: '2rem' }}>
        <div className="profile-section-title">
          <span>Hồ Sơ Doanh Nghiệp & Đơn Vị Tổ Chức (Profile)</span>
        </div>
        <p style={{ fontSize: '0.875rem', color: '#6b7280', margin: '-0.5rem 0 1.25rem' }}>
          Thông tin thương hiệu chính thức hiển thị trên trang tuyển dụng giúp nhân sự an tâm ứng tuyển.
        </p>

        <form onSubmit={handleSaveProfile}>
          <div className="profile-form-grid">
            <div className="profile-form-field">
              <label>Tên Doanh Nghiệp / Công Ty / Đơn Vị Tổ Chức *</label>
              <input
                type="text"
                required
                placeholder="VD: Công ty TNHH Truyền Thông CANI"
                value={profile.companyName}
                onChange={(e) => handleProfileChange('companyName', e.target.value)}
              />
            </div>

            <div className="profile-form-field">
              <label>Mã Số Thuế (MST) / Số ĐKKD</label>
              <input
                type="text"
                placeholder="VD: 0109876543"
                value={profile.taxId}
                onChange={(e) => handleProfileChange('taxId', e.target.value)}
              />
            </div>

            <div className="profile-form-field">
              <label>Người Đại Diện Pháp Luật / Quản Lý *</label>
              <input
                type="text"
                required
                placeholder="VD: Nguyễn Văn A"
                value={profile.fullName}
                onChange={(e) => handleProfileChange('fullName', e.target.value)}
              />
            </div>

            <div className="profile-form-field">
              <label>Số Điện Thoại Liên Hệ *</label>
              <input
                type="text"
                required
                placeholder="VD: 0912345678"
                value={profile.phone}
                onChange={(e) => handleProfileChange('phone', e.target.value)}
              />
            </div>

            <div className="profile-form-field" style={{ gridColumn: '1 / -1' }}>
              <label>Địa Chỉ Trụ Sở Công Ty / Văn Phòng</label>
              <input
                type="text"
                placeholder="VD: Tòa nhà FPT, Khu CNC Hòa Lạc, Thạch Thất, Hà Nội"
                value={profile.address}
                onChange={(e) => handleProfileChange('address', e.target.value)}
              />
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <button
              type="submit"
              disabled={savingProfile}
              className="eo-btn-primary"
              style={{
                padding: '0.65rem 1.5rem',
                fontSize: '0.9rem',
                fontWeight: 600,
                borderRadius: '0.5rem',
                backgroundColor: '#2563eb',
                color: '#ffffff',
                border: 'none',
                cursor: savingProfile ? 'not-allowed' : 'pointer',
              }}
            >
              {savingProfile ? 'Đang lưu...' : 'Lưu Cập Nhật Hồ Sơ'}
            </button>
          </div>
        </form>
      </div>

      {/* PHẦN 2: ĐỊNH DANH SỐ eKYC */}
      <div className="eo-ekyc-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 700, margin: 0, color: '#111827' }}>
              Xác Thực Định Danh Doanh Nghiệp (eKYC)
            </h2>
            <p style={{ fontSize: '0.875rem', color: '#6b7280', margin: '0.25rem 0 0' }}>
              Chứng thực người đại diện & nâng cao độ uy tín tuyển dụng trên sàn CANi
            </p>
          </div>

          <div>
            {status === 'APPROVED' && (
              <span className="eo-ekyc-badge badge-approved">
                ĐÃ CHỨNG THỰC UY TÍN
              </span>
            )}
            {status === 'PENDING_REVIEW' && (
              <span className="eo-ekyc-badge badge-pending">
                ĐANG CHỜ DUYỆT
              </span>
            )}
            {status === 'REJECTED' && (
              <span className="eo-ekyc-badge badge-rejected">
                HỒ SƠ BỊ TỪ CHỐI
              </span>
            )}
            {status === 'NOT_SUBMITTED' && (
              <span className="eo-ekyc-badge badge-none">
                CHƯA ĐỊNH DANH
              </span>
            )}
          </div>
        </div>

        {/* Thông báo trạng thái chi tiết */}
        {status === 'APPROVED' && (
          <div style={{ backgroundColor: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: '0.75rem', padding: '1.25rem', marginBottom: '1.5rem' }}>
            <div style={{ fontWeight: 600, color: '#166534', marginBottom: '0.5rem' }}>
              Doanh nghiệp của bạn đã hoàn thành kiểm duyệt định danh eKYC.
            </div>
            <div style={{ fontSize: '0.875rem', color: '#15803d' }}>
              Tin tuyển dụng của bạn được hiển thị ưu tiên với huy hiệu xác minh chính thức, gia tăng 85% tỷ lệ nhận ca của các nhân sự kinh nghiệm.
            </div>

            <div className="info-grid">
              <div className="info-item">
                <span className="info-label">Người đại diện pháp luật</span>
                <span className="info-val">{ekycData.representativeName || profile.fullName || 'N/A'}</span>
              </div>
              <div className="info-item">
                <span className="info-label">Số CCCD (Bảo mật NĐ 13)</span>
                <span className="info-val">{ekycData.cccdNumberMasked || 'N/A'}</span>
              </div>
              <div className="info-item">
                <span className="info-label">Độ khớp khuôn mặt (Face Match)</span>
                <span className="info-val" style={{ color: '#16a34a' }}>{ekycData.faceMatchScore ? `${ekycData.faceMatchScore}%` : 'Chuẩn hóa VNPT'}</span>
              </div>
              <div className="info-item">
                <span className="info-label">Địa chỉ thường trú</span>
                <span className="info-val">{ekycData.residenceAddress || 'Việt Nam'}</span>
              </div>
            </div>
          </div>
        )}

        {status === 'PENDING_REVIEW' && (
          <div style={{ backgroundColor: '#fefce8', border: '1px solid #fef08a', borderRadius: '0.75rem', padding: '1.25rem', marginBottom: '1.5rem' }}>
            <div style={{ fontWeight: 600, color: '#854d0e', marginBottom: '0.25rem' }}>
              Hồ sơ eKYC đang được đội ngũ Admin / CSKH thẩm định
            </div>
            <div style={{ fontSize: '0.875rem', color: '#a16207' }}>
              Hệ thống đã tiếp nhận ảnh CCCD và thông tin đại diện của bạn. Quá trình xét duyệt diễn ra tự động trong vòng 15-30 phút làm việc.
            </div>
          </div>
        )}

        {status === 'REJECTED' && (
          <div style={{ backgroundColor: '#fef2f2', border: '1px solid #fecaca', borderRadius: '0.75rem', padding: '1.25rem', marginBottom: '1.5rem' }}>
            <div style={{ fontWeight: 600, color: '#991b1b', marginBottom: '0.25rem' }}>
              Hồ sơ định danh eKYC trước đó chưa được duyệt
            </div>
            <div style={{ fontSize: '0.875rem', color: '#b91c1c' }}>
              Lý do: {ekycData.routingReason || 'Ảnh chụp giấy tờ mờ, bị che khuất hoặc không khớp với ảnh selfie.'}. Vui lòng chụp lại ảnh rõ nét và nộp lại hồ sơ bên dưới.
            </div>
          </div>
        )}

        {/* Form nộp hồ sơ eKYC nếu chưa được duyệt */}
        {status !== 'APPROVED' && (
          <form onSubmit={handleSubmitEkyc}>
            <p style={{ fontSize: '0.9375rem', fontWeight: 600, color: '#374151', margin: '0 0 0.5rem' }}>
              Tải lên hồ sơ xác thực định danh:
            </p>

            <div className="ekyc-upload-grid">
              {/* Mặt trước */}
              <label className={`upload-box ${previews.frontImage ? 'has-file' : ''}`}>
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  style={{ display: 'none' }}
                  onChange={(e) => handleFileChange('frontImage', e)}
                  disabled={submittingEkyc}
                />
                {previews.frontImage && (
                  <img src={previews.frontImage} alt="Mặt trước CCCD" className="upload-preview-img" />
                )}
                <div className="upload-label">Mặt trước CCCD *</div>
                <div className="upload-hint">Chụp rõ nét, không lóa sáng</div>
              </label>

              {/* Mặt sau */}
              <label className={`upload-box ${previews.backImage ? 'has-file' : ''}`}>
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  style={{ display: 'none' }}
                  onChange={(e) => handleFileChange('backImage', e)}
                  disabled={submittingEkyc}
                />
                {previews.backImage && (
                  <img src={previews.backImage} alt="Mặt sau CCCD" className="upload-preview-img" />
                )}
                <div className="upload-label">Mặt sau CCCD *</div>
                <div className="upload-hint">Rõ mã QR và chip CCCD</div>
              </label>

              {/* Ảnh Selfie */}
              <label className={`upload-box ${previews.selfieImage ? 'has-file' : ''}`}>
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  style={{ display: 'none' }}
                  onChange={(e) => handleFileChange('selfieImage', e)}
                  disabled={submittingEkyc}
                />
                {previews.selfieImage && (
                  <img src={previews.selfieImage} alt="Selfie khuôn mặt" className="upload-preview-img" />
                )}
                <div className="upload-label">Ảnh chân dung đại diện *</div>
                <div className="upload-hint">Chụp thẳng mặt, không đeo kính râm</div>
              </label>

              {/* Giấy phép kinh doanh */}
              <label className={`upload-box ${previews.businessLicenseImage ? 'has-file' : ''}`}>
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  style={{ display: 'none' }}
                  onChange={(e) => handleFileChange('businessLicenseImage', e)}
                  disabled={submittingEkyc}
                />
                {previews.businessLicenseImage && (
                  <img src={previews.businessLicenseImage} alt="Giấy phép kinh doanh" className="upload-preview-img" />
                )}
                <div className="upload-label">Giấy phép kinh doanh (Tùy chọn)</div>
                <div className="upload-hint">Bản chụp GPKD hoặc Giấy ủy quyền</div>
              </label>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '1.5rem' }}>
              <button
                type="submit"
                disabled={submittingEkyc}
                className="eo-btn-primary"
                style={{
                  padding: '0.75rem 1.75rem',
                  fontSize: '0.9375rem',
                  fontWeight: 600,
                  borderRadius: '0.5rem',
                  backgroundColor: '#2563eb',
                  color: '#ffffff',
                  border: 'none',
                  cursor: submittingEkyc ? 'not-allowed' : 'pointer',
                }}
              >
                {submittingEkyc ? 'Đang gửi & phân tích eKYC...' : 'Gửi Hồ Sơ Xác Thực eKYC'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};

export default EoEkycTab;
