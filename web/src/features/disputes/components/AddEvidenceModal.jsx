import { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { showToast } from '../../../components/common/Toast';
import { eoService } from '../../../services/eoService';
import {
  DISPUTE_EVIDENCE_TYPES,
  DISPUTE_EVIDENCE_LABELS,
} from '../types/dispute.constants';

const AddEvidenceModal = ({ dispute, onClose, onSubmit }) => {
  const [fileType, setFileType] = useState(DISPUTE_EVIDENCE_TYPES.IMAGE);
  const [fileUrl, setFileUrl] = useState('');
  const [label, setLabel] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = prev;
    };
  }, []);

  const handleFileUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    try {
      const url = await eoService.uploadImage(file);
      if (url) {
        setFileUrl(url);
        setFileType(DISPUTE_EVIDENCE_TYPES.IMAGE);
        if (!label) setLabel(file.name);
        showToast('Tải ảnh minh chứng lên thành công', 'success');
      }
    } catch (err) {
      showToast('Lỗi tải ảnh: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setUploading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!fileUrl.trim()) {
      showToast('Vui lòng nhập đường dẫn tệp bằng chứng (URL)', 'warning');
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit(dispute.id, {
        fileType,
        fileUrl: fileUrl.trim(),
        label: label.trim() || undefined,
      });
      onClose();
    } catch {
      // Toast lỗi đã được hook xử lý
    } finally {
      setSubmitting(false);
    }
  };

  return createPortal(
    <div className="dispute-modal-overlay" onClick={onClose}>
      <div className="dispute-modal" onClick={(e) => e.stopPropagation()}>
        <div className="dispute-modal-header">
          <div>
            <h3>Nạp Bằng Chứng Tranh Chấp</h3>
            <span className="dispute-modal-subtitle">
              Đơn: <code>{dispute.id}</code>
            </span>
          </div>
          <button type="button" className="dispute-modal-close" onClick={onClose}>&times;</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="dispute-modal-body">
            <div className="dispute-field-group">
              <label>Loại bằng chứng *</label>
              <select
                className="dispute-select"
                value={fileType}
                onChange={(e) => setFileType(e.target.value)}
              >
                {Object.entries(DISPUTE_EVIDENCE_TYPES).map(([k, val]) => (
                  <option key={k} value={val}>
                    {DISPUTE_EVIDENCE_LABELS[val] || val}
                  </option>
                ))}
              </select>
            </div>

            <div className="dispute-field-group">
              <label>Đường dẫn tệp chứng từ / Hình ảnh *</label>
              <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginBottom: '0.5rem' }}>
                <label className="btn-quick-fill" style={{ margin: 0, cursor: 'pointer', display: 'inline-flex', alignItems: 'center' }}>
                  {uploading ? 'Đang tải ảnh...' : 'Chọn ảnh từ thiết bị'}
                  <input
                    type="file"
                    accept="image/*"
                    style={{ display: 'none' }}
                    onChange={handleFileUpload}
                    disabled={uploading}
                  />
                </label>
                <span style={{ fontSize: '0.78rem', color: '#64748b' }}>hoặc dán đường dẫn trực tiếp</span>
              </div>
              <input
                type="text"
                className="dispute-input"
                placeholder="https://... hoặc đường dẫn hình ảnh"
                value={fileUrl}
                onChange={(e) => setFileUrl(e.target.value)}
                required
              />
              {fileUrl && fileType === DISPUTE_EVIDENCE_TYPES.IMAGE && (
                <div style={{ marginTop: '0.5rem' }}>
                  <img
                    src={fileUrl}
                    alt="Xem trước ảnh"
                    style={{ maxHeight: 100, maxWidth: '100%', borderRadius: 6, border: '1px solid #cbd5e1', objectFit: 'cover' }}
                  />
                </div>
              )}
            </div>

            <div className="dispute-field-group">
              <label>Nhãn / Chú thích tệp</label>
              <input
                type="text"
                className="dispute-input"
                maxLength={255}
                placeholder="Ví dụ: Ảnh chụp màn hình tin nhắn thỏa thuận phụ cấp..."
                value={label}
                onChange={(e) => setLabel(e.target.value)}
              />
            </div>
          </div>

          <div className="dispute-modal-footer">
            <button
              type="button"
              className="btn-cancel"
              onClick={onClose}
              disabled={submitting}
            >
              Hủy
            </button>
            <button
              type="submit"
              className="btn-submit"
              disabled={submitting || !fileUrl.trim()}
            >
              {submitting ? 'Đang lưu...' : 'Lưu bằng chứng'}
            </button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
};

export default AddEvidenceModal;
