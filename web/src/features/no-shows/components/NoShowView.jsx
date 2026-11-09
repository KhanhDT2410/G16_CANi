import React from 'react';
import { useNoShows } from '../hooks/useNoShows';
import { NoShowStatsCards } from './NoShowStatsCards';
import { NoShowTable } from './NoShowTable';
import { NoShowDetailModal } from './NoShowDetailModal';
import { ManualMarkNoShowModal } from './ManualMarkNoShowModal';
import './NoShow.css';

/**
 * Màn hình trung tâm quản lý Vi Phạm Vắng Mặt (No-show - UC-88) - Giao diện Clean
 */
export const NoShowView = ({ onNavigateAppeal }) => {
  const {
    items,
    stats,
    loading,
    scanning,
    submitting,
    keyword,
    setKeyword,
    selectedItem,
    detailModalOpen,
    manualModalOpen,
    setManualModalOpen,
    handleOpenDetail,
    handleCloseDetail,
    handleManualMark,
    handleTriggerScan,
    refresh,
  } = useNoShows();

  return (
    <div className="noshow-container">
      {/* Header Bar */}
      <div className="noshow-header-bar">
        <div>
          <h2 className="noshow-title">Quản Lý Vi Phạm No-Show</h2>
          <p className="noshow-sub">
            Theo dõi, chế tài vắng mặt ca trực và kích hoạt điều phối nhân sự thay thế (UC-88)
          </p>
        </div>

        <div className="noshow-actions">
          <button
            type="button"
            className="btn-noshow-manual"
            onClick={() => setManualModalOpen(true)}
          >
            Đánh Dấu Thủ Công
          </button>

          <button
            type="button"
            className="btn-noshow-scan"
            onClick={handleTriggerScan}
            disabled={scanning}
          >
            {scanning ? 'Đang quét hệ thống...' : 'Quét Vi Phạm Ngay'}
          </button>

          <button
            type="button"
            className="btn-noshow-refresh"
            onClick={refresh}
            title="Tải lại dữ liệu"
          >
            Làm Mới
          </button>
        </div>
      </div>

      {/* Stats Cards */}
      <NoShowStatsCards stats={stats} />

      {/* Filter Bar */}
      <div className="noshow-filter-bar">
        <input
          type="text"
          className="noshow-search-input"
          placeholder="Tìm theo tên nhân sự, email, sự kiện, vị trí công việc..."
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
      </div>

      {/* Table */}
      <NoShowTable
        items={items}
        loading={loading}
        onSelectDetail={handleOpenDetail}
      />

      {/* Detail Modal */}
      {detailModalOpen && (
        <NoShowDetailModal
          item={selectedItem}
          onClose={handleCloseDetail}
          onNavigateAppeal={onNavigateAppeal}
        />
      )}

      {/* Manual Mark Modal */}
      {manualModalOpen && (
        <ManualMarkNoShowModal
          isOpen={manualModalOpen}
          onClose={() => setManualModalOpen(false)}
          onSubmit={handleManualMark}
          submitting={submitting}
        />
      )}
    </div>
  );
};
