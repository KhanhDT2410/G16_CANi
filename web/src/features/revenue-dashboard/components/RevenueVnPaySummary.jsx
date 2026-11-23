import { formatCurrency } from '../types/revenue.constants';

export const RevenuePayOSSummary = ({ payosSummary, vnpaySummary }) => {
  const summary = payosSummary || vnpaySummary;
  if (!summary) return null;

  return (
    <div className="rev-panel rev-vnpay-box">
      <div className="rev-vnpay-header">
        <div>
          <h3 className="rev-panel-title">Tổng Hợp Giao Dịch Cổng PayOS</h3>
          <p className="rev-panel-desc">Theo dõi trạng thái các giao dịch thanh toán qua cổng PayOS</p>
        </div>
        <div className="rev-vnpay-badge">PayOS VietQR</div>
      </div>

      <div className="rev-vnpay-stats">
        <div className="rev-vnpay-stat">
          <span className="stat-label">Tổng giao dịch PayOS</span>
          <span className="stat-value">{summary.totalTransactions ?? 0}</span>
          <span className="stat-money">{formatCurrency(summary.totalAmount ?? 0)}</span>
        </div>
        <div className="rev-vnpay-stat success">
          <span className="stat-label">Đã xác nhận thành công</span>
          <span className="stat-value">{summary.confirmedCount ?? 0}</span>
          <span className="stat-hint">Khớp đối soát</span>
        </div>
        <div className="rev-vnpay-stat pending">
          <span className="stat-label">Đang chờ xử lý</span>
          <span className="stat-value">{summary.pendingCount ?? 0}</span>
          <span className="stat-hint">Chưa thanh toán</span>
        </div>
        <div className="rev-vnpay-stat alert">
          <span className="stat-label">Đã hủy / Thất bại</span>
          <span className="stat-value">{summary.flaggedCount ?? 0}</span>
          <span className="stat-hint">Đã hủy đơn</span>
        </div>
      </div>
    </div>
  );
};

export const RevenueVnPaySummary = RevenuePayOSSummary;


