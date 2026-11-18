import { useState } from 'react';
import DOMPurify from 'dompurify';
import { FAQ_CATEGORY_CONFIG, FAQ_STATUS_CONFIG } from '../types/faq.constants';

/**
 * Thẻ hiển thị câu hỏi thường gặp dạng Accordion (Admin & Staff View - ADM-08).
 * Thu gọn mặc định chỉ thấy tiêu đề, bấm vào tiêu đề để mở chi tiết & tệp đính kèm.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const FaqCard = ({ faq, onEdit, onPublish, onArchive, isActionLoading }) => {
  const [isExpanded, setIsExpanded] = useState(false);

  const catConfig = FAQ_CATEGORY_CONFIG[faq.category] || { label: faq.category, badgeClass: '' };
  const statusConfig = FAQ_STATUS_CONFIG[faq.status] || { label: faq.status, badgeClass: '' };

  const isArchived = faq.status === 'ARCHIVED';
  const isDraft = faq.status === 'DRAFT';
  const hasAttachment = Boolean(faq.imageUrl || faq.attachmentUrl);

  const toggleExpand = () => {
    setIsExpanded((prev) => !prev);
  };

  return (
    <div className={`faq-accordion-item ${isExpanded ? 'open' : ''} ${isArchived ? 'archived' : ''}`}>
      {/* Header dòng tiêu đề (Click vào để xem chi tiết) */}
      <div className="faq-accordion-header" onClick={toggleExpand} role="button" tabIndex={0}>
        <div className="faq-accordion-header-left">
          <span className="faq-accordion-chevron" aria-hidden="true">
            {isExpanded ? '▾' : '▸'}
          </span>
          <div className="faq-accordion-heading-group">
            <h4 className="faq-accordion-title">{faq.question}</h4>
            <div className="faq-accordion-tags">
              <span className={`faq-category-badge ${catConfig.badgeClass}`}>
                {catConfig.label}
              </span>
              <span className={`faq-status-badge ${statusConfig.badgeClass}`}>
                {statusConfig.label}
              </span>
              {hasAttachment && (
                <span className="faq-has-attachment-badge" title="Có ảnh hoặc tệp đính kèm">
                  📎 Đính kèm
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Nhóm thao tác nhanh */}
        <div className="faq-card-actions" onClick={(e) => e.stopPropagation()}>
          {!isArchived && (
            <button
              type="button"
              className="faq-btn-edit"
              onClick={() => onEdit(faq)}
              disabled={isActionLoading}
              title="Chỉnh sửa câu hỏi"
            >
              Sửa
            </button>
          )}

          {isDraft && (
            <button
              type="button"
              className="faq-btn-publish"
              onClick={() => onPublish(faq.id)}
              disabled={isActionLoading}
              title="Xuất bản lên Help Center"
            >
              Xuất bản
            </button>
          )}

          {!isArchived && (
            <button
              type="button"
              className="faq-btn-archive"
              onClick={() => onArchive(faq.id)}
              disabled={isActionLoading}
              title="Lưu trữ câu hỏi này"
            >
              Lưu trữ
            </button>
          )}
        </div>
      </div>

      {/* Chi tiết nội dung bung ra khi click vào tiêu đề */}
      {isExpanded && (
        <div className="faq-accordion-body">
          {faq.summary && (
            <div className="faq-summary-box">
              <strong>Tóm tắt:</strong> {faq.summary}
            </div>
          )}
          {/<[a-z][\s\S]*>/i.test(faq.answer || '') ? (
            <div className="faq-answer-content" dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(faq.answer) }} />
          ) : (
            <div className="faq-answer-content">{faq.answer}</div>
          )}

          {/* Phần ảnh / tệp đính kèm (ADM-08) */}
          {hasAttachment && (
            <div className="faq-attachments-container">
              {faq.imageUrl && (
                <div className="faq-attachment-card">
                  <span className="faq-attachment-title">🖼️ Hình ảnh minh họa:</span>
                  <div className="faq-image-wrapper">
                    <img
                      src={faq.imageUrl}
                      alt="Minh họa câu hỏi"
                      className="faq-image-thumb"
                      onError={(e) => {
                        e.target.style.display = 'none';
                      }}
                    />
                    <a
                      href={faq.imageUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="faq-view-image-link"
                    >
                      Mở ảnh kích thước đầy đủ ↗
                    </a>
                  </div>
                </div>
              )}

              {faq.attachmentUrl && (
                <div className="faq-attachment-card">
                  <span className="faq-attachment-title">📎 Tài liệu đính kèm:</span>
                  <a
                    href={faq.attachmentUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="faq-attachment-file-btn"
                  >
                    📄 {faq.attachmentName || 'Tải tệp đính kèm hướng dẫn'} ↗
                  </a>
                </div>
              )}
            </div>
          )}

          {/* Footer thông tin & nút thu gọn */}
          <div className="faq-accordion-footer">
            <div className="faq-author-info">
              <span>Tác giả: <strong>{faq.authorName || 'Hệ thống'}</strong></span>
              <span className="faq-divider">•</span>
              <span>
                {faq.publishedAt
                  ? `Xuất bản: ${new Date(faq.publishedAt).toLocaleDateString('vi-VN')}`
                  : `Tạo ngày: ${new Date(faq.createdAt).toLocaleDateString('vi-VN')}`}
              </span>
            </div>
            <button
              type="button"
              className="faq-collapse-btn"
              onClick={toggleExpand}
            >
              Thu gọn ▴
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default FaqCard;
