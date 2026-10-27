import React from 'react';
import { RISK_LEVELS, CRUD_ACTIONS } from '../types/rbac.constants';

/**
 * Ma Trận Phân Quyền Tổng Thể (W-40 Matrix View - UC-113)
 * Liệt kê toàn bộ phân hệ và quyền hạn theo từng cột vai trò với toggle bật/tắt trực tiếp.
 */
export const PermissionMatrixTable = ({
  roles,
  permissionsByModule,
  modules,
  onTogglePermission,
  onToggleModulePermissions,
}) => {
  return (
    <div className="rbac-matrix-wrapper">
      <div className="rbac-table-scroll">
        <table className="rbac-matrix-table">
          <thead>
            <tr>
              <th className="rbac-th-perm-info">
                <span>Phân Hệ & Quyền Hạn Chi Tiết (FE-01 → FE-13)</span>
              </th>
              <th className="rbac-th-action-type">Thao Tác</th>
              <th className="rbac-th-risk">Mức Rủi Ro</th>
              {roles.map((role) => (
                <th key={role.id} className="rbac-th-role">
                  <div className="rbac-th-role-inner">
                    <span
                      className="rbac-th-role-dot"
                      style={{ backgroundColor: role.color }}
                    />
                    <span className="rbac-th-role-name" title={role.name}>
                      {role.name}
                    </span>
                    <span className="rbac-th-role-count">
                      {role.permissions.length} quyền
                    </span>
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {modules.map((mod) => {
              const moduleData = permissionsByModule[mod.id];
              const permList = moduleData?.permissions || [];
              if (permList.length === 0) return null;

              return (
                <React.Fragment key={mod.id}>
                  {/* Module Header Row */}
                  <tr className="rbac-module-header-row">
                    <td colSpan={3} className="rbac-module-td-title">
                      <div className="rbac-module-header-content">
                        <span className="rbac-module-icon">{mod.icon}</span>
                        <div>
                          <span className="rbac-module-code-badge">{mod.code}</span>
                          <strong className="rbac-module-title">{mod.name}</strong>
                          <span className="rbac-module-desc"> — {mod.description}</span>
                        </div>
                      </div>
                    </td>
                    {roles.map((role) => {
                      const activeInMod = permList.filter((p) =>
                        role.permissions.includes(p.code)
                      ).length;
                      const allActive = activeInMod === permList.length;

                      return (
                        <td key={role.id} className="rbac-module-td-action">
                          <button
                            type="button"
                            className={`rbac-btn-mod-toggle ${allActive ? 'active' : ''}`}
                            onClick={() =>
                              onToggleModulePermissions(role.id, mod.id, !allActive)
                            }
                            title={`${allActive ? 'Tắt' : 'Bật'} toàn bộ quyền phân hệ này`}
                          >
                            {activeInMod}/{permList.length}
                          </button>
                        </td>
                      );
                    })}
                  </tr>

                  {/* Individual Permission Rows */}
                  {permList.map((perm) => {
                    const riskMeta = RISK_LEVELS[perm.risk] || RISK_LEVELS.LOW;
                    const actionMeta = CRUD_ACTIONS[perm.actionType] || CRUD_ACTIONS.VIEW;

                    return (
                      <tr key={perm.id} className="rbac-perm-row">
                        <td className="rbac-perm-info-td">
                          <div className="rbac-perm-name-group">
                            <span className="rbac-perm-name">{perm.name}</span>
                            <span className="rbac-perm-code">{perm.code}</span>
                          </div>
                          <div className="rbac-perm-desc">{perm.description}</div>
                        </td>
                        <td className="rbac-perm-action-td">
                          <span className="rbac-crud-badge" title={actionMeta.label}>
                            {actionMeta.icon} {perm.actionType}
                          </span>
                        </td>
                        <td className="rbac-perm-risk-td">
                          <span
                            className="rbac-risk-badge"
                            style={{
                              color: riskMeta.color,
                              backgroundColor: riskMeta.bg,
                              borderColor: riskMeta.border,
                            }}
                          >
                            {riskMeta.label}
                          </span>
                        </td>
                        {roles.map((role) => {
                          const isGranted = role.permissions.includes(perm.code);
                          const isProtectedAdmin =
                            role.code === 'ADMIN' &&
                            perm.code === 'SYSTEM:RBAC_MATRIX_MANAGE';

                          return (
                            <td key={role.id} className="rbac-perm-toggle-td">
                              <label
                                className={`rbac-switch ${isProtectedAdmin ? 'disabled' : ''}`}
                                title={
                                  isProtectedAdmin
                                    ? 'Quyền quản trị tối cao được bảo vệ cốt lõi (BR-69)'
                                    : `${isGranted ? 'Thu hồi' : 'Cấp'} quyền cho ${role.name}`
                                }
                              >
                                <input
                                  type="checkbox"
                                  checked={isGranted}
                                  disabled={isProtectedAdmin}
                                  onChange={() => onTogglePermission(role.id, perm.code)}
                                />
                                <span className="rbac-slider" />
                              </label>
                            </td>
                          );
                        })}
                      </tr>
                    );
                  })}
                </React.Fragment>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default PermissionMatrixTable;
