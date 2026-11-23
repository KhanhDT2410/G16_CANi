import React from 'react';

/**
 * Thanh tìm kiếm & bộ lọc trạng thái ca làm việc cho Admin.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const ShiftFilterBar = ({
  searchTerm,
  onSearchChange,
  statusFilter,
  onStatusChange,
  onRefresh,
  loading,
}) => {
  const FILTER_TABS = [
    { id: 'ALL', label: 'Tất Cả' },
    { id: 'IN_PROGRESS', label: 'Đang Diễn Ra' },
    { id: 'ACTIVE', label: 'Đang Tuyển' },
    { id: 'FULLY_STAFFED', label: 'Đã Đủ Người' },
    { id: 'COMPLETED', label: 'Hoàn Thành' },
    { id: 'SUSPENDED', label: 'Tạm Đình Chỉ' },
    { id: 'CANCELLED', label: 'Đã Hủy' },
  ];

  return (
    <div className="tickets-toolbar" style={{ flexWrap: 'wrap', gap: '0.75rem', marginBottom: '1rem' }}>
      <div style={{ display: 'flex', gap: '0.45rem', flexWrap: 'wrap' }}>
        {FILTER_TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            className={`ticket-filter-btn ${statusFilter === tab.id ? 'active' : ''}`}
            onClick={() => onStatusChange(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginLeft: 'auto', flexWrap: 'wrap' }}>
        <div className="tickets-search-box">
          <input
            type="text"
            className="tickets-search-input"
            placeholder="Tìm theo mã ca, tên sự kiện, EO..."
            value={searchTerm}
            onChange={(e) => onSearchChange(e.target.value)}
          />
          {searchTerm && (
            <button
              type="button"
              className="tickets-search-clear"
              onClick={() => onSearchChange('')}
            >
              ✕
            </button>
          )}
        </div>

        <button
          type="button"
          className="ticket-btn ticket-btn-secondary"
          onClick={onRefresh}
          disabled={loading}
          style={{ height: '38px', whiteSpace: 'nowrap' }}
        >
          {loading ? 'Đang tải...' : 'Làm Mới'}
        </button>
      </div>
    </div>
  );
};

export default ShiftFilterBar;
