import React, { useState } from 'react';

const QUICK_REASONS = [
  'Đánh giá oan uổng / sai sự thật gây tổn hại chỉ số matching',
  'Ngôn từ xúc phạm, thô tục hoặc công kích cá nhân',
  'Spam đánh giá chéo giữa các tài khoản để kéo điểm ảo',
  'Tranh chấp ca làm việc đã có quyết định phân xử chính thức',
];

const AdminReviewModerateModal = ({ actionType, review, onClose, onSubmit, loading }) => {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

  if (!review) return null;

  const isHide = actionType === 'HIDE';
  const title = isHide ? 'Xác Nhận Ẩn / Gỡ Bỏ Đánh Giá' : 'Gắn Cờ Cảnh Báo Đánh Giá';

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!reason.trim()) {
      setError('Vui lòng nhập lý do kiểm duyệt.');
      return;
    }
    onSubmit(review.reviewId, reason.trim());
  };

  return (
    <div className="admin-review-modal-backdrop" onClick={onClose}>
      <div className="admin-review-modal-card admin-review-modal-sm" onClick={(e) => e.stopPropagation()}>
        <div className="admin-review-modal-header">
          <h3>{title}</h3>
          <button className="admin-review-btn-close" onClick={onClose}>✕</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="admin-review-modal-body">
            {isHide && (
              <div className="admin-review-notice-box">
                <p>
                  <strong>Lưu ý hệ thống:</strong> Khi gỡ đánh giá này, hệ thống sẽ <strong>tự động tính lại điểm trung bình</strong> cho đối tượng nhận và <strong>hoàn trả điểm Uy tín (RS)</strong> nếu là review 1-2★.
                </p>
              </div>
            )}

            <div className="form-group">
              <label className="form-label">Chọn nhanh lý do mẫu:</label>
              <div className="quick-reasons-container">
                {QUICK_REASONS.map((r, idx) => (
                  <button
                    type="button"
                    key={idx}
                    className="quick-reason-btn"
                    onClick={() => {
                      setReason(r);
                      setError('');
                    }}
                  >
                    {r}
                  </button>
                ))}
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Lý do kiểm duyệt chi tiết (Bắt buộc):</label>
              <textarea
                className={`form-control ${error ? 'is-invalid' : ''}`}
                rows={3}
                placeholder="Nhập lý do cụ thể để lưu lại vết kiểm toán..."
                value={reason}
                onChange={(e) => {
                  setReason(e.target.value);
                  if (error) setError('');
                }}
              />
              {error && <div className="form-error-text">{error}</div>}
            </div>
          </div>

          <div className="admin-review-modal-footer">
            <button type="button" className="review-btn review-btn-secondary" onClick={onClose} disabled={loading}>
              Hủy
            </button>
            <button
              type="submit"
              className={`review-btn ${isHide ? 'review-btn-danger' : 'review-btn-warning'}`}
              disabled={loading}
            >
              {loading ? 'Đang xử lý...' : (isHide ? 'Xác Nhận Ẩn' : 'Gắn Cờ')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default AdminReviewModerateModal;

