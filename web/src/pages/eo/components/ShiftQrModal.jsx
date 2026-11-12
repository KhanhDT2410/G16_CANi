import React, { useState, useEffect } from 'react';
import { QRCodeSVG } from 'qrcode.react';

/**
 * ShiftQrModal
 * UC-52: Sinh mã QR Điểm danh ĐỘNG của Ca làm việc (Time-based Rotating Dynamic QR)
 * Quy định bảo mật: Tuyệt đối KHÔNG in ra giấy, mã xoay vòng tự động mỗi 20s chống gian lận/chụp ảnh từ xa.
 */
const ShiftQrModal = ({
  isOpen,
  onClose,
  qrData,
  loading,
}) => {
  const [activeTab, setActiveTab] = useState('CHECKIN'); // 'CHECKIN' | 'CHECKOUT'
  const ROTATE_INTERVAL = 20; // 20 giây làm mới 1 lần
  const [countdown, setCountdown] = useState(ROTATE_INTERVAL);
  const [refreshNonce, setRefreshNonce] = useState(Date.now());

  useEffect(() => {
    if (!isOpen) return;
    const timer = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          setRefreshNonce(Date.now());
          return ROTATE_INTERVAL;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [isOpen]);

  if (!isOpen) return null;

  const isCheckin = activeTab === 'CHECKIN';
  const accentColor = isCheckin ? '#2563eb' : '#0d9488';
  const epochSeconds = Math.floor(refreshNonce / 1000);
  const currentPayload = isCheckin
    ? `CANI_CHECKIN:${qrData?.shiftId || ''}:${epochSeconds}:${refreshNonce.toString(36)}`
    : `CANI_CHECKOUT:${qrData?.shiftId || ''}:${epochSeconds}:${refreshNonce.toString(36)}`;

  const handleManualRefresh = () => {
    setRefreshNonce(Date.now());
    setCountdown(ROTATE_INTERVAL);
  };

  const progressPercent = Math.round((countdown / ROTATE_INTERVAL) * 100);

  return (
    <div className="eo-modal-overlay">
      <style>{`
        @media print {
          .eo-modal-overlay, .shift-qr-modal-container {
            display: none !important;
          }
        }
      `}</style>
      <div className="eo-modal-box shift-qr-modal-container" style={{ maxWidth: 490, textAlign: 'center' }}>
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <span style={{ fontSize: '1.25rem' }}>🛡️</span>
            <h3 style={{ margin: 0, textAlign: 'left', fontSize: '1.15rem', fontWeight: 700, color: '#1e293b' }}>
              Mã QR Điểm Danh Động (Dynamic QR)
            </h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              fontSize: '0.9rem',
              cursor: 'pointer',
              color: '#64748b',
              fontWeight: 600,
            }}
          >
            Đóng
          </button>
        </div>

        {/* Tab Switcher: Check-in vs Check-out */}
        <div style={{ display: 'flex', background: '#f1f5f9', borderRadius: 8, padding: 4, marginBottom: '1rem' }}>
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
            Check-in (Vào ca)
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
            Check-out (Tan ca)
          </button>
        </div>

        {loading ? (
          <div style={{ padding: '3rem 0', color: '#64748b' }}>
            Đang tạo mã QR bảo mật...
          </div>
        ) : (
          <div>
            <div style={{ marginBottom: '0.75rem', fontSize: '0.9rem', color: '#1e293b' }}>
              <strong>{qrData?.jobType || 'Nhân sự ca'}</strong> • {qrData?.eventName || ''}
            </div>

            {/* Countdown Bar & Refresh Row */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              backgroundColor: '#eff6ff',
              border: '1px solid #bfdbfe',
              padding: '0.45rem 0.85rem',
              borderRadius: '0.5rem',
              marginBottom: '0.85rem',
              fontSize: '0.825rem',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#1e40af', fontWeight: 600 }}>
                <span className="pulse-dot" style={{ display: 'inline-block', width: 8, height: 8, borderRadius: '50%', backgroundColor: '#2563eb' }} />
                <span>Mã tự động đổi sau: <strong>{countdown}s</strong></span>
              </div>
              <button
                type="button"
                onClick={handleManualRefresh}
                style={{
                  background: 'none',
                  border: 'none',
                  color: '#2563eb',
                  fontWeight: 600,
                  fontSize: '0.8rem',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.25rem',
                }}
                title="Đổi mã QR ngay lập tức"
              >
                🔄 Đổi mã ngay
              </button>
            </div>

            {/* Thanh tiến trình thời gian */}
            <div style={{ width: '100%', height: '4px', backgroundColor: '#e2e8f0', borderRadius: '2px', marginBottom: '1rem', overflow: 'hidden' }}>
              <div
                style={{
                  height: '100%',
                  width: `${progressPercent}%`,
                  backgroundColor: countdown <= 5 ? '#ef4444' : accentColor,
                  transition: 'width 1s linear, background-color 0.3s',
                }}
              />
            </div>

            {/* QR Card Frame - Render trực tiếp bằng SVG */}
            <div style={{
              background: '#ffffff',
              padding: 16,
              borderRadius: 16,
              display: 'inline-block',
              border: `2px solid ${accentColor}`,
              boxShadow: '0 8px 24px rgba(0,0,0,0.08)',
              position: 'relative',
            }}>
              <QRCodeSVG
                value={currentPayload}
                size={220}
                level="H"
                includeMargin
                fgColor="#0f172a"
              />
              <div style={{
                marginTop: 8,
                fontSize: '0.72rem',
                color: '#64748b',
                fontFamily: 'monospace',
                background: '#f8fafc',
                padding: '4px 8px',
                borderRadius: 4,
                letterSpacing: '-0.02em',
                wordBreak: 'break-all',
              }}>
                Mã động: {currentPayload}
              </div>
            </div>

            {/* Cảnh báo an toàn: KHÔNG CHO IN RA GIẤY */}
            <div style={{
              backgroundColor: '#fffbeb',
              border: '1px solid #fde68a',
              borderRadius: '0.625rem',
              padding: '0.75rem 1rem',
              marginTop: '1.25rem',
              textAlign: 'left',
              fontSize: '0.8rem',
              color: '#92400e',
              lineHeight: 1.5,
            }}>
              <div style={{ fontWeight: 700, marginBottom: '0.25rem', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                <span>🚫 Không hỗ trợ in ra giấy & chống chụp ảnh:</span>
              </div>
              <div>
                Để triệt tiêu gian lận check-in hộ từ xa, mã QR này <strong>chỉ có hiệu lực quét trực tiếp trên màn hình thiết bị điều phối</strong> tại sự kiện kết hợp bán kính định vị Geofence GPS 200m. Mã sẽ tự vô hiệu hóa sau thời gian làm mới.
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default ShiftQrModal;
