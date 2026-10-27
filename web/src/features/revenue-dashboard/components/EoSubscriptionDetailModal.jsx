/**
 * Modal Khoan Sâu Chi Tiết Gói Thuê Bao Của EO (W-46 / UC-12, UC-114)
 * Cho phép Admin xem lịch sử nạp gói, số credit sự kiện còn lại và gia hạn thủ công.
 */
const EoSubscriptionDetailModal = ({
  isOpen,
  onClose,
  eoDetail,
  loading,
  onManualExtend,
}) => {
  if (!isOpen) return null;

  return (
    <div className="rev-modal-overlay" onClick={onClose}>
      <div className="rev-modal-container" onClick={(e) => e.stopPropagation()}>
        {/* Modal Header */}
        <div className="rev-modal-header">
          <div>
            <div className="rev-modal-badge">W-46 • EO Subscription Detail</div>
            <h3 className="rev-modal-title">
              Hồ Sơ Gói Dịch Vụ: {eoDetail?.eoName || 'Đang tải...'}
            </h3>
          </div>
          <button type="button" className="rev-modal-close" onClick={onClose}>
            &times;
          </button>
        </div>

        {/* Modal Body */}
        {loading ? (
          <div className="rev-modal-loading">
            <div className="cani-spinner" />
            <span>Đang tra cứu dữ liệu gói thuê bao...</span>
          </div>
        ) : !eoDetail ? (
          <div className="rev-modal-empty">Không tìm thấy thông tin gói dịch vụ.</div>
        ) : (
          <div className="rev-modal-body">
            {/* 1. Thông tin chung về tài khoản EO */}
            <div className="eo-info-grid">
              <div className="eo-info-item">
                <span className="label">Mã Ban Tổ Chức:</span>
                <strong className="value">{eoDetail.eoId}</strong>
              </div>
              <div className="eo-info-item">
                <span className="label">Mã Số Thuế:</span>
                <strong className="value">{eoDetail.taxCode || 'Chưa cập nhật'}</strong>
              </div>
              <div className="eo-info-item">
                <span className="label">Người Đại Diện:</span>
                <strong className="value">{eoDetail.representative}</strong>
              </div>
              <div className="eo-info-item">
                <span className="label">Số Điện Thoại:</span>
                <strong className="value">{eoDetail.phone}</strong>
              </div>
            </div>

            {/* 2. Trạng thái gói dịch vụ hiện tại */}
            <div className="eo-package-status-box">
              <div className="pkg-status-left">
                <div className="pkg-current-tag">GÓI ĐANG KÍCH HOẠT</div>
                <h4 className="pkg-name">{eoDetail.currentPackage}</h4>
                <div className="pkg-dates">
                  Thời hạn: <strong>{eoDetail.startDate}</strong> đến <strong>{eoDetail.expiryDate}</strong>
                  {eoDetail.autoRenew && <span className="auto-renew-tag">✓ Tự động gia hạn</span>}
                </div>
              </div>

              <div className="pkg-status-right">
                <div className="credits-display">
                  <span className="credits-number">{eoDetail.remainingCredits}</span>
                  <span className="credits-label">Credits Sự Kiện Còn Lại</span>
                </div>
                <div className="credits-sub">
                  Đã dùng {eoDetail.usedCredits} / {eoDetail.totalCreditsPurchased} lượt
                </div>
              </div>
            </div>

            {/* 3. Lịch sử các lần mua gói qua VNPay */}
            <div className="eo-history-section">
              <h5 className="eo-section-title">Lịch Sử Nạp Gói Qua Cổng VNPay</h5>
              <div className="eo-history-table-wrapper">
                <table className="eo-history-table">
                  <thead>
                    <tr>
                      <th>Mã Đơn / VNPay No</th>
                      <th>Gói Dịch Vụ</th>
                      <th>Số Tiền</th>
                      <th>Ngày Giờ</th>
                      <th>Trạng Thái</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(eoDetail.vnpayHistory || []).map((h, i) => (
                      <tr key={i}>
                        <td>
                          <strong>{h.txnRef}</strong>
                          <div style={{ fontSize: '0.75rem', color: '#64748b' }}>No: {h.vnpNo}</div>
                        </td>
                        <td>{h.packageName}</td>
                        <td style={{ fontWeight: 700, color: '#0f172a' }}>
                          {h.amount.toLocaleString('vi-VN')} đ
                        </td>
                        <td style={{ fontSize: '0.8rem', color: '#64748b' }}>{h.date}</td>
                        <td>
                          <span className="eo-status-tag success">Thành công</span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* 4. Thao tác Admin: Gia hạn thủ công & Đổi gói */}
            <div className="rev-modal-actions">
              <button
                type="button"
                className="rev-modal-btn cancel"
                onClick={onClose}
              >
                Đóng
              </button>

              <button
                type="button"
                className="rev-modal-btn extend"
                onClick={() => onManualExtend(30)}
              >
                + Gia Hạn Thủ Công (+5 Credit sự kiện)
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default EoSubscriptionDetailModal;
