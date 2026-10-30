import { USER_ROLE_LABELS, USER_STATUS_LABELS } from '../types/userManagement.constants';

const UserTable = ({
  users,
  loading,
  page,
  totalPages,
  totalElements,
  onPageChange,
  onViewDetail,
  onOpenStatusModal,
}) => {
  if (loading) {
    return (
      <div className="user-table-wrapper" style={{ padding: '2rem', textAlign: 'center' }}>
        <div className="cani-spinner" style={{ margin: '0 auto 1rem' }} />
        <span style={{ color: '#6b7280' }}>Đang tải danh sách người dùng...</span>
      </div>
    );
  }

  if (!users || users.length === 0) {
    return (
      <div className="user-table-wrapper" style={{ padding: '3rem', textAlign: 'center', color: '#6b7280' }}>
        Không tìm thấy người dùng phù hợp với bộ lọc.
      </div>
    );
  }

  return (
    <div className="user-table-wrapper">
      <table className="user-table">
        <thead>
          <tr>
            <th>Người Dùng</th>
            <th>Email</th>
            <th>Số Điện Thoại</th>
            <th>Vai Trò</th>
            <th>Trạng Thái</th>
            <th>Ngày Tạo</th>
            <th style={{ textAlign: 'right' }}>Thao Tác</th>
          </tr>
        </thead>
        <tbody>
          {users.map((user) => {
            const roleInfo = USER_ROLE_LABELS[user.role] || { label: user.role, badgeClass: '' };
            const statusInfo = USER_STATUS_LABELS[user.status] || { label: user.status, badgeClass: '' };
            const isLocked = user.status === 'LOCKED';

            return (
              <tr key={user.id}>
                <td>
                  <div className="user-info-cell">
                    {user.avatarUrl ? (
                      <img src={user.avatarUrl} alt={user.fullName} className="user-avatar" />
                    ) : (
                      <div className="user-avatar">
                        {(user.fullName || 'U').charAt(0).toUpperCase()}
                      </div>
                    )}
                    <span style={{ fontWeight: 600, color: '#111827' }}>{user.fullName}</span>
                  </div>
                </td>
                <td>{user.email}</td>
                <td>{user.phone || '—'}</td>
                <td>
                  <span className={roleInfo.badgeClass}>{roleInfo.label}</span>
                </td>
                <td>
                  <span className={statusInfo.badgeClass}>{statusInfo.label}</span>
                </td>
                <td style={{ color: '#6b7280' }}>
                  {user.createdAt ? new Date(user.createdAt).toLocaleDateString('vi-VN') : '—'}
                </td>
                <td>
                  <div className="user-actions" style={{ justifyContent: 'flex-end' }}>
                    <button
                      type="button"
                      className="btn-action-view"
                      onClick={() => onViewDetail(user.id)}
                    >
                      Chi tiết
                    </button>
                    {isLocked ? (
                      <button
                        type="button"
                        className="btn-action-unlock"
                        onClick={() => onOpenStatusModal(user)}
                      >
                        Mở khóa
                      </button>
                    ) : (
                      <button
                        type="button"
                        className="btn-action-lock"
                        onClick={() => onOpenStatusModal(user)}
                      >
                        Khóa
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>

      {/* Pagination */}
      <div className="user-pagination">
        <div>
          Hiển thị <strong>{users.length}</strong> / <strong>{totalElements}</strong> người dùng (Trang {page + 1}/{Math.max(totalPages, 1)})
        </div>
        <div className="user-pagination-btns">
          <button
            type="button"
            className="btn-page"
            disabled={page === 0}
            onClick={() => onPageChange(page - 1)}
          >
            &laquo; Trước
          </button>
          <button
            type="button"
            className="btn-page"
            disabled={page >= totalPages - 1}
            onClick={() => onPageChange(page + 1)}
          >
            Sau &raquo;
          </button>
        </div>
      </div>
    </div>
  );
};

export default UserTable;
