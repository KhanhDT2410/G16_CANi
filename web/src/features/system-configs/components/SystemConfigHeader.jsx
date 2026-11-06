import { CONFIG_CATEGORIES } from '../types/systemConfig.constants';

const SystemConfigHeader = ({
  totalCount,
  dirtyCount,
  searchTerm,
  onSearchChange,
  selectedCategory,
  onCategoryChange,
  onSaveBulk,
  onResetChanges,
  onRefresh,
  saving,
  loading,
}) => {
  return (
    <div className="sys-config-header">
      {/* Top Banner */}
      <div className="sys-config-topbar">
        <div>
          <h2 className="sys-config-title">Tham Số Cấu Hình Toàn Sàn</h2>
          <p className="sys-config-subtitle">
            Quản trị và điều chỉnh các ngưỡng vận hành, tỷ lệ hoa hồng và quy tắc hệ thống theo thời gian thực (Zero-Downtime).
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
            ↻
          </button>
        </div>
      </div>

      {/* Control Bar: Search & Category Tabs */}
      <div className="sys-config-controls">
        <div className="sys-config-search-wrapper">
          <input
            type="text"
            className="sys-config-search-input"
            placeholder="Tìm kiếm theo tên tham số, key hoặc mô tả..."
            value={searchTerm}
            onChange={(e) => onSearchChange(e.target.value)}
          />
          {searchTerm && (
            <button
              type="button"
              className="sys-config-search-clear"
              onClick={() => onSearchChange('')}
            >
              ✕
            </button>
          )}
        </div>

        <div className="sys-config-category-tabs">
          {Object.values(CONFIG_CATEGORIES).map((cat) => (
            <button
              key={cat.key}
              type="button"
              className={`sys-config-cat-btn ${selectedCategory === cat.key ? 'active' : ''}`}
              onClick={() => onCategoryChange(cat.key)}
            >
              {cat.label}
              {cat.key === 'ALL' && <span className="cat-count-badge">{totalCount}</span>}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
};

export default SystemConfigHeader;
