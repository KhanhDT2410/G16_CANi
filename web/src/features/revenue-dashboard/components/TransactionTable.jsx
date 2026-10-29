import { VNPAY_STATUS, PACKAGE_TYPES } from '../types/revenueDashboard.constants';

/**
 * Bảng Danh Sách Giao Dịch Mua Gói Dịch Vụ VNPay (W-34)
 * Hỗ trợ liên kết khoan sâu sang W-46 (EO Subscription Detail)
 */
const TransactionTable = ({
  transactions = [],
  loading,
  onOpenEoDetail,
  onReconcile,
}) => {
  if (loading) {
    return (
      <div className="rev-table-container">
        <div className="rev-table-loading">
          <div className="cani-spinner" />
          <span>Đang nạp danh sách giao dịch VNPay...</span>
        </div>
      </div>
    );
  }

  if (transactions.length === 0) {
    return (
      <div className="rev-table-container">
        <div className="rev-empty-state">
          <div className="rev-empty-icon">📭</div>
          <h4 className="rev-empty-title">Không tìm thấy giao dịch nào</h4>
          <p className="rev-empty-desc">
            Không có dữ liệu giao dịch phù hợp với tiêu chí lọc hoặc từ khóa tìm kiếm đã chọn.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="rev-table-container">
      <table className="rev-table">
        <thead>
          <tr>
            <th>Mã Đơn / Mã VNPay</th>
            <th>Ban Tổ Chức (EO)</th>
            <th>Gói Dịch Vụ</th>
            <th>Số Tiền</th>
            <th>Cổng / Ngân Hàng</th>
            <th>Thời Gian</th>
            <th>Trạng Thái</th>
            <th style={{ textAlign: 'right' }}>Thao Tác</th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((txn) => {
            const statusConfig = VNPAY_STATUS[txn.status] || VNPAY_STATUS.SUCCESS;
            const packageConfig = PACKAGE_TYPES[txn.packageType];

            return (
              <tr key={txn.id} className="rev-table-row">
                {/* 1. Mã đơn & Mã VNPay */}
                <td>
                  <div className="rev-cell-code">
                    <span className="code-ref">{txn.vnpTxnRef}</span>
                    <span className="code-vnp">No: {txn.vnpTransactionNo}</span>
                  </div>
                </td>

                {/* 2. Ban Tổ Chức (EO) */}
                <td>
                  <div className="rev-cell-eo">
                    <strong className="eo-name">{txn.eoName}</strong>
                    <span className="eo-email">{txn.eoEmail}</span>
                  </div>
                </td>

                {/* 3. Gói Dịch Vụ */}
                <td>
                  <span
                    className="rev-pkg-badge"
                    style={{
                      borderColor: packageConfig?.color || '#5B86E5',
                      color: packageConfig?.color || '#5B86E5',
                      background: 'rgba(91, 134, 229, 0.06)',
                    }}
                  >
                    {packageConfig?.name || txn.packageName}
                  </span>
                </td>

                {/* 4. Số tiền */}
                <td>
                  <span className="rev-amount-text">
                    {txn.amount.toLocaleString('vi-VN')} đ
                  </span>
                </td>

                {/* 5. Ngân hàng & Kênh */}
                <td>
                  <div className="rev-cell-bank">
                    <span className="bank-badge">{txn.bankCode}</span>
                    <span className="method-label">{txn.paymentMethod}</span>
                  </div>
                </td>

                {/* 6. Thời gian */}
                <td>
                  <span className="rev-time-text">{txn.paidAt}</span>
                </td>

                {/* 7. Trạng thái */}
                <td>
                  <span
                    className={`rev-status-pill ${statusConfig.badgeClass}`}
                    style={{
                      color: statusConfig.color,
                      backgroundColor: statusConfig.bg,
                      borderColor: statusConfig.border,
                    }}
                  >
                    {statusConfig.label}
                  </span>
                </td>

                {/* 8. Thao tác */}
                <td style={{ textAlign: 'right' }}>
                  <div className="rev-row-actions">
                    {/* Xem chi tiết thuê bao EO (W-46) */}
                    <button
                      type="button"
                      className="rev-action-btn primary"
                      title="Xem chi tiết gói thuê bao của EO này (W-46)"
                      onClick={() => onOpenEoDetail(txn.eoId)}
                    >
                      Chi Tiết Gói (W-46)
                    </button>

                    {/* Đối soát nếu thất bại/pending */}
                    {(txn.status === 'FAILED' || txn.status === 'PENDING') && (
                      <button
                        type="button"
                        className="rev-action-btn warn"
                        title="Gửi yêu cầu truy vấn đối soát VNPay"
                        onClick={() => onReconcile(txn)}
                      >
                        Đối Soát
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
  );
};

export default TransactionTable;
