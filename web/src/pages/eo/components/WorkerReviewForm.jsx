import React, { useState, useEffect } from 'react';

export const COMMON_REVIEW_TAGS = [
  'Đúng giờ',
  'Nhiệt tình',
  'Giao tiếp tốt',
  'Kỹ năng chuyên môn tốt',
  'Tác phong chuẩn mực',
  'Hợp tác tốt',
  'Chăm chỉ',
  'Cần cải thiện',
];

const RATING_LABELS = {
  5: 'Tuyệt vời - Rất hài lòng',
  4: 'Tốt - Đạt yêu cầu',
  3: 'Bình thường - Tạm ổn',
  2: 'Cần cải thiện',
  1: 'Kém - Không hài lòng',
};

/**
 * Biểu mẫu đánh giá nhân sự dùng chung cho chế độ nhúng trực tiếp (inline) và hộp thoại.
 */
export default function WorkerReviewForm({
  assignment,
  onSubmitReview,
  onCancel,
  submitting = false,
  layout = 'inline', // 'inline' | 'modal'
}) {
  const [rating, setRating] = useState(5);
  const [criteriaWorkCompletion, setCriteriaWorkCompletion] = useState(5);
  const [criteriaPunctuality, setCriteriaPunctuality] = useState(5);
  const [criteriaProfessionalism, setCriteriaProfessionalism] = useState(5);
  const [comment, setComment] = useState('');
  const [selectedTags, setSelectedTags] = useState(['Đúng giờ', 'Nhiệt tình']);

  useEffect(() => {
    if (assignment?.assignmentId) {
      setRating(5);
      setCriteriaWorkCompletion(5);
      setCriteriaPunctuality(5);
      setCriteriaProfessionalism(5);
      setComment('');
      setSelectedTags(['Đúng giờ', 'Nhiệt tình']);
    }
  }, [assignment?.assignmentId]);

  if (!assignment) return null;

  const isInline = layout === 'inline';

  const toggleTag = (tag) => {
    setSelectedTags((prev) =>
      prev.includes(tag) ? prev.filter((t) => t !== tag) : [...prev, tag]
    );
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

  const criteriaFields = [
    { label: 'Hoàn thành việc', value: criteriaWorkCompletion, setter: setCriteriaWorkCompletion },
    { label: 'Đúng giờ', value: criteriaPunctuality, setter: setCriteriaPunctuality },
    { label: 'Tác phong', value: criteriaProfessionalism, setter: setCriteriaProfessionalism },
  ];

  return (
    <form onSubmit={handleSubmit}>
      <div
        style={
          isInline
            ? {
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
                gap: '1rem',
                alignItems: 'start',
              }
            : undefined
        }
      >
        {/* Cột trái (hoặc trên): Điểm tổng thể & 3 tiêu chí */}
        <div>
          <div style={{ textAlign: isInline ? 'left' : 'center', marginBottom: '0.9rem' }}>
            <label
              style={{
                display: 'block',
                fontSize: '0.84rem',
                color: '#334155',
                marginBottom: 4,
                fontWeight: 700,
              }}
            >
              Mức độ hài lòng tổng thể
            </label>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
              <div style={{ display: 'inline-flex', gap: 6, fontSize: isInline ? '1.6rem' : '2rem', cursor: 'pointer', lineHeight: 1 }}>
                {[1, 2, 3, 4, 5].map((star) => (
                  <button
                    key={star}
                    type="button"
                    onClick={() => setRating(star)}
                    aria-label={`${star} sao`}
                    style={{
                      background: 'none',
                      border: 'none',
                      padding: 0,
                      fontSize: 'inherit',
                      lineHeight: 1,
                      cursor: 'pointer',
                      color: star <= rating ? '#eab308' : '#cbd5e1',
                      transition: 'color 0.15s',
                    }}
                  >
                    ★
                  </button>
                ))}
              </div>
              <span style={{ fontSize: '0.82rem', color: '#2563eb', fontWeight: 600 }}>
                {RATING_LABELS[rating]}
              </span>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8, marginBottom: isInline ? 0 : '1.1rem' }}>
            {criteriaFields.map((field) => (
              <div
                key={field.label}
                style={{
                  background: '#ffffff',
                  border: '1px solid #e2e8f0',
                  padding: '0.5rem',
                  borderRadius: 8,
                  textAlign: 'center',
                }}
              >
                <div style={{ fontSize: '0.73rem', color: '#475569', fontWeight: 600 }}>{field.label}</div>
                <select
                  className="eo-form-input"
                  style={{ marginTop: 4, padding: '0.28rem 0.4rem', fontSize: '0.82rem' }}
                  value={field.value}
                  onChange={(e) => field.setter(Number(e.target.value))}
                >
                  {[5, 4, 3, 2, 1].map((n) => (
                    <option key={n} value={n}>{n} sao</option>
                  ))}
                </select>
              </div>
            ))}
          </div>
        </div>

        {/* Cột phải (hoặc dưới): Nhãn nhận xét nhanh & nhận xét chi tiết */}
        <div>
          <div style={{ marginBottom: '0.85rem' }}>
            <label
              style={{
                display: 'block',
                fontSize: '0.82rem',
                color: '#334155',
                marginBottom: 6,
                fontWeight: 700,
              }}
            >
              Nhãn nhận xét nhanh:
            </label>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {COMMON_REVIEW_TAGS.map((tag) => {
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
                      padding: '3px 10px',
                      borderRadius: 16,
                      fontSize: '0.78rem',
                      cursor: 'pointer',
                      fontWeight: active ? 600 : 400,
                    }}
                  >
                    {active ? '• ' : '+ '}{tag}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="eo-form-group" style={{ marginBottom: 0 }}>
            <label style={{ fontSize: '0.82rem', fontWeight: 700 }}>
              Nhận xét chi tiết (Được lưu vào hồ sơ nhân sự)
            </label>
            <textarea
              rows={isInline ? 2 : 3}
              className="eo-form-textarea"
              placeholder="Nhận xét về thái độ, kỹ năng thực tế của nhân sự trong ca làm..."
              value={comment}
              onChange={(e) => setComment(e.target.value)}
            />
          </div>
        </div>
      </div>

      <div
        className={isInline ? undefined : 'eo-modal-footer'}
        style={
          isInline
            ? {
                display: 'flex',
                justifyContent: 'flex-end',
                gap: '0.6rem',
                marginTop: '0.85rem',
                paddingTop: '0.75rem',
                borderTop: '1px solid #e2e8f0',
              }
            : { marginTop: '1rem' }
        }
      >
        <button type="button" className="eo-btn-secondary" onClick={onCancel}>
          {isInline ? 'Đóng' : 'Hủy Bỏ'}
        </button>
        <button type="submit" className="eo-btn-primary" disabled={submitting}>
          {submitting ? 'Đang lưu đánh giá...' : 'Gửi Đánh Giá'}
        </button>
      </div>
    </form>
  );
}
