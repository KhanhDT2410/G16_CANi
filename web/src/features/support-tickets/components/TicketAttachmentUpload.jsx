import { useState, useRef } from 'react';
import { supportTicketApi } from '../api/supportTicketApi';
import { resolveAssetUrl, isImageUrl, getFileExtension, getFileName } from '../utils/resolveAssetUrl';
import { showToast } from '../../../components/common/Toast';

export const TicketAttachmentUpload = ({ attachments = [], onChange, disabled = false, maxFiles = 5 }) => {
  const [uploading, setUploading] = useState(false);
  const [showUrlInput, setShowUrlInput] = useState(false);
  const [manualUrl, setManualUrl] = useState('');
  const fileInputRef = useRef(null);

  const handleFileChange = async (e) => {
    const files = Array.from(e.target.files || []);
    if (!files.length) return;

    if (attachments.length + files.length > maxFiles) {
      showToast(`Chỉ được đính kèm tối đa ${maxFiles} tệp`, 'warning');
      return;
    }

    setUploading(true);
    const newItems = [...attachments];

    for (const file of files) {
      if (file.size > 10 * 1024 * 1024) {
        showToast(`Tệp "${file.name}" vượt quá 10MB`, 'warning');
        continue;
      }

      try {
        const res = await supportTicketApi.uploadAttachment(file);
        if (res?.url) {
          newItems.push({
            url: res.url,
            name: res.name || file.name,
            size: res.size || file.size,
          });
          continue;
        }
      } catch {
        // Fallback đọc dạng Base64 Data URI khi endpoint máy chủ không khả dụng
      }

      try {
        const dataUrl = await new Promise((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => resolve(reader.result);
          reader.onerror = reject;
          reader.readAsDataURL(file);
        });
        newItems.push({
          url: dataUrl,
          name: file.name,
          size: file.size,
        });
      } catch {
        showToast(`Không thể đọc tệp "${file.name}"`, 'error');
      }
    }

    setUploading(false);
    if (fileInputRef.current) fileInputRef.current.value = '';
    onChange(newItems);
  };

  const handleAddManualUrl = () => {
    const trimmed = manualUrl.trim();
    if (!trimmed) return;
    if (attachments.length >= maxFiles) {
      showToast(`Chỉ được đính kèm tối đa ${maxFiles} tệp`, 'warning');
      return;
    }
    onChange([...attachments, { url: trimmed, name: getFileName(trimmed) }]);
    setManualUrl('');
    setShowUrlInput(false);
  };

  const handleRemove = (index) => {
    const updated = attachments.filter((_, i) => i !== index);
    onChange(updated);
  };

  const formatSize = (bytes) => {
    if (!bytes || bytes <= 0) return '';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  return (
    <div className="ticket-attachment-upload-box">
      <div className="ticket-attachment-upload-actions">
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept="image/*,.pdf,.doc,.docx,.xls,.xlsx,.txt,.csv,.zip"
          style={{ display: 'none' }}
          onChange={handleFileChange}
          disabled={disabled || uploading || attachments.length >= maxFiles}
        />
        <button
          type="button"
          className="ticket-btn ticket-btn-secondary ticket-btn-sm"
          onClick={() => fileInputRef.current?.click()}
          disabled={disabled || uploading || attachments.length >= maxFiles}
        >
          {uploading ? 'Đang tải tệp lên...' : 'Tải tệp từ thiết bị'}
        </button>
        <button
          type="button"
          className="ticket-btn ticket-btn-link ticket-btn-sm"
          onClick={() => setShowUrlInput(!showUrlInput)}
          disabled={disabled || uploading}
        >
          {showUrlInput ? 'Ẩn nhập liên kết' : 'Dán liên kết tệp'}
        </button>
      </div>

      {showUrlInput && (
        <div className="ticket-manual-url-row">
          <input
            type="url"
            className="ticket-form-input"
            placeholder="https://storage.cani.vn/evidence.pdf"
            value={manualUrl}
            onChange={(e) => setManualUrl(e.target.value)}
            disabled={disabled}
          />
          <button
            type="button"
            className="ticket-btn ticket-btn-primary ticket-btn-sm"
            onClick={handleAddManualUrl}
            disabled={disabled || !manualUrl.trim()}
          >
            Thêm
          </button>
        </div>
      )}

      {attachments.length > 0 && (
        <div className="ticket-attachments-preview-list">
          {attachments.map((item, idx) => {
            const fileUrl = typeof item === 'string' ? item : item.url;
            const fileName = (typeof item === 'object' && item.name) ? item.name : getFileName(fileUrl);
            const fileSize = (typeof item === 'object' && item.size) ? formatSize(item.size) : '';
            const isImg = isImageUrl(fileUrl);

            return (
              <div key={idx} className="ticket-attachment-preview-card">
                {isImg ? (
                  <img
                    src={resolveAssetUrl(fileUrl)}
                    alt={fileName}
                    className="ticket-attachment-thumb"
                  />
                ) : (
                  <span className="ticket-attachment-file-tag">
                    {getFileExtension(fileUrl)}
                  </span>
                )}
                <div className="ticket-attachment-meta">
                  <span className="ticket-attachment-name" title={fileName}>
                    {fileName}
                  </span>
                  {fileSize && (
                    <span className="ticket-attachment-size">{fileSize}</span>
                  )}
                </div>
                {!disabled && (
                  <button
                    type="button"
                    className="ticket-attachment-remove-btn"
                    onClick={() => handleRemove(idx)}
                    title="Xóa tệp đính kèm này"
                  >
                    Xóa
                  </button>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default TicketAttachmentUpload;
