import { useRsAppeals } from '../hooks/useRsAppeals';
import { RsAppealsTable } from './RsAppealsTable';
import { RsAppealDetailModal } from './RsAppealDetailModal';
import { RsAppealReviewModal } from './RsAppealReviewModal';
import './RsAppeals.css';

export const RsAppealsView = () => {
  const {
    appeals,
    totalElements,
    currentPage,
    setCurrentPage,
    pageSize,
    statusFilter,
    setStatusFilter,
    selectedAppeal,
    detailModalOpen,
    setDetailModalOpen,
    reviewModalOpen,
    setReviewModalOpen,
    loading,
    submitting,
    handleOpenDetail,
    handleOpenReview,
    handleReviewDecision,
    fetchAppeals,
  } = useRsAppeals();

  return (
    <div className="appeals-container">
      {/* Header bar */}
      <div className="appeals-header-bar">
        <div>
          <h2 className="appeals-title">Hàng Đợi Kháng Cáo Điểm Uy Tín</h2>
          <p className="appeals-sub">
            Xem xét các trường hợp người lao động khiếu nại điểm phạt do lý do bất khả kháng
          </p>
        </div>

        <div className="appeals-actions">
          <select
            className="appeals-filter-select"
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value);
              setCurrentPage(0);
            }}
          >
            <option value="">Tất cả trạng thái</option>
            <option value="SUBMITTED">Chờ xét duyệt</option>
            <option value="UNDER_REVIEW">Đang xem xét</option>
            <option value="APPROVED">Đã chấp thuận (Hoàn điểm)</option>
            <option value="REJECTED">Đã từ chối</option>
          </select>

          <button className="btn-appeals-refresh" onClick={fetchAppeals} title="Làm mới">
            ↻ Làm Mới
          </button>
        </div>
      </div>

      {/* Main Table */}
      {loading ? (
        <div className="appeals-loading-box">
          <div className="appeals-spinner" />
          <p>Đang tải danh sách kháng cáo điểm uy tín...</p>
        </div>
      ) : (
        <RsAppealsTable
          appeals={appeals}
          totalElements={totalElements}
          currentPage={currentPage}
          setCurrentPage={setCurrentPage}
          pageSize={pageSize}
          onViewDetail={handleOpenDetail}
          onReview={handleOpenReview}
        />
      )}

      {/* Detail Modal */}
      <RsAppealDetailModal
        appeal={selectedAppeal}
        isOpen={detailModalOpen}
        onClose={() => setDetailModalOpen(false)}
        onReview={handleOpenReview}
      />

      {/* Review Modal */}
      <RsAppealReviewModal
        appeal={selectedAppeal}
        isOpen={reviewModalOpen}
        onClose={() => setReviewModalOpen(false)}
        onSubmit={handleReviewDecision}
        submitting={submitting}
      />
    </div>
  );
};

