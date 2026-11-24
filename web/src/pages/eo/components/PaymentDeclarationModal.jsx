import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { eoService } from '../../../services/eoService';
import { resolveAssetUrl, buildWorkerVietQrUrl } from '../utils/resolveAssetUrl';
import { roundVndAmount } from '../utils/eventHelpers';

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
  const [uploadingProof, setUploadingProof] = useState(false);

  useEffect(() => {
    if (!isOpen) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      document.body.style.overflow = prev;
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen || !selectedAssignment) return null;

  const bankCode = selectedAssignment.bankCode;
  const accountNumber = selectedAssignment.bankAccountNumber;
  const accountName = selectedAssignment.bankAccountName || selectedAssignment.workerName || '';
  const amount = roundVndAmount(
    paymentForm.declaredAmount || selectedAssignment.agreedWage || selectedAssignment.agreedAmount || 0
  );
  const hasBankInfo = Boolean(bankCode && accountNumber);

  const dynamicVietQrUrl = buildWorkerVietQrUrl(selectedAssignment, amount);

  return createPortal(
    <div
      className="eo-modal-overlay"
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        width: '100vw',
        height: '100vh',
        backgroundColor: 'rgba(15, 23, 42, 0.65)',
        backdropFilter: 'blur(6px)',
        WebkitBackdropFilter: 'blur(6px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 99999,
        padding: '1rem',
        boxSizing: 'border-box',
      }}
    >
      <div
        className="eo-modal-box animate-scale-up"
        onClick={(e) => e.stopPropagation()}
        style={{
          maxWidth: '540px',
          margin: 'auto',
          position: 'relative',
        }}
      >
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
              KHỐI MÃ VIETQR HOẶC HƯỚNG DẪN TRẢ TIỀN MẶT (MỤC 11 & 12 SRS)
              ========================================================================= */}
          {paymentForm.paymentMethod === 'BANK_TRANSFER' ? (
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
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <div>
                          <span style={{ color: '#64748b' }}>Ngân hàng: </span>
                          <strong style={{ color: '#0f172a' }}>{bankCode}</strong>
                        </div>
                        <span style={{ fontSize: '0.68rem', color: '#166534', background: '#dcfce7', padding: '1px 6px', borderRadius: 4, fontWeight: 600 }}>
                          🔒 Mã hóa FLE
                        </span>
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
                <div style={{ textAlign: 'center', padding: '0.85rem', color: '#64748b', fontSize: '0.82rem' }}>
                  <div style={{ fontSize: '1.4rem', marginBottom: '0.35rem' }}>⚠️</div>
                  <strong style={{ color: '#b45309' }}>Worker thiếu thông tin ngân hàng</strong>
                  <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: 4, lineHeight: 1.45 }}>
                    Nhân sự này chưa cập nhật tài khoản nhận lương trên ứng dụng di động CANi. Hệ thống đã ẩn mã QR chuyển khoản.
                    Vui lòng chọn hình thức <strong>Tiền Mặt Trực Tiếp</strong> bên trên để chi trả.
                  </div>
                  <button
                    type="button"
                    onClick={() => setPaymentForm({ ...paymentForm, paymentMethod: 'CASH' })}
                    style={{
                      marginTop: 8,
                      background: '#fef3c7',
                      color: '#92400e',
                      border: '1px solid #fde68a',
                      borderRadius: 6,
                      padding: '4px 10px',
                      fontSize: '0.75rem',
                      fontWeight: 700,
                      cursor: 'pointer',
                    }}
                  >
                    Chuyển sang trả Tiền Mặt Trực Tiếp
                  </button>
                </div>
              )}
            </div>
          ) : (
            <div
              style={{
                margin: '1rem 0',
                padding: '1rem',
                backgroundColor: '#fffbeb',
                border: '1.5px solid #fcd34d',
                borderRadius: '8px',
                fontSize: '0.82rem',
                lineHeight: 1.5,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4, fontWeight: 700, color: '#92400e' }}>
                <span>💵</span> Nhận Tiền Mặt Trực Tiếp
              </div>
              <div style={{ color: '#78350f', fontSize: '0.8rem' }}>
                Số tiền cần trao: <strong style={{ color: '#b45309', fontSize: '0.92rem' }}>{new Intl.NumberFormat('vi-VN').format(amount)} đ</strong>
              </div>
              <div style={{ color: '#64748b', fontSize: '0.75rem', marginTop: 4 }}>
                Hướng dẫn: Vui lòng trao tiền mặt trực tiếp cho nhân sự tại bàn điều phối sự kiện hoặc địa điểm hai bên đã thống nhất.
                Hai bên xác nhận hoàn tất trên hệ thống sau khi giao nhận tiền mặt (có thể đính kèm ảnh biên nhận nếu có).
              </div>
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
            <label>
              {paymentForm.paymentMethod === 'BANK_TRANSFER'
                ? 'Tải Lên Bằng Chứng / Ảnh Chụp Biên Lai Chuyển Khoản *'
                : 'Ảnh Chụp Biên Nhận Tiền Mặt (Tùy chọn)'}
            </label>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              <input
                type="file"
                accept="image/*"
                disabled={uploadingProof}
                onChange={async (e) => {
                  const file = e.target.files?.[0];
                  if (!file) return;
                  setUploadingProof(true);
                  try {
                    const uploadedUrl = await eoService.uploadImage(file);
                    if (uploadedUrl) {
                      setPaymentForm((prev) => ({ ...prev, paymentProofUrl: uploadedUrl }));
                      return;
                    }
                  } catch {
                    // Fallback Base64 Data URI khi endpoint upload không khả dụng (VD: chế độ Demo)
                  } finally {
                    setUploadingProof(false);
                  }
                  const reader = new FileReader();
                  reader.onload = () => {
                    setPaymentForm((prev) => ({ ...prev, paymentProofUrl: reader.result }));
                  };
                  reader.readAsDataURL(file);
                }}
                className="eo-form-input"
                style={{ padding: '6px' }}
              />
              {uploadingProof && (
                <div style={{ fontSize: '0.78rem', color: '#2563eb', fontWeight: 600 }}>
                  Đang tải ảnh chứng từ lên máy chủ...
                </div>
              )}
              {paymentForm.paymentProofUrl && (
                <div style={{ position: 'relative', width: 'fit-content', border: '1px solid #cbd5e1', borderRadius: 8, padding: 6, background: '#f8fafc' }}>
                  <img
                    src={resolveAssetUrl(paymentForm.paymentProofUrl)}
                    alt="Chứng từ thanh toán"
                    style={{ maxHeight: '140px', maxWidth: '240px', objectFit: 'contain', borderRadius: 6, display: 'block' }}
                  />
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 4 }}>
                    <span style={{ fontSize: '0.7rem', color: '#059669', fontWeight: 600 }}>
                      ✓ Đã tải chứng từ xác nhận
                    </span>
                    <button
                      type="button"
                      onClick={() => setPaymentForm({ ...paymentForm, paymentProofUrl: '' })}
                      style={{ background: 'none', border: 'none', color: '#ef4444', fontSize: '0.72rem', cursor: 'pointer', fontWeight: 700 }}
                    >
                      Xóa / Thay ảnh
                    </button>
                  </div>
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
            <strong>ℹ️ Quy trình đối soát 2 bên:</strong> Sau khi Ban tổ chức bấm <strong>{paymentForm.paymentMethod === 'BANK_TRANSFER' ? '"Xác nhận đã thanh toán"' : '"Xác nhận đã trả tiền mặt"'}</strong>, hệ thống sẽ gửi thông báo đến ứng dụng của Worker để Worker kiểm tra và xác nhận hoàn tất.
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
              disabled={uploadingProof || (paymentForm.paymentMethod === 'BANK_TRANSFER' && !paymentForm.paymentProofUrl)}
              style={{
                background: (uploadingProof || (paymentForm.paymentMethod === 'BANK_TRANSFER' && !paymentForm.paymentProofUrl))
                  ? '#94a3b8'
                  : 'linear-gradient(135deg, #059669 0%, #10b981 100%)',
                border: 'none',
                cursor: (uploadingProof || (paymentForm.paymentMethod === 'BANK_TRANSFER' && !paymentForm.paymentProofUrl)) ? 'not-allowed' : 'pointer',
              }}
            >
              {paymentForm.paymentMethod === 'BANK_TRANSFER' ? 'Xác Nhận Đã Thanh Toán' : 'Xác Nhận Đã Trả Tiền Mặt'}
            </button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
};

export default PaymentDeclarationModal;
