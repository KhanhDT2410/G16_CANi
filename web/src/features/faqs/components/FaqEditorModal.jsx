import { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { FAQ_CATEGORY, FAQ_CATEGORY_CONFIG, FAQ_STATUS_CONFIG } from '../types/faq.constants';
import FaqImageUploader from './FaqImageUploader';

/**
 * Modal tạo mới hoặc chỉnh sửa câu hỏi thường gặp (FAQ) kèm ảnh và tệp đính kèm (ADM-08 / SRS UC-110).
 * Cho phép Lưu Bản Nháp hoặc Xuất Bản Trực Tiếp theo đúng Bước 5 UC-110.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const FaqEditorModal = ({ isOpen, onClose, onSubmit, initialFaq, loading }) => {
  const isEditMode = Boolean(initialFaq?.id);
  const currentStatus = initialFaq?.status;

  const [category, setCategory] = useState(FAQ_CATEGORY.ACCOUNT);
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState('');
  const [imageUrl, setImageUrl] = useState('');
  const [attachmentUrl, setAttachmentUrl] = useState('');
  const [attachmentName, setAttachmentName] = useState('');
  const [errorMsg, setErrorMsg] = useState('');

  useEffect(() => {
    setCategory(initialFaq?.category || FAQ_CATEGORY.ACCOUNT);
    setQuestion(initialFaq?.question || '');
    setAnswer(initialFaq?.answer || '');
    setImageUrl(initialFaq?.imageUrl || '');
    setAttachmentUrl(initialFaq?.attachmentUrl || '');
    setAttachmentName(initialFaq?.attachmentName || '');
    setErrorMsg('');
  }, [initialFaq, isOpen]);

  if (!isOpen) return null;

  const handleAction = (e, shouldPublish = false) => {
    if (e) e.preventDefault();
    const trimmedQuestion = question.trim();
    const trimmedAnswer = answer.trim();

    if (!trimmedQuestion || trimmedQuestion.length < 10) return setErrorMsg('Câu hỏi phải có ít nhất 10 ký tự');
    if (trimmedQuestion.length > 500) return setErrorMsg('Câu hỏi không được vượt quá 500 ký tự');
    if (!trimmedAnswer || trimmedAnswer.length < 20) return setErrorMsg('Câu trả lời phải có ít nhất 20 ký tự');

    setErrorMsg('');
    onSubmit({
      category,
      question: trimmedQuestion,
      answer: trimmedAnswer,
      imageUrl: imageUrl.trim() || null,
      attachmentUrl: attachmentUrl.trim() || null,
      attachmentName: attachmentName.trim() || null,
    }, shouldPublish);
  };

  const modalNode = (
    <div className="faq-modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="faq-modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="faq-modal-header">
          <div className="faq-modal-title-group">
            <h3>{isEditMode ? 'Chỉnh Sửa Câu Hỏi Thường Gặp' : 'Tạo Câu Hỏi Thường Gặp Mới'}</h3>
            {isEditMode && currentStatus && (
              <span className={`faq-badge ${FAQ_STATUS_CONFIG[currentStatus]?.badgeClass || ''}`}>
                {FAQ_STATUS_CONFIG[currentStatus]?.label || currentStatus}
              </span>
            )}
          </div>
          <button type="button" className="faq-modal-close" onClick={onClose}>×</button>
        </div>

        <form onSubmit={(e) => handleAction(e, false)} className="faq-modal-form">
          <div className="faq-modal-body">
            {errorMsg && <div className="faq-error-banner">{errorMsg}</div>}

            <div className="faq-form-group">
              <label htmlFor="faq-category">Danh mục phân loại <span className="req">*</span></label>
              <select id="faq-category" value={category} onChange={(e) => setCategory(e.target.value)} disabled={loading}>
                {Object.keys(FAQ_CATEGORY).map((catKey) => (
                  <option key={catKey} value={catKey}>{FAQ_CATEGORY_CONFIG[catKey]?.label}</option>
                ))}
              </select>
            </div>

            <div className="faq-form-group">
              <label htmlFor="faq-question">
                Câu hỏi thường gặp <span className="req">*</span>
                <span className="faq-char-count">{question.length}/500</span>
              </label>
              <textarea
                id="faq-question" rows={2} maxLength={500} disabled={loading}
                placeholder="Ví dụ: Làm sao để đặt lại mật khẩu hoặc đổi số điện thoại?"
                value={question} onChange={(e) => setQuestion(e.target.value)}
              />
            </div>

            <div className="faq-form-group">
              <label htmlFor="faq-answer">
                Nội dung trả lời chi tiết <span className="req">*</span>
                <span className="faq-char-count">Tối thiểu 20 ký tự ({answer.length})</span>
              </label>
              <textarea
                id="faq-answer" rows={3} disabled={loading}
                placeholder="Nhập nội dung giải đáp rõ ràng, từng bước hướng dẫn người dùng tự thao tác..."
                value={answer} onChange={(e) => setAnswer(e.target.value)}
              />
            </div>

            {/* Tải ảnh từ thiết bị (Kéo & Thả) hoặc nhập URL (ADM-08) */}
            <div className="faq-form-group">
              <FaqImageUploader imageUrl={imageUrl} onImageChange={setImageUrl} disabled={loading} />
            </div>

            {/* Đính kèm tài liệu đính kèm (PDF/Doc) */}
            <div className="faq-form-row">
              <div className="faq-form-group flex-1">
                <label htmlFor="faq-attachment-url">Đường dẫn tệp tài liệu đính kèm</label>
                <input
                  id="faq-attachment-url" type="url" disabled={loading}
                  placeholder="https://.../tai-lieu-huong-dan.pdf"
                  value={attachmentUrl} onChange={(e) => setAttachmentUrl(e.target.value)}
                />
              </div>
              {attachmentUrl && (
                <div className="faq-form-group flex-1">
                  <label htmlFor="faq-attachment-name">Tên hiển thị tệp tài liệu</label>
                  <input
                    id="faq-attachment-name" type="text" disabled={loading}
                    placeholder="Ví dụ: huong-dan-checkin-su-kien.pdf"
                    value={attachmentName} onChange={(e) => setAttachmentName(e.target.value)}
                  />
                </div>
              )}
            </div>
          </div>

          <div className="faq-modal-footer">
            <button
              type="button"
              className="faq-btn-secondary"
              onClick={onClose}
              disabled={loading}
            >
              Hủy
            </button>

            {isEditMode && currentStatus === 'PUBLISHED' ? (
              <button
                type="button"
                className="faq-btn-primary"
                onClick={(e) => handleAction(e, false)}
                disabled={loading}
              >
                {loading ? 'Đang lưu...' : 'Lưu Thay Đổi'}
              </button>
            ) : (
              <>
                <button
                  type="button"
                  className="faq-btn-secondary"
                  onClick={(e) => handleAction(e, false)}
                  disabled={loading}
                >
                  {loading ? 'Đang lưu...' : 'Lưu Bản Nháp'}
                </button>
                <button
                  type="button"
                  className="faq-btn-primary"
                  onClick={(e) => handleAction(e, true)}
                  disabled={loading}
                >
                  {loading ? 'Đang lưu & xuất bản...' : 'Xuất Bản Ngay'}
                </button>
              </>
            )}
          </div>
        </form>
      </div>
    </div>
  );

  return typeof document !== 'undefined' ? createPortal(modalNode, document.body) : modalNode;
};

export default FaqEditorModal;
