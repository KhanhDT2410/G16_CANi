import React from 'react';

/**
 * WorkerReviewsSection: Hiển thị nhận xét và đánh giá 5 tiêu chí thực tế từ các EO khác
 * Tiêu chuẩn < 200 dòng theo AGENTS.md
 */
export const WorkerReviewsSection = ({ reviews = [] }) => {
  if (!reviews || reviews.length === 0) {
    return (
      <div style={{
        padding: '2rem 1rem',
        textAlign: 'center',
        background: '#f8fafc',
        borderRadius: 10,
        border: '1px dashed #cbd5e1',
        color: '#64748b',
        fontSize: '0.85rem',
      }}>
        Chưa có đánh giá nào từ các Nhà tổ chức sự kiện trước đó.
      </div>
    );
  }

  const formatDate = (isoStr) => {
    if (!isoStr) return '';
    try {
      return new Date(isoStr).toLocaleDateString('vi-VN', {
        day: '2-digit', month: '2-digit', year: 'numeric',
      });
    } catch {
      return '';
    }
  };

  return (
    <div className="reviews-list-container">
      {reviews.map((rev) => {
        const stars = '★'.repeat(Math.max(1, Math.min(5, rev.rating || 5)));

        return (
          <div key={rev.reviewId || Math.random()} className="review-card">
            <div className="review-card-top">
              <div>
                <span className="review-eo-name">
                  {rev.companyName ? `${rev.companyName} (${rev.reviewerName})` : rev.reviewerName}
                </span>
                {rev.createdAt && (
                  <span style={{ fontSize: '0.75rem', color: '#94a3b8', marginLeft: 8 }}>
                    {formatDate(rev.createdAt)}
                  </span>
                )}
              </div>
              <span className="review-rating-stars">{stars}</span>
            </div>

            {rev.comment && (
              <p className="review-comment-text">"{rev.comment}"</p>
            )}

            {/* 5 Tiêu chí con */}
            <div className="review-criteria-chips">
              {rev.criteriaWorkCompletion && (
                <span className="criteria-mini-item">Hoàn thành: {rev.criteriaWorkCompletion}/5</span>
              )}
              {rev.criteriaPunctuality && (
                <span className="criteria-mini-item">Đúng giờ: {rev.criteriaPunctuality}/5</span>
              )}
              {rev.criteriaProfessionalism && (
                <span className="criteria-mini-item">Chuyên nghiệp: {rev.criteriaProfessionalism}/5</span>
              )}
              {rev.criteriaTeamwork && (
                <span className="criteria-mini-item">Làm việc nhóm: {rev.criteriaTeamwork}/5</span>
              )}
              {rev.criteriaVenueRules && (
                <span className="criteria-mini-item">Nội quy: {rev.criteriaVenueRules}/5</span>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
};

export default WorkerReviewsSection;
