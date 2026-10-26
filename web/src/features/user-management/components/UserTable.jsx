import UserTableRow from './UserTableRow';

/**
 * UserTable.jsx
 * Khung hiển thị bảng danh sách tài khoản W-39 với thiết kế Clean React JSX & Design Tokens.
 */

export const UserTable = ({
  users,
  loading,
  onViewProfile,
  onEditRole,
  onResetPassword,
  onOpenStatusModal,
}) => {
  if (loading) {
    return (
      <div className="um-table-card">
        <div className="um-table-skeleton">
          {[1, 2, 3, 4, 5].map((i) => (
            <div
              key={i}
              className="skeleton"
              style={{ height: 68, borderRadius: 8, marginBottom: 8 }}
            />
          ))}
        </div>
      </div>
    );
  }

  if (!users || users.length === 0) {
    return (
      <div className="um-table-card">
        <div className="um-table-empty">
          <div className="um-empty-icon">🔍</div>
          <h3>Không tìm thấy tài khoản người dùng phù hợp</h3>
          <p>
            Vui lòng thử thay đổi từ khóa tìm kiếm hoặc điều chỉnh bộ lọc vai trò,
            trạng thái.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="um-table-card animate-fade-in-up">
      <div className="um-table-responsive">
        <table className="um-table">
          <thead>
            <tr>
              <th style={{ minWidth: 260 }}>Người Dùng &amp; Mã</th>
              <th style={{ minWidth: 190 }}>Liên Hệ</th>
              <th style={{ minWidth: 140 }}>Vai Trò</th>
              <th style={{ minWidth: 170 }}>Uy Tín &amp; Ca Làm</th>
              <th style={{ minWidth: 140 }}>Trạng Thái</th>
              <th style={{ minWidth: 250, textAlign: 'right' }}>Thao Tác Quản Trị</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <UserTableRow
                key={user.id}
                user={user}
                onViewProfile={onViewProfile}
                onEditRole={onEditRole}
                onResetPassword={onResetPassword}
                onOpenStatusModal={onOpenStatusModal}
              />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default UserTable;
