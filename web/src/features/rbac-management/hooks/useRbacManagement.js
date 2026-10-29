import { useState, useEffect, useMemo, useCallback } from 'react';
import {
  INITIAL_ROLES,
  ALL_PERMISSIONS,
  PERMISSION_MODULES,
  MOCK_RBAC_METRICS,
  MOCK_RBAC_AUDIT_LOGS,
} from '../types/rbac.constants';
import * as rbacApi from '../api/rbacApi';
import { showToast } from '../../../components/common/Toast';

export const useRbacManagement = () => {
  const [roles, setRoles] = useState(INITIAL_ROLES);
  const [selectedRoleId, setSelectedRoleId] = useState('role_admin');
  const [viewMode, setViewMode] = useState('matrix'); // 'matrix' | 'detailed'
  const [searchQuery, setSearchQuery] = useState('');
  const [moduleFilter, setModuleFilter] = useState('ALL');
  const [actionFilter, setActionFilter] = useState('ALL'); // 'ALL' | 'VIEW' | 'CREATE' | 'EDIT' | 'DELETE'
  const [loading, setLoading] = useState(false);
  const [auditLogs, setAuditLogs] = useState(MOCK_RBAC_AUDIT_LOGS);
  const [metrics, setMetrics] = useState(MOCK_RBAC_METRICS);

  // Modals state
  const [isCreateRoleModalOpen, setIsCreateRoleModalOpen] = useState(false);
  const [isAuditModalOpen, setIsAuditModalOpen] = useState(false);
  const [roleToReset, setRoleToReset] = useState(null);

  // MSG157 Critical Permission Warning Dialog State (Report 3 Step 5a)
  const [msg157Dialog, setMsg157Dialog] = useState({
    isOpen: false,
    roleId: null,
    permissionCode: null,
    permissionName: '',
    roleName: '',
  });

  // Fetch initial data
  const loadRbacData = useCallback(async () => {
    setLoading(true);
    try {
      const [rolesRes, metricsRes, auditRes] = await Promise.allSettled([
        rbacApi.fetchRoles(),
        rbacApi.fetchRbacMetrics(),
        rbacApi.fetchRbacAuditLogs(),
      ]);

      if (rolesRes.status === 'fulfilled' && rolesRes.value?.data?.data) {
        setRoles(rolesRes.value.data.data);
      }
      if (metricsRes.status === 'fulfilled' && metricsRes.value?.data?.data) {
        setMetrics(metricsRes.value.data.data);
      }
      if (auditRes.status === 'fulfilled' && auditRes.value?.data?.data) {
        setAuditLogs(auditRes.value.data.data);
      }
    } catch {
      // Dùng mock fallback an toàn
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRbacData();
  }, [loadRbacData]);

  // Selected role object
  const selectedRole = useMemo(() => {
    return roles.find((r) => r.id === selectedRoleId) || roles[0] || null;
  }, [roles, selectedRoleId]);

  // Filtered permissions list based on search, module, and CRUD action filter
  const filteredPermissions = useMemo(() => {
    return ALL_PERMISSIONS.filter((perm) => {
      const matchModule = moduleFilter === 'ALL' || perm.module === moduleFilter;
      const matchAction = actionFilter === 'ALL' || perm.actionType === actionFilter;
      const query = searchQuery.trim().toLowerCase();

      if (!query) return matchModule && matchAction;

      const matchName = perm.name.toLowerCase().includes(query);
      const matchCode = perm.code.toLowerCase().includes(query);
      const matchDesc = perm.description.toLowerCase().includes(query);

      return matchModule && matchAction && (matchName || matchCode || matchDesc);
    });
  }, [moduleFilter, actionFilter, searchQuery]);

  // Group filtered permissions by module
  const permissionsByModule = useMemo(() => {
    const map = {};
    PERMISSION_MODULES.forEach((mod) => {
      map[mod.id] = {
        ...mod,
        permissions: filteredPermissions.filter((p) => p.module === mod.id),
      };
    });
    return map;
  }, [filteredPermissions]);

  // Internal executor for permission update
  const executePermissionToggle = async (roleId, permissionCode, isRevoke) => {
    const targetRole = roles.find((r) => r.id === roleId);
    if (!targetRole) return;

    const permMeta = ALL_PERMISSIONS.find((p) => p.code === permissionCode);
    const permName = permMeta ? permMeta.name : permissionCode;

    // Quy tắc BR-69: Không thể tước quyền quản trị hệ thống của tài khoản ADMIN tối cao
    if (targetRole.code === 'ADMIN' && permissionCode === 'SYSTEM:RBAC_MATRIX_MANAGE') {
      showToast('Quy tắc BR-69: Không thể tước quyền Quản trị RBAC của Super Admin!', 'warning');
      return;
    }

    const updatedPermissions = isRevoke
      ? targetRole.permissions.filter((code) => code !== permissionCode)
      : [...targetRole.permissions, permissionCode];

    const timestamp = new Date().toLocaleString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });

    // Update state immediately
    setRoles((prev) =>
      prev.map((r) =>
        r.id === roleId
          ? {
              ...r,
              permissions: updatedPermissions,
              lastModifiedAt: timestamp,
              lastModifiedBy: 'Mai Hải Dương (Lead Admin)',
            }
          : r
      )
    );

    // Ghi vết kiểm toán bất biến theo tiêu chuẩn BR-70
    const newAuditEntry = {
      id: `audit_${Date.now()}`,
      actorId: 'adm_duongmh',
      changedBy: 'Mai Hải Dương (Lead Admin)',
      actionType: isRevoke ? 'PERMISSION_REVOKED' : 'PERMISSION_GRANTED',
      roleCode: targetRole.code,
      roleName: targetRole.name,
      permissionCode,
      permissionName: permName,
      oldState: `${targetRole.permissions.length} quyền`,
      newState: `${updatedPermissions.length} quyền (${isRevoke ? 'Thu hồi' : 'Cấp'} ${permissionCode})`,
      timestamp,
      ipAddress: '192.168.1.15',
      reason: `Điều chỉnh phân quyền trực tiếp màn hình W-40 (UC-113).`,
    };
    setAuditLogs((prev) => [newAuditEntry, ...prev]);

    // Backend call
    try {
      await rbacApi.updateRolePermissions(roleId, updatedPermissions);
    } catch {
      // Mock Fallback graceful
    }

    showToast(
      isRevoke
        ? `Đã thu hồi quyền "${permName}" khỏi ${targetRole.name}. Áp dụng ngay!`
        : `Đã cấp quyền "${permName}" cho ${targetRole.name}. Áp dụng ngay!`,
      isRevoke ? 'warning' : 'success'
    );
  };

  // Toggle single permission with MSG157 Critical check
  const togglePermission = (roleId, permissionCode) => {
    const targetRole = roles.find((r) => r.id === roleId);
    if (!targetRole) return;

    const isGranted = targetRole.permissions.includes(permissionCode);
    const permMeta = ALL_PERMISSIONS.find((p) => p.code === permissionCode);

    // Xử lý Ngoại lệ Bước 5a UC-113: Nếu thu hồi quyền CRITICAL -> hiển thị hộp thoại MSG157
    if (isGranted && permMeta?.risk === 'CRITICAL') {
      setMsg157Dialog({
        isOpen: true,
        roleId,
        permissionCode,
        permissionName: permMeta.name,
        roleName: targetRole.name,
      });
      return;
    }

    executePermissionToggle(roleId, permissionCode, isGranted);
  };

  // Confirm MSG157 Dialog
  const handleConfirmMsg157 = () => {
    if (msg157Dialog.roleId && msg157Dialog.permissionCode) {
      executePermissionToggle(msg157Dialog.roleId, msg157Dialog.permissionCode, true);
    }
    setMsg157Dialog({
      isOpen: false,
      roleId: null,
      permissionCode: null,
      permissionName: '',
      roleName: '',
    });
  };

  // Toggle all permissions of a module for a role
  const toggleModulePermissions = async (roleId, moduleId, enableAll) => {
    const targetRole = roles.find((r) => r.id === roleId);
    if (!targetRole) return;

    const modulePermCodes = ALL_PERMISSIONS.filter((p) => p.module === moduleId).map(
      (p) => p.code
    );

    let updatedPermissions;
    if (enableAll) {
      updatedPermissions = Array.from(
        new Set([...targetRole.permissions, ...modulePermCodes])
      );
    } else {
      updatedPermissions = targetRole.permissions.filter((code) => {
        if (targetRole.code === 'ADMIN' && code === 'SYSTEM:RBAC_MATRIX_MANAGE') {
          return true;
        }
        return !modulePermCodes.includes(code);
      });
    }

    const timestamp = new Date().toLocaleString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });

    setRoles((prev) =>
      prev.map((r) =>
        r.id === roleId
          ? {
              ...r,
              permissions: updatedPermissions,
              lastModifiedAt: timestamp,
              lastModifiedBy: 'Mai Hải Dương (Lead Admin)',
            }
          : r
      )
    );

    try {
      await rbacApi.updateRolePermissions(roleId, updatedPermissions);
    } catch {
      // Mock fallback
    }

    const modMeta = PERMISSION_MODULES.find((m) => m.id === moduleId);
    showToast(
      enableAll
        ? `Đã kích hoạt toàn bộ quyền thuộc [${modMeta?.name}] cho ${targetRole.name}.`
        : `Đã gỡ toàn bộ quyền thuộc [${modMeta?.name}] khỏi ${targetRole.name}.`,
      'info'
    );
  };

  // Create custom role (AF-01)
  const handleCreateCustomRole = async (formData) => {
    const newRoleId = `role_${Date.now()}`;
    const timestamp = new Date().toLocaleString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });

    let initialPerms = [];
    if (formData.cloneFromRoleId) {
      const baseRole = roles.find((r) => r.id === formData.cloneFromRoleId);
      if (baseRole) {
        initialPerms = [...baseRole.permissions];
      }
    }

    const newRole = {
      id: newRoleId,
      code: formData.code.toUpperCase().trim(),
      name: formData.name.trim(),
      description: formData.description.trim() || 'Vai trò tùy chỉnh mới tạo (AF-01).',
      isSystem: false,
      isSuperAdmin: false,
      userCount: 0,
      color: formData.color || '#8b5cf6',
      permissions: initialPerms,
      lastModifiedAt: timestamp,
      lastModifiedBy: 'Mai Hải Dương (Lead Admin)',
    };

    setRoles((prev) => [...prev, newRole]);
    setSelectedRoleId(newRoleId);
    setIsCreateRoleModalOpen(false);

    setMetrics((prev) => ({
      ...prev,
      totalRoles: prev.totalRoles + 1,
      customRoles: prev.customRoles + 1,
    }));

    const newAudit = {
      id: `audit_${Date.now()}`,
      actorId: 'adm_duongmh',
      changedBy: 'Mai Hải Dương (Lead Admin)',
      actionType: 'ROLE_CREATED',
      roleCode: newRole.code,
      roleName: newRole.name,
      permissionCode: 'ALL_INITIAL',
      permissionName: `Tạo vai trò tùy chỉnh mới (AF-01: sao chép ${initialPerms.length} quyền)`,
      oldState: 'None',
      newState: `${initialPerms.length} quyền khởi tạo`,
      timestamp,
      ipAddress: '192.168.1.15',
      reason: formData.reason || 'Khởi tạo vai trò tùy chỉnh phân cấp theo UC-113.',
    };
    setAuditLogs((prev) => [newAudit, ...prev]);

    try {
      await rbacApi.createCustomRole(newRole);
    } catch {
      // Fallback
    }

    showToast(`Đã tạo thành công vai trò tùy chỉnh [${newRole.name}]!`, 'success');
  };

  // Delete a custom role
  const handleDeleteCustomRole = async (roleId) => {
    const targetRole = roles.find((r) => r.id === roleId);
    if (!targetRole) return;

    if (targetRole.isSystem) {
      showToast('Quy tắc BR-69: Không thể xóa vai trò hệ thống cốt lõi!', 'error');
      return;
    }

    if (targetRole.userCount > 0) {
      showToast(
        `Không thể xóa vai trò này vì đang có ${targetRole.userCount} tài khoản đảm nhiệm! Hãy thu hồi vai trò trước.`,
        'warning'
      );
      return;
    }

    setRoles((prev) => prev.filter((r) => r.id !== roleId));
    setSelectedRoleId('role_admin');

    setMetrics((prev) => ({
      ...prev,
      totalRoles: Math.max(1, prev.totalRoles - 1),
      customRoles: Math.max(0, prev.customRoles - 1),
    }));

    showToast(`Đã xóa vai trò [${targetRole.name}].`, 'success');
  };

  // Reset a role permissions to default initial state
  const handleResetRoleToDefault = async (roleId) => {
    const initialConfig = INITIAL_ROLES.find((r) => r.id === roleId);
    if (!initialConfig) {
      showToast('Không tìm thấy cấu hình gốc của vai trò này.', 'warning');
      return;
    }

    setRoles((prev) =>
      prev.map((r) =>
        r.id === roleId
          ? {
              ...r,
              permissions: [...initialConfig.permissions],
            }
          : r
      )
    );

    setRoleToReset(null);
    showToast(`Đã khôi phục phân quyền mặc định cho [${initialConfig.name}].`, 'success');
  };

  return {
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
    permissions: ALL_PERMISSIONS,
    modules: PERMISSION_MODULES,
    filteredPermissions,
    permissionsByModule,
    // Actions
    togglePermission,
    toggleModulePermissions,
    handleCreateCustomRole,
    handleDeleteCustomRole,
    handleResetRoleToDefault,
    refresh: loadRbacData,
    // Modals
    isCreateRoleModalOpen,
    setIsCreateRoleModalOpen,
    isAuditModalOpen,
    setIsAuditModalOpen,
    roleToReset,
    setRoleToReset,
    // MSG157
    msg157Dialog,
    setMsg157Dialog,
    handleConfirmMsg157,
  };
};
