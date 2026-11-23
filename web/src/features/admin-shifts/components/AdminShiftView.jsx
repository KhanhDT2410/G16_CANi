import React, { useState } from 'react';
import { useAdminShifts } from '../hooks/useAdminShifts';
import ShiftFilterBar from './ShiftFilterBar';
import ShiftTable from './ShiftTable';
import ShiftDetailModal from './ShiftDetailModal';
import ShiftActionModal from './ShiftActionModal';

/**
 * Giao diện Quản trị & Tra cứu Ca làm việc (Admin Shift Management View).
 * Hỗ trợ tra cứu, xem chi tiết và can thiệp chế tài trực tiếp.
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
    actionLoading,
    setPage,
    handleSearchChange,
    handleStatusChange,
    fetchShifts,
    openDetailModal,
    closeDetailModal,
    handleCancelShift,
    handleSuspendShift,
    handleWarnEo,
  } = useAdminShifts();

  const [quickAction, setQuickAction] = useState(null);

  const handleQuickActionConfirm = async (reason) => {
    if (!quickAction?.shift) return;
    let ok = false;
    if (quickAction.type === 'SUSPEND') {
      ok = await handleSuspendShift(quickAction.shift.shiftId, reason);
    } else if (quickAction.type === 'CANCEL') {
      ok = await handleCancelShift(quickAction.shift.shiftId, reason);
    } else if (quickAction.type === 'WARN') {
      ok = await handleWarnEo(quickAction.shift.shiftId, reason);
    }
    if (ok) setQuickAction(null);
  };

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
        onQuickAction={(shift, type) => setQuickAction({ shift, type })}
      />

      {/* Modal xem chi tiết ca làm & nhân sự tham gia */}
      {isDetailOpen && (
        <ShiftDetailModal
          shift={selectedShift}
          loading={detailLoading}
          actionLoading={actionLoading}
          onClose={closeDetailModal}
          onCancelShift={handleCancelShift}
          onSuspendShift={handleSuspendShift}
          onWarnEo={handleWarnEo}
        />
      )}

      {/* Modal xác nhận can thiệp nhanh trực tiếp từ bảng */}
      <ShiftActionModal
        isOpen={Boolean(quickAction)}
        actionType={quickAction?.type}
        shiftCode={quickAction?.shift?.shiftCode}
        loading={actionLoading}
        onClose={() => setQuickAction(null)}
        onConfirm={handleQuickActionConfirm}
      />
    </div>
  );
};

export default AdminShiftView;
