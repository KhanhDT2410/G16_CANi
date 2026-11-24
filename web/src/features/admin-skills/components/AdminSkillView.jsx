import { useAdminSkills } from '../hooks/useAdminSkills';
import { SkillTable } from './SkillTable';
import { SkillFormModal } from './SkillFormModal';
import { DEFAULT_SKILL_CATEGORIES } from '../types/adminSkill.constants';
import './AdminSkills.css';

/**
 * Màn hình Quản trị Danh mục Kỹ năng
 * Giao diện tối giản, trực quan, không icon, tuân thủ dưới 200 dòng
 */
export const AdminSkillView = () => {
  const {
    skills,
    categories,
    loading,
    page,
    totalPages,
    totalElements,
    filters,
    modalState,
    submitting,
    togglingId,
    openCreateModal,
    openEditModal,
    closeModal,
    handleSaveSkill,
    handleToggleStatus,
    handleFilterChange,
    handlePageChange,
  } = useAdminSkills();

  const allCategories = Array.from(
    new Set([...DEFAULT_SKILL_CATEGORIES, ...categories])
  ).filter(Boolean);

  const handleResetFilters = () => {
    handleFilterChange('keyword', '');
    handleFilterChange('category', 'ALL');
    handleFilterChange('isActive', 'ALL');
  };

  return (
    <div className="admin-skills-container">
      {/* Header Action Button */}
      <div className="admin-skills-header">
        <button
          type="button"
          className="admin-skills-btn-primary"
          onClick={openCreateModal}
        >
          Thêm kỹ năng mới
        </button>
      </div>

      {/* Filter Bar */}
      <div className="admin-skills-filter-bar">
        <input
          type="text"
          className="admin-skills-search-input"
          placeholder="Tìm theo tên kỹ năng hoặc mô tả..."
          value={filters.keyword}
          onChange={(e) => handleFilterChange('keyword', e.target.value)}
        />

        <select
          className="admin-skills-select"
          value={filters.category}
          onChange={(e) => handleFilterChange('category', e.target.value)}
        >
          <option value="ALL">Tất cả danh mục</option>
          {allCategories.map((cat) => (
            <option key={cat} value={cat}>
              {cat}
            </option>
          ))}
        </select>

        <select
          className="admin-skills-select"
          value={filters.isActive}
          onChange={(e) => handleFilterChange('isActive', e.target.value)}
        >
          <option value="ALL">Tất cả trạng thái</option>
          <option value="true">Đang hoạt động</option>
          <option value="false">Tạm ẩn</option>
        </select>

        <button
          type="button"
          className="admin-skills-btn-secondary"
          onClick={handleResetFilters}
        >
          Đặt lại
        </button>
      </div>

      {/* Table Content */}
      <SkillTable
        skills={skills}
        loading={loading}
        onEdit={openEditModal}
        onToggleStatus={handleToggleStatus}
        togglingId={togglingId}
      />

      {/* Pagination Controls */}
      {totalPages > 1 && (
        <div className="admin-skills-pagination">
          <div>
            Trang <strong>{page + 1}</strong> / {totalPages} (Tổng số <strong>{totalElements}</strong> kỹ năng)
          </div>
          <div className="admin-skills-page-btns">
            <button
              type="button"
              className="admin-skills-page-btn"
              disabled={page === 0 || loading}
              onClick={() => handlePageChange(page - 1)}
            >
              Trang trước
            </button>
            <button
              type="button"
              className="admin-skills-page-btn"
              disabled={page >= totalPages - 1 || loading}
              onClick={() => handlePageChange(page + 1)}
            >
              Trang sau
            </button>
          </div>
        </div>
      )}

      {/* Centered Modal Popup */}
      <SkillFormModal
        isOpen={modalState.isOpen}
        mode={modalState.mode}
        initialData={modalState.initialData}
        categories={allCategories}
        onClose={closeModal}
        onSubmit={handleSaveSkill}
        submitting={submitting}
      />
    </div>
  );
};
