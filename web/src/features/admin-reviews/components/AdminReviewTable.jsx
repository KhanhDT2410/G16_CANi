import React from 'react';

const formatRole = (role) => {
  if (!role) return '—';
  const upper = String(role).toUpperCase();
  if (upper === 'EVENT_ORGANIZER' || upper === 'EO') return 'Nhà tổ chức';
  if (upper === 'WORKER') return 'Người lao động';
  return role;
};

const AdminReviewTable = ({
  reviews,
  loading,
  onViewDetail,
  onOpenModerate,
  onUnhide,
  onUnflag
}) => {
  const renderStars = (rating) => {
    return '★'.repeat(rating || 0) + '☆'.repeat(5 - (rating || 0));
  };

  const getStatusBadge = (review) => {
    if (review.isHidden) {
      return <span className="status-badge status-hidden">Đã ẩn</span>;
    }
    if (review.isFlagged) {
      return <span className="status-badge status-flagged">Bị gắn cờ</span>;
    }
    return <span className="status-badge status-active">Hiển thị</span>;
  };

  if (loading) {
    return (
      <div className="admin-review-loading">
        <div className="spinner" />
        <p>Đang tải danh sách đánh giá...</p>
      </div>
    );
  }

  if (!reviews || reviews.length === 0) {
    return (
      <div className="admin-review-empty">
        <p>Không tìm thấy đánh giá nào phù hợp với bộ lọc.</p>
      </div>
    );
  }

  return (
    <div className="admin-review-table-responsive">
      <table className="admin-review-table">
        <thead>
          <tr>
            <th>Thời gian</th>
            <th>Ca / Sự kiện</th>
            <th>Người đánh giá</th>
            <th>Người nhận</th>
            <th>Số sao</th>
            <th>Nhận xét</th>
            <th>Trạng thái</th>
            <th className="text-right">Hành động</th>
          </tr>
        </thead>
        <tbody>
          {reviews.map((r) => (
            <tr key={r.reviewId} className={r.isHidden ? 'row-hidden' : (r.isFlagged ? 'row-flagged' : '')}>
              <td className="cell-date">
                {new Date(r.createdAt).toLocaleDateString('vi-VN')}
                <br />
                <small className="text-muted">{new Date(r.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}</small>
              </td>
              <td>
                <div className="cell-shift-title">{r.shiftTitle || 'Ca không tên'}</div>
                <div className="cell-event-title text-muted">{r.eventTitle || 'Sự kiện hệ thống'}</div>
              </td>
              <td>
                <div className="user-name">{r.reviewerName || 'N/A'}</div>
                <span className={`role-badge role-${r.reviewerRole?.toLowerCase()}`}>{formatRole(r.reviewerRole)}</span>
              </td>
              <td>
                <div className="user-name">{r.revieweeName || 'N/A'}</div>
                <span className={`role-badge role-${r.revieweeRole?.toLowerCase()}`}>{formatRole(r.revieweeRole)}</span>
              </td>
              <td>
                <div className={`rating-chip rating-${r.rating}`}>
                  <span className="rating-num">{r.rating}★</span>
                  <span className="rating-stars-small">{renderStars(r.rating)}</span>
                </div>
              </td>
              <td className="cell-comment">
                <p className="comment-preview" title={r.comment}>
                  {r.comment ? (r.comment.length > 60 ? r.comment.substring(0, 60) + '...' : r.comment) : <em className="text-muted">Không có nhận xét</em>}
                </p>
                {r.hiddenReason && (
                  <div>
                    <span className="review-reason-tag danger" title={r.hiddenReason}>
                      Lý do ẩn: {r.hiddenReason}
                    </span>
                  </div>
                )}
                {r.flagReason && (
                  <div>
                    <span className="review-reason-tag warning" title={r.flagReason}>
                      Cảnh báo: {r.flagReason}
                    </span>
                  </div>
                )}
              </td>
              <td>{getStatusBadge(r)}</td>
              <td className="text-right action-cell">
                <button
                  className="table-btn-icon"
                  title="Xem chi tiết"
                  onClick={() => onViewDetail(r)}
                >
                  Chi tiết
                </button>

                {r.isHidden ? (
                  <button
                    className="table-btn-icon text-success"
                    title="Khôi phục hiển thị"
                    onClick={() => onUnhide(r.reviewId)}
                  >
                    Khôi phục
                  </button>
                ) : (
                  <button
                    className="table-btn-icon text-danger"
                    title="Gỡ / Ẩn đánh giá"
                    onClick={() => onOpenModerate('HIDE', r)}
                  >
                    Ẩn
                  </button>
                )}

                {r.isFlagged ? (
                  <button
                    className="table-btn-icon text-secondary"
                    title="Gỡ cờ cảnh báo"
                    onClick={() => onUnflag(r.reviewId)}
                  >
                    Gỡ cờ
                  </button>
                ) : (
                  <button
                    className="table-btn-icon text-warning"
                    title="Gắn cờ nghi vấn"
                    onClick={() => onOpenModerate('FLAG', r)}
                  >
                    Gắn cờ
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default AdminReviewTable;

