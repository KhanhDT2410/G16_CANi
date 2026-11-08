import { useState, useEffect } from 'react';
import { FAQ_CATEGORY, FAQ_CATEGORY_CONFIG } from '../types/faq.constants';

/**
 * Modal tạo mới hoặc chỉnh sửa câu hỏi thường gặp (FAQ).
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const FaqEditorModal = ({ isOpen, onClose, onSubmit, initialFaq, loading }) => {
  const isEditMode = Boolean(initialFaq?.id);

  const [category, setCategory] = useState(FAQ_CATEGORY.ACCOUNT);
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState('');
  const [errorMsg, setErrorMsg] = useState('');

  useEffect(() => {
    if (initialFaq) {
      setCategory(initialFaq.category || FAQ_CATEGORY.ACCOUNT);
      setQuestion(initialFaq.question || '');
      setAnswer(initialFaq.answer || '');
    } else {
      setCategory(FAQ_CATEGORY.ACCOUNT);
      setQuestion('');
      setAnswer('');
    }
    setErrorMsg('');
  }, [initialFaq, isOpen]);

  if (!isOpen) return null;

  const handleSubmit = (e) => {
    e.preventDefault();
    const trimmedQuestion = question.trim();
    const trimmedAnswer = answer.trim();

    if (!trimmedQuestion || trimmedQuestion.length < 10) {
      setErrorMsg('Câu hỏi phải có ít nhất 10 ký tự');
      return;
    }
    if (trimmedQuestion.length > 500) {
      setErrorMsg('Câu hỏi không được vượt quá 500 ký tự');
      return;
    }
    if (!trimmedAnswer || trimmedAnswer.length < 20) {
      setErrorMsg('Câu trả lời phải có ít nhất 20 ký tự để đảm bảo chi tiết');
      return;
    }

    setErrorMsg('');
    onSubmit({
      category,
      question: trimmedQuestion,
      answer: trimmedAnswer,
    });
  };

  return (
    <div className="faq-modal-overlay" onClick={onClose}>
      <div className="faq-modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="faq-modal-header">
          <h3>{isEditMode ? 'Chỉnh Sửa Câu Hỏi Thường Gặp' : 'Tạo Câu Hỏi Thường Gặp Mới'}</h3>
          <button type="button" className="faq-modal-close" onClick={onClose}>×</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="faq-modal-body">
            {errorMsg && <div className="faq-error-banner">{errorMsg}</div>}

            <div className="faq-form-group">
              <label htmlFor="faq-category">
                Danh mục phân loại <span className="req">*</span>
              </label>
              <select
                id="faq-category"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                disabled={loading}
              >
                {Object.keys(FAQ_CATEGORY).map((catKey) => (
                  <option key={catKey} value={catKey}>
                    {FAQ_CATEGORY_CONFIG[catKey]?.icon} {FAQ_CATEGORY_CONFIG[catKey]?.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="faq-form-group">
              <label htmlFor="faq-question">
                Câu hỏi thường gặp <span className="req">*</span>
                <span className="faq-char-count">{question.length}/500</span>
              </label>
              <textarea
                id="faq-question"
                rows={3}
                placeholder="Ví dụ: Làm sao để đặt lại mật khẩu hoặc đổi số điện thoại?"
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                disabled={loading}
                maxLength={500}
              />
            </div>

            <div className="faq-form-group">
              <label htmlFor="faq-answer">
                Nội dung trả lời chi tiết <span className="req">*</span>
                <span className="faq-char-count">Tối thiểu 20 ký tự (hiện có: {answer.length})</span>
              </label>
              <textarea
                id="faq-answer"
                rows={6}
                placeholder="Nhập nội dung giải đáp rõ ràng, từng bước hướng dẫn người dùng tự thao tác..."
                value={answer}
                onChange={(e) => setAnswer(e.target.value)}
                disabled={loading}
              />
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
            <button
              type="submit"
              className="faq-btn-primary"
              disabled={loading}
            >
              {loading ? 'Đang lưu...' : isEditMode ? 'Lưu Thay Đổi' : 'Tạo Bản Nháp'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default FaqEditorModal;

