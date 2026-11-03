import React, { useState, useEffect } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';

export default function ShiftInviteModal({ isOpen, onClose, shift, event }) {
  const [invite, setInvite] = useState(null);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);

  useEffect(() => {
    if (isOpen && shift) {
      loadInvite();
    } else {
      setInvite(null);
    }
  }, [isOpen, shift]);

  const loadInvite = async () => {
    setLoading(true);
    try {
      const data = await eoService.getShiftInvite(shift.shiftId);
      if (data) {
        setInvite(data);
      } else {
        // Nếu chưa có, tự động sinh mã mới
        handleGenerateNewInvite();
      }
    } catch {
      handleGenerateNewInvite();
    } finally {
      setLoading(false);
    }
  };

  const handleGenerateNewInvite = async () => {
    setGenerating(true);
    try {
      const maxSlots = Math.max(
        1,
        (shift.requiredWorkers || 1) - (shift.confirmedWorkers || 0)
      );
      const newInv = await eoService.createShiftInvite(shift.shiftId, {
        maxUses: maxSlots,
        expiryHours: 72,
      });
      setInvite(newInv);
      showToast('Đã tạo liên kết & mã mời trực tiếp thành công!', 'success');
    } catch (err) {
      showToast('Lỗi tạo mã mời: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setGenerating(false);
    }
  };

  const handleCopy = (text, label) => {
    if (!text) return;
    navigator.clipboard.writeText(text);
    showToast(`Đã sao chép ${label} vào bộ nhớ tạm!`, 'success');
  };

  if (!isOpen || !shift) return null;

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box" style={{ maxWidth: 520, textAlign: 'center' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
          <h3 style={{ margin: 0, textAlign: 'left', color: '#0f172a' }}>
            Mã Mời Trực Tiếp (Invite Mode)
          </h3>
          <button
            type="button"
            onClick={onClose}
            style={{ background: 'none', border: 'none', fontSize: '0.9rem', cursor: 'pointer', color: '#64748b' }}
          >
            Đóng
          </button>
        </div>

        {loading || generating ? (
          <div style={{ padding: '2.5rem', color: '#64748b' }}>
            <div className="cani-spinner" style={{ margin: '0 auto 1rem' }} />
            Đang tải dữ liệu mã mời...
          </div>
        ) : invite ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            {/* Hộp mã code nổi bật */}
            <div
              style={{
                background: 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)',
                border: '1px solid #bfdbfe',
                borderRadius: 12,
                padding: '1.25rem',
              }}
            >
              <div style={{ fontSize: '0.825rem', textTransform: 'uppercase', color: '#1e40af', fontWeight: 600, letterSpacing: '0.05em' }}>
                MÃ THAM GIA CA LÀM
              </div>
              <div style={{ fontSize: '2rem', fontWeight: 800, color: '#1d4ed8', letterSpacing: '0.1em', margin: '0.5rem 0' }}>
                {invite.code}
              </div>
              <button
                type="button"
                onClick={() => handleCopy(invite.code, 'Mã mời')}
                style={{
                  background: '#2563eb',
                  color: '#ffffff',
                  border: 'none',
                  borderRadius: 6,
                  padding: '6px 16px',
                  fontWeight: 600,
                  fontSize: '0.85rem',
                  cursor: 'pointer',
                }}
              >
                Sao Chép Mã
              </button>
            </div>

            {/* Mã QR tham gia */}
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.5rem' }}>
              <div style={{ background: '#ffffff', padding: '12px', border: '1px solid #e2e8f0', borderRadius: 12, display: 'inline-block' }}>
                <QRCodeSVG
                  value={invite.qrContent || `CANI_INVITE:${invite.code}`}
                  size={180}
                  level="H"
                  includeMargin={true}
                />
              </div>
              <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                Worker quét mã bằng Camera trên App để vào ca ngay
              </span>
            </div>

            {/* Link chia sẻ trực tiếp */}
            <div style={{ textAlign: 'left', background: '#f8fafc', padding: '0.85rem 1rem', borderRadius: 8, border: '1px solid #e2e8f0' }}>
              <div style={{ fontSize: '0.8rem', fontWeight: 600, color: '#475569', marginBottom: '0.35rem' }}>
                Đường dẫn chia sẻ (Zalo, Messenger, SMS):
              </div>
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <input
                  type="text"
                  readOnly
                  value={invite.shareLink}
                  style={{
                    flex: 1,
                    fontSize: '0.85rem',
                    padding: '6px 10px',
                    borderRadius: 6,
                    border: '1px solid #cbd5e1',
                    background: '#ffffff',
                    color: '#334155',
                  }}
                />
                <button
                  type="button"
                  onClick={() => handleCopy(invite.shareLink, 'Đường dẫn mời')}
                  style={{
                    background: '#0284c7',
                    color: '#ffffff',
                    border: 'none',
                    borderRadius: 6,
                    padding: '6px 12px',
                    fontWeight: 600,
                    fontSize: '0.85rem',
                    cursor: 'pointer',
                    whiteSpace: 'nowrap',
                  }}
                >
                  Sao chép Link
                </button>
              </div>
            </div>

            {/* Thông tin ca */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: '0.75rem',
                textAlign: 'left',
                fontSize: '0.85rem',
                borderTop: '1px dashed #e2e8f0',
                paddingTop: '0.85rem',
              }}
            >
              <div>
                <span style={{ color: '#64748b' }}>Vị trí / Ca: </span>
                <strong>{invite.jobType}</strong>
              </div>
              <div>
                <span style={{ color: '#64748b' }}>Thù lao: </span>
                <strong style={{ color: '#059669' }}>
                  {Number(invite.hourlyWage || 50000).toLocaleString()} đ/h
                </strong>
              </div>
              <div>
                <span style={{ color: '#64748b' }}>Đã đăng ký qua mã: </span>
                <strong>{invite.usedCount} / {invite.maxUses} lượt</strong>
              </div>
              <div>
                <span style={{ color: '#64748b' }}>Trạng thái: </span>
                <span style={{ color: invite.isActive ? '#059669' : '#dc2626', fontWeight: 600 }}>
                  {invite.isActive ? '● Đang mở nhận' : '● Đã đóng'}
                </span>
              </div>
            </div>
          </div>
        ) : (
          <div>Không tìm thấy thông tin mã mời.</div>
        )}

        <div style={{ marginTop: '1.5rem', display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
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

