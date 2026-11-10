import { useState, useMemo } from 'react';
import { useFaqs } from '../hooks/useFaqs';
import { FAQ_CATEGORY, FAQ_CATEGORY_CONFIG, FAQ_STATUS, FAQ_STATUS_CONFIG } from '../types/faq.constants';
import FaqCard from './FaqCard';
import FaqEditorModal from './FaqEditorModal';
import './Faq.css';

/**
 * Màn hình Quản trị Câu hỏi thường gặp (Admin & Customer Care - ADM-08).
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const FaqView = () => {
  const {
    faqs,
    loading,
    loadFaqs,
    createFaq,
    updateFaq,
    publishFaq,
    archiveFaq,
  } = useFaqs('admin');

  const [activeStatus, setActiveStatus] = useState('ALL');
  const [activeCategory, setActiveCategory] = useState('ALL');
  const [sortBy, setSortBy] = useState('NEWEST');
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

  const handleModalSubmit = async (payload, shouldPublish = false) => {
    try {
      if (editingFaq?.id) {
        await updateFaq(editingFaq.id, payload);
        if (shouldPublish && editingFaq.status !== 'PUBLISHED') {
          await publishFaq(editingFaq.id);
        }
      } else {
        const created = await createFaq(payload);
        if (shouldPublish && created?.id) {
          await publishFaq(created.id);
        }
      }
      setIsModalOpen(false);
      setEditingFaq(null);
    } catch {
      // Lỗi đã được hiển thị toast qua useFaqs
    }
  };

  const filteredAndSortedFaqs = useMemo(() => {
    const list = faqs.filter((f) => {
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

    return list.sort((a, b) => {
      if (sortBy === 'NEWEST') return new Date(b.createdAt || 0) - new Date(a.createdAt || 0);
      if (sortBy === 'OLDEST') return new Date(a.createdAt || 0) - new Date(b.createdAt || 0);
      if (sortBy === 'TITLE_AZ') return (a.question || '').localeCompare(b.question || '', 'vi');
      if (sortBy === 'TITLE_ZA') return (b.question || '').localeCompare(a.question || '', 'vi');
      return 0;
    });
  }, [faqs, activeStatus, activeCategory, searchTerm, sortBy]);

  return (
    <div className="faq-view-container">
      {/* Header */}
      <div className="faq-header">
        <div>
          <h2>Quản Lý Câu Hỏi Thường Gặp & Trợ Giúp</h2>
          <p className="faq-subtitle">
            Tạo, xuất bản và quản lý bộ câu hỏi giải đáp thắc mắc người dùng
          </p>
        </div>
        <div className="faq-header-actions">
          <button
            type="button"
            className="faq-btn-refresh"
            onClick={() => loadFaqs()}
            disabled={loading}
          >
            Làm mới
          </button>
          <button type="button" className="faq-btn-primary" onClick={handleOpenCreate}>
            Thêm Câu Hỏi Mới
          </button>
        </div>
      </div>

      {/* Toolbar & Filters */}
      <div className="faq-toolbar">
        <div className="faq-filter-group">
          <label>Trạng thái:</label>
          <select value={activeStatus} onChange={(e) => setActiveStatus(e.target.value)}>
            <option value="ALL">Tất cả trạng thái</option>
            {Object.keys(FAQ_STATUS).map((k) => (
              <option key={k} value={k}>{FAQ_STATUS_CONFIG[k].label}</option>
            ))}
          </select>

          <label>Danh mục:</label>
          <select value={activeCategory} onChange={(e) => setActiveCategory(e.target.value)}>
            <option value="ALL">Tất cả danh mục</option>
            {Object.keys(FAQ_CATEGORY).map((k) => (
              <option key={k} value={k}>{FAQ_CATEGORY_CONFIG[k].label}</option>
            ))}
          </select>

          <label>Sắp xếp:</label>
          <select value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
            <option value="NEWEST">Mới nhất</option>
            <option value="OLDEST">Cũ nhất</option>
            <option value="TITLE_AZ">Tiêu đề (A → Z)</option>
            <option value="TITLE_ZA">Tiêu đề (Z → A)</option>
          </select>
        </div>

        <div className="faq-search-inline">
          <input
            type="text"
            placeholder="Tìm kiếm theo tiêu đề câu hỏi hoặc đáp án..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
      </div>

      {/* Accordion Cards List */}
      <div className="faq-cards-list">
        {loading && faqs.length === 0 ? (
          <div className="faq-loading-placeholder">Đang tải danh sách câu hỏi...</div>
        ) : filteredAndSortedFaqs.length === 0 ? (
          <div className="faq-empty-state">
            <p>Không có câu hỏi thường gặp nào phù hợp với bộ lọc.</p>
            <button type="button" className="faq-btn-secondary" onClick={handleOpenCreate}>
              Tạo câu hỏi đầu tiên
            </button>
          </div>
        ) : (
          filteredAndSortedFaqs.map((faq) => (
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
