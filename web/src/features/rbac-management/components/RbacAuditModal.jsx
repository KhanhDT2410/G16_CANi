import React from 'react';

/**
 * Modal Nhật Ký Kiểm Toán Phân Quyền (RBAC Audit Logs Modal - UC-113 & BR-70)
 * Hiển thị toàn bộ lịch sử can thiệp, cấp/thu hồi quyền của Quản trị viên.
 * Lưu vết bất biến trong 60 tháng: actor_id, action_type, old_state, new_state, timestamp, ip_address.
 */
export const RbacAuditModal = ({ isOpen, onClose, auditLogs }) => {
  if (!isOpen) return null;

  const getActionBadge = (action) => {
    switch (action) {
      case 'PERMISSION_GRANTED':
        return <span className="rbac-audit-badge grant">Cấp Quyền</span>;
      case 'PERMISSION_REVOKED':
        return <span className="rbac-audit-badge revoke">Thu Hồi Quyền</span>;
      case 'ROLE_CREATED':
        return <span className="rbac-audit-badge create">Tạo Vai Trò</span>;
      default:
        return <span className="rbac-audit-badge mod">Thay Đổi</span>;
    }
  };

  return (
    <div className="rbac-modal-backdrop" onClick={onClose}>
      <div
        className="rbac-modal-card rbac-audit-modal-card animate-scale-in"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="rbac-modal-header">
          <div>
            <h3 className="rbac-modal-title">Nhật Ký Kiểm Toán Phân Quyền</h3>
            <p className="rbac-modal-sub">
              Kho lưu trữ bất biến theo quy định(Bảo lưu 60 tháng,không thể xóa/sửa)
            </p>
          </div>
          <button type="button" className="rbac-modal-close" onClick={onClose}>
            ✕
          </button>
        </div>

        <div className="rbac-audit-list">
          {auditLogs.length === 0 ? (
            <div className="rbac-audit-empty">
              <span>Chưa có bản ghi kiểm toán phân quyền nào.</span>
            </div>
          ) : (
            auditLogs.map((log) => (
              <div key={log.id} className="rbac-audit-item">
                <div className="rbac-audit-item-header">
                  <div className="rbac-audit-item-action">
                    {getActionBadge(log.actionType)}
                    <span className="rbac-audit-role-tag">{log.roleName} ({log.roleCode})</span>
                  </div>
                  <div className="rbac-audit-meta-right">
                    <span className="rbac-audit-ip-tag">IP: {log.ipAddress || '192.168.1.15'}</span>
                    <span className="rbac-audit-time-tag">{log.timestamp}</span>
                  </div>
                </div>

                <div className="rbac-audit-perm-line">
                  <strong>Thao tác:</strong> {log.permissionName}{' '}
                  {log.permissionCode !== 'ALL_INITIAL' && (
                    <code>({log.permissionCode})</code>
                  )}
                </div>

                {(log.oldState || log.newState) && (
                  <div className="rbac-audit-state-diff">
                    <span className="diff-old">Cũ: {log.oldState}</span>
                    <span className="diff-arrow">→</span>
                    <span className="diff-new">Mới: {log.newState}</span>
                  </div>
                )}

                {log.reason && (
                  <div className="rbac-audit-reason-line">
                    <em>Căn cứ:</em> {log.reason}
                  </div>
                )}

                <div className="rbac-audit-author-line">
                  <span>Thực hiện bởi: <strong>{log.changedBy}</strong> <code>(ID: {log.actorId || 'adm_duongmh'})</code></span>
                </div>
              </div>
            ))
          )}
        </div>

        <div className="rbac-modal-footer">
          <div className="rbac-audit-footer-hint">
            <em>Mọi phiên truy cập và can thiệp đều được niêm phong chống sửa đổi.</em>
          </div>
          <button type="button" className="rbac-btn-secondary" onClick={onClose}>
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
};

export default RbacAuditModal;
