/**
 * Thanh tiêu đề và chuyển đổi chế độ xem cho Trung tâm Tranh chấp.
 * Tuân thủ < 200 dòng theo AGENTS.md.
 */
export const DisputeTopHeader = ({
  viewMode,
  setViewMode,
  onRefresh,
  isArbitrator,
  onOpenCreate,
}) => {
  return (
    <div className="disputes-header">
      <div className="view-mode-toggle">
        <button
          type="button"
          className={`toggle-btn ${viewMode === 'SPLIT' ? 'active' : ''}`}
          onClick={() => setViewMode('SPLIT')}
        >
          Không gian hội thoại
        </button>
        <button
          type="button"
          className={`toggle-btn ${viewMode === 'TABLE' ? 'active' : ''}`}
          onClick={() => setViewMode('TABLE')}
        >
          Bảng hàng đợi
        </button>
      </div>
      <div className="disputes-actions-group">
        <button type="button" className="dispute-btn-secondary" onClick={onRefresh}>
          Làm mới
        </button>
        {!isArbitrator && (
          <button type="button" className="dispute-btn-primary" onClick={onOpenCreate}>
            + Gửi khiếu nại mới
          </button>
        )}
      </div>
    </div>
  );
};

export default DisputeTopHeader;
