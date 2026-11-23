import SkeletonCard from '../../../components/common/SkeletonLoader';

export default function EOEventDataState({ loading, onRetry }) {
  if (loading) {
    return (
      <div className="eo-event-data-state" role="status" aria-label="Đang tải dữ liệu sự kiện">
        <SkeletonCard count={3} height="3.5rem" />
      </div>
    );
  }

  return (
    <div className="eo-event-data-state eo-event-data-error">
      <div role="alert">
        <strong>Chưa tải được dữ liệu sự kiện</strong>
        <p>Vui lòng thử lại để cập nhật danh sách.</p>
      </div>
      <button type="button" className="eo-btn-secondary" onClick={onRetry}>Thử lại</button>
    </div>
  );
}
