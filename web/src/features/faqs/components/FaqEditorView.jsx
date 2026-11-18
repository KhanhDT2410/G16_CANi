import { useState, useEffect } from 'react';
import { FAQ_CATEGORY, FAQ_CATEGORY_CONFIG } from '../types/faq.constants';
import FaqRichTextEditor from './FaqRichTextEditor';
import FaqThumbnailPicker from './FaqThumbnailPicker';

/**
 * Màn hình toàn trang tạo/chỉnh sửa FAQ & bài viết hướng dẫn (ADM-08).
 * 3 nút tác vụ: Lưu bản nháp - Gửi duyệt - Quay lại bài viết. Chuẩn < 200 dòng theo AGENTS.md.
 */
export const FaqEditorView = ({ faq, onBack, onSubmit, loading }) => {
  const isEditMode = Boolean(faq?.id);

  const [category, setCategory] = useState(FAQ_CATEGORY.ACCOUNT);
  const [question, setQuestion] = useState('');
  const [summary, setSummary] = useState('');
  const [answer, setAnswer] = useState('');
  const [imageUrl, setImageUrl] = useState('');
  const [attachmentUrl, setAttachmentUrl] = useState('');
  const [attachmentName, setAttachmentName] = useState('');
  const [errorMsg, setErrorMsg] = useState('');

  useEffect(() => {
    setCategory(faq?.category || FAQ_CATEGORY.ACCOUNT);
    setQuestion(faq?.question || '');
    setSummary(faq?.summary || '');
    setAnswer(faq?.answer || '');
    setImageUrl(faq?.imageUrl || '');
    setAttachmentUrl(faq?.attachmentUrl || '');
    setAttachmentName(faq?.attachmentName || '');
    setErrorMsg('');
  }, [faq]);

  const handleSubmit = (shouldPublish = false) => {
    const trimmedQuestion = question.trim();
    const trimmedSummary = summary.trim();
    const trimmedAnswer = answer.trim();

    if (!trimmedQuestion || trimmedQuestion.length < 10) {
      return setErrorMsg('Tiêu đề câu hỏi phải có ít nhất 10 ký tự');
    }
    if (trimmedQuestion.length > 500) {
      return setErrorMsg('Tiêu đề câu hỏi không được vượt quá 500 ký tự');
    }
    if (!trimmedSummary) {
      return setErrorMsg('Vui lòng nhập tóm tắt ngắn cho bài viết');
    }
    const textOnly = trimmedAnswer.replace(/<[^>]*>/g, '').trim();
    if (!trimmedAnswer || textOnly.length < 20) {
      return setErrorMsg('Nội dung bài viết phải có ít nhất 20 ký tự');
    }

    setErrorMsg('');
    onSubmit({
      category,
      question: trimmedQuestion,
      summary: trimmedSummary,
      answer: trimmedAnswer,
      imageUrl: imageUrl.trim() || null,
      attachmentUrl: attachmentUrl.trim() || null,
      attachmentName: attachmentName.trim() || null,
    }, shouldPublish);
  };

  return (
    <div className="faq-editor-page">
      <div className="faq-editor-top-bar">
        <h2 className="faq-editor-heading">
          {isEditMode ? 'Chỉnh sửa câu hỏi thường gặp' : 'Tạo câu hỏi thường gặp'}
        </h2>

        <div className="faq-top-actions">
          <button type="button" className="faq-btn-draft" onClick={() => handleSubmit(false)} disabled={loading}>
            {loading ? 'Đang lưu...' : 'Lưu bản nháp'}
          </button>
          <button type="button" className="faq-btn-submit" onClick={() => handleSubmit(true)} disabled={loading}>
            {loading ? 'Đang xử lý...' : 'Gửi duyệt'}
          </button>
          <button type="button" className="faq-btn-back" onClick={onBack} disabled={loading}>
            Quay lại bài viết của tôi
          </button>
        </div>
      </div>

      {errorMsg && <div className="faq-error-banner">{errorMsg}</div>}

      <div className="faq-editor-card">
        <div className="faq-form-field">
          <label className="faq-field-label">Danh mục phân loại <span className="req">*</span></label>
          <select className="faq-form-input" value={category} onChange={(e) => setCategory(e.target.value)} disabled={loading}>
            {Object.keys(FAQ_CATEGORY).map((k) => (
              <option key={k} value={k}>{FAQ_CATEGORY_CONFIG[k]?.label}</option>
            ))}
          </select>
        </div>

        <div className="faq-form-field">
          <label className="faq-field-label">
            Tiêu đề <span className="req">*</span>
            <span className="faq-field-counter">{question.length}/500</span>
          </label>
          <input
            type="text" className="faq-form-input" placeholder="Nhập tiêu đề câu hỏi thường gặp..."
            maxLength={500} value={question} onChange={(e) => setQuestion(e.target.value)} disabled={loading}
          />
        </div>

        <div className="faq-form-field">
          <label className="faq-field-label">Tóm tắt <span className="req">*</span></label>
          <textarea
            className="faq-form-textarea" rows={3} placeholder="Nhập phần tóm tắt ngắn của câu hỏi / bài viết..."
            value={summary} onChange={(e) => setSummary(e.target.value)} disabled={loading}
          />
        </div>

        <div className="faq-form-field">
          <FaqThumbnailPicker imageUrl={imageUrl} onImageChange={setImageUrl} disabled={loading} />
        </div>

        <div className="faq-form-field">
          <label className="faq-field-label">Nội dung bài viết <span className="req">*</span></label>
          <FaqRichTextEditor
            value={answer} onChange={setAnswer} disabled={loading}
            placeholder="Nhập nội dung chi tiết bài viết, các bước hướng dẫn giải quyết..."
          />
        </div>

        <div className="faq-form-field-attachment">
          <label className="faq-field-label">Tài liệu hướng dẫn đính kèm (Tùy chọn)</label>
          <div className="faq-form-row">
            <input
              type="url" className="faq-form-input flex-1" placeholder="Đường dẫn tệp đính kèm (URL)"
              value={attachmentUrl} onChange={(e) => setAttachmentUrl(e.target.value)} disabled={loading}
            />
            {attachmentUrl && (
              <input
                type="text" className="faq-form-input flex-1" placeholder="Tên hiển thị tệp đính kèm"
                value={attachmentName} onChange={(e) => setAttachmentName(e.target.value)} disabled={loading}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default FaqEditorView;
