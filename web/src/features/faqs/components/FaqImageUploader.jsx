import { useState, useRef } from 'react';
import { faqApi } from '../api/faqApi';

/**
 * Component hỗ trợ Kéo & Thả hoặc Chọn ảnh từ thiết bị local cho FAQ (ADM-08).
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const FaqImageUploader = ({ imageUrl, onImageChange, disabled }) => {
  const [isDragOver, setIsDragOver] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');
  const [showUrlInput, setShowUrlInput] = useState(false);
  const fileInputRef = useRef(null);

  const processFile = async (file) => {
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      setUploadError('Chỉ chấp nhận tệp định dạng hình ảnh (PNG, JPG, WEBP, GIF)');
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      setUploadError('Dung lượng tệp ảnh không được vượt quá 5MB');
      return;
    }

    setUploadError('');
    setIsUploading(true);

    try {
      const serverUrl = await faqApi.uploadImage(file);
      if (serverUrl) {
        onImageChange(serverUrl);
      }
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải ảnh từ thiết bị lên server';
      setUploadError(msg);
    } finally {
      setIsUploading(false);
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragOver(false);
    if (disabled || isUploading) return;
    const file = e.dataTransfer?.files?.[0];
    if (file) processFile(file);
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    if (!disabled && !isUploading) setIsDragOver(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    setIsDragOver(false);
  };

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (file) processFile(file);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleRemoveImage = () => {
    onImageChange('');
    setUploadError('');
  };

  return (
    <div className="faq-image-uploader">
      <div className="faq-uploader-header">
        <label className="faq-uploader-label">Ảnh minh họa</label>
        <button
          type="button"
          className="faq-toggle-mode-btn"
          onClick={() => setShowUrlInput((p) => !p)}
          disabled={disabled || isUploading}
        >
          {showUrlInput ? 'Tải từ thiết bị' : 'Nhập liên kết ngoài'}
        </button>
      </div>

      {uploadError && <div className="faq-uploader-error">{uploadError}</div>}

      {showUrlInput ? (
        <input
          type="url"
          className="faq-input-control"
          placeholder="https://.../anh-minh-hoa.png"
          value={imageUrl || ''}
          onChange={(e) => onImageChange(e.target.value)}
          disabled={disabled || isUploading}
        />
      ) : imageUrl ? (
        <div className="faq-image-preview-card">
          <img
            src={imageUrl}
            alt="Ảnh minh họa"
            className="faq-uploader-thumb"
            onError={(e) => { e.target.style.display = 'none'; }}
          />
          <div className="faq-preview-info">
            <span className="faq-preview-status">Đã đính kèm ảnh minh họa</span>
            <div className="faq-preview-actions">
              <button
                type="button"
                className="btn-faq-replace-img"
                onClick={() => fileInputRef.current?.click()}
                disabled={disabled || isUploading}
              >
                Đổi ảnh khác
              </button>
              <button
                type="button"
                className="btn-faq-remove-img"
                onClick={handleRemoveImage}
                disabled={disabled || isUploading}
              >
                Xóa ảnh
              </button>
            </div>
          </div>
        </div>
      ) : (
        <div
          className={`faq-dropzone ${isDragOver ? 'dragover' : ''} ${isUploading ? 'uploading' : ''}`}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          onClick={() => !isUploading && fileInputRef.current?.click()}
          role="button"
          tabIndex={0}
        >
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileChange}
            accept="image/*"
            style={{ display: 'none' }}
            disabled={disabled || isUploading}
          />
          {isUploading ? (
            <div className="faq-dropzone-uploading">
              <span className="faq-spinner" />
              <span>Đang tải ảnh từ thiết bị lên...</span>
            </div>
          ) : (
            <div className="faq-dropzone-content">
              <span className="faq-dropzone-icon">📷</span>
              <p className="faq-dropzone-title">
                Kéo thả ảnh vào đây hoặc <strong>bấm để chọn từ máy tính</strong>
              </p>
              <span className="faq-dropzone-hint">
                Hỗ trợ PNG, JPG, JPEG, WEBP, GIF (Tối đa 5MB)
              </span>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default FaqImageUploader;

