import { formatCurrency, formatGrowthRate } from '../types/revenue.constants';

export const RevenueMetricsCards = ({ currentPeriod, growth }) => {
  if (!currentPeriod) return null;

  const isPositive = (growth?.packageRevenueGrowthRate || 0) >= 0;

  return (
    <div className="rev-metrics-grid">
      {/* Thẻ 1: Doanh thu gói dịch vụ */}
      <div className="rev-metric-card primary">
        <div className="rev-card-header">
          <span className="rev-card-title">Doanh Thu Gói Dịch Vụ</span>
          <span className={`rev-growth-tag ${isPositive ? 'positive' : 'negative'}`}>
            {formatGrowthRate(growth?.packageRevenueGrowthRate)}
          </span>
        </div>
        <div className="rev-card-value">
          {formatCurrency(currentPeriod.totalPackageRevenue)}
        </div>
        <div className="rev-card-sub">
          {currentPeriod.totalPackageSales} gói đã được Nhà tổ chức thanh toán
        </div>
      </div>

      {/* Thẻ 2: Khối lượng giao dịch thù lao */}
      <div className="rev-metric-card">
        <div className="rev-card-header">
          <span className="rev-card-title">Khối Lượng Thù Lao (Đối Soát)</span>
          <span className="rev-growth-tag neutral">
            {formatGrowthRate(growth?.paymentVolumeGrowthRate)}
          </span>
        </div>
        <div className="rev-card-value">
          {formatCurrency(currentPeriod.totalPaymentVolume)}
        </div>
        <div className="rev-card-sub">
          {currentPeriod.totalPaymentTransactions} giao dịch điều phối trực tiếp
        </div>
      </div>

      {/* Thẻ 3: Tổng khối lượng giao dịch nền tảng */}
      <div className="rev-metric-card">
        <div className="rev-card-header">
          <span className="rev-card-title">Tổng Khối Lượng Dòng Tiền</span>
          <span className="rev-growth-tag neutral">
            {formatGrowthRate(growth?.combinedVolumeGrowthRate)}
          </span>
        </div>
        <div className="rev-card-value">
          {formatCurrency(currentPeriod.combinedVolume)}
        </div>
        <div className="rev-card-sub">
          Bao gồm doanh số gói và tổng lượng thanh toán ca
        </div>
      </div>

      {/* Thẻ 4: Tăng trưởng số lượng gói bán */}
      <div className="rev-metric-card">
        <div className="rev-card-header">
          <span className="rev-card-title">Tăng Trưởng Bán Gói</span>
          <span className={`rev-growth-tag ${(growth?.packageSalesGrowthRate || 0) >= 0 ? 'positive' : 'negative'}`}>
            {formatGrowthRate(growth?.packageSalesGrowthRate)}
          </span>
        </div>
        <div className="rev-card-value">
          {currentPeriod.totalPackageSales} gói
        </div>
        <div className="rev-card-sub">
          So sánh cùng kỳ trước đó
        </div>
      </div>
    </div>
  );
};

