import { useState, useMemo } from 'react';
import { useFaqs } from '../hooks/useFaqs';
import { FAQ_CATEGORY, FAQ_CATEGORY_CONFIG, FAQ_STATUS, FAQ_STATUS_CONFIG } from '../types/faq.constants';
import FaqCard from './FaqCard';
import FaqEditorModal from './FaqEditorModal';
import './Faq.css';

/**
 * Màn hình Quản trị Câu hỏi thường gặp (Admin & Customer Care - UC-110a/c).
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const FaqView = () => {
  const {
    faqs,
    analytics,
    loading,
    loadFaqs,
    loadAnalytics,
    createFaq,
    updateFaq,
    publishFaq,
    archiveFaq,
  } = useFaqs('admin');

  const [activeStatus, setActiveStatus] = useState('ALL');
  const [activeCategory, setActiveCategory] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingFaq, setEditingFaq] = useState(null);

  const handleOpenCreate = () => {
    setEditingFaq(null);
    setIsModalOpen(true);
  };

  const handleOpenEdit = (faq) => {
    setEditingFaq(faq);
    setIsModalOpen(true);
  };

  const handleModalSubmit = async (payload) => {
    if (editingFaq?.id) {
      await updateFaq(editingFaq.id, payload);
    } else {
      await createFaq(payload);
    }
    setIsModalOpen(false);
    setEditingFaq(null);
  };

  const filteredFaqs = useMemo(() => {
    return faqs.filter((f) => {
      if (activeStatus !== 'ALL' && f.status !== activeStatus) return false;
      if (activeCategory !== 'ALL' && f.category !== activeCategory) return false;
      if (searchTerm.trim()) {
        const term = searchTerm.toLowerCase();
        const qMatch = f.question?.toLowerCase().includes(term);
        const aMatch = f.answer?.toLowerCase().includes(term);
        if (!qMatch && !aMatch) return false;
      }
      return true;
    });
  }, [faqs, activeStatus, activeCategory, searchTerm]);

  return (
    <div className="faq-view-container">
      {/* Header */}
      <div className="faq-header">
        <div>
          <h2>Quản Lý Câu Hỏi Thường Gặp (FAQ & Help Center)</h2>
          <p className="faq-subtitle">
            Tạo, xuất bản và theo dõi hiệu quả bài viết giải đáp thắc mắc người dùng (SRS UC-110)
          </p>
        </div>
        <div className="faq-header-actions">
          <button
            type="button"
            className="faq-btn-refresh"
            onClick={() => {
              loadFaqs();
              loadAnalytics();
            }}
            disabled={loading}
          >
            Làm mới
          </button>
          <button type="button" className="faq-btn-primary" onClick={handleOpenCreate}>
            Thêm Câu Hỏi Mới
          </button>
        </div>
      </div>

      {/* Analytics Summary */}
      <div className="faq-analytics-grid">
        <div className="faq-stat-card">
          <span className="faq-stat-label">Tổng lượt xem FAQ</span>
          <span className="faq-stat-value">{analytics?.totalViews ?? 0}</span>
          <span className="faq-stat-desc">Đọc qua Help Center</span>
        </div>
        <div className="faq-stat-card highlight">
          <span className="faq-stat-label">Tỷ lệ giảm tải Ticket (Deflection Rate)</span>
          <span className="faq-stat-value">
            {analytics?.ticketDeflectionRate != null ? `${analytics.ticketDeflectionRate}%` : '0%'}
          </span>
          <span className="faq-stat-desc" title={analytics?.calculationFormula}>
            Ticket thuộc danh mục đã có FAQ giải đáp
          </span>
        </div>
        <div className="faq-stat-card">
          <span className="faq-stat-label">Lượt xem theo Danh mục</span>
          <div className="faq-stat-cats">
            <span>Tài khoản: {analytics?.viewsByCategory?.ACCOUNT || 0}</span>
            <span>Ca làm: {analytics?.viewsByCategory?.SHIFT || 0}</span>
            <span>Thù lao: {analytics?.viewsByCategory?.PAYMENT || 0}</span>
            <span>Khiếu nại: {analytics?.viewsByCategory?.DISPUTE || 0}</span>
          </div>
        </div>
      </div>

      {/* Toolbar & Filters */}
      <div className="faq-toolbar">
        <div className="faq-filter-group">
          <label>Trạng thái:</label>
          <select value={activeStatus} onChange={(e) => setActiveStatus(e.target.value)}>
            <option value="ALL">Tất cả trạng thái</option>
            {Object.keys(FAQ_STATUS).map((k) => (
              <option key={k} value={k}>
                {FAQ_STATUS_CONFIG[k].label}
              </option>
            ))}
          </select>

          <label>Danh mục:</label>
          <select value={activeCategory} onChange={(e) => setActiveCategory(e.target.value)}>
            <option value="ALL">Tất cả danh mục</option>
            {Object.keys(FAQ_CATEGORY).map((k) => (
              <option key={k} value={k}>
                {FAQ_CATEGORY_CONFIG[k].label}
              </option>
            ))}
          </select>
        </div>

        <div className="faq-search-inline">
          <input
            type="text"
            placeholder="Tìm theo nội dung câu hỏi hoặc đáp án..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
      </div>

      {/* Cards List */}
      <div className="faq-cards-list">
        {loading && faqs.length === 0 ? (
          <div className="faq-loading-placeholder">Đang tải danh sách câu hỏi...</div>
        ) : filteredFaqs.length === 0 ? (
          <div className="faq-empty-state">
            <p>Không có câu hỏi thường gặp nào phù hợp với bộ lọc.</p>
            <button type="button" className="faq-btn-secondary" onClick={handleOpenCreate}>
              Tạo câu hỏi đầu tiên
            </button>
          </div>
        ) : (
          filteredFaqs.map((faq) => (
            <FaqCard
              key={faq.id}
              faq={faq}
              onEdit={handleOpenEdit}
              onPublish={publishFaq}
              onArchive={archiveFaq}
              isActionLoading={loading}
            />
          ))
        )}
      </div>

      {/* Modal Editor */}
      <FaqEditorModal
        isOpen={isModalOpen}
        onClose={() => {
          setIsModalOpen(false);
          setEditingFaq(null);
        }}
        onSubmit={handleModalSubmit}
        initialFaq={editingFaq}
        loading={loading}
      />
    </div>
  );
};

export default FaqView;

