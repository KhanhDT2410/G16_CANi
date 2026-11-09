import { formatDateTime, REASON_CODE_LABELS } from '../types/rsAppeal.constants';
import { RsAppealStatusBadge } from './RsAppealStatusBadge';

export const RsAppealsTable = ({
  appeals,
  totalElements,
  currentPage,
  setCurrentPage,
  pageSize,
  onViewDetail,
  onReview,
}) => {
  const totalPages = Math.ceil(totalElements / pageSize) || 1;

  if (!appeals || appeals.length === 0) {
    return (
      <div className="appeal-empty-card">
        <p>Hiện không có đơn kháng cáo điểm uy tín nào trong hàng đợi.</p>
      </div>
    );
  }

  return (
    <div className="appeal-table-container">
      <div className="appeal-table-wrapper">
        <table className="appeal-table">
          <thead>
            <tr>
              <th>Thời Gian Nộp</th>
              <th>Nhân Sự (Worker)</th>
              <th>Mức Phạt (RS)</th>
              <th>Lý Do Khiếu Nại</th>
              <th>Trạng Thái</th>
              <th>Thao Tác</th>
            </tr>
          </thead>
          <tbody>
            {appeals.map((item) => {
              const reasonText = REASON_CODE_LABELS[item.reasonCode] || item.reasonCode;
              const isPending = item.status === 'SUBMITTED' || item.status === 'UNDER_REVIEW';

              return (
                <tr key={item.id}>
                  <td>{formatDateTime(item.createdAt)}</td>
                  <td>
                    <div className="worker-cell">
                      <span className="worker-name">{item.workerName || 'Worker'}</span>
                      <span className="worker-email">{item.workerEmail || ''}</span>
                    </div>
                  </td>
                  <td>
                    <span className="penalty-amount-tag">
                      -{item.penaltyAmount} RS
                    </span>
                  </td>
                  <td>
                    <div className="reason-cell">
                      <span className="reason-code-title">{reasonText}</span>
                      <span className="reason-snippet">{item.reasonDetail}</span>
                    </div>
                  </td>
                  <td>
                    <RsAppealStatusBadge status={item.status} />
                  </td>
                  <td>
                    <div className="appeal-action-btns">
                      <button
                        className="btn-appeal-detail"
                        onClick={() => onViewDetail(item)}
                      >
                        Chi Tiết
                      </button>
                      {isPending && (
                        <button
                          className="btn-appeal-review"
                          onClick={() => onReview(item)}
                        >
                          Xử Lý
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <div className="appeal-pagination">
        <span className="appeal-page-info">
          Trang {currentPage + 1} / {totalPages} (Tổng số {totalElements} đơn)
        </span>
        <div className="appeal-page-btns">
          <button
            className="btn-appeal-page"
            disabled={currentPage === 0}
            onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
          >
            Trang Trước
          </button>
          <button
            className="btn-appeal-page"
            disabled={currentPage >= totalPages - 1}
            onClick={() => setCurrentPage((p) => p + 1)}
          >
            Trang Kế
          </button>
        </div>
      </div>
    </div>
  );
};

