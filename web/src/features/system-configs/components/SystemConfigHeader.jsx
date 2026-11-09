import { CONFIG_CATEGORIES } from '../types/systemConfig.constants';

/**
 * Thanh tiêu đề và công cụ cho Tham số hệ thống (ADM-09).
 * Danh mục đã được chuyển sang menu xổ xuống ở Sidebar chính.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const SystemConfigHeader = ({
  totalCount,
  dirtyCount,
  searchTerm,
  onSearchChange,
  selectedCategory,
  categoryCounts,
  onSaveBulk,
  onResetChanges,
  onRefresh,
  saving,
  loading,
}) => {
  const currentCategoryInfo = CONFIG_CATEGORIES[selectedCategory] || { label: 'Tất cả tham số' };
  const currentCount = categoryCounts ? categoryCounts[selectedCategory] ?? 0 : totalCount;

  return (
    <div className="sys-config-header">
      {/* Top Banner */}
      <div className="sys-config-topbar">
        <div>
          <h2 className="sys-config-title">Tham Số & Quy Tắc Nghiệp Vụ Toàn Sàn</h2>
          <p className="sys-config-subtitle">
            Quản trị và điều chỉnh thuật toán ghép ca Matching, tỷ lệ hoa hồng, ngưỡng uy tín và các quy tắc vận hành theo thời gian thực.
          </p>
        </div>

        <div className="sys-config-actions">
          {dirtyCount > 0 && (
            <button
              type="button"
              className="btn-config-discard"
              onClick={onResetChanges}
              disabled={saving}
            >
              Hủy thay đổi ({dirtyCount})
            </button>
          )}

          <button
            type="button"
            className="btn-config-save-all"
            onClick={onSaveBulk}
            disabled={saving || dirtyCount === 0}
          >
            {saving ? 'Đang lưu...' : `Lưu tất cả thay đổi ${dirtyCount > 0 ? `(${dirtyCount})` : ''}`}
          </button>

          <button
            type="button"
            className="btn-config-refresh"
            onClick={onRefresh}
            disabled={loading || saving}
            title="Tải lại dữ liệu"
          >
            Làm mới
          </button>
        </div>
      </div>

      {/* Control Bar: Search & Active Group Indicator (Thay thế dãy tab cũ) */}
      <div className="sys-config-controls">
        <div className="sys-config-search-wrapper">
          <input
            type="text"
            className="sys-config-search-input"
            placeholder="Tìm kiếm theo tên tham số, key hoặc mô tả quy tắc..."
            value={searchTerm}
            onChange={(e) => onSearchChange(e.target.value)}
          />
          {searchTerm && (
            <button
              type="button"
              className="sys-config-search-clear"
              onClick={() => onSearchChange('')}
              title="Xóa tìm kiếm"
            >
              Xóa
            </button>
          )}
        </div>

        <div className="sys-config-active-group-pill">
          <span className="pill-prefix">Phân nhóm:</span>
          <span className="pill-category-name">{currentCategoryInfo.label}</span>
          <span className="pill-count-badge">{currentCount} tham số</span>
        </div>
      </div>
    </div>
  );
};

export default SystemConfigHeader;
