import { useState } from 'react';
import {
  DISPUTE_EVIDENCE_TYPES,
  DISPUTE_EVIDENCE_LABELS,
} from '../types/dispute.constants';

const AddEvidenceModal = ({ dispute, onClose, onSubmit }) => {
  const [fileType, setFileType] = useState(DISPUTE_EVIDENCE_TYPES.IMAGE);
  const [fileUrl, setFileUrl] = useState('');
  const [label, setLabel] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleQuickFill = () => {
    setFileUrl('https://storage.cani.vn/disputes/test-receipt-' + Date.now() + '.jpg');
    setLabel('Biên lai chuyển khoản và sao kê tài khoản ngân hàng');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!fileUrl.trim()) {
      alert('Vui lòng nhập đường dẫn file bằng chứng (URL)');
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
      // Đã bắt toast
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="dispute-modal-overlay" onClick={onClose}>
      <div className="dispute-modal" onClick={(e) => e.stopPropagation()}>
        <div className="dispute-modal-header">
          <div>
            <h3>Nạp Bằng Chứng Tranh Chấp (UC-98)</h3>
            <span style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
              Đơn: <code>{dispute.id}</code>
            </span>
          </div>
          <button type="button" className="dispute-modal-close" onClick={onClose}>✕</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="dispute-modal-body">
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button type="button" className="btn-quick-fill" onClick={handleQuickFill}>
                ⚡ Điền URL mẫu
              </button>
            </div>

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
              <label>Đường dẫn tệp / Hình ảnh chứng từ (URL) *</label>
              <input
                type="url"
                className="dispute-input"
                placeholder="https://storage.cani.vn/disputes/..."
                value={fileUrl}
                onChange={(e) => setFileUrl(e.target.value)}
                required
              />
            </div>

            <div className="dispute-field-group">
              <label>Mô tả / Nhãn bằng chứng</label>
              <input
                type="text"
                className="dispute-input"
                maxLength={255}
                placeholder="Ví dụ: Ảnh chụp màn hình tin nhắn thỏa thuận..."
                value={label}
                onChange={(e) => setLabel(e.target.value)}
              />
            </div>
          </div>

          <div className="dispute-modal-footer">
            <button
              type="button"
              className="btn-quick-fill"
              style={{ background: 'var(--color-bg-subtle)' }}
              onClick={onClose}
              disabled={submitting}
            >
              Hủy
            </button>
            <button
              type="submit"
              className="btn-quick-fill"
              style={{
                color: '#fff',
                background: 'var(--color-primary)',
                borderColor: 'transparent',
                padding: '0.5rem 1.25rem',
              }}
              disabled={submitting || !fileUrl.trim()}
            >
              {submitting ? 'Đang lưu...' : 'Lưu bằng chứng'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default AddEvidenceModal;

