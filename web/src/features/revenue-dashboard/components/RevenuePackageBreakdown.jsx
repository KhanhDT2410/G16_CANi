import { formatCurrency } from '../types/revenue.constants';

export const RevenuePackageBreakdown = ({ packageTypeBreakdown, eventModeBreakdown }) => {
  return (
    <div className="rev-breakdown-row">
      {/* Phân bổ theo loại gói dịch vụ */}
      <div className="rev-panel">
        <h3 className="rev-panel-title">Phân Bổ Doanh Thu Theo Gói Dịch Vụ</h3>
        <p className="rev-panel-desc">Tỷ trọng doanh số giữa Gói Mời đích danh và Gói Ghép nâng cao</p>
        <div className="rev-breakdown-list">
          {packageTypeBreakdown?.map((item) => (
            <div key={item.planType} className="rev-breakdown-item">
              <div className="rev-item-info">
                <span className="rev-item-name">{item.planTypeName}</span>
                <span className="rev-item-val">{formatCurrency(item.totalRevenue)}</span>
              </div>
              <div className="rev-progress-bar">
                <div
                  className={`rev-progress-fill ${item.planType === 'MATCHING_PLUS' ? 'accent' : 'primary'}`}
                  style={{ width: `${item.percentageOfRevenue || 0}%` }}
                />
              </div>
              <div className="rev-item-sub">
                <span>{item.salesCount} lượt đăng ký</span>
                <span>{item.percentageOfRevenue?.toFixed(1)}% doanh thu</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Phân bổ theo hình thức sự kiện */}
      <div className="rev-panel">
        <h3 className="rev-panel-title">Dòng Tiền Theo Hình Thức Ca Làm Việc</h3>
        <p className="rev-panel-desc">Khối lượng thù lao chi trả cho sự kiện Ghép tự động và Mời trực tiếp</p>
        <div className="rev-breakdown-list">
          {eventModeBreakdown?.map((item) => (
            <div key={item.mode} className="rev-breakdown-item">
              <div className="rev-item-info">
                <span className="rev-item-name">{item.modeName}</span>
                <span className="rev-item-val">{formatCurrency(item.totalPaymentAmount)}</span>
              </div>
              <div className="rev-progress-bar">
                <div
                  className="rev-progress-fill mode"
                  style={{ width: `${Math.min(100, Math.max(15, item.transactionCount * 10))}%` }}
                />
              </div>
              <div className="rev-item-sub">
                <span>{item.eventCount} sự kiện</span>
                <span>{item.transactionCount} giao dịch thù lao</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

