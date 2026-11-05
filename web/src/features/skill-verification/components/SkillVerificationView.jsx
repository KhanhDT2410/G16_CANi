import { useSkillVerification } from '../hooks/useSkillVerification';
import './SkillVerification.css';

export const SkillVerificationView = ({ onCountChange }) => {
  const { skillQueue, loading, handleReview } = useSkillVerification({ onCountChange });

  if (loading) {
    return (
      <div className="skill-card-list">
        <div style={{ padding: '1rem 0' }}>
          <div className="skeleton" style={{ height: '320px', borderRadius: '14px', marginBottom: '1rem' }} />
          <div className="skeleton" style={{ height: '320px', borderRadius: '14px' }} />
        </div>
      </div>
    );
  }

  if (skillQueue.length === 0) {
    return (
      <div className="skill-card-list">
        <div className="skill-empty-state">
          <p style={{ color: '#059669', fontWeight: 600, margin: 0 }}>
            Hiện tại không còn hồ sơ chứng chỉ kỹ năng nào đang chờ duyệt.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="skill-card-list">
      {skillQueue.map((item) => (
        <div key={item.id} className="skill-verif-card animate-fade-in-up">
          <div className="skill-card-top">
            <div>
              <h3 style={{ margin: '0 0 0.25rem 0', fontSize: '1.2rem', fontWeight: 700 }}>
                {item.workerName || 'Nhân sự Worker'}
              </h3>
              <span style={{ fontSize: '0.85rem', color: '#6b7280' }}>
                Kỹ năng thẩm định: <strong style={{ color: '#2563eb' }}>{item.skillName || item.jobType}</strong> | Cấp bởi: {item.institution || 'Cơ sở đào tạo'} ({item.issueDate || 'N/A'})
              </span>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: '0.85rem', fontWeight: 700, color: '#f59e0b' }}>
                Độ tin cậy OCR: {item.overallConfidence ? (item.overallConfidence > 1 ? item.overallConfidence : (item.overallConfidence * 100).toFixed(0)) : 0}%
              </div>
              <div style={{ fontSize: '0.75rem', color: '#6b7280' }}>
                Tier 2: AI Document Review
              </div>
            </div>
          </div>

          <div className="skill-reason-box">
            <strong>Lý do AI chuyển duyệt:</strong> {item.routingReason || 'Tài liệu cần Admin/CSKH đối soát thủ công.'}
          </div>

          <div className="skill-doc-grid">
            <div className="skill-doc-preview">
              {item.documentUrl ? (
                <img
                  src={item.documentUrl}
                  alt="Chứng chỉ kỹ năng"
                />
              ) : (
                <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#9ca3af' }}>
                  Không có hình ảnh đính kèm
                </div>
              )}
            </div>
            <div className="skill-doc-info">
              <p style={{ margin: '0 0 0.5rem 0' }}><strong>Đơn vị cấp bằng:</strong> {item.institution || 'Chưa xác định'}</p>
              <p style={{ margin: '0 0 0.5rem 0' }}><strong>Ngày phát hành:</strong> {item.issueDate || 'N/A'}</p>
              <p style={{ margin: '0 0 0.5rem 0' }}><strong>Dấu mộc / Chữ ký:</strong> {item.hasSealOrSignature ? 'Có xác nhận hợp lệ' : 'Chưa nhận diện được'}</p>
              <div className="skill-sp-badge">
                Điểm cộng khi duyệt: +25 SP_verify (Kích hoạt Tier 2 AI-Verified)
              </div>
            </div>
          </div>

          <div className="skill-action-row">
            <button
              type="button"
              className="btn-skill-reject"
              onClick={() => handleReview(item.id, false)}
            >
              Từ chối
            </button>
            <button
              type="button"
              className="btn-skill-approve"
              onClick={() => handleReview(item.id, true)}
            >
              Phê duyệt (+25 SP)
            </button>
          </div>
        </div>
      ))}
    </div>
  );
};

export default SkillVerificationView;

