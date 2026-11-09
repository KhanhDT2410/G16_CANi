import { useEkycVerification } from '../hooks/useEkycVerification';
import { EKYC_DECISION } from '../types/ekycVerification.constants';
import './EkycVerification.css';

export const EkycVerificationView = ({ onCountChange }) => {
  const { ekycQueue, loading, handleReview } = useEkycVerification({ onCountChange });

  if (loading) {
    return (
      <div className="ekyc-card-list">
        <div style={{ padding: '1rem 0' }}>
          <div className="skeleton" style={{ height: '320px', borderRadius: '14px', marginBottom: '1rem' }} />
          <div className="skeleton" style={{ height: '320px', borderRadius: '14px' }} />
        </div>
      </div>
    );
  }

  if (ekycQueue.length === 0) {
    return (
      <div className="ekyc-card-list">
        <div className="ekyc-empty-state">
          <p style={{ color: '#059669', fontWeight: 600, margin: 0 }}>
            Hiện tại không còn hồ sơ eKYC nào đang chờ duyệt.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="ekyc-card-list">
      {ekycQueue.map((item) => (
        <div key={item.verificationId} className="ekyc-verif-card animate-fade-in-up">
          <div className="ekyc-card-top">
            <div>
              <h3 style={{ margin: '0 0 0.25rem 0', fontSize: '1.2rem', fontWeight: 700 }}>
                {item.fullName}
              </h3>
              <span style={{ fontSize: '0.85rem', color: '#6b7280' }}>
                CCCD: <strong>{item.cccdNumberMasked}</strong> | Ngày sinh: {item.dob}
              </span>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: '0.85rem', fontWeight: 700, color: '#2563eb' }}>
                Độ khớp khuôn mặt: {item.faceMatchScore}%
              </div>
              <div style={{ fontSize: '0.75rem', color: '#6b7280' }}>
                Độ tin cậy OCR: {(item.ocrConfidence * 100).toFixed(0)}%
              </div>
            </div>
          </div>

          <div className="ekyc-reason-box">
            <strong>Lý do AI cần can thiệp:</strong> {item.routingReason}
          </div>

          <div className="ekyc-images-grid">
            <div className="ekyc-img-box">
              <img src={item.frontImageUrl} alt="Mặt trước CCCD" />
              <div className="ekyc-img-label">1. Mặt Trước CCCD</div>
            </div>
            <div className="ekyc-img-box">
              <img src={item.backImageUrl} alt="Mặt sau CCCD" />
              <div className="ekyc-img-label">2. Mặt Sau CCCD</div>
            </div>
            <div className="ekyc-img-box">
              <img src={item.selfieImageUrl} alt="Selfie Chân dung" />
              <div className="ekyc-img-label">3. Chân Dung Selfie</div>
            </div>
          </div>

          <div className="ekyc-action-row">
            <button
              type="button"
              className="btn-ekyc-reject"
              onClick={() => handleReview(item.verificationId, EKYC_DECISION.REJECTED)}
            >
              Từ Chối
            </button>
            <button
              type="button"
              className="btn-ekyc-approve"
              onClick={() => handleReview(item.verificationId, EKYC_DECISION.APPROVED)}
            >
              Phê Duyệt Cấp Tích Xanh
            </button>
          </div>
        </div>
      ))}
    </div>
  );
};

export default EkycVerificationView;

