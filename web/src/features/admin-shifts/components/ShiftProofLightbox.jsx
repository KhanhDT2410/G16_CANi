import React, { useState } from 'react';
import { formatDateTime, resolveAssetUrl } from '../utils/shiftModalHelpers';

/**
 * Hộp thoại phóng to ảnh minh chứng / điểm danh của nhân sự.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const ShiftProofLightbox = ({ previewImage, onClose }) => {
  const [loadError, setLoadError] = useState(false);
  if (!previewImage) return null;

  const rawUrl = typeof previewImage === 'string' ? previewImage : previewImage.url;
  const fullUrl = resolveAssetUrl(rawUrl);

  return (
    <div className="proof-lightbox-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="proof-lightbox-card" onClick={(e) => e.stopPropagation()}>
        <div className="proof-lightbox-header">
          <div>
            <div style={{ fontWeight: 700, fontSize: '0.92rem', color: '#0f172a' }}>
              {previewImage.title || 'Ảnh Bằng Chứng'}
            </div>
            {previewImage.time && (
              <div style={{ fontSize: '0.78rem', color: '#64748b', marginTop: '2px', fontWeight: 500 }}>
                Thời gian nộp: {formatDateTime(previewImage.time)}
              </div>
            )}
          </div>
          <button type="button" onClick={onClose} aria-label="Đóng cửa sổ">✕</button>
        </div>

        {loadError ? (
          <div style={{ padding: '2rem 1.5rem', textAlign: 'center', color: '#64748b', fontSize: '0.88rem' }}>
            <div style={{ fontWeight: 600, color: '#dc2626', marginBottom: '0.5rem' }}>
              Không thể tải tệp ảnh từ máy chủ
            </div>
            <div>Tệp ảnh chưa được lưu trữ trên ổ đĩa máy chủ hoặc đường dẫn không hợp lệ.</div>
            <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: '0.5rem', wordBreak: 'break-all' }}>
              {rawUrl}
            </div>
          </div>
        ) : (
          <img
            src={fullUrl}
            alt="Bằng chứng"
            className="proof-full-img"
            onError={() => setLoadError(true)}
          />
        )}
      </div>
    </div>
  );
};

export default ShiftProofLightbox;
