import React from 'react';

/**
 * PaymentDeclarationModal
 * Modal khai báo đã trả lương trực tiếp cho Worker (Mục 11 & 12 SRS - Mô hình Non-Escrow)
 */
const PaymentDeclarationModal = ({
  isOpen,
  onClose,
  onSubmit,
  paymentForm,
  setPaymentForm,
  selectedAssignment,
}) => {
  if (!isOpen || !selectedAssignment) return null;

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box">
        <h3>Khai Báo Đã Trả Lương Trực Tiếp</h3>
        <p style={{ fontSize: '0.875rem', color: '#4b5563', marginBottom: '1.25rem' }}>
          Theo Mục 11 & 12 SRS, CANi không giữ cọc hay giữ lương. Sau khi chi trả ngoài cho
          Worker, vui lòng khai báo để hệ thống đối soát và Worker bấm xác nhận.
        </p>
        <form onSubmit={onSubmit}>
          <div className="eo-form-group">
            <label>Nhân sự được chi trả:</label>
            <input
              type="text"
              disabled
              className="eo-form-input"
              style={{ background: '#f3f4f6' }}
              value={`${selectedAssignment.workerName} (${selectedAssignment.jobType})`}
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
              <option value="BANK_TRANSFER">Chuyển Khoản Ngân Hàng (Bank Transfer)</option>
              <option value="CASH">Tiền Mặt Trực Tiếp (Cash)</option>
            </select>
          </div>

          <div className="eo-form-group">
            <label>Mã Giao Dịch Ngân Hàng Ngoài (Nếu chuyển khoản)</label>
            <input
              type="text"
              className="eo-form-input"
              placeholder="Ví dụ: VCB-123456789"
              value={paymentForm.externalRef}
              onChange={(e) =>
                setPaymentForm({ ...paymentForm, externalRef: e.target.value })
              }
            />
          </div>

          <div className="eo-form-group">
            <label>Link Ảnh Chụp Biên Lai / Bằng Chứng Chuyển Khoản</label>
            <input
              type="text"
              className="eo-form-input"
              placeholder="https://storage.cani.vn/proofs/bill.jpg"
              value={paymentForm.paymentProofUrl}
              onChange={(e) =>
                setPaymentForm({ ...paymentForm, paymentProofUrl: e.target.value })
              }
            />
          </div>

          <div className="eo-modal-footer">
            <button
              type="button"
              className="eo-btn-secondary"
              onClick={onClose}
            >
              Đóng
            </button>
            <button type="submit" className="eo-btn-primary">
              Gửi Khai Báo Thanh Toán
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default PaymentDeclarationModal;
