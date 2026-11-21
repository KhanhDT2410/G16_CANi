import React, { useState } from 'react';

/**
 * PaymentDeclarationModal
 * Modal khai báo đã trả lương trực tiếp cho Worker (Mục 11 & 12 SRS - Mô hình Non-Escrow)
 * Tích hợp chuẩn VietQR Napas 247: Hiển thị mã QR và thông tin tài khoản thật từ Database
 */
const PaymentDeclarationModal = ({
  isOpen,
  onClose,
  onSubmit,
  paymentForm,
  setPaymentForm,
  selectedAssignment,
}) => {
  const [copied, setCopied] = useState(false);

  if (!isOpen || !selectedAssignment) return null;

  const bankCode = selectedAssignment.bankCode;
  const accountNumber = selectedAssignment.bankAccountNumber;
  const accountName = selectedAssignment.bankAccountName || selectedAssignment.workerName || '';
  const amount = paymentForm.declaredAmount || selectedAssignment.agreedWage || 0;
  const hasBankInfo = Boolean(bankCode && accountNumber);

  const dynamicVietQrUrl = (selectedAssignment.vietQrUrl && amount === (selectedAssignment.agreedWage || 0))
    ? selectedAssignment.vietQrUrl
    : (hasBankInfo
      ? `https://img.vietqr.io/image/${bankCode}-${accountNumber}-compact2.png?amount=${amount}&accountName=${encodeURIComponent(accountName)}`
      : (selectedAssignment.vietQrUrl || null));

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box" style={{ maxWidth: '540px' }}>
        <h3>Khai Báo Đã Trả Lương Trực Tiếp</h3>
        <p style={{ fontSize: '0.875rem', color: '#4b5563', marginBottom: '1.25rem' }}>
          Sau khi chuyển khoản hoặc chi trả thù lao cho nhân sự, vui lòng khai báo để hệ thống ghi nhận đối soát và gửi thông báo xác nhận đến nhân sự.
        </p>
        <form onSubmit={onSubmit}>
          <div className="eo-form-group">
            <label>Nhân sự được chi trả:</label>
            <input
              type="text"
              disabled
              className="eo-form-input"
              style={{ background: '#f3f4f6' }}
              value={`${selectedAssignment.workerName} (${selectedAssignment.jobType || 'Nhân sự'})`}
            />
          </div>

          <div className="eo-form-group">
            <label>Số Tiền Đã Trả (VNĐ) *</label>
            <input
              type="number"
              required
              className="eo-form-input"
              value={paymentForm.declaredAmount}
              onChange={(e) =>
                setPaymentForm({ ...paymentForm, declaredAmount: parseFloat(e.target.value) || 0 })
              }
            />
          </div>

          <div className="eo-form-group">
            <label>Phương Thức Chi Trả *</label>
            <select
              className="eo-form-select"
              value={paymentForm.paymentMethod}
              onChange={(e) =>
                setPaymentForm({ ...paymentForm, paymentMethod: e.target.value })
              }
            >
              <option value="BANK_TRANSFER">Chuyển Khoản Ngân Hàng (VietQR 247 / Bank Transfer)</option>
              <option value="CASH">Tiền Mặt Trực Tiếp (Cash)</option>
            </select>
          </div>

          {/* =========================================================================
              KHỐI MÃ VIETQR NHẬN TIỀN TRỰC TIẾP CỦA WORKER (MỤC 11 & 12 SRS)
              ========================================================================= */}
          {paymentForm.paymentMethod === 'BANK_TRANSFER' && (
            <div
              style={{
                margin: '1rem 0',
                padding: '1rem',
                backgroundColor: '#f8fafc',
                border: '1px solid #e2e8f0',
                borderRadius: '8px',
              }}
            >
              {hasBankInfo ? (
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
                    <span style={{ fontSize: '0.85rem', fontWeight: 700, color: '#1e293b' }}>
                      Mã VietQR Chuyển Khoản Trực Tiếp (Napas 247)
                    </span>
                    <span style={{ fontSize: '0.72rem', color: '#059669', fontWeight: 600, background: '#ecfdf5', padding: '2px 8px', borderRadius: 4 }}>
                      Đã liên kết tài khoản
                    </span>
                  </div>

                  <div style={{ display: 'flex', gap: '1rem', alignItems: 'center', flexWrap: 'wrap' }}>
                    {/* Ảnh VietQR chuẩn Napas 247 */}
                    <div
                      style={{
                        background: '#ffffff',
                        padding: '6px',
                        borderRadius: '8px',
                        border: '1px solid #e2e8f0',
                        boxShadow: '0 1px 3px rgba(0,0,0,0.06)',
                        textAlign: 'center',
                      }}
                    >
                      <img
                        src={dynamicVietQrUrl}
                        alt="Mã VietQR"
                        style={{ width: '135px', height: '135px', objectFit: 'contain', display: 'block' }}
                      />
                      <span style={{ fontSize: '0.65rem', color: '#64748b', display: 'block', marginTop: 4 }}>
                        Quét app ngân hàng
                      </span>
                    </div>

                    {/* Chi tiết thông tin tài khoản */}
                    <div style={{ flex: 1, minWidth: '180px', fontSize: '0.8rem', lineHeight: 1.6 }}>
                      <div>
                        <span style={{ color: '#64748b' }}>Ngân hàng: </span>
                        <strong style={{ color: '#0f172a' }}>{bankCode}</strong>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px', margin: '2px 0' }}>
                        <span style={{ color: '#64748b' }}>Số tài khoản: </span>
                        <strong style={{ color: '#4338ca', fontSize: '0.92rem' }}>{accountNumber}</strong>
                        <button
                          type="button"
                          onClick={() => {
                            navigator.clipboard.writeText(accountNumber);
                            setCopied(true);
                            setTimeout(() => setCopied(false), 2000);
                          }}
                          style={{
                            background: '#e0e7ff',
                            color: '#4338ca',
                            border: 'none',
                            borderRadius: '4px',
                            padding: '1px 6px',
                            fontSize: '0.7rem',
                            cursor: 'pointer',
                          }}
                        >
                          {copied ? 'Đã copy ✓' : 'Copy'}
                        </button>
                      </div>
                      <div>
                        <span style={{ color: '#64748b' }}>Chủ tài khoản: </span>
                        <strong style={{ color: '#0f172a' }}>{accountName}</strong>
                      </div>
                      <div style={{ marginTop: 4 }}>
                        <span style={{ color: '#64748b' }}>Số tiền chuyển: </span>
                        <strong style={{ color: '#059669', fontSize: '0.92rem' }}>
                          {new Intl.NumberFormat('vi-VN').format(amount)} đ
                        </strong>
                      </div>
                    </div>
                  </div>
                </div>
              ) : (
                <div style={{ textAlign: 'center', padding: '0.75rem', color: '#64748b', fontSize: '0.82rem' }}>
                  <div style={{ fontSize: '1.4rem', marginBottom: '0.35rem' }}>ℹ️</div>
                  <strong style={{ color: '#334155' }}>Chưa có thông tin tài khoản ngân hàng</strong>
                  <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: 3 }}>
                    Nhân sự này chưa cập nhật tài khoản nhận lương trên ứng dụng di động CANi. Bạn có thể chi trả bằng <strong>Tiền Mặt Trực Tiếp</strong> hoặc liên hệ nhân sự để nhận số tài khoản.
                  </div>
                </div>
              )}
            </div>
          )}

          <div className="eo-form-group">
            <label>Mã Giao Dịch Ngân Hàng Ngoài (Nếu chuyển khoản)</label>
            <input
              type="text"
              className="eo-form-input"
              placeholder="Ví dụ: VCB-123456789 hoặc MB-888999"
              value={paymentForm.externalRef}
              onChange={(e) =>
                setPaymentForm({ ...paymentForm, externalRef: e.target.value })
              }
            />
          </div>

          <div className="eo-form-group">
            <label>Tải Lên Bằng Chứng / Ảnh Chụp Biên Lai Chuyển Khoản *</label>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              <input
                type="file"
                accept="image/*"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) {
                    const reader = new FileReader();
                    reader.onload = () => {
                      setPaymentForm({ ...paymentForm, paymentProofUrl: reader.result });
                    };
                    reader.readAsDataURL(file);
                  }
                }}
                className="eo-form-input"
                style={{ padding: '6px' }}
              />
              {paymentForm.paymentProofUrl && (
                <div style={{ position: 'relative', width: 'fit-content', border: '1px solid #cbd5e1', borderRadius: 8, padding: 4, background: '#f8fafc' }}>
                  <img
                    src={paymentForm.paymentProofUrl}
                    alt="Biên lai chuyển tiền"
                    style={{ maxHeight: '140px', maxWidth: '240px', objectFit: 'contain', borderRadius: 6, display: 'block' }}
                  />
                  <span style={{ fontSize: '0.7rem', color: '#059669', fontWeight: 600, display: 'block', marginTop: 4 }}>
                    ✓ Đã tải biên lai xác nhận
                  </span>
                </div>
              )}
            </div>
          </div>

          <div
            style={{
              backgroundColor: '#eff6ff',
              border: '1px solid #bfdbfe',
              borderRadius: '0.5rem',
              padding: '0.75rem',
              fontSize: '0.78rem',
              color: '#1e40af',
              lineHeight: 1.45,
              margin: '0.75rem 0',
            }}
          >
            <strong>ℹ️ Quy trình đối soát 2 bên:</strong> Sau khi Ban tổ chức bấm <strong>"Xác nhận đã chuyển"</strong>, hệ thống sẽ gửi thông báo kèm bằng chứng này về ứng dụng của Worker để Worker kiểm tra tài khoản và bấm xác nhận hoàn tất.
          </div>

          <div className="eo-modal-footer">
            <button
              type="button"
              className="eo-btn-secondary"
              onClick={onClose}
            >
              Hủy
            </button>
            <button
              type="submit"
              className="eo-btn-primary"
              style={{ background: 'linear-gradient(135deg, #059669 0%, #10b981 100%)', border: 'none' }}
            >
              Xác Nhận Đã Chuyển Tiền
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default PaymentDeclarationModal;
