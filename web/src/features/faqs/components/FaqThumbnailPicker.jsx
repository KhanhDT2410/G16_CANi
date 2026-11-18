import { useState, useRef } from 'react';
import { faqApi } from '../api/faqApi';

/**
 * Component chọn và tải ảnh Thumbnail / Ảnh đầu bài cho FAQ/Bài viết.
 * Chuẩn thiết kế ảnh mẫu, < 200 dòng theo AGENTS.md.
 */
export const FaqThumbnailPicker = ({ imageUrl, onImageChange, disabled }) => {
  const [uploading, setUploading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const fileInputRef = useRef(null);

  const handleFileChange = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      setErrorMsg('Vui lòng chọn tệp định dạng hình ảnh (PNG, JPG, WEBP, GIF)');
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      setErrorMsg('Dung lượng tệp ảnh không được vượt quá 5MB');
      return;
    }

    setErrorMsg('');
    setUploading(true);

    try {
      const serverUrl = await faqApi.uploadImage(file);
      if (serverUrl) {
        onImageChange(serverUrl);
      }
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải ảnh lên máy chủ';
      setErrorMsg(msg);
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleRemove = () => {
    onImageChange('');
    setErrorMsg('');
  };

  return (
    <div className="faq-thumbnail-picker">
      <label className="faq-field-label">Ảnh thumbnail / ảnh đầu bài</label>
      
      <div className="faq-thumbnail-box">
        <div className="faq-thumbnail-input-row">
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            disabled={disabled || uploading}
            onChange={handleFileChange}
            className="faq-native-file-input"
          />
          {uploading && <span className="faq-uploading-text">Đang tải ảnh lên...</span>}
        </div>

        <p className="faq-thumbnail-hint">
          Ảnh này sẽ hiển thị ở trang danh sách bài viết và đầu trang chi tiết.
        </p>

        {errorMsg && <div className="faq-thumbnail-error">{errorMsg}</div>}

        {imageUrl && (
          <div className="faq-thumbnail-preview-wrap">
            <img src={imageUrl} alt="Thumbnail preview" className="faq-thumbnail-preview" />
            <button
              type="button"
              className="faq-thumbnail-remove-btn"
              onClick={handleRemove}
              disabled={disabled}
              title="Xóa ảnh"
            >
              ✕ Xóa ảnh
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default FaqThumbnailPicker;

