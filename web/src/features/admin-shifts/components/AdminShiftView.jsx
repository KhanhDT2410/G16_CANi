import React from 'react';
import { useAdminShifts } from '../hooks/useAdminShifts';
import ShiftFilterBar from './ShiftFilterBar';
import ShiftTable from './ShiftTable';
import ShiftDetailModal from './ShiftDetailModal';

/**
 * Giao diện Quản trị & Tra cứu Ca làm việc (Admin Shift Management View - Read-only).
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const AdminShiftView = () => {
  const {
    shifts,
    loading,
    searchTerm,
    statusFilter,
    page,
    totalPages,
    totalElements,
    selectedShift,
    detailLoading,
    isDetailOpen,
    setPage,
    handleSearchChange,
    handleStatusChange,
    fetchShifts,
    openDetailModal,
    closeDetailModal,
  } = useAdminShifts();

  return (
    <div className="tickets-container">
      {/* Thanh lọc & tìm kiếm */}
      <ShiftFilterBar
        searchTerm={searchTerm}
        onSearchChange={handleSearchChange}
        statusFilter={statusFilter}
        onStatusChange={handleStatusChange}
        onRefresh={fetchShifts}
        loading={loading}
      />

      {/* Bảng danh sách ca làm việc */}
      <ShiftTable
        shifts={shifts}
        loading={loading}
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onPageChange={setPage}
        onViewDetail={openDetailModal}
      />

      {/* Modal xem chi tiết ca làm & nhân sự tham gia */}
      {isDetailOpen && (
        <ShiftDetailModal
          shift={selectedShift}
          loading={detailLoading}
          onClose={closeDetailModal}
        />
      )}
    </div>
  );
};

export default AdminShiftView;
