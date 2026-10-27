import { useState } from 'react';
import { RISK_LEVELS, CRUD_ACTIONS } from '../types/rbac.constants';

/**
 * Danh Sách Quyền Theo Phân Hệ (Detailed Role View Accordion - W-40)
 * Cho phép quản trị viên xem và tinh chỉnh quyền của từng phân hệ cho vai trò đang chọn.
 */
export const RolePermissionAccordion = ({
  role,
  permissionsByModule,
  modules,
  onTogglePermission,
  onToggleModulePermissions,
}) => {
  const [collapsedModules, setCollapsedModules] = useState({});

  const toggleCollapse = (modId) => {
    setCollapsedModules((prev) => ({
      ...prev,
      [modId]: !prev[modId],
    }));
  };

  if (!role) return null;

  return (
    <div className="rbac-accordion-container">
      {modules.map((mod) => {
        const moduleData = permissionsByModule[mod.id];
        const permList = moduleData?.permissions || [];
        if (permList.length === 0) return null;

        const isCollapsed = !!collapsedModules[mod.id];
        const grantedCount = permList.filter((p) =>
          role.permissions.includes(p.code)
        ).length;
        const allActive = grantedCount === permList.length;

        return (
          <div key={mod.id} className="rbac-accordion-card">
            {/* Header Accordion Phân Hệ */}
            <div className="rbac-accordion-header">
              <button
                type="button"
                className="rbac-accordion-title-btn"
                onClick={() => toggleCollapse(mod.id)}
              >
                <span className="rbac-accordion-caret">{isCollapsed ? '▶' : '▼'}</span>
                <span className="rbac-accordion-icon">{mod.icon}</span>
                <div className="rbac-accordion-text">
                  <div className="rbac-accordion-title-group">
                    <span className="rbac-module-code-badge">{mod.code}</span>
                    <h3 className="rbac-accordion-name">{mod.name}</h3>
                  </div>
                  <p className="rbac-accordion-desc">{mod.description}</p>
                </div>
              </button>

              <div className="rbac-accordion-actions">
                <span className="rbac-accordion-counter">
                  Đã cấp: <strong>{grantedCount}</strong>/{permList.length}
                </span>
                <button
                  type="button"
                  className={`rbac-btn-batch-toggle ${allActive ? 'active' : ''}`}
                  onClick={() => onToggleModulePermissions(role.id, mod.id, !allActive)}
                >
                  {allActive ? 'Bỏ Chọn Cả Nhóm' : 'Cấp Tất Cả Nhóm'}
                </button>
              </div>
            </div>

            {/* Danh Sách Quyền Thuộc Phân Hệ */}
            {!isCollapsed && (
              <div className="rbac-accordion-body">
                {permList.map((perm) => {
                  const isGranted = role.permissions.includes(perm.code);
                  const riskMeta = RISK_LEVELS[perm.risk] || RISK_LEVELS.LOW;
                  const actionMeta = CRUD_ACTIONS[perm.actionType] || CRUD_ACTIONS.VIEW;
                  const isProtectedAdmin =
                    role.code === 'ADMIN' &&
                    perm.code === 'SYSTEM:RBAC_MATRIX_MANAGE';

                  return (
                    <div
                      key={perm.id}
                      className={`rbac-perm-item ${isGranted ? 'granted' : ''}`}
                    >
                      <div className="rbac-perm-item-left">
                        <div className="rbac-perm-item-title-row">
                          <span className="rbac-perm-item-name">{perm.name}</span>
                          <span className="rbac-perm-item-code">{perm.code}</span>
                          <span className="rbac-crud-badge" title={actionMeta.label}>
                            {actionMeta.icon} {perm.actionType}
                          </span>
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
                        </div>
                        <div className="rbac-perm-item-desc">{perm.description}</div>
                      </div>

                      <div className="rbac-perm-item-right">
                        <label
                          className={`rbac-switch ${isProtectedAdmin ? 'disabled' : ''}`}
                          title={
                            isProtectedAdmin
                              ? 'Quyền quản trị tối cao được bảo vệ cốt lõi (BR-69)'
                              : `${isGranted ? 'Thu hồi' : 'Cấp'} quyền`
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
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
};

export default RolePermissionAccordion;
