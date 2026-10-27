import React from 'react';
import { CRUD_ACTIONS } from '../types/rbac.constants';

/**
 * Thanh Điều Hướng Chế Độ Xem & Bộ Lọc Phân Quyền (W-40)
 * Tuân thủ quy tắc DESIGN.md & Bước 4 UC-113 (Lọc theo View/Create/Edit/Delete).
 */
export const RoleSelectorBar = ({
  viewMode,
  onViewModeChange,
  roles,
  selectedRoleId,
  onSelectRole,
  searchQuery,
  onSearchChange,
  moduleFilter,
  onModuleFilterChange,
  actionFilter,
  onActionFilterChange,
  modules,
  onOpenCreateRole,
  onOpenAuditLogs,
}) => {
  return (
    <div className="rbac-toolbar-card">
      {/* Hàng 1: Switcher Chế độ xem & Nút hành động */}
      <div className="rbac-toolbar-row">
        {/* Pill Segmented Control theo DESIGN.md Mục 5.3 */}
        <div className="rbac-segmented-pill">
          <button
            type="button"
            className={`rbac-pill-btn ${viewMode === 'matrix' ? 'active' : ''}`}
            onClick={() => onViewModeChange('matrix')}
          >
            <span className="rbac-pill-icon">▦</span>
            Ma Trận Phân Quyền (Matrix)
          </button>
          <button
            type="button"
            className={`rbac-pill-btn ${viewMode === 'detailed' ? 'active' : ''}`}
            onClick={() => onViewModeChange('detailed')}
          >
            <span className="rbac-pill-icon">📑</span>
            Chi Tiết Từng Vai Trò
          </button>
        </div>

        {/* Nút thao tác thêm vai trò và nhật ký kiểm toán BR-70 */}
        <div className="rbac-toolbar-actions">
          <button
            type="button"
            className="rbac-btn-secondary"
            onClick={onOpenAuditLogs}
            title="Xem nhật ký kiểm toán thay đổi phân quyền (BR-70 - 60 tháng)"
          >
            <span>📜</span>
            Nhật Ký Kiểm Toán (W-42)
          </button>
          <button
            type="button"
            className="rbac-btn-primary"
            onClick={onOpenCreateRole}
            title="Khởi tạo vai trò tùy chỉnh mới (AF-01)"
          >
            <span>+</span>
            Tạo Vai Trò Mới
          </button>
        </div>
      </div>

      {/* Hàng 2: Bộ lọc tìm kiếm quyền hạn, Phân hệ & Cấp độ CRUD (UC-113 Bước 4) */}
      <div className="rbac-filter-row">
        {/* Ô tìm kiếm quyền hạn */}
        <div className="rbac-search-box">
          <span className="rbac-search-icon">🔍</span>
          <input
            type="text"
            className="rbac-search-input"
            placeholder="Tìm theo tên, mã quyền (VD: EVENT:CREATE, QR_SCAN, PAYROLL)..."
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
          />
          {searchQuery && (
            <button
              type="button"
              className="rbac-search-clear"
              onClick={() => onSearchChange('')}
            >
              ✕
            </button>
          )}
        </div>

        {/* Lọc theo phân hệ chức năng */}
        <div className="rbac-module-select-wrapper">
          <select
            className="rbac-module-select"
            value={moduleFilter}
            onChange={(e) => onModuleFilterChange(e.target.value)}
          >
            <option value="ALL">Tất cả phân hệ ({modules.length})</option>
            {modules.map((mod) => (
              <option key={mod.id} value={mod.id}>
                {mod.icon} [{mod.code}] {mod.name}
              </option>
            ))}
          </select>
        </div>

        {/* Lọc theo thao tác CRUD (Bước 4 UC-113: View / Create / Edit / Delete) */}
        <div className="rbac-action-filter-pills">
          {Object.values(CRUD_ACTIONS).map((act) => (
            <button
              key={act.key}
              type="button"
              className={`rbac-crud-pill ${actionFilter === act.key ? 'active' : ''}`}
              onClick={() => onActionFilterChange(act.key)}
            >
              <span className="rbac-crud-icon">{act.icon}</span>
              <span>{act.label}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Hàng 3: Danh sách vai trò (Role Tabs) */}
      <div className="rbac-role-tabs-container">
        <div className="rbac-role-tabs-scroll">
          {roles.map((role) => {
            const isSelected = selectedRoleId === role.id;
            return (
              <button
                key={role.id}
                type="button"
                className={`rbac-role-tab ${isSelected ? 'active' : ''}`}
                onClick={() => onSelectRole(role.id)}
              >
                <span
                  className="rbac-role-dot"
                  style={{ backgroundColor: role.color }}
                />
                <span className="rbac-role-name">{role.name}</span>
                <span className="rbac-role-chip">
                  {role.permissions.length} quyền
                </span>
                {role.isSystem ? (
                  <span className="rbac-chip-sys" title="Vai trò mặc định hệ thống (BR-69)">
                    {role.isSuperAdmin ? 'Super Admin' : 'Hệ thống'}
                  </span>
                ) : (
                  <span className="rbac-chip-custom" title="Vai trò do Admin khởi tạo (AF-01)">
                    Tùy chỉnh
                  </span>
                )}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
};

export default RoleSelectorBar;
