import React from 'react';
import { DirectChatInboxTable } from '../../../features/direct-chat';

/**
 * Tab Hộp Thư Trao Đổi Nhân Sự trong Portal Event Organizer.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const EOMessagesTab = ({
  directChatState,
  onOpenShiftDetail,
}) => {
  const {
    conversations,
    loading,
    filterType,
    setFilterType,
    searchTerm,
    setSearchTerm,
    counts,
    setActiveChat,
  } = directChatState;

  return (
    <div className="eo-messages-tab animate-fade-in-up">
      {/* 1. Header Banner đồng bộ chuẩn EO Design System */}
      <div className="eo-widget-card" style={{ padding: '1.25rem 1.5rem', width: '100%', boxSizing: 'border-box' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
              <h2 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 800, color: '#0f172a' }}>
                Tin Nhắn & Trao Đổi Nhân Sự
              </h2>
              <span
                style={{
                  background: '#eff6ff',
                  color: '#1d4ed8',
                  padding: '0.2rem 0.65rem',
                  borderRadius: '4px',
                  fontSize: '0.78rem',
                  fontWeight: 500,
                }}
              >
                {counts.all || 0} cuộc trò chuyện
              </span>
              {counts.unread > 0 && (
                <span
                  style={{
                    background: '#fef2f2',
                    color: '#ef4444',
                    border: '1px solid #fecaca',
                    padding: '0.2rem 0.65rem',
                    borderRadius: '999px',
                    fontSize: '0.75rem',
                    fontWeight: 700,
                  }}
                >
                  +{counts.unread} chưa đọc
                </span>
              )}
            </div>
            <p style={{ margin: '0.35rem 0 0', fontSize: '0.85rem', color: '#64748b' }}>
              Trao đổi trực tiếp 1-1 với nhân sự hoặc liên lạc nhanh theo ca làm việc sự kiện.
            </p>
          </div>
        </div>
      </div>

      {/* 2. Bảng Hộp Thư Chiếm Trọn Chiều Rộng */}
      <DirectChatInboxTable
        conversations={conversations}
        loading={loading}
        filterType={filterType}
        onFilterChange={setFilterType}
        searchTerm={searchTerm}
        onSearchChange={setSearchTerm}
        counts={counts}
        onOpenChat={(conv) => setActiveChat(conv)}
        onOpenShiftDetail={onOpenShiftDetail}
      />
    </div>
  );
};

export default EOMessagesTab;
