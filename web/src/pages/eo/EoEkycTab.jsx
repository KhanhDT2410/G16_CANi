import React, { useState, useEffect } from 'react';
import { eoService } from '../../services/eoService';
import { showToast } from '../../components/common/Toast';
import './EoEkycTab.css';

export const EoEkycTab = () => {
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
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

  const fetchStatus = async () => {
    setLoading(true);
    try {
      const data = await eoService.getEkycStatus();
      setEkycData(data);
    } catch (err) {
      console.error('Lỗi tải trạng thái eKYC:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStatus();
  }, []);

  const handleFileChange = (field, e) => {
    const file = e.target.files?.[0];
    if (file) {
      setFiles((prev) => ({ ...prev, [field]: file }));
      setPreviews((prev) => ({ ...prev, [field]: URL.createObjectURL(file) }));
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!files.frontImage || !files.backImage || !files.selfieImage) {
      showToast('Vui lòng tải lên đầy đủ ảnh mặt trước, mặt sau CCCD và ảnh chân dung selfie', 'error');
      return;
    }

    setSubmitting(true);
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
      showToast(err.message || 'Lỗi khi nộp hồ sơ eKYC. Vui lòng thử lại.', 'error');
    } finally {
      setSubmitting(false);
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
                <svg width="16" height="16" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd"/>
                </svg>
                ĐÃ CHỨNG THỰC UY TÍN
              </span>
            )}
            {status === 'PENDING_REVIEW' && (
              <span className="eo-ekyc-badge badge-pending">
                <svg width="16" height="16" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clipRule="evenodd"/>
                </svg>
                ĐANG CHỜ DUYỆT
              </span>
            )}
            {status === 'REJECTED' && (
              <span className="eo-ekyc-badge badge-rejected">
                <svg width="16" height="16" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd"/>
                </svg>
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
              🎉 Doanh nghiệp của bạn đã hoàn thành kiểm duyệt định danh eKYC!
            </div>
            <div style={{ fontSize: '0.875rem', color: '#15803d' }}>
              Tin tuyển dụng của bạn được hiển thị ưu tiên với huy hiệu xác minh chính thức, gia tăng 85% tỷ lệ nhận ca của các nhân sự kinh nghiệm.
            </div>

            <div className="info-grid">
              <div className="info-item">
                <span className="info-label">Người đại diện pháp luật</span>
                <span className="info-val">{ekycData.representativeName || 'N/A'}</span>
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
              ⏳ Hồ sơ eKYC đang được đội ngũ Admin / CSKH thẩm định
            </div>
            <div style={{ fontSize: '0.875rem', color: '#a16207' }}>
              Hệ thống đã tiếp nhận ảnh CCCD và thông tin đại diện của bạn. Quá trình xét duyệt diễn ra tự động trong vòng 15-30 phút làm việc.
            </div>
          </div>
        )}

        {status === 'REJECTED' && (
          <div style={{ backgroundColor: '#fef2f2', border: '1px solid #fecaca', borderRadius: '0.75rem', padding: '1.25rem', marginBottom: '1.5rem' }}>
            <div style={{ fontWeight: 600, color: '#991b1b', marginBottom: '0.25rem' }}>
              ❌ Hồ sơ định danh eKYC trước đó chưa được duyệt
            </div>
            <div style={{ fontSize: '0.875rem', color: '#b91c1c' }}>
              Lý do: {ekycData.routingReason || 'Ảnh chụp giấy tờ mờ, bị che khuất hoặc không khớp với ảnh selfie.'}. Vui lòng chụp lại ảnh rõ nét và nộp lại hồ sơ bên dưới.
            </div>
          </div>
        )}

        {/* Form nộp hồ sơ eKYC nếu chưa được duyệt */}
        {status !== 'APPROVED' && (
          <form onSubmit={handleSubmit}>
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
                  disabled={submitting}
                />
                {previews.frontImage ? (
                  <img src={previews.frontImage} alt="Mặt trước CCCD" className="upload-preview-img" />
                ) : (
                  <div style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>🪪</div>
                )}
                <div className="upload-label">Mặt trước CCCD</div>
                <div className="upload-hint">Chụp rõ nét, không lóa sáng</div>
              </label>

              {/* Mặt sau */}
              <label className={`upload-box ${previews.backImage ? 'has-file' : ''}`}>
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  style={{ display: 'none' }}
                  onChange={(e) => handleFileChange('backImage', e)}
                  disabled={submitting}
                />
                {previews.backImage ? (
                  <img src={previews.backImage} alt="Mặt sau CCCD" className="upload-preview-img" />
                ) : (
                  <div style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>🔄</div>
                )}
                <div className="upload-label">Mặt sau CCCD</div>
                <div className="upload-hint">Rõ mã QR và chip CCCD</div>
              </label>

              {/* Ảnh Selfie */}
              <label className={`upload-box ${previews.selfieImage ? 'has-file' : ''}`}>
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  style={{ display: 'none' }}
                  onChange={(e) => handleFileChange('selfieImage', e)}
                  disabled={submitting}
                />
                {previews.selfieImage ? (
                  <img src={previews.selfieImage} alt="Selfie khuôn mặt" className="upload-preview-img" />
                ) : (
                  <div style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>🤳</div>
                )}
                <div className="upload-label">Ảnh chân dung đại diện</div>
                <div className="upload-hint">Chụp thẳng mặt, không đeo kính râm</div>
              </label>

              {/* Giấy phép kinh doanh */}
              <label className={`upload-box ${previews.businessLicenseImage ? 'has-file' : ''}`}>
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  style={{ display: 'none' }}
                  onChange={(e) => handleFileChange('businessLicenseImage', e)}
                  disabled={submitting}
                />
                {previews.businessLicenseImage ? (
                  <img src={previews.businessLicenseImage} alt="Giấy phép kinh doanh" className="upload-preview-img" />
                ) : (
                  <div style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>📑</div>
                )}
                <div className="upload-label">Giấy phép kinh doanh (Tùy chọn)</div>
                <div className="upload-hint">Bản chụp GPKD hoặc Giấy ủy quyền</div>
              </label>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '1.5rem' }}>
              <button
                type="submit"
                disabled={submitting}
                className="eo-btn-primary"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                  padding: '0.75rem 1.75rem',
                  fontSize: '0.9375rem',
                  fontWeight: 600,
                  borderRadius: '0.5rem',
                  backgroundColor: '#2563eb',
                  color: '#ffffff',
                  border: 'none',
                  cursor: submitting ? 'not-allowed' : 'pointer',
                }}
              >
                {submitting ? (
                  <>
                    <span className="spinner" style={{ width: '16px', height: '16px' }} />
                    Đang gửi & phân tích eKYC...
                  </>
                ) : (
                  'Gửi Hồ Sơ Xác Thực eKYC'
                )}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};

export default EoEkycTab;
