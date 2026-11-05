import React from 'react';

/**
 * PublishEventModal
 * Modal xác nhận xuất bản sự kiện & kích hoạt Matching Engine (Clean UI - No Emoji)
 */
const PublishEventModal = ({
  isOpen,
  onClose,
  onExecutePublish,
  event,
  publishPayMethod,
  setPublishPayMethod,
  onCopyToClipboard,
}) => {
  if (!isOpen || !event) return null;

  const workersCount = event.totalRequiredWorkers || 0;
  const platformFee = Math.max(50000, workersCount * 3000);
  const eventIdCode = (event.eventId || 'EVT').replace(/[^a-zA-Z0-9]/g, '').slice(-6).toUpperCase();
  const transferMemo = `CANI PHI ${eventIdCode}`;
  const vietQrUrl = `https://img.vietqr.io/image/MB-2622014789096-compact2.png?amount=${platformFee}&addInfo=${encodeURIComponent(transferMemo)}&accountName=PHAM%20DUY%20HUNG`;

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box publish-modal-box animate-fade-in-up">
        <div className="publish-modal-header">
          <div>
            <h3>Xác Nhận Xuất Bản & Bắt Đầu Tìm Kiếm</h3>
            <p>Hệ thống CANi sẽ quét ứng viên phù hợp theo độ tin cậy và bán kính điều phối</p>
          </div>
        </div>

        {/* Thông tin tóm tắt sự kiện */}
        <div className="publish-summary-card">
          <div className="publish-summary-title">
            {event.name}
          </div>
          <div className="publish-grid">
            <div className="publish-grid-item">
              <span className="lbl">Số ca làm việc:</span>
              <span className="val">{event.totalShifts || 0} ca</span>
            </div>
            <div className="publish-grid-item">
              <span className="lbl">Tổng nhân sự tuyển:</span>
              <span className="val highlight">
                {event.totalRequiredWorkers || 0} người
              </span>
            </div>
            <div className="publish-grid-item full-width">
              <span className="lbl">Tổng ngân sách lương dự kiến:</span>
              <span className="val wage">
                {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(
                  event.totalEstimatedCost || 0
                )}
              </span>
              <div className="sub-note">
                (Thanh toán trực tiếp giữa EO & Worker sau khi kết thúc ca làm - CANi không giữ cọc)
              </div>
            </div>
          </div>
        </div>

        {/* Phí Dịch Vụ Điều Phối Của Hệ Thống */}
        <div className="publish-fee-box">
          <div className="publish-fee-header">
            <span className="publish-fee-title">
              Phí dịch vụ điều phối:
            </span>
            <span className="publish-fee-amount">
              {platformFee.toLocaleString()} đ
            </span>
          </div>
          <div className="publish-fee-detail">
            Định mức: <strong>3.000 VNĐ / slot ứng viên cần tuyển</strong> &times; {workersCount} người.
            <br />
            <em>(Phí duy trì nền tảng, đối soát chấm công và tìm kiếm ứng viên phù hợp)</em>
          </div>
        </div>

        {/* Lựa chọn phương thức thanh toán phí */}
        <div className="publish-payment-methods">
          <div className="publish-method-tabs">
            <button
              type="button"
              className={`publish-tab-btn ${publishPayMethod === 'VIETQR' ? 'active' : ''}`}
              onClick={() => setPublishPayMethod('VIETQR')}
            >
              Quét Mã VietQR
            </button>
            <button
              type="button"
              className={`publish-tab-btn ${publishPayMethod === 'SUBSCRIPTION' ? 'active' : ''}`}
              onClick={() => setPublishPayMethod('SUBSCRIPTION')}
            >
              Trừ Gói Hội Viên
            </button>
          </div>

          {/* Tab 1: VietQR tự động sinh theo chuẩn NAPAS 247 */}
          {publishPayMethod === 'VIETQR' && (
            <div className="vietqr-container animate-fade-in-up">
              <div className="vietqr-qr-col">
                <div className="vietqr-image-wrapper">
                  <img
                    src={vietQrUrl}
                    alt="Mã VietQR Thanh Toán Phí Điều Phối"
                    className="vietqr-image"
                  />
                  <div className="vietqr-live-tag">
                    Quét bằng App Ngân Hàng bất kỳ
                  </div>
                </div>
              </div>

              <div className="vietqr-info-col">
                <div className="vietqr-bank-badge">
                  Ngân hàng Quân Đội (MBBank)
                </div>

                <div className="vietqr-detail-row">
                  <span className="vietqr-lbl">Số tài khoản:</span>
                  <div className="vietqr-val-group">
                    <strong>2622 0147 8909 6</strong>
                    <button
                      type="button"
                      className="vietqr-copy-btn"
                      onClick={() => onCopyToClipboard('2622014789096', 'Số tài khoản')}
                    >
                      Sao chép
                    </button>
                  </div>
                </div>

                <div className="vietqr-detail-row">
                  <span className="vietqr-lbl">Chủ tài khoản:</span>
                  <span className="vietqr-val">PHAM DUY HUNG</span>
                </div>

                <div className="vietqr-detail-row">
                  <span className="vietqr-lbl">Số tiền cần nạp:</span>
                  <div className="vietqr-val-group">
                    <span className="vietqr-amount-highlight">
                      {platformFee.toLocaleString()} đ
                    </span>
                    <button
                      type="button"
                      className="vietqr-copy-btn"
                      onClick={() => onCopyToClipboard(platformFee.toString(), 'Số tiền')}
                    >
                      Sao chép
                    </button>
                  </div>
                </div>

                <div className="vietqr-detail-row">
                  <span className="vietqr-lbl">Nội dung chuyển khoản:</span>
                  <div className="vietqr-val-group">
                    <code className="vietqr-memo-code">{transferMemo}</code>
                    <button
                      type="button"
                      className="vietqr-copy-btn"
                      onClick={() => onCopyToClipboard(transferMemo, 'Nội dung CK')}
                    >
                      Sao chép
                    </button>
                  </div>
                </div>

                <div className="vietqr-guide-box">
                  Khi bạn quét mã QR bằng App Mobile Banking, Số tiền và Nội dung sẽ tự động được điền sẵn 100%.
                </div>
              </div>
            </div>
          )}

          {/* Tab 2: Trừ Quota gói Hội viên */}
          {publishPayMethod === 'SUBSCRIPTION' && (
            <div className="subscription-quota-box animate-fade-in-up">
              <div className="quota-header">
                <span className="quota-badge">GÓI MATCHING PLUS (Doanh Nghiệp)</span>
                <span className="quota-status">Đang hoạt động</span>
              </div>
              <div className="quota-body">
                <p>
                  Số lượt tạo sự kiện còn lại trong gói: <strong>3 lượt</strong>.
                </p>
                <p style={{ fontSize: '0.825rem', color: '#64748b' }}>
                  Khi kích hoạt, hệ thống sẽ tự động trừ <strong>1 lượt quota</strong> sự kiện và miễn phí toàn bộ phí matching theo chính sách gói.
                </p>
              </div>
            </div>
          )}
        </div>

        <div className="eo-modal-footer">
          <button
            type="button"
            className="eo-btn-secondary"
            onClick={onClose}
          >
            Để Sau
          </button>
          <button
            type="button"
            className="eo-btn-primary"
            style={{ background: '#10b981' }}
            onClick={onExecutePublish}
          >
            Bắt Đầu Tìm Kiếm
          </button>
        </div>
      </div>
    </div>
  );
};

export default PublishEventModal;
