import React from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { showToast } from '../../../components/common/Toast';

export default function PayOSQrModal({ isOpen, onClose, data, onConfirm }) {
  if (!isOpen || !data) return null;

  const handleCopy = (text, label) => {
    if (!text) return;
    navigator.clipboard.writeText(text);
    showToast(`Đã sao chép ${label}!`, 'success');
  };

  const formatCurrency = (val) => new Intl.NumberFormat('vi-VN').format(val || 0);

  const qrValue = data.qrCode || `https://img.vietqr.io/image/MB-${data.accountNumber || '2622014789096'}-compact2.png?amount=${data.amount}&addInfo=${encodeURIComponent(data.description || '')}&accountName=${encodeURIComponent(data.accountName || 'PHAM DUY HUNG')}`;

  const isRawQrString = data.qrCode && data.qrCode.startsWith('000201');

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box" style={{ maxWidth: 540, textAlign: 'center' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
          <h3 style={{ margin: 0, textAlign: 'left', display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#0f172a' }}>
            <span>⚡</span> Thanh Toán VietQR Tự Động (PayOS)
          </h3>
          <button
            type="button"
            onClick={onClose}
            style={{ background: 'none', border: 'none', fontSize: '1.25rem', cursor: 'pointer', color: '#64748b' }}
          >
            ✕
          </button>
        </div>

        {/* Card hiển thị QR */}
        <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 12, padding: '1.25rem', marginBottom: '1.25rem' }}>
          <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '0.75rem' }}>
            {isRawQrString ? (
              <div style={{ background: '#ffffff', padding: 12, borderRadius: 12, border: '1px solid #cbd5e1' }}>
                <QRCodeSVG value={qrValue} size={220} level="M" includeMargin={true} />
              </div>
            ) : (
              <img
                src={qrValue}
                alt="VietQR PayOS"
                style={{ width: '100%', maxWidth: 300, borderRadius: 10, border: '1px solid #cbd5e1' }}
              />
            )}
          </div>

          <div style={{ fontSize: '0.85rem', color: '#059669', fontWeight: 600, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.35rem' }}>
            <span style={{ display: 'inline-block', width: 8, height: 8, borderRadius: '50%', background: '#10b981' }} />
            Quét mã bằng App Ngân Hàng bất kỳ (Tự động điền 100%)
          </div>
        </div>

        {/* Thông tin chuyển khoản chi tiết */}
        <div style={{ textAlign: 'left', display: 'flex', flexDirection: 'column', gap: '0.5rem', fontSize: '0.875rem', marginBottom: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.5rem 0.75rem', background: '#f1f5f9', borderRadius: 6 }}>
            <div>
              <span style={{ color: '#64748b' }}>Ngân hàng thụ hưởng: </span>
              <strong>MBBank (Quân Đội)</strong>
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.5rem 0.75rem', background: '#f1f5f9', borderRadius: 6 }}>
            <div>
              <span style={{ color: '#64748b' }}>Chủ tài khoản: </span>
              <strong>{data.accountName || 'PHAM DUY HUNG'}</strong>
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.5rem 0.75rem', background: '#f1f5f9', borderRadius: 6 }}>
            <div>
              <span style={{ color: '#64748b' }}>Số tài khoản: </span>
              <strong style={{ color: '#0f172a', fontSize: '1rem' }}>{data.accountNumber || '2622014789096'}</strong>
            </div>
            <button
              type="button"
              onClick={() => handleCopy(data.accountNumber || '2622014789096', 'Số tài khoản')}
              style={{ background: '#ffffff', border: '1px solid #cbd5e1', borderRadius: 4, padding: '3px 8px', fontSize: '0.75rem', cursor: 'pointer', fontWeight: 600 }}
            >
              Sao chép
            </button>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.5rem 0.75rem', background: '#f1f5f9', borderRadius: 6 }}>
            <div>
              <span style={{ color: '#64748b' }}>Số tiền: </span>
              <strong style={{ color: '#059669', fontSize: '1.1rem' }}>{formatCurrency(data.amount)} đ</strong>
            </div>
            <button
              type="button"
              onClick={() => handleCopy(data.amount?.toString(), 'Số tiền')}
              style={{ background: '#ffffff', border: '1px solid #cbd5e1', borderRadius: 4, padding: '3px 8px', fontSize: '0.75rem', cursor: 'pointer', fontWeight: 600 }}
            >
              Sao chép
            </button>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.5rem 0.75rem', background: '#fef3c7', borderRadius: 6, border: '1px solid #fde68a' }}>
            <div>
              <span style={{ color: '#92400e', fontWeight: 600 }}>Nội dung CK: </span>
              <strong style={{ color: '#b45309', fontFamily: 'monospace', fontSize: '0.95rem' }}>{data.description}</strong>
            </div>
            <button
              type="button"
              onClick={() => handleCopy(data.description, 'Nội dung chuyển khoản')}
              style={{ background: '#ffffff', border: '1px solid #f59e0b', color: '#b45309', borderRadius: 4, padding: '3px 8px', fontSize: '0.75rem', cursor: 'pointer', fontWeight: 600 }}
            >
              Sao chép
            </button>
          </div>
        </div>

        {/* Nút hành động */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.65rem' }}>
          {data.checkoutUrl && data.checkoutUrl.startsWith('http') && (
            <a
              href={data.checkoutUrl}
              target="_blank"
              rel="noreferrer"
              style={{
                display: 'block',
                background: '#0284c7',
                color: '#ffffff',
                padding: '0.75rem',
                borderRadius: 8,
                fontWeight: 700,
                textDecoration: 'none',
                fontSize: '0.9rem',
              }}
            >
              🔗 Mở Trang Thanh Toán PayOS Checkout &rarr;
            </a>
          )}

          <button
            type="button"
            onClick={() => onConfirm(data.orderCode)}
            style={{
              background: '#10b981',
              color: '#ffffff',
              border: 'none',
              padding: '0.75rem',
              borderRadius: 8,
              fontWeight: 700,
              cursor: 'pointer',
              fontSize: '0.9rem',
            }}
          >
            ✅ Tôi Đã Chuyển Khoản Xong (Kích Hoạt Gói)
          </button>

          <button
            type="button"
            className="eo-btn-secondary"
            onClick={onClose}
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}

