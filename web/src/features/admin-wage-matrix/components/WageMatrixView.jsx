import React from 'react';
import { useWageMatrix } from '../hooks/useWageMatrix';
import { WageMatrixFilter } from './WageMatrixFilter';
import { WageMatrixTable } from './WageMatrixTable';
import { WageMatrixModal } from './WageMatrixModal';
import '../WageMatrix.css';

export default function WageMatrixView() {
  const {
    items,
    loading,
    page,
    totalPages,
    totalElements,
    filters,
    modalState,
    submitting,
    togglingId,
    handleApplyFilters,
    handleResetFilters,
    handlePageChange,
    handleOpenCreateModal,
    handleOpenEditModal,
    handleCloseModal,
    handleSubmitModal,
    handleToggleActive,
    handleDelete,
  } = useWageMatrix();

  const activeCount = items.filter((i) => i.isActive).length;
  const inactiveCount = items.length - activeCount;

  return (
    <div className="wage-matrix-container">
      {/* Thanh thống kê nhanh (Stats Toolbar) */}
      <div className="wage-matrix-stats-toolbar">
        <div className="wage-stats-group">
          <div className="wage-stat-pill">
            <span>Tổng cấu hình chuẩn:</span>
            <strong>{totalElements}</strong>
          </div>
          <div className="wage-stat-pill success">
            <span>Đang áp dụng:</span>
            <strong>{activeCount}</strong>
          </div>
          {inactiveCount > 0 && (
            <div className="wage-stat-pill warning">
              <span>Tạm ngưng:</span>
              <strong>{inactiveCount}</strong>
            </div>
          )}
        </div>
      </div>

      {/* Bộ lọc và Tìm kiếm */}
      <WageMatrixFilter
        filters={filters}
        onApplyFilters={handleApplyFilters}
        onResetFilters={handleResetFilters}
        onOpenCreate={handleOpenCreateModal}
        loading={loading}
      />

      {/* Bảng dữ liệu */}
      <WageMatrixTable
        items={items}
        loading={loading}
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onPageChange={handlePageChange}
        onEdit={handleOpenEditModal}
        onDelete={handleDelete}
        onToggleActive={handleToggleActive}
        togglingId={togglingId}
      />

      {/* Modal Thêm mới / Chỉnh sửa */}
      <WageMatrixModal
        isOpen={modalState.isOpen}
        mode={modalState.mode}
        initialData={modalState.initialData}
        onClose={handleCloseModal}
        onSubmit={handleSubmitModal}
        submitting={submitting}
      />
    </div>
  );
}
