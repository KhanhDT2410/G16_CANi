import { useState, useEffect, useCallback } from 'react';
import { userManagementApi } from '../api/userManagementApi';
import { showToast } from '../../../components/common/Toast';

export const useAdminUsers = () => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [filter, setFilter] = useState({ search: '', role: '', status: '' });
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Modals state
  const [selectedUser, setSelectedUser] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [statusModalUser, setStatusModalUser] = useState(null);
  const [actionSubmitting, setActionSubmitting] = useState(false);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    try {
      const params = {
        page,
        size,
        sort: 'createdAt,desc',
      };
      if (filter.search?.trim()) params.search = filter.search.trim();
      if (filter.role) params.role = filter.role;
      if (filter.status) params.status = filter.status;

      const result = await userManagementApi.getUsers(params);
      if (result) {
        setUsers(result.content || []);
        setTotalPages(result.totalPages || 0);
        setTotalElements(result.totalElements || 0);
      }
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải danh sách người dùng';
      showToast(msg, 'error');
    } finally {
      setLoading(false);
    }
  }, [page, size, filter]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const handleFilterChange = (key, value) => {
    setFilter((prev) => ({ ...prev, [key]: value }));
    setPage(0); // Reset ve trang 0 khi doi bo loc
  };

  const handleResetFilter = () => {
    setFilter({ search: '', role: '', status: '' });
    setPage(0);
  };

  const openDetailModal = async (userId) => {
    setDetailLoading(true);
    setSelectedUser(null);
    try {
      const detail = await userManagementApi.getUserById(userId);
      setSelectedUser(detail);
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể xem chi tiết tài khoản';
      showToast(msg, 'error');
    } finally {
      setDetailLoading(false);
    }
  };

  const closeDetailModal = () => {
    setSelectedUser(null);
  };

  const openStatusModal = (user) => {
    setStatusModalUser(user);
  };

  const closeStatusModal = () => {
    setStatusModalUser(null);
  };

  const confirmUpdateStatus = async (targetStatus, reason) => {
    if (!statusModalUser) return;
    setActionSubmitting(true);
    try {
      await userManagementApi.updateUserStatus(statusModalUser.id, {
        status: targetStatus,
        reason,
      });

      const actionText = targetStatus === 'LOCKED' ? 'Khóa tài khoản' : 'Mở khóa tài khoản';
      showToast(`${actionText} thành công!`, 'success');
      closeStatusModal();
      fetchUsers();
    } catch (err) {
      const msg = err.response?.data?.message || 'Cập nhật trạng thái thất bại';
      showToast(msg, 'error');
    } finally {
      setActionSubmitting(false);
    }
  };

  return {
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
    refresh: fetchUsers,
  };
};
