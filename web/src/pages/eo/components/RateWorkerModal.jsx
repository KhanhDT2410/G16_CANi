import React, { useState } from 'react';

const COMMON_TAGS = [
  'Đúng giờ',
  'Nhiệt tình',
  'Giao tiếp tốt',
  'Kỹ năng chuyên môn tốt',
  'Tác phong chuẩn mực',
  'Hợp tác tốt',
  'Chăm chỉ',
  'Cần cải thiện',
];

const RateWorkerModal = ({
  isOpen,
  onClose,
  assignment,
  onSubmitReview,
  submitting,
}) => {
  const [rating, setRating] = useState(5);
  const [criteriaWorkCompletion, setCriteriaWorkCompletion] = useState(5);
  const [criteriaPunctuality, setCriteriaPunctuality] = useState(5);
  const [criteriaProfessionalism, setCriteriaProfessionalism] = useState(5);
  const [comment, setComment] = useState('');
  const [selectedTags, setSelectedTags] = useState(['Đúng giờ', 'Nhiệt tình']);

  if (!isOpen || !assignment) return null;

  const toggleTag = (tag) => {
    if (selectedTags.includes(tag)) {
      setSelectedTags(selectedTags.filter((t) => t !== tag));
    } else {
      setSelectedTags([...selectedTags, tag]);
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onSubmitReview({
      assignmentId: assignment.assignmentId,
      rating,
      criteriaWorkCompletion,
      criteriaPunctuality,
      criteriaProfessionalism,
      comment,
      tags: selectedTags,
    });
  };

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box" style={{ maxWidth: 520 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h3 style={{ margin: 0 }}>⭐ Đánh Giá Nhân Sự Sau Ca Làm</h3>
          <button type="button" onClick={onClose} style={{ background: 'none', border: 'none', fontSize: '1.2rem', cursor: 'pointer', color: '#64748b' }}>✕</button>
        </div>

        <div style={{ background: '#f8fafc', padding: '0.8rem 1rem', borderRadius: 8, marginBottom: '1.2rem', border: '1px solid #e2e8f0' }}>
          <div style={{ fontWeight: 600, color: '#1e293b' }}>{assignment.workerName || 'Worker'}</div>
          <div style={{ fontSize: '0.85rem', color: '#64748b', marginTop: 2 }}>
            SĐT: {assignment.workerPhone || 'Chưa cập nhật'} • Điểm uy tín: <strong style={{ color: '#2563eb' }}>{assignment.workerReliabilityScore || 100}/100</strong>
          </div>
        </div>

        <form onSubmit={handleSubmit}>
          {/* Tổng thể rating 1-5 sao */}
          <div style={{ textAlign: 'center', marginBottom: '1.2rem' }}>
            <label style={{ display: 'block', fontSize: '0.9rem', color: '#475569', marginBottom: 6, fontWeight: 500 }}>
              Mức độ hài lòng tổng thể
            </label>
            <div style={{ display: 'inline-flex', gap: 8, fontSize: '2rem', cursor: 'pointer' }}>
              {[1, 2, 3, 4, 5].map((star) => (
                <span
                  key={star}
                  onClick={() => setRating(star)}
                  style={{ color: star <= rating ? '#eab308' : '#cbd5e1', transition: 'color 0.15s' }}
                >
                  ★
                </span>
              ))}
            </div>
            <div style={{ fontSize: '0.85rem', color: '#2563eb', fontWeight: 600, marginTop: 4 }}>
              {rating === 5 && 'Tuyệt vời - Rất hài lòng'}
              {rating === 4 && 'Tốt - Đạt yêu cầu'}
              {rating === 3 && 'Bình thường - Tạm ổn'}
              {rating === 2 && 'Cần cải thiện'}
              {rating === 1 && 'Kém - Không hài lòng'}
            </div>
          </div>

          {/* Chi tiết tiêu chí */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10, marginBottom: '1.2rem' }}>
            <div style={{ background: '#f1f5f9', padding: '0.6rem', borderRadius: 6, textAlign: 'center' }}>
              <div style={{ fontSize: '0.75rem', color: '#475569', fontWeight: 600 }}>Hoàn thành việc</div>
              <select
                className="eo-form-input"
                style={{ marginTop: 4, padding: '0.3rem', fontSize: '0.85rem' }}
                value={criteriaWorkCompletion}
                onChange={(e) => setCriteriaWorkCompletion(Number(e.target.value))}
              >
                {[5, 4, 3, 2, 1].map((n) => (
                  <option key={n} value={n}>{n} sao</option>
                ))}
              </select>
            </div>

            <div style={{ background: '#f1f5f9', padding: '0.6rem', borderRadius: 6, textAlign: 'center' }}>
              <div style={{ fontSize: '0.75rem', color: '#475569', fontWeight: 600 }}>Đúng giờ</div>
              <select
                className="eo-form-input"
                style={{ marginTop: 4, padding: '0.3rem', fontSize: '0.85rem' }}
                value={criteriaPunctuality}
                onChange={(e) => setCriteriaPunctuality(Number(e.target.value))}
              >
                {[5, 4, 3, 2, 1].map((n) => (
                  <option key={n} value={n}>{n} sao</option>
                ))}
              </select>
            </div>

            <div style={{ background: '#f1f5f9', padding: '0.6rem', borderRadius: 6, textAlign: 'center' }}>
              <div style={{ fontSize: '0.75rem', color: '#475569', fontWeight: 600 }}>Tác phong</div>
              <select
                className="eo-form-input"
                style={{ marginTop: 4, padding: '0.3rem', fontSize: '0.85rem' }}
                value={criteriaProfessionalism}
                onChange={(e) => setCriteriaProfessionalism(Number(e.target.value))}
              >
                {[5, 4, 3, 2, 1].map((n) => (
                  <option key={n} value={n}>{n} sao</option>
                ))}
              </select>
            </div>
          </div>

          {/* Quick tags */}
          <div style={{ marginBottom: '1.2rem' }}>
            <label style={{ display: 'block', fontSize: '0.85rem', color: '#475569', marginBottom: 6, fontWeight: 500 }}>
              Nhãn nhận xét nhanh:
            </label>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {COMMON_TAGS.map((tag) => {
                const active = selectedTags.includes(tag);
                return (
                  <button
                    key={tag}
                    type="button"
                    onClick={() => toggleTag(tag)}
                    style={{
                      border: active ? '1px solid #2563eb' : '1px solid #cbd5e1',
                      background: active ? '#eff6ff' : '#ffffff',
                      color: active ? '#1d4ed8' : '#475569',
                      padding: '4px 10px',
                      borderRadius: 16,
                      fontSize: '0.8rem',
                      cursor: 'pointer',
                      fontWeight: active ? 600 : 400,
                    }}
                  >
                    {active ? '✓ ' : '+ '}{tag}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Nhận xét văn bản */}
          <div className="eo-form-group">
            <label>Nhận xét chi tiết (Được lưu vào hồ sơ nhân sự)</label>
            <textarea
              rows="3"
              className="eo-form-textarea"
              placeholder="Nhận xét về thái độ, kỹ năng thực tế của Worker trong ca làm..."
              value={comment}
              onChange={(e) => setComment(e.target.value)}
            />
          </div>

          <div className="eo-form-actions">
            <button type="button" className="eo-btn-cancel" onClick={onClose}>
              Đóng
            </button>
            <button type="submit" className="eo-btn-submit" disabled={submitting}>
              {submitting ? 'Đang lưu đánh giá...' : '⭐ Gửi Đánh Giá'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default RateWorkerModal;

