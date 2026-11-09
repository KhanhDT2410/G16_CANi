import { useState } from 'react';
import { FAQ_CATEGORY_CONFIG, FAQ_STATUS_CONFIG } from '../types/faq.constants';

/**
 * Thẻ hiển thị một câu hỏi thường gặp (Admin & Staff View).
 * Đáp ứng tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const FaqCard = ({ faq, onEdit, onPublish, onArchive, isActionLoading }) => {
  const [isExpanded, setIsExpanded] = useState(false);

  const catConfig = FAQ_CATEGORY_CONFIG[faq.category] || { label: faq.category, badgeClass: '' };
  const statusConfig = FAQ_STATUS_CONFIG[faq.status] || { label: faq.status, badgeClass: '' };

  const isArchived = faq.status === 'ARCHIVED';
  const isDraft = faq.status === 'DRAFT';

  return (
    <div className={`faq-card ${isArchived ? 'archived' : ''}`}>
      <div className="faq-card-header">
        <div className="faq-card-meta">
          <span className={`faq-category-badge ${catConfig.badgeClass}`}>
            {catConfig.label}
          </span>
          <span className={`faq-status-badge ${statusConfig.badgeClass}`}>
            {statusConfig.label}
          </span>
          <span className="faq-views-badge" title="Lượt xem trên Help Center">
            {faq.viewCount || 0} lượt xem
          </span>
        </div>

        <div className="faq-card-actions">
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

      <div className="faq-card-body">
        <h3 className="faq-question">{faq.question}</h3>
        <div className={`faq-answer ${isExpanded ? 'expanded' : ''}`}>
          {faq.answer}
        </div>
        {faq.answer && faq.answer.length > 180 && (
          <button
            type="button"
            className="faq-expand-btn"
            onClick={() => setIsExpanded(!isExpanded)}
          >
            {isExpanded ? 'Thu gọn' : 'Xem đầy đủ'}
          </button>
        )}
      </div>

      <div className="faq-card-footer">
        <span className="faq-author">
          Tác giả: <strong>{faq.authorName || 'Hệ thống'}</strong>
        </span>
        <span className="faq-date">
          {faq.publishedAt
            ? `Xuất bản: ${new Date(faq.publishedAt).toLocaleDateString('vi-VN')}`
            : `Tạo ngày: ${new Date(faq.createdAt).toLocaleDateString('vi-VN')}`}
        </span>
      </div>
    </div>
  );
};

export default FaqCard;

