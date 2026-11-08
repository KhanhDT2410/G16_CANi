import { formatCurrency, formatDateTime } from '../types/revenue.constants';

export const RevenueFlaggedTable = ({ flaggedTransactions }) => {
  if (!flaggedTransactions || flaggedTransactions.length === 0) {
    return (
      <div className="rev-panel rev-flagged-empty">
        <h3 className="rev-panel-title">Giao Dịch Cần Theo Dõi (Rủi Ro & Tranh Chấp)</h3>
        <p className="rev-empty-text">Hiện tại không có giao dịch nào bị tranh chấp hoặc cần can thiệp đối soát.</p>
      </div>
    );
  }

  return (
    <div className="rev-panel rev-flagged-panel">
      <div className="rev-flagged-header">
        <div>
          <h3 className="rev-panel-title" style={{ color: '#b91c1c' }}>
            ⚠ Giao Dịch Cần Theo Dõi (Rủi Ro & Khiếu Nại)
          </h3>
          <p className="rev-panel-desc">
            Danh sách các giao dịch thanh toán đang bị tranh chấp (DISPUTED) hoặc chưa xác minh (UNVERIFIED)
          </p>
        </div>
        <span className="rev-flagged-count">{flaggedTransactions.length} giao dịch</span>
      </div>

      <div className="rev-table-wrapper">
        <table className="rev-table">
          <thead>
            <tr>
              <th>Thời Gian</th>
              <th>Bên Tổ Chức (EO)</th>
              <th>Nhân Sự (Worker)</th>
              <th>Số Tiền</th>
              <th>Phương Thức</th>
              <th>Trạng Thái</th>
              <th>Lý Do Cảnh Báo</th>
            </tr>
          </thead>
          <tbody>
            {flaggedTransactions.map((tx) => (
              <tr key={tx.id} className="flagged-row">
                <td>{formatDateTime(tx.createdAt)}</td>
                <td className="font-semibold">{tx.organizerName || '—'}</td>
                <td>{tx.workerName || '—'}</td>
                <td className="rev-amount-highlight">{formatCurrency(tx.amount)}</td>
                <td>
                  <span className="rev-badge method">{tx.paymentMethod || '—'}</span>
                </td>
                <td>
                  <span className="rev-badge status-disputed">{tx.status}</span>
                </td>
                <td className="rev-flag-reason">{tx.flagReason || 'Cần kiểm tra đối soát'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

