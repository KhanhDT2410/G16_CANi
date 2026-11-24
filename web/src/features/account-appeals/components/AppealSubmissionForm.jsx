import React, { useState } from 'react';
import { APPEAL_REASON_CATEGORIES } from '../types/accountAppeal.constants';
import { accountAppealApi } from '../api/accountAppealApi';
import { showToast } from '../../../components/common/Toast';

export default function AppealSubmissionForm({ email, appealToken, onSubmitSuccess }) {
  const [reasonCategory, setReasonCategory] = useState(APPEAL_REASON_CATEGORIES[0].value);
  const [appealContent, setAppealContent] = useState('');
  const [attachmentUrls, setAttachmentUrls] = useState([]);
  const [uploadingFile, setUploadingFile] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const handleFileUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 10 * 1024 * 1024) {
      const msg = 'Kích thước tài liệu không vượt quá 10MB';
      setErrorMessage(msg);
      showToast(msg, 'error');
      return;
    }

    setUploadingFile(true);
    setErrorMessage('');
    try {
      const url = await accountAppealApi.uploadEvidence(file);
      if (url) {
        setAttachmentUrls((prev) => [...prev, url]);
        showToast('Tải tài liệu đính kèm thành công!', 'success');
      }
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải file lên';
      setErrorMessage(msg);
      showToast(msg, 'error');
    } finally {
      setUploadingFile(false);
      e.target.value = '';
    }
  };

  const handleRemoveFile = (index) => {
    setAttachmentUrls((prev) => prev.filter((_, i) => i !== index));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage('');
    setSuccessMessage('');

    if (!appealContent.trim() || appealContent.trim().length < 10) {
      const msg = 'Vui lòng nhập nội dung giải trình tối thiểu 10 ký tự';
      setErrorMessage(msg);
      showToast(msg, 'error');
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        email: email.trim().toLowerCase(),
        reasonCategory,
        appealContent: appealContent.trim(),
        attachmentUrls: attachmentUrls.join(','),
        appealToken: appealToken || '',
      };
      const result = await accountAppealApi.submitAppeal(payload);
      const okMsg = 'Nộp đơn giải trình thành công! Đang chờ Admin xét duyệt.';
      setSuccessMessage(okMsg);
      showToast(okMsg, 'success');
      setTimeout(() => {
        if (onSubmitSuccess) onSubmitSuccess(result);
      }, 700);
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Lỗi khi nộp đơn giải trình';
      setErrorMessage(msg);
      showToast(msg, 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className="appeal-form-box" onSubmit={handleSubmit}>
      <h3 className="appeal-form-heading">Gửi Đơn Giải Trình &amp; Khắc Phục Mở Khóa Tài Khoản</h3>
      <p className="appeal-form-subtitle">
        Vui lòng trình bày rõ nguyên nhân sự cố, các bước khắc phục đã thực hiện và tài liệu đính kèm chứng thực.
      </p>

      {errorMessage && (
        <div className="appeal-alert appeal-alert-error" role="alert">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
          <div className="appeal-alert-text">
            <strong>Không thể nộp đơn:</strong> {errorMessage}
          </div>
        </div>
      )}

      {successMessage && (
        <div className="appeal-alert appeal-alert-success" role="alert">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
            <polyline points="22 4 12 14.01 9 11.01" />
          </svg>
          <div className="appeal-alert-text">
            <strong>Thành công:</strong> {successMessage}
          </div>
        </div>
      )}

      <div className="appeal-field-group">
        <label className="appeal-field-label">Nhóm nguyên nhân giải trình *</label>
        <select
          className="appeal-select"
          value={reasonCategory}
          onChange={(e) => setReasonCategory(e.target.value)}
          disabled={submitting}
        >
          {APPEAL_REASON_CATEGORIES.map((cat) => (
            <option key={cat.value} value={cat.value}>
              {cat.label}
            </option>
          ))}
        </select>
      </div>

      <div className="appeal-field-group">
        <label className="appeal-field-label">Nội dung giải trình chi tiết &amp; Cam kết khắc phục *</label>
        <textarea
          className="appeal-textarea"
          rows={5}
          required
          minLength={10}
          maxLength={3000}
          placeholder="Trình bày chi tiết lý do sự việc, biên lai/hóa đơn đã giải quyết với nhân sự/đối tác, và cam kết tuân thủ quy chế vận hành CANi..."
          value={appealContent}
          onChange={(e) => setAppealContent(e.target.value)}
          disabled={submitting}
        />
        <span className="appeal-char-counter">{appealContent.length} / 3000 ký tự</span>
      </div>

      <div className="appeal-field-group">
        <label className="appeal-field-label">Tài liệu minh chứng đính kèm (Biên lai, hợp đồng, văn bản khắc phục)</label>
        <div className="appeal-upload-container">
          <label className={`appeal-upload-btn ${uploadingFile ? 'disabled' : ''}`}>
            <input
              type="file"
              accept="image/*,.pdf"
              style={{ display: 'none' }}
              onChange={handleFileUpload}
              disabled={uploadingFile || submitting}
            />
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
              <polyline points="17 8 12 3 7 8"></polyline>
              <line x1="12" y1="3" x2="12" y2="15"></line>
            </svg>
            <span>{uploadingFile ? 'Đang tải file...' : 'Tải lên tài liệu minh chứng (Ảnh / PDF)'}</span>
          </label>
        </div>

        {attachmentUrls.length > 0 && (
          <div className="appeal-files-list">
            {attachmentUrls.map((url, idx) => (
              <div key={idx} className="appeal-file-chip">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                  <polyline points="14 2 14 8 20 8"></polyline>
                </svg>
                <span className="appeal-file-url" title={url}>Tài liệu minh chứng #{idx + 1}</span>
                <button
                  type="button"
                  className="appeal-file-remove"
                  onClick={() => handleRemoveFile(idx)}
                  title="Xóa tài liệu"
                >
                  ✕
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="appeal-form-actions">
        <button
          type="submit"
          className="btn-appeal-primary"
          disabled={submitting || uploadingFile}
        >
          {submitting ? 'Đang nộp đơn giải trình...' : 'Gửi Đơn Giải Trình Lên Admin'}
        </button>
      </div>
    </form>
  );
}
