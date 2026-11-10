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

  const headerMeta = {
    MATCHING: {
      title: 'Thuật Toán Ghép Nối & Matching',
      subtitle: 'Quản trị các hệ số trọng số khoảng cách, đánh giá sao, kinh nghiệm và công thức phân bổ đề xuất ca.',
    },
    OPERATION: {
      title: 'Vận Hành & Điểm Uy Tín',
      subtitle: 'Thiết lập ngưỡng phát hiện vắng mặt (No-show), mức trừ điểm uy tín và thời hạn đánh giá sau ca làm.',
    },
    FINANCIAL: {
      title: 'Tài Chính & Hoa Hồng',
      subtitle: 'Quản trị tỷ lệ hoa hồng nền tảng và thời hạn đối soát xác nhận thù lao trực tiếp Non-Escrow.',
    },
    GENERAL: {
      title: 'Hệ Thống & Tài Khoản',
      subtitle: 'Quy định độ tuổi lao động tối thiểu, phiên bản ứng dụng di động bắt buộc và hộp thư CSKH sàn.',
    },
    ALL: {
      title: 'Tham Số & Quy Tắc Nghiệp Vụ Toàn Sàn',
      subtitle: 'Quản trị và điều chỉnh thuật toán ghép ca Matching, tỷ lệ hoa hồng, ngưỡng uy tín và các quy tắc vận hành theo thời gian thực.',
    },
  };
  const currentHeader = headerMeta[selectedCategory] || headerMeta.ALL;

  return (
    <div className="sys-config-header">
      {/* Top Banner */}
      <div className="sys-config-topbar">
        <div>
          <h2 className="sys-config-title">{currentHeader.title}</h2>
          <p className="sys-config-subtitle">{currentHeader.subtitle}</p>
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
