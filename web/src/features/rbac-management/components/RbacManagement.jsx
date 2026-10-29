import { useRbacManagement } from '../hooks/useRbacManagement';
import RbacMetricsGrid from './RbacMetricsGrid';
import RoleSelectorBar from './RoleSelectorBar';
import RoleDetailsCard from './RoleDetailsCard';
import PermissionMatrixTable from './PermissionMatrixTable';
import RolePermissionAccordion from './RolePermissionAccordion';
import CreateRoleModal from './CreateRoleModal';
import RbacAuditModal from './RbacAuditModal';
import Msg157Modal from './Msg157Modal';
import ResetRoleModal from './ResetRoleModal';
import '../rbacManagement.css';

/**
 * W-40: Roles & Permissions (RBAC) Screen
 *
 * Màn hình cấu hình Ma trận Phân quyền & Vai trò tập trung cho Quản trị viên (Admin):
 * - Quản lý các vai trò hệ thống (Admin, CSKH, CSKH & Admin, EO, Worker) và vai trò tùy chỉnh.
 * - Ma trận phân quyền chi tiết (Matrix View) và chế độ xem chuyên sâu theo vai trò (Detailed View).
 * - Bật/tắt phân quyền theo từng tính năng hoặc theo cụm phân hệ chức năng.
 * - Cơ chế có hiệu lực ngay lập tức (Immediate Effect) cho toàn bộ tài khoản.
 * - Xử lý ngoại lệ Bước 5a: Cảnh báo xác nhận khi thu hồi quyền hạn nguy cấp (MSG157).
 * - Lưu vết và hiển thị nhật ký kiểm toán phân quyền (RBAC Audit Trail - BR-70 lưu trữ 60 tháng).
 * - Tuân thủ SSOT tại DESIGN.md và đặc tả UC-113.
 */
export const RbacManagement = () => {
  const {
    roles,
    selectedRoleId,
    setSelectedRoleId,
    selectedRole,
    viewMode,
    setViewMode,
    searchQuery,
    setSearchQuery,
    moduleFilter,
    setModuleFilter,
    actionFilter,
    setActionFilter,
    loading,
    metrics,
    auditLogs,
    modules,
    permissionsByModule,
    togglePermission,
    toggleModulePermissions,
    handleCreateCustomRole,
    handleDeleteCustomRole,
    handleResetRoleToDefault,
    isCreateRoleModalOpen,
    setIsCreateRoleModalOpen,
    isAuditModalOpen,
    setIsAuditModalOpen,
    roleToReset,
    setRoleToReset,
    msg157Dialog,
    setMsg157Dialog,
    handleConfirmMsg157,
  } = useRbacManagement();

  return (
    <div className="rbac-container">
      {/* 1. Header Màn hình W-40 */}
      <div className="rbac-header">
        <div>
          <h1 className="rbac-header-title">
            Phân Quyền & Vai Trò Hệ Thống

          </h1>
          <p className="rbac-header-sub">
            cấu hình vai trò tùy chỉnh.Mọi thay đổi có hiệu lực ngay lập tức và lưu nhật ký kiểm toán bất biến 60 tháng.
          </p>
        </div>
      </div>

      {/* 2. Thẻ KPI Thống Kê Phân Quyền */}
      <RbacMetricsGrid metrics={metrics} loading={loading} />

      {/* 3. Thanh Điều Hướng Chế Độ Xem & Bộ Lọc */}
      <RoleSelectorBar
        viewMode={viewMode}
        onViewModeChange={setViewMode}
        roles={roles}
        selectedRoleId={selectedRoleId}
        onSelectRole={setSelectedRoleId}
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        moduleFilter={moduleFilter}
        onModuleFilterChange={setModuleFilter}
        actionFilter={actionFilter}
        onActionFilterChange={setActionFilter}
        modules={modules}
        onOpenCreateRole={() => setIsCreateRoleModalOpen(true)}
        onOpenAuditLogs={() => setIsAuditModalOpen(true)}
      />

      {/* 4. Thẻ Chi Tiết Vai Trò (Hiển thị khi ở chế độ xem chi tiết) */}
      {viewMode === 'detailed' && (
        <RoleDetailsCard
          role={selectedRole}
          onResetRole={(id) => setRoleToReset(id)}
          onDeleteRole={handleDeleteCustomRole}
        />
      )}

      {/* 5. Nội Dung Chính Phân Quyền Theo Chế Độ Xem */}
      {viewMode === 'matrix' ? (
        <PermissionMatrixTable
          roles={roles}
          permissionsByModule={permissionsByModule}
          modules={modules}
          onTogglePermission={togglePermission}
          onToggleModulePermissions={toggleModulePermissions}
        />
      ) : (
        <RolePermissionAccordion
          role={selectedRole}
          permissionsByModule={permissionsByModule}
          modules={modules}
          onTogglePermission={togglePermission}
          onToggleModulePermissions={toggleModulePermissions}
        />
      )}

      {/* 6. Modal Tạo Vai Trò Tùy Chỉnh (AF-01) */}
      <CreateRoleModal
        isOpen={isCreateRoleModalOpen}
        onClose={() => setIsCreateRoleModalOpen(false)}
        onConfirm={handleCreateCustomRole}
        existingRoles={roles}
      />

      {/* 7. Modal Nhật Ký Kiểm Toán Phân Quyền (BR-70) */}
      <RbacAuditModal
        isOpen={isAuditModalOpen}
        onClose={() => setIsAuditModalOpen(false)}
        auditLogs={auditLogs}
      />

      {/* 8. Hộp Thoại Ngoại Lệ Bước 5a: Cảnh Báo Thu Hồi Quyền Nguy Cấp (MSG157) */}
      <Msg157Modal
        isOpen={msg157Dialog.isOpen}
        dialogData={msg157Dialog}
        onClose={() => setMsg157Dialog({ isOpen: false })}
        onConfirm={handleConfirmMsg157}
      />

      {/* 9. Modal Xác Nhận Khôi Phục Phân Quyền Mặc Định */}
      <ResetRoleModal
        isOpen={!!roleToReset}
        onClose={() => setRoleToReset(null)}
        onConfirm={() => handleResetRoleToDefault(roleToReset)}
      />
    </div>
  );
};

export default RbacManagement;
