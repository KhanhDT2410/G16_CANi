import { USER_ROLES, USER_STATUSES, STATUS_CONFIG } from '../types/userManagement.constants';

/**
 * UserFiltersBar.jsx
 * Thanh tìm kiếm & bộ lọc người dùng với Pill Segmented Control chuẩn DESIGN.md.
 */

export const UserFiltersBar = ({
  searchQuery,
  onSearchChange,
  roleFilter,
  onRoleChange,
  statusFilter,
  onStatusChange,
  sortBy,
  onSortChange,
  totalCount,
  filteredCount,
  onRefresh,
}) => {
  const roleTabs = [
    { key: USER_ROLES.ALL, label: 'Tất cả' },
    { key: USER_ROLES.WORKER, label: 'Nhân Sự (Worker)' },
    { key: USER_ROLES.EVENT_ORGANIZER, label: 'Nhà Tổ Chức (EO)' },
    { key: USER_ROLES.CSKH, label: 'CSKH' },
    { key: USER_ROLES.ADMIN, label: 'Admin' },
  ];

  return (
    <div className="um-filters-wrap">
      {/* Hàng 1: Pill Segmented Tabs chọn Vai trò */}
      <div className="um-role-tabs-container">
        <div className="um-pill-tabs">
          {roleTabs.map((tab) => (
            <button
              key={tab.key}
              type="button"
              className={`um-pill-tab ${roleFilter === tab.key ? 'active' : ''}`}
              onClick={() => onRoleChange(tab.key)}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <div className="um-counts-badge">
          Hiển thị <strong>{filteredCount}</strong> / {totalCount} tài khoản
        </div>
      </div>

      {/* Hàng 2: Search input + Status dropdown + Sort dropdown + Refresh */}
      <div className="um-filter-controls">
        <div className="um-search-box">
          <span className="um-search-icon">🔍</span>
          <input
            type="text"
            className="um-search-input"
            placeholder="Tìm theo Tên, Email, SĐT, Mã định danh..."
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
          />
          {searchQuery && (
            <button
              type="button"
              className="um-search-clear"
              onClick={() => onSearchChange('')}
              title="Xóa tìm kiếm"
            >
              ✕
            </button>
          )}
        </div>

        <div className="um-select-group">
          {/* Lọc Trạng Thái */}
          <select
            className="um-select"
            value={statusFilter}
            onChange={(e) => onStatusChange(e.target.value)}
          >
            <option value={USER_STATUSES.ALL}>Tất cả trạng thái</option>
            <option value={USER_STATUSES.ACTIVE}>🟢 Đang hoạt động</option>
            <option value={USER_STATUSES.PENDING_VERIFICATION}>🟡 Chờ duyệt eKYC</option>
            <option value={USER_STATUSES.SUSPENDED}>🟠 Tạm khóa</option>
            <option value={USER_STATUSES.BANNED}>🔴 Bị cấm vĩnh viễn</option>
            <option value={USER_STATUSES.DEACTIVATED}>⚪ Đã vô hiệu hóa</option>
          </select>

          {/* Sắp xếp */}
          <select
            className="um-select"
            value={sortBy}
            onChange={(e) => onSortChange(e.target.value)}
          >
            <option value="newest">📅 Mới nhất trước</option>
            <option value="oldest">📅 Cũ nhất trước</option>
            <option value="reliability">⭐ Điểm uy tín cao nhất</option>
            <option value="name">🔤 Tên A - Z</option>
          </select>

          {/* Nút Làm Mới */}
          <button
            type="button"
            className="um-btn-refresh"
            onClick={onRefresh}
            title="Tải lại dữ liệu"
          >
            🔄
          </button>
        </div>
      </div>
    </div>
  );
};

export default UserFiltersBar;
