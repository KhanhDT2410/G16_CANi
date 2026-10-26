import { USER_ROLES, USER_ROLE_LABELS, USER_STATUSES, USER_STATUS_LABELS } from '../types/userManagement.constants';

const UserFilterBar = ({ filter, onFilterChange, onReset }) => {
  return (
    <div className="user-filter-bar">
      <input
        type="text"
        className="user-filter-input"
        placeholder="Tìm kiếm theo họ tên, email, SĐT..."
        value={filter.search || ''}
        onChange={(e) => onFilterChange('search', e.target.value)}
      />

      <select
        className="user-filter-select"
        value={filter.role || ''}
        onChange={(e) => onFilterChange('role', e.target.value)}
      >
        <option value="">Tất cả vai trò</option>
        {Object.keys(USER_ROLES).map((key) => (
          <option key={key} value={key}>
            {USER_ROLE_LABELS[key]?.label || key}
          </option>
        ))}
      </select>

      <select
        className="user-filter-select"
        value={filter.status || ''}
        onChange={(e) => onFilterChange('status', e.target.value)}
      >
        <option value="">Tất cả trạng thái</option>
        {Object.keys(USER_STATUSES).map((key) => (
          <option key={key} value={key}>
            {USER_STATUS_LABELS[key]?.label || key}
          </option>
        ))}
      </select>

      <button className="btn-filter-reset" onClick={onReset} type="button">
        Đặt lại
      </button>
    </div>
  );
};

export default UserFilterBar;
