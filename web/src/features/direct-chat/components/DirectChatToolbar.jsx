import React from 'react';
import { CHAT_FILTER_TABS } from '../types/directChat.constants';

/**
 * Thanh công cụ tìm kiếm và các tab bộ lọc trong hộp thư trực tiếp.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const DirectChatToolbar = ({
  filterType = 'ALL',
  onFilterChange,
  searchTerm = '',
  onSearchChange,
  counts = {},
}) => {
  return (
    <div className="direct-chat-toolbar">
      <div style={{ display: 'flex', gap: '0.45rem', flexWrap: 'wrap' }}>
        {CHAT_FILTER_TABS.map((tab) => {
          const count = counts[tab.id.toLowerCase()] ?? 0;
          return (
            <button
              key={tab.id}
              type="button"
              className={`direct-chat-filter-btn ${filterType === tab.id ? 'active' : ''}`}
              onClick={() => onFilterChange(tab.id)}
            >
              <span>{tab.label}</span>
              <span className="direct-filter-count">{count}</span>
            </button>
          );
        })}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
        <input
          type="text"
          className="direct-chat-search-input"
          placeholder="Tìm theo tên, SĐT, mã ca..."
          value={searchTerm}
          onChange={(e) => onSearchChange(e.target.value)}
        />
        {searchTerm && (
          <button
            type="button"
            onClick={() => onSearchChange('')}
            style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#64748b', fontWeight: 'bold' }}
            title="Xóa tìm kiếm"
          >
            ✕
          </button>
        )}
      </div>
    </div>
  );
};

export default DirectChatToolbar;
