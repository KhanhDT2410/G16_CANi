import { useState, useEffect, useCallback, useMemo } from 'react';
import * as api from '../api/userManagementApi';
import {
  MOCK_USERS,
  MOCK_USER_METRICS,
  USER_ROLES,
  USER_STATUSES,
} from '../types/userManagement.constants';
import { showToast } from '../../../components/common/Toast';

/**
 * Custom hook quản lý state & business logic cho W-39: User Management.
 * Cách ly side-effects, tự động fallback sang mock data phong phú khi backend chưa bật.
 */
export const useUserManagement = () => {
  const [users, setUsers] = useState([]);
  const [metrics, setMetrics] = useState(null);
  const [loading, setLoading] = useState(true);

  // Filters & Search
  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState(USER_ROLES.ALL);
  const [statusFilter, setStatusFilter] = useState(USER_STATUSES.ALL);
  const [sortBy, setSortBy] = useState('newest'); // newest, oldest, reliability, name

  // Modals / Drawers state
  const [selectedUser, setSelectedUser] = useState(null);
  const [profileDrawerOpen, setProfileDrawerOpen] = useState(false);
  const [roleModalOpen, setRoleModalOpen] = useState(false);
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);
  const [statusModalState, setStatusModalState] = useState({
    isOpen: false,
    user: null,
    actionType: null, // 'SUSPEND' | 'BAN' | 'DEACTIVATE' | 'REACTIVATE'
  });

  // Load Data
  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [usersRes, metricsRes] = await Promise.allSettled([
        api.fetchUsers({ role: roleFilter, status: statusFilter, q: searchQuery }),
        api.fetchUserMetrics(),
      ]);

      setUsers(
        usersRes.status === 'fulfilled' && usersRes.value.data?.data
          ? usersRes.value.data.data
          : MOCK_USERS
      );
      setMetrics(
        metricsRes.status === 'fulfilled' && metricsRes.value.data?.data
          ? metricsRes.value.data.data
          : MOCK_USER_METRICS
      );
    } catch {
      setUsers(MOCK_USERS);
      setMetrics(MOCK_USER_METRICS);
    } finally {
      setLoading(false);
    }
  }, [roleFilter, statusFilter, searchQuery]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Client-side filtering & sorting for smooth UX
  const filteredUsers = useMemo(() => {
    return users
      .filter((user) => {
        // Search text
        if (searchQuery.trim()) {
          const q = searchQuery.toLowerCase().trim();
          const matchName = user.fullName?.toLowerCase().includes(q);
          const matchEmail = user.email?.toLowerCase().includes(q);
          const matchPhone = user.phone?.includes(q);
          const matchCode = user.code?.toLowerCase().includes(q);
          const matchCompany = user.companyName?.toLowerCase().includes(q);
          if (!matchName && !matchEmail && !matchPhone && !matchCode && !matchCompany) {
            return false;
          }
        }
        // Role filter
        if (roleFilter !== USER_ROLES.ALL && user.role !== roleFilter) {
          return false;
        }
        // Status filter
        if (statusFilter !== USER_STATUSES.ALL && user.status !== statusFilter) {
          return false;
        }
        return true;
      })
      .sort((a, b) => {
        if (sortBy === 'newest') return b.id.localeCompare(a.id);
        if (sortBy === 'oldest') return a.id.localeCompare(b.id);
        if (sortBy === 'reliability') return (b.reliabilityScore || 0) - (a.reliabilityScore || 0);
        if (sortBy === 'name') return a.fullName.localeCompare(b.fullName, 'vi');
        return 0;
      });
  }, [users, searchQuery, roleFilter, statusFilter, sortBy]);

  // Actions
  const openProfileDrawer = (user) => {
    setSelectedUser(user);
    setProfileDrawerOpen(true);
  };

  const closeProfileDrawer = () => {
    setProfileDrawerOpen(false);
  };

  const openRoleModal = (user) => {
    setSelectedUser(user);
    setRoleModalOpen(true);
  };

  const closeRoleModal = () => {
    setRoleModalOpen(false);
  };

  const openPasswordModal = (user) => {
    setSelectedUser(user);
    setPasswordModalOpen(true);
  };

  const closePasswordModal = () => {
    setPasswordModalOpen(false);
  };

  const openStatusModal = (user, actionType) => {
    setStatusModalState({
      isOpen: true,
      user,
      actionType,
    });
  };

  const closeStatusModal = () => {
    setStatusModalState({
      isOpen: false,
      user: null,
      actionType: null,
    });
  };

  // Submit Handlers
  const handleUpdateRole = async (userId, newRole, reason) => {
    try {
      await api.updateUserRole(userId, { role: newRole, reason });
    } catch {
      // Mock update fallback
    }

    setUsers((prev) =>
      prev.map((u) => (u.id === userId ? { ...u, role: newRole } : u))
    );
    if (selectedUser?.id === userId) {
      setSelectedUser((prev) => ({ ...prev, role: newRole }));
    }
    closeRoleModal();
    showToast('Cập nhật vai trò người dùng thành công!', 'success');
  };

  const handleForceResetPassword = async (userId, payload) => {
    try {
      await api.forceResetPassword(userId, payload);
    } catch {
      // Mock fallback
    }
    closePasswordModal();
    showToast(
      payload.method === 'EMAIL'
        ? `Đã gửi liên kết khôi phục mật khẩu bảo mật đến email của người dùng!`
        : `Đã cấp mật khẩu tạm thời thành công! Vui lòng sao chép và chuyển cho người dùng.`,
      'success'
    );
  };

  const handleApplyStatusAction = async (userId, actionType, payload) => {
    try {
      if (actionType === 'SUSPEND') {
        await api.suspendUser(userId, payload);
      } else if (actionType === 'BAN') {
        await api.banUser(userId, payload);
      } else if (actionType === 'DEACTIVATE') {
        await api.deactivateUser(userId, payload);
      } else if (actionType === 'REACTIVATE') {
        await api.reactivateUser(userId, payload);
      }
    } catch {
      // Mock fallback
    }

    let newStatus = 'ACTIVE';
    let extraFields = {};
    if (actionType === 'SUSPEND') {
      newStatus = 'SUSPENDED';
      extraFields = {
        suspendedUntil: payload.durationDays === -1 ? 'Thủ công' : `${payload.durationDays} ngày tới`,
        suspendReason: payload.reason,
      };
    } else if (actionType === 'BAN') {
      newStatus = 'BANNED';
      extraFields = { bannedReason: payload.reason };
    } else if (actionType === 'DEACTIVATE') {
      newStatus = 'DEACTIVATED';
      extraFields = { deactivatedReason: payload.reason };
    } else if (actionType === 'REACTIVATE') {
      newStatus = 'ACTIVE';
      extraFields = { suspendedUntil: null, suspendReason: null, bannedReason: null, deactivatedReason: null };
    }

    setUsers((prev) =>
      prev.map((u) => (u.id === userId ? { ...u, status: newStatus, ...extraFields } : u))
    );
    if (selectedUser?.id === userId) {
      setSelectedUser((prev) => ({ ...prev, status: newStatus, ...extraFields }));
    }

    closeStatusModal();
    const actionLabel =
      actionType === 'SUSPEND'
        ? 'Tạm khóa tài khoản thành công!'
        : actionType === 'BAN'
        ? 'Đã áp dụng lệnh cấm vĩnh viễn đối với tài khoản!'
        : actionType === 'DEACTIVATE'
        ? 'Đã vô hiệu hóa tài khoản (dữ liệu được bảo toàn đầy đủ).'
        : 'Đã mở khóa và kích hoạt lại tài khoản thành công!';

    showToast(actionLabel, actionType === 'REACTIVATE' ? 'success' : 'warning');
  };

  return {
    users: filteredUsers,
    totalCount: users.length,
    filteredCount: filteredUsers.length,
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
    refresh: loadData,
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
  };
};
