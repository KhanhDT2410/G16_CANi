import { formatCurrency } from '../types/revenue.constants';

export const RevenueVnPaySummary = ({ vnpaySummary }) => {
  if (!vnpaySummary) return null;

  return (
    <div className="rev-panel rev-vnpay-box">
      <div className="rev-vnpay-header">
        <div>
          <h3 className="rev-panel-title">Tổng Hợp Giao Dịch Cổng VNPay</h3>
          <p className="rev-panel-desc">Theo dõi trạng thái các giao dịch thanh toán qua cổng VNPay</p>
        </div>
        <div className="rev-vnpay-badge">VNPay Integration</div>
      </div>

      <div className="rev-vnpay-stats">
        <div className="rev-vnpay-stat">
          <span className="stat-label">Tổng giao dịch VNPay</span>
          <span className="stat-value">{vnpaySummary.totalTransactions}</span>
          <span className="stat-money">{formatCurrency(vnpaySummary.totalAmount)}</span>
        </div>
        <div className="rev-vnpay-stat success">
          <span className="stat-label">Đã xác nhận thành công</span>
          <span className="stat-value">{vnpaySummary.confirmedCount}</span>
          <span className="stat-hint">Khớp đối soát</span>
        </div>
        <div className="rev-vnpay-stat pending">
          <span className="stat-label">Đang chờ xử lý</span>
          <span className="stat-value">{vnpaySummary.pendingCount}</span>
          <span className="stat-hint">Chưa đối soát xong</span>
        </div>
        <div className="rev-vnpay-stat alert">
          <span className="stat-label">Cờ cảnh báo (Tranh chấp)</span>
          <span className="stat-value">{vnpaySummary.flaggedCount}</span>
          <span className="stat-hint">Cần theo dõi ngay</span>
        </div>
      </div>

      {vnpaySummary.dataReliabilityNote && (
        <div className="rev-notice-alert">
          <span className="rev-notice-icon">ℹ</span>
          <span>{vnpaySummary.dataReliabilityNote}</span>
        </div>
      )}
    </div>
  );
};

