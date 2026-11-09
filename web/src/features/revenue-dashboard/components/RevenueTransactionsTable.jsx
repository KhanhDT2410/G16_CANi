import { formatCurrency, formatDateTime } from '../types/revenue.constants';

export const RevenueTransactionsTable = ({
  transactions,
  totalElements,
  currentPage,
  setCurrentPage,
  pageSize,
  statusFilter,
  setStatusFilter,
  packageTypeFilter,
  setPackageTypeFilter,
}) => {
  const totalPages = Math.ceil(totalElements / pageSize) || 1;

  return (
    <div className="rev-panel">
      <div className="rev-filter-bar">
        <div>
          <h3 className="rev-panel-title">Lịch Sử Giao Dịch Nền Tảng</h3>
          <p className="rev-panel-desc">Toàn bộ các giao dịch mua gói dịch vụ và đối soát thanh toán</p>
        </div>

        <div className="rev-controls-group">
          {/* Lọc loại gói */}
          <select
            className="rev-select"
            value={packageTypeFilter}
            onChange={(e) => {
              setPackageTypeFilter(e.target.value);
              setCurrentPage(0);
            }}
          >
            <option value="">Tất cả loại gói</option>
            <option value="INVITE_ONLY">Gói Mời Đích Danh</option>
            <option value="MATCHING_PLUS">Gói Ghép Nâng Cao</option>
          </select>

          {/* Lọc trạng thái */}
          <select
            className="rev-select"
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value);
              setCurrentPage(0);
            }}
          >
            <option value="">Tất cả trạng thái</option>
            <option value="ACTIVE">Hoạt động (Active)</option>
            <option value="CONFIRMED_FULL">Xác nhận đủ</option>
            <option value="CONFIRMED_PARTIAL">Xác nhận một phần</option>
            <option value="DECLARED_PENDING_WORKER">Chờ Worker duyệt</option>
            <option value="DISPUTED">Đang tranh chấp</option>
            <option value="UNVERIFIED">Chưa xác minh</option>
          </select>
        </div>
      </div>

      <div className="rev-table-wrapper">
        <table className="rev-table">
          <thead>
            <tr>
              <th>Loại Giao Dịch</th>
              <th>Thời Gian</th>
              <th>Bên Trả / Đăng Ký</th>
              <th>Nội Dung</th>
              <th>Số Tiền</th>
              <th>Phương Thức</th>
              <th>Trạng Thái</th>
            </tr>
          </thead>
          <tbody>
            {transactions.length === 0 ? (
              <tr>
                <td colSpan="7" className="rev-table-empty">
                  Không tìm thấy giao dịch nào phù hợp với bộ lọc.
                </td>
              </tr>
            ) : (
              transactions.map((tx) => (
                <tr key={tx.id}>
                  <td>
                    <span className={`rev-type-tag ${tx.transactionType === 'PACKAGE_SALE' ? 'package' : 'payment'}`}>
                      {tx.transactionType === 'PACKAGE_SALE' ? 'Mua Gói' : 'Chi Trả Ca'}
                    </span>
                  </td>
                  <td>{formatDateTime(tx.createdAt)}</td>
                  <td className="font-semibold">{tx.organizerName || '—'}</td>
                  <td>
                    {tx.transactionType === 'PACKAGE_SALE'
                      ? tx.planName || 'Gói dịch vụ'
                      : `Worker: ${tx.workerName || '—'}`}
                  </td>
                  <td className="rev-amount-highlight">{formatCurrency(tx.amount)}</td>
                  <td>
                    <span className="rev-badge method">{tx.paymentMethod || 'Chuyển khoản'}</span>
                  </td>
                  <td>
                    <span className={`rev-badge status-${(tx.status || '').toLowerCase()}`}>
                      {tx.status}
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Phân trang */}
      <div className="rev-pagination">
        <span className="rev-page-info">
          Hiển thị trang {currentPage + 1} / {totalPages} (Tổng số {totalElements} giao dịch)
        </span>
        <div className="rev-page-btns">
          <button
            className="rev-btn-page"
            disabled={currentPage === 0}
            onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
          >
            Trang Trước
          </button>
          <button
            className="rev-btn-page"
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

