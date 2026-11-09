import { useState, useMemo } from 'react';
import { FAQ_CATEGORY, FAQ_CATEGORY_CONFIG } from '../types/faq.constants';

/**
 * Danh sách Câu hỏi thường gặp dành cho người dùng công khai (Help Center - UC-110b).
 * Tự động ghi nhận lượt xem khi người dùng bấm xem chi tiết câu trả lời.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const FaqPublicList = ({ publicFaqs = [], onReadFaq, loading }) => {
  const [activeCategory, setActiveCategory] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [expandedId, setExpandedId] = useState(null);

  const handleToggle = (faq) => {
    if (expandedId === faq.id) {
      setExpandedId(null);
    } else {
      setExpandedId(faq.id);
      if (onReadFaq) {
        onReadFaq(faq.id);
      }
    }
  };

  const filteredFaqs = useMemo(() => {
    return publicFaqs.filter((item) => {
      if (activeCategory !== 'ALL' && item.category !== activeCategory) {
        return false;
      }
      if (searchTerm.trim()) {
        const term = searchTerm.toLowerCase();
        const qMatch = item.question?.toLowerCase().includes(term);
        const aMatch = item.answer?.toLowerCase().includes(term);
        if (!qMatch && !aMatch) return false;
      }
      return true;
    });
  }, [publicFaqs, activeCategory, searchTerm]);

  return (
    <div className="faq-public-container">
      <div className="faq-public-hero">
        <h2>Trung Tâm Trợ Giúp & Câu Hỏi Thường Gặp (Help Center)</h2>
        <p>Tìm kiếm câu trả lời nhanh chóng cho các thắc mắc về tài khoản, ca làm việc, thù lao và khiếu nại</p>
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

      <div className="faq-public-categories">
        <button
          type="button"
          className={`faq-cat-pill ${activeCategory === 'ALL' ? 'active' : ''}`}
          onClick={() => setActiveCategory('ALL')}
        >
          Tất cả ({publicFaqs.length})
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
            return (
              <div key={faq.id} className={`faq-accordion-item ${isExpanded ? 'open' : ''}`}>
                <button
                  type="button"
                  className="faq-accordion-header"
                  onClick={() => handleToggle(faq)}
                >
                  <div className="faq-acc-title">
                    <span className="faq-acc-question">{faq.question}</span>
                  </div>
                  <div className="faq-acc-meta">
                    <span className="faq-acc-views">{faq.viewCount || 0} lượt xem</span>
                    <span className="faq-chevron">{isExpanded ? 'Thu gọn' : 'Chi tiết'}</span>
                  </div>
                </button>
                {isExpanded && (
                  <div className="faq-accordion-body">
                    <div className="faq-acc-answer">{faq.answer}</div>
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
