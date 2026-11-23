import React, { useState, useEffect, useCallback } from 'react';
import { adminReviewApi } from '../api/adminReviewApi';
import AdminReviewTable from './AdminReviewTable';
import AdminReviewDetailModal from './AdminReviewDetailModal';
import AdminReviewModerateModal from './AdminReviewModerateModal';
import './AdminReviews.css';

export const AdminReviewView = () => {
  const [reviews, setReviews] = useState([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Filters
  const [ratingFilter, setRatingFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');

  // Modals
  const [selectedReview, setSelectedReview] = useState(null);
  const [moderateModal, setModerateModal] = useState({ open: false, type: 'HIDE', review: null });
  const [actionLoading, setActionLoading] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });

  const loadReviews = useCallback(async () => {
    setLoading(true);
    try {
      const params = {
        page,
        size: 15,
        rating: ratingFilter ? parseInt(ratingFilter, 10) : undefined,
        search: searchTerm ? searchTerm.trim() : undefined,
      };

      if (statusFilter === 'HIDDEN') params.isHidden = true;
      if (statusFilter === 'ACTIVE') params.isHidden = false;
      if (statusFilter === 'FLAGGED') params.isFlagged = true;

      const data = await adminReviewApi.getReviews(params);
      if (data) {
        setReviews(data.content || []);
        setTotalPages(data.totalPages || 0);
        setTotalElements(data.totalElements || 0);
      }
    } catch (err) {
      console.error('Lỗi khi tải danh sách reviews:', err);
      setMessage({ type: 'error', text: 'Không thể tải danh sách đánh giá. Vui lòng thử lại!' });
    } finally {
      setLoading(false);
    }
  }, [page, ratingFilter, statusFilter, searchTerm]);

  useEffect(() => {
    loadReviews();
  }, [loadReviews]);

  const handleOpenModerate = (type, review) => {
    setModerateModal({ open: true, type, review });
  };

  const handleModerateSubmit = async (reviewId, reason) => {
    setActionLoading(true);
    try {
      if (moderateModal.type === 'HIDE') {
        await adminReviewApi.hideReview(reviewId, reason);
        setMessage({ type: 'success', text: 'Đã ẩn đánh giá và tự động tính lại điểm số matching thành công!' });
      } else {
        await adminReviewApi.flagReview(reviewId, reason);
        setMessage({ type: 'success', text: 'Đã gắn cờ cảnh báo đánh giá thành công!' });
      }
      setModerateModal({ open: false, type: 'HIDE', review: null });
      setSelectedReview(null);
      loadReviews();
    } catch (err) {
      setMessage({ type: 'error', text: err?.response?.data?.message || 'Có lỗi xảy ra khi thực hiện kiểm duyệt.' });
    } finally {
      setActionLoading(false);
    }
  };

  const handleUnhide = async (reviewId) => {
    if (!window.confirm('Bạn có chắc chắn muốn khôi phục hiển thị cho đánh giá này? Điểm matching sẽ được tính toán lại.')) return;
    try {
      await adminReviewApi.unhideReview(reviewId);
      setMessage({ type: 'success', text: 'Đã khôi phục hiển thị đánh giá thành công!' });
      setSelectedReview(null);
      loadReviews();
    } catch (err) {
      setMessage({ type: 'error', text: err?.response?.data?.message || 'Không thể khôi phục đánh giá.' });
    }
  };

  const handleUnflag = async (reviewId) => {
    try {
      await adminReviewApi.unflagReview(reviewId);
      setMessage({ type: 'success', text: 'Đã gỡ cờ cảnh báo thành công!' });
      setSelectedReview(null);
      loadReviews();
    } catch (err) {
      setMessage({ type: 'error', text: err?.response?.data?.message || 'Không thể gỡ cờ.' });
    }
  };

  return (
    <div className="admin-reviews-container">
      {/* Thông báo Alert */}
      {message.text && (
        <div className={`admin-review-alert-toast ${message.type}`}>
          <span>{message.text}</span>
          <button onClick={() => setMessage({ type: '', text: '' })}>×</button>
        </div>
      )}

      {/* KPI Cards */}
      <div className="admin-review-kpis">
        <div className="kpi-card">
          <div className="kpi-val">{totalElements}</div>
          <div className="kpi-lbl">Tổng Đánh Giá Khớp Lọc</div>
        </div>
        <div className="kpi-card" onClick={() => { setRatingFilter('1'); setPage(0); }}>
          <div className="kpi-val text-danger">1★ - 2★</div>
          <div className="kpi-lbl">Đánh Giá Nguy Cơ Phạt Cao</div>
        </div>
        <div className="kpi-card" onClick={() => { setStatusFilter('HIDDEN'); setPage(0); }}>
          <div className="kpi-val text-warning">Đã Ẩn</div>
          <div className="kpi-lbl">Đánh Giá Đã Gỡ Bỏ</div>
        </div>
        <div className="kpi-card" onClick={() => { setStatusFilter('FLAGGED'); setPage(0); }}>
          <div className="kpi-val text-info">Gắn Cờ</div>
          <div className="kpi-lbl">Cần Theo Dõi / Bơm Điểm</div>
        </div>
      </div>

      {/* Thanh công cụ lọc */}
      <div className="admin-review-filters">
        <div className="filter-group">
          <label>Số sao:</label>
          <select value={ratingFilter} onChange={(e) => { setRatingFilter(e.target.value); setPage(0); }}>
            <option value="">Tất cả số sao</option>
            <option value="5">5★ - Xuất sắc</option>
            <option value="4">4★ - Tốt</option>
            <option value="3">3★ - Trung bình</option>
            <option value="2">2★ - Kém</option>
            <option value="1">1★ - Rất kém</option>
          </select>
        </div>

        <div className="filter-group">
          <label>Trạng thái:</label>
          <select value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}>
            <option value="ALL">Tất cả trạng thái</option>
            <option value="ACTIVE">Đang hiển thị bình thường</option>
            <option value="HIDDEN">Đã ẩn / Đã gỡ bỏ</option>
            <option value="FLAGGED">Đang bị gắn cờ cảnh báo</option>
          </select>
        </div>

        <div className="filter-group search-group">
          <label>Tìm kiếm:</label>
          <input
            type="text"
            placeholder="Tìm theo nội dung nhận xét..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && loadReviews()}
          />
        </div>

        <button className="review-btn review-btn-primary" onClick={loadReviews}>
          Lọc dữ liệu
        </button>
      </div>

      {/* Bảng dữ liệu */}
      <AdminReviewTable
        reviews={reviews}
        loading={loading}
        onViewDetail={(r) => setSelectedReview(r)}
        onOpenModerate={handleOpenModerate}
        onUnhide={handleUnhide}
        onUnflag={handleUnflag}
      />

      {/* Phân trang */}
      {totalPages > 1 && (
        <div className="admin-review-pagination">
          <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            ‹ Trang trước
          </button>
          <span>Trang {page + 1} / {totalPages}</span>
          <button disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}>
            Trang sau ›
          </button>
        </div>
      )}

      {/* Modals */}
      {selectedReview && (
        <AdminReviewDetailModal
          review={selectedReview}
          onClose={() => setSelectedReview(null)}
          onOpenModerate={handleOpenModerate}
          onUnhide={handleUnhide}
          onUnflag={handleUnflag}
        />
      )}

      {moderateModal.open && (
        <AdminReviewModerateModal
          actionType={moderateModal.type}
          review={moderateModal.review}
          loading={actionLoading}
          onClose={() => setModerateModal({ open: false, type: 'HIDE', review: null })}
          onSubmit={handleModerateSubmit}
        />
      )}
    </div>
  );
};

