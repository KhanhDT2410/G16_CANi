import { useAdminUsers } from '../hooks/useAdminUsers';
import UserFilterBar from './UserFilterBar';
import UserTable from './UserTable';
import UserDetailModal from './UserDetailModal';
import UserStatusModal from './UserStatusModal';
import './UserManagement.css';

const UserManagementView = () => {
  const {
    users,
    loading,
    filter,
    page,
    totalPages,
    totalElements,
    selectedUser,
    detailLoading,
    statusModalUser,
    actionSubmitting,
    setPage,
    handleFilterChange,
    handleResetFilter,
    openDetailModal,
    closeDetailModal,
    openStatusModal,
    closeStatusModal,
    confirmUpdateStatus,
  } = useAdminUsers();

  return (
    <div className="user-mgmt-container">
      {/* Bộ lọc và thanh tìm kiếm */}
      <UserFilterBar
        filter={filter}
        onFilterChange={handleFilterChange}
        onReset={handleResetFilter}
      />

      {/* Bảng danh sách người dùng kèm phân trang */}
      <UserTable
        users={users}
        loading={loading}
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onPageChange={setPage}
        onViewDetail={openDetailModal}
        onOpenStatusModal={openStatusModal}
      />

      {/* Modal chi tiết tài khoản */}
      <UserDetailModal
        user={selectedUser}
        loading={detailLoading}
        onClose={closeDetailModal}
        onOpenStatusModal={openStatusModal}
      />

      {/* Modal xác nhận khóa / mở khóa tài khoản */}
      <UserStatusModal
        user={statusModalUser}
        submitting={actionSubmitting}
        onClose={closeStatusModal}
        onConfirm={confirmUpdateStatus}
      />
    </div>
  );
};

export default UserManagementView;
