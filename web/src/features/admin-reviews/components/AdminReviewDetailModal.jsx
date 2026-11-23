import React from 'react';

const AdminReviewDetailModal = ({ review, onClose, onOpenModerate, onUnhide, onUnflag }) => {
  if (!review) return null;

  const renderStars = (rating) => {
    return '★'.repeat(rating || 0) + '☆'.repeat(5 - (rating || 0));
  };

  return (
    <div className="admin-review-modal-backdrop" onClick={onClose}>
      <div className="admin-review-modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="admin-review-modal-header">
          <h3>Chi Tiết Đánh Giá</h3>
          <button className="admin-review-btn-close" onClick={onClose}>×</button>
        </div>

        <div className="admin-review-modal-body">
          {/* Status banner if hidden or flagged */}
          {review.isHidden && (
            <div className="admin-review-alert admin-review-alert-danger">
              <strong>ĐÁNH GIÁ ĐÃ BỊ ẨN</strong>
              <p>Lý do: {review.hiddenReason || 'Không ghi rõ'}</p>
              <small>Người duyệt: {review.hiddenByAdminName || 'Admin'} | Lúc: {new Date(review.hiddenAt).toLocaleString('vi-VN')}</small>
            </div>
          )}

          {review.isFlagged && (
            <div className="admin-review-alert admin-review-alert-warning">
              <strong>ĐÁNH GIÁ ĐANG BỊ GẮN CỜ</strong>
              <p>Cảnh báo: {review.flagReason || 'Nghi vấn sai lệch / spam'}</p>
            </div>
          )}

          {/* Core Info */}
          <div className="admin-review-info-grid">
            <div className="admin-review-info-item">
              <span className="info-label">Ca làm việc:</span>
              <span className="info-value">{review.shiftTitle || 'Ca không định danh'}</span>
            </div>
            <div className="admin-review-info-item">
              <span className="info-label">Sự kiện:</span>
              <span className="info-value">{review.eventTitle || 'Sự kiện hệ thống'}</span>
            </div>
            <div className="admin-review-info-item">
              <span className="info-label">Người đánh giá:</span>
              <span className="info-value">
                {review.reviewerName || 'N/A'} ({review.reviewerRole})
              </span>
            </div>
            <div className="admin-review-info-item">
              <span className="info-label">Người nhận:</span>
              <span className="info-value">
                {review.revieweeName || 'N/A'} ({review.revieweeRole})
              </span>
            </div>
            <div className="admin-review-info-item">
              <span className="info-label">Thời gian gửi:</span>
              <span className="info-value">{new Date(review.createdAt).toLocaleString('vi-VN')}</span>
            </div>
            <div className="admin-review-info-item">
              <span className="info-label">Số sao tổng quan:</span>
              <span className="info-value rating-stars">
                {renderStars(review.rating)} ({review.rating}/5)
              </span>
            </div>
          </div>

          {/* Criteria Breakdown */}
          <div className="admin-review-criteria-box">
            <h4>Chi tiết các tiêu chí chấm điểm</h4>
            <div className="criteria-list">
              {review.criteriaWorkCompletion && <div>Hoàn thành công việc: {review.criteriaWorkCompletion}★</div>}
              {review.criteriaPunctuality && <div>Đúng giờ: {review.criteriaPunctuality}★</div>}
              {review.criteriaProfessionalism && <div>Tác phong chuyên nghiệp: {review.criteriaProfessionalism}★</div>}
              {review.criteriaVenueRules && <div>Nội quy địa điểm: {review.criteriaVenueRules}★</div>}
              {review.criteriaTeamwork && <div>Tinh thần đồng đội: {review.criteriaTeamwork}★</div>}
              {review.criteriaWorkEnvironment && <div>Môi trường làm việc: {review.criteriaWorkEnvironment}★</div>}
              {review.criteriaSupport && <div>Hỗ trợ của BTC: {review.criteriaSupport}★</div>}
              {review.criteriaPaymentTiming && <div>Thanh toán đúng hẹn: {review.criteriaPaymentTiming}★</div>}
            </div>
          </div>

          {/* Comment & Tags */}
          <div className="admin-review-comment-box">
            <h4>Nội dung nhận xét</h4>
            <p className="review-comment-text">"{review.comment || 'Không có nhận xét chi tiết.'}"</p>
            {review.tags && review.tags.length > 0 && (
              <div className="review-tags-wrapper">
                {review.tags.map((tag, idx) => (
                  <span key={idx} className="review-tag-badge">#{tag}</span>
                ))}
              </div>
            )}
          </div>
        </div>

        <div className="admin-review-modal-footer">
          <button className="review-btn review-btn-secondary" onClick={onClose}>Đóng</button>

          {review.isFlagged ? (
            <button className="review-btn review-btn-outline" onClick={() => onUnflag(review.reviewId)}>
              Gỡ cờ
            </button>
          ) : (
            <button className="review-btn review-btn-warning" onClick={() => onOpenModerate('FLAG', review)}>
              Gắn cờ
            </button>
          )}

          {review.isHidden ? (
            <button className="review-btn review-btn-success" onClick={() => onUnhide(review.reviewId)}>
              Khôi phục hiển thị
            </button>
          ) : (
            <button className="review-btn review-btn-danger" onClick={() => onOpenModerate('HIDE', review)}>
              Gỡ / Ẩn đánh giá
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default AdminReviewDetailModal;

