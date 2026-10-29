/**
 * Thẻ Giám Sát Sức Khỏe Cổng Thanh Toán VNPay & Cảnh Báo Đối Soát (UC-108)
 * Quản trị viên/CSKH theo dõi tỷ lệ thành công và xử lý các giao dịch lỗi/pending.
 */
const VnPayHealthCard = ({ vnpayHealth, onReconcile, transactions = [] }) => {
  // Tìm các giao dịch FAILED hoặc PENDING cần lưu tâm
  const flaggedList = transactions.filter(
    (t) => t.status === 'FAILED' || t.status === 'PENDING'
  );

  return (
    <div className="vnp-health-container">
      {/* 1. Thanh trạng thái Gateway */}
      <div className="vnp-status-bar">
        <div className="vnp-status-left">
          <div className="vnp-indicator-pulse online" />
          <div>
            <h4 className="vnp-title">VNPay Gateway Service Health</h4>
            <span className="vnp-subtitle">
              Kết nối trực tiếp API VNPAY-QR & VNPAY-ATM • Cập nhật lúc {vnpayHealth?.lastSyncAt || 'Vừa xong'}
            </span>
          </div>
        </div>

        <div className="vnp-metrics-row">
          <div className="vnp-metric-pill">
            <span className="pill-label">Sẵn sàng (Uptime):</span>
            <strong className="pill-value text-success">{vnpayHealth?.uptime || 99.98}%</strong>
          </div>
          <div className="vnp-metric-pill">
            <span className="pill-label">Độ trễ trung bình:</span>
            <strong className="pill-value text-blue">{vnpayHealth?.avgLatencyMs || 820} ms</strong>
          </div>
          <div className="vnp-metric-pill">
            <span className="pill-label">Tỷ lệ thành công:</span>
            <strong className="pill-value text-success">{vnpayHealth?.successRate || 98.7}%</strong>
          </div>
        </div>
      </div>

      {/* 2. Cảnh báo giao dịch lỗi hoặc chờ đối soát (Flagged for follow-up) */}
      {flaggedList.length > 0 && (
        <div className="vnp-flagged-section">
          <div className="vnp-flagged-header">
            <span className="vnp-alert-icon">⚡</span>
            <strong>Cảnh báo {flaggedList.length} giao dịch cần CSKH đối soát với VNPay:</strong>
          </div>
          <div className="vnp-flagged-list">
            {flaggedList.map((item) => (
              <div key={item.id} className="vnp-flagged-item">
                <div className="vnp-item-info">
                  <span className="vnp-item-code">{item.vnpTxnRef}</span>
                  <span className="vnp-item-eo">{item.eoName}</span>
                  <span className="vnp-item-pkg">{item.packageName}</span>
                  <span className="vnp-item-amount">{item.amount.toLocaleString('vi-VN')} đ</span>
                  <span className={`vnp-item-status ${item.status}`}>
                    {item.status === 'FAILED' ? 'Thất bại' : 'Chờ Webhook IPN'}
                  </span>
                </div>
                <div className="vnp-item-reason">
                  <em>{item.notes}</em>
                </div>
                <div className="vnp-item-actions">
                  <button
                    type="button"
                    className="vnp-btn-reconcile"
                    onClick={() => onReconcile(item)}
                  >
                    🔄 Gửi QueryDR Đối Soát
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default VnPayHealthCard;
