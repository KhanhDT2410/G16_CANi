import React, { useState } from 'react';
import { QRCodeSVG } from 'qrcode.react';

/**
 * ShiftQrModal
 * UC-52: Sinh mã QR Điểm danh của Ca làm việc (Check-in & Check-out QR)
 */
const ShiftQrModal = ({
  isOpen,
  onClose,
  qrData,
  loading,
}) => {
  const [activeTab, setActiveTab] = useState('CHECKIN'); // 'CHECKIN' | 'CHECKOUT'

  if (!isOpen) return null;

  const isCheckin = activeTab === 'CHECKIN';
  const accentColor = isCheckin ? '#2563eb' : '#0d9488';
  const currentPayload = isCheckin
    ? (qrData?.checkinQrContent || `CANI_CHECKIN:${qrData?.shiftId || ''}`)
    : (qrData?.checkoutQrContent || `CANI_CHECKOUT:${qrData?.shiftId || ''}`);

  const handlePrint = () => {
    const printQrUrl = `https://api.qrserver.com/v1/create-qr-code/?size=300x300&margin=10&data=${encodeURIComponent(currentPayload)}`;
    const printWindow = window.open('', '_blank');
    if (!printWindow) return;
    printWindow.document.write(`
      <html>
        <head>
          <title>Mã QR Điểm Danh - CANi</title>
          <style>
            body { font-family: sans-serif; text-align: center; padding: 40px; }
            .card { border: 2px solid #2563eb; border-radius: 16px; padding: 30px; display: inline-block; max-width: 450px; }
            h2 { color: #1e3a8a; margin: 0 0 10px; }
            h3 { color: #2563eb; margin: 0 0 20px; }
            .info { font-size: 14px; color: #475569; margin-bottom: 20px; line-height: 1.5; }
            img { width: 280px; height: 280px; margin: 10px 0; }
            .badge { display: inline-block; padding: 6px 14px; background: #dbeafe; color: #1d4ed8; border-radius: 20px; font-weight: bold; margin-bottom: 15px; }
          </style>
        </head>
        <body>
          <div class="card">
            <h2>HỆ THỐNG ĐIỂM DANH CANi</h2>
            <div class="badge">${isCheckin ? 'MÃ QR CHECK-IN (BẮT ĐẦU CA)' : 'MÃ QR CHECK-OUT (KẾT THÚC CA)'}</div>
            <h3>${qrData?.eventName || 'Sự kiện'}</h3>
            <div class="info">
              <strong>Vị trí:</strong> ${qrData?.jobType || 'Nhân sự'}<br>
              <strong>Địa điểm:</strong> ${qrData?.venueAddress || 'Tại điểm sự kiện'}
            </div>
            <img src="${printQrUrl}" alt="QR Code" />
            <p style="font-size: 13px; color: #64748b; margin-top: 15px;">
              Worker mở ứng dụng <strong>CANi</strong> trên điện thoại, chọn mục <em>Ca của tôi</em> và nhấn <strong>Quét mã QR</strong> để điểm danh.
            </p>
          </div>
          <script>
            window.onload = function() { window.print(); window.close(); }
          </script>
        </body>
      </html>
    `);
    printWindow.document.close();
  };

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box" style={{ maxWidth: 480, textAlign: 'center' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h3 style={{ margin: 0, textAlign: 'left' }}>📱 Mã QR Điểm Danh Ca Làm</h3>
          <button type="button" onClick={onClose} style={{ background: 'none', border: 'none', fontSize: '1.2rem', cursor: 'pointer', color: '#64748b' }}>✕</button>
        </div>

        {/* Tab Switcher: Check-in vs Check-out */}
        <div style={{ display: 'flex', background: '#f1f5f9', borderRadius: 8, padding: 4, marginBottom: '1.2rem' }}>
          <button
            type="button"
            onClick={() => setActiveTab('CHECKIN')}
            style={{
              flex: 1,
              padding: '8px 12px',
              border: 'none',
              borderRadius: 6,
              background: isCheckin ? '#2563eb' : 'transparent',
              color: isCheckin ? '#ffffff' : '#64748b',
              fontWeight: 600,
              fontSize: '0.85rem',
              cursor: 'pointer',
              transition: 'all 0.2s',
            }}
          >
            🟢 Check-in (Vào ca)
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('CHECKOUT')}
            style={{
              flex: 1,
              padding: '8px 12px',
              border: 'none',
              borderRadius: 6,
              background: !isCheckin ? '#0d9488' : 'transparent',
              color: !isCheckin ? '#ffffff' : '#64748b',
              fontWeight: 600,
              fontSize: '0.85rem',
              cursor: 'pointer',
              transition: 'all 0.2s',
            }}
          >
            🔵 Check-out (Tan ca)
          </button>
        </div>

        {loading ? (
          <div style={{ padding: '3rem 0', color: '#64748b' }}>
            🔄 Đang tải mã QR ca làm...
          </div>
        ) : (
          <div>
            <div style={{ marginBottom: '0.8rem', fontSize: '0.9rem', color: '#1e293b' }}>
              <strong>{qrData?.jobType || 'Nhân sự ca'}</strong> • {qrData?.eventName || ''}
            </div>

            {/* QR Card Frame - Render trực tiếp bằng SVG (Offline-Ready) */}
            <div style={{
              background: '#ffffff',
              padding: 16,
              borderRadius: 16,
              display: 'inline-block',
              border: `2px dashed ${accentColor}`,
              boxShadow: '0 4px 12px rgba(0,0,0,0.05)',
            }}>
              <QRCodeSVG
                value={currentPayload}
                size={210}
                level="H"
                includeMargin
                fgColor="#0f172a"
              />
              <div style={{
                marginTop: 8,
                fontSize: '0.75rem',
                color: '#64748b',
                fontFamily: 'monospace',
                background: '#f8fafc',
                padding: '4px 8px',
                borderRadius: 4,
              }}>
                Payload: {currentPayload}
              </div>
            </div>

            <p style={{ fontSize: '0.82rem', color: '#64748b', margin: '1rem 0 1.2rem', lineHeight: 1.4 }}>
              💡 <em>Hướng dẫn:</em> Hiển thị màn hình này tại bàn tiếp tân sự kiện hoặc in ra giấy để Worker quét mã trực tiếp trên ứng dụng CANi.
            </p>

            <div style={{ display: 'flex', gap: 10, justifyContent: 'center' }}>
              <button
                type="button"
                className="eo-btn-submit"
                onClick={handlePrint}
                style={{
                  background: accentColor,
                  padding: '8px 16px',
                  fontSize: '0.85rem',
                }}
              >
                🖨️ In Mã QR Ra Giấy
              </button>
              <button
                type="button"
                className="eo-btn-cancel"
                onClick={onClose}
                style={{ padding: '8px 16px', fontSize: '0.85rem' }}
              >
                Đóng
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default ShiftQrModal;
