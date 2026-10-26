import { useUserManagement } from '../hooks/useUserManagement';
import UserMetricsGrid from './UserMetricsGrid';
import UserFiltersBar from './UserFiltersBar';
import UserTable from './UserTable';
import UserProfileDrawer from './UserProfileDrawer';
import EditRoleModal from './EditRoleModal';
import ForceResetPasswordModal from './ForceResetPasswordModal';
import AccountStatusModal from './AccountStatusModal';
import '../userManagement.css';

/**
 * W-39: User Management – Screen
 *
 * Màn hình quản lý người dùng tập trung cho Quản trị viên (Admin):
 * - Tìm kiếm người dùng theo email, vai trò, trạng thái, SĐT, mã định danh.
 * - Xem hồ sơ chi tiết (Profile drawer / eKYC / Điểm uy tín / Lịch sử ca làm).
 * - Chỉnh sửa vai trò (Worker, EO, CSKH, Admin).
 * - Buộc đặt lại mật khẩu bảo mật (Email hoặc mật khẩu tạm).
 * - Quản lý trạng thái: Tạm khóa (Suspend), Cấm vĩnh viễn (Ban), Vô hiệu hóa (Deactivate - Không xóa cứng).
 * - Khôi phục tài khoản (Reactivate/Unban).
 * - Tuân thủ SSOT tại DESIGN.md và đặc tả nghiệp vụ UC-112.
 */

export const UserManagement = () => {
  const {
    users,
    totalCount,
    filteredCount,
    metrics,
    loading,
    searchQuery,
    setSearchQuery,
    roleFilter,
    setRoleFilter,
    statusFilter,
    setStatusFilter,
    sortBy,
    setSortBy,
    refresh,
    // Modals
    selectedUser,
    profileDrawerOpen,
    roleModalOpen,
    passwordModalOpen,
    statusModalState,
    openProfileDrawer,
    closeProfileDrawer,
    openRoleModal,
    closeRoleModal,
    openPasswordModal,
    closePasswordModal,
    openStatusModal,
    closeStatusModal,
    // Handlers
    handleUpdateRole,
    handleForceResetPassword,
    handleApplyStatusAction,
  } = useUserManagement();

  return (
    <div className="um-container">
      {/* 1. Header Màn hình W-39 */}
      <div className="um-header">
        <div>
          <h1 className="um-header-title">
            Quản Lý Tài Khoản Người Dùng
            <span className="um-header-badge">W-39 • UC-112</span>
          </h1>
          <p className="um-header-sub">
            Tra cứu, kiểm soát vai trò, đặt lại mật khẩu và áp dụng chế tài kỷ luật
            theo chuẩn kiểm toán CANi.
          </p>
        </div>
      </div>

      {/* 2. Thẻ KPI Thống Kê Tổng Quan */}
      <UserMetricsGrid metrics={metrics} loading={loading} />

      {/* 3. Thanh Tìm Kiếm & Lọc Nâng Cao (Pill Segmented Control) */}
      <UserFiltersBar
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        roleFilter={roleFilter}
        onRoleChange={setRoleFilter}
        statusFilter={statusFilter}
        onStatusChange={setStatusFilter}
        sortBy={sortBy}
        onSortChange={setSortBy}
        totalCount={totalCount}
        filteredCount={filteredCount}
        onRefresh={refresh}
      />

      {/* 4. Bảng Dữ Liệu Tài Khoản */}
      <UserTable
        users={users}
        loading={loading}
        onViewProfile={openProfileDrawer}
        onEditRole={openRoleModal}
        onResetPassword={openPasswordModal}
        onOpenStatusModal={openStatusModal}
      />

      {/* 5. Slide-Over Drawer: Xem Hồ Sơ Chi Tiết */}
      <UserProfileDrawer
        isOpen={profileDrawerOpen}
        user={selectedUser}
        onClose={closeProfileDrawer}
        onEditRole={openRoleModal}
        onResetPassword={openPasswordModal}
        onOpenStatusModal={openStatusModal}
      />

      {/* 6. Modal: Đổi Vai Trò Người Dùng */}
      <EditRoleModal
        isOpen={roleModalOpen}
        user={selectedUser}
        onClose={closeRoleModal}
        onConfirm={handleUpdateRole}
      />

      {/* 7. Modal: Buộc Đặt Lại Mật Khẩu */}
      <ForceResetPasswordModal
        isOpen={passwordModalOpen}
        user={selectedUser}
        onClose={closePasswordModal}
        onConfirm={handleForceResetPassword}
      />

      {/* 8. Modal: Tạm Khóa / Cấm / Vô Hiệu Hóa / Mở Lại (UC-112) */}
      <AccountStatusModal
        isOpen={statusModalState.isOpen}
        user={statusModalState.user}
        actionType={statusModalState.actionType}
        onClose={closeStatusModal}
        onConfirm={handleApplyStatusAction}
      />
    </div>
  );
};

export default UserManagement;
