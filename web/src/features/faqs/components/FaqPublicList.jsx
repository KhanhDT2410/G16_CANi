import { useState, useMemo } from 'react';
import DOMPurify from 'dompurify';
import {
  FAQ_CATEGORY, FAQ_CATEGORY_CONFIG,
  FAQ_TARGET_ROLE, FAQ_TARGET_ROLE_CONFIG,
} from '../types/faq.constants';
import './Faq.css';

/**
 * Danh sách Câu hỏi thường gặp dành cho người dùng công khai (Help Center - UC-110b).
 * Hỗ trợ tham số lockedRole để khóa theo vai trò nội bộ (EO / Worker) ẩn bộ lọc role.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const FaqPublicList = ({
  publicFaqs = [],
  onReadFaq,
  loading,
  lockedRole = null,
  title,
  subtitle,
}) => {
  const [activeCategory, setActiveCategory] = useState('ALL');
  const [internalTargetRole, setInternalTargetRole] = useState(lockedRole || 'ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [expandedId, setExpandedId] = useState(null);

  const activeTargetRole = lockedRole || internalTargetRole;

  const handleToggle = (faq) => {
    if (expandedId === faq.id) {
      setExpandedId(null);
    } else {
      setExpandedId(faq.id);
      if (onReadFaq) onReadFaq(faq.id);
    }
  };

  const filteredFaqs = useMemo(() => {
    return publicFaqs.filter((item) => {
      if (activeCategory !== 'ALL' && item.category !== activeCategory) return false;
      if (activeTargetRole !== 'ALL' && item.targetRole !== 'ALL' && item.targetRole !== activeTargetRole) return false;
      if (searchTerm.trim()) {
        const term = searchTerm.toLowerCase();
        return item.question?.toLowerCase().includes(term) || item.answer?.toLowerCase().includes(term);
      }
      return true;
    });
  }, [publicFaqs, activeCategory, activeTargetRole, searchTerm]);

  return (
    <div className="faq-public-container">
      <div className="faq-public-hero">
        <h2>{title || 'Trung Tâm Trợ Giúp & Câu Hỏi Thường Gặp (Help Center)'}</h2>
        <p>{subtitle || 'Tìm kiếm câu trả lời nhanh chóng cho các thắc mắc về tài khoản, ca làm việc, thù lao và khiếu nại'}</p>
        <div className="faq-search-box">
          <input
            type="text"
            placeholder="Nhập từ khóa tìm kiếm câu hỏi (vd: tài khoản, mật khẩu, lương, hủy ca)..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
          {searchTerm && (
            <button type="button" className="faq-search-clear" onClick={() => setSearchTerm('')}>
              ✕
            </button>
          )}
        </div>
      </div>

      {!lockedRole && (
        <div className="faq-public-roles">
          <button
            type="button"
            className={`faq-cat-pill ${activeTargetRole === 'ALL' ? 'active' : ''}`}
            onClick={() => setInternalTargetRole('ALL')}
          >
            Tất cả vai trò
          </button>
          {Object.keys(FAQ_TARGET_ROLE).filter((k) => k !== 'ALL').map((roleKey) => (
            <button
              key={roleKey}
              type="button"
              className={`faq-cat-pill ${activeTargetRole === roleKey ? 'active' : ''}`}
              onClick={() => setInternalTargetRole(roleKey)}
            >
              {FAQ_TARGET_ROLE_CONFIG[roleKey]?.shortLabel}
            </button>
          ))}
        </div>
      )}

      <div className="faq-public-categories">
        <button
          type="button"
          className={`faq-cat-pill ${activeCategory === 'ALL' ? 'active' : ''}`}
          onClick={() => setActiveCategory('ALL')}
        >
          Tất cả danh mục ({publicFaqs.length})
        </button>
        {Object.keys(FAQ_CATEGORY).map((catKey) => {
          const cfg = FAQ_CATEGORY_CONFIG[catKey];
          const count = publicFaqs.filter((f) => f.category === catKey).length;
          return (
            <button
              key={catKey}
              type="button"
              className={`faq-cat-pill ${activeCategory === catKey ? 'active' : ''}`}
              onClick={() => setActiveCategory(catKey)}
            >
              {cfg.label} ({count})
            </button>
          );
        })}
      </div>

      <div className="faq-public-list">
        {loading ? (
          <div className="faq-loading-box">Đang tải danh sách câu hỏi trợ giúp...</div>
        ) : filteredFaqs.length === 0 ? (
          <div className="faq-empty-box">
            <p>Không tìm thấy câu hỏi thường gặp nào phù hợp với tìm kiếm của bạn.</p>
          </div>
        ) : (
          filteredFaqs.map((faq) => {
            const isExpanded = expandedId === faq.id;
            const rConfig = FAQ_TARGET_ROLE_CONFIG[faq.targetRole];
            return (
              <div key={faq.id} className={`faq-accordion-item ${isExpanded ? 'open' : ''}`}>
                <button
                  type="button"
                  className="faq-accordion-header"
                  onClick={() => handleToggle(faq)}
                >
                  <div className="faq-acc-title">
                    <span className="faq-acc-question">{faq.question}</span>
                    {rConfig && !lockedRole && faq.targetRole !== 'ALL' && (
                      <span className={`faq-role-badge ${rConfig.badgeClass}`}>
                        {rConfig.shortLabel}
                      </span>
                    )}
                  </div>
                  <div className="faq-acc-meta">
                    <span className="faq-chevron">{isExpanded ? 'Thu gọn' : 'Chi tiết'}</span>
                  </div>
                </button>
                {isExpanded && (
                  <div className="faq-accordion-body">
                    {faq.summary && (
                      <div className="faq-summary-box">
                        <strong>Tóm tắt:</strong> {faq.summary}
                      </div>
                    )}
                    {/<[a-z][\s\S]*>/i.test(faq.answer || '') ? (
                      <div className="faq-acc-answer" dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(faq.answer) }} />
                    ) : (
                      <div className="faq-acc-answer">{faq.answer}</div>
                    )}
                    {(faq.imageUrl || faq.attachmentUrl) && (
                      <div className="faq-attachments-container">
                        {faq.imageUrl && (
                          <div className="faq-attachment-card">
                            <span className="faq-attachment-title">Hình ảnh minh họa:</span>
                            <div className="faq-image-wrapper">
                              <img
                                src={faq.imageUrl}
                                alt="Minh họa"
                                className="faq-image-thumb"
                                onError={(e) => { e.target.style.display = 'none'; }}
                              />
                              <a
                                href={faq.imageUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="faq-view-image-link"
                              >
                                Xem ảnh kích thước đầy đủ
                              </a>
                            </div>
                          </div>
                        )}
                        {faq.attachmentUrl && (
                          <div className="faq-attachment-card">
                            <span className="faq-attachment-title">Tài liệu đính kèm:</span>
                            <a
                              href={faq.attachmentUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="faq-attachment-file-btn"
                            >
                              {faq.attachmentName || 'Tải tệp đính kèm'}
                            </a>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};

export default FaqPublicList;
