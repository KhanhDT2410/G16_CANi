import React, { useState } from 'react';
import { useWageMatrix } from '../hooks/useWageMatrix';
import { WageMatrixFilter } from './WageMatrixFilter';
import { WageMatrixTable } from './WageMatrixTable';
import { WageMatrixModal } from './WageMatrixModal';
import '../WageMatrix.css';

export default function WageMatrixView() {
  const {
    regions,
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
    handleUpdateRegion,
    handleToggleActive,
    handleDelete,
  } = useWageMatrix();

  const [editingRegion, setEditingRegion] = useState(null);
  const [regionFloorInput, setRegionFloorInput] = useState('');

  const activeCount = items.filter((i) => i.isActive).length;
  const inactiveCount = items.length - activeCount;

  const startEditRegion = (region) => {
    setEditingRegion(region);
    setRegionFloorInput(String(Number(region.legalFloorHourly || 0)));
  };

  const saveEditRegion = async () => {
    if (!editingRegion) return;
    const numFloor = Number(regionFloorInput);
    if (!Number.isFinite(numFloor) || numFloor < 10000) return;
    const ok = await handleUpdateRegion(editingRegion.code, {
      name: editingRegion.name,
      legalFloorHourly: numFloor,
      marketMultiplier: Number(editingRegion.marketMultiplier || 1),
      description: editingRegion.description || '',
      isActive: editingRegion.isActive !== false,
    });
    if (ok) {
      setEditingRegion(null);
    }
  };

  return (
    <div className="wage-matrix-container">
      {/* Bảng 4 Vùng pháp lý theo NĐ 293/2025/NĐ-CP */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))',
          gap: '0.75rem',
        }}
      >
        {regions.map((reg) => {
          const isEditing = editingRegion?.code === reg.code;
          return (
            <div
              key={reg.code}
              style={{
                background: '#ffffff',
                border: '1px solid #e2e8f0',
                borderRadius: '10px',
                padding: '0.85rem 1rem',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.35rem',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <strong style={{ color: '#0f172a', fontSize: '0.92rem' }}>{reg.name}</strong>
                {!isEditing && (
                  <button
                    type="button"
                    onClick={() => startEditRegion(reg)}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: '#2563eb',
                      fontSize: '0.78rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                    }}
                  >
                    Sửa sàn
                  </button>
                )}
              </div>

              {isEditing ? (
                <div style={{ display: 'flex', gap: '0.4rem', alignItems: 'center', marginTop: '0.2rem' }}>
                  <input
                    type="number"
                    min="10000"
                    step="100"
                    value={regionFloorInput}
                    onChange={(e) => setRegionFloorInput(e.target.value)}
                    className="filter-input"
                    style={{ width: '110px', padding: '0.3rem 0.5rem', fontSize: '0.82rem' }}
                  />
                  <button
                    type="button"
                    onClick={saveEditRegion}
                    disabled={submitting}
                    className="btn-filter-submit"
                    style={{ padding: '4px 10px', fontSize: '0.78rem' }}
                  >
                    Lưu
                  </button>
                  <button
                    type="button"
                    onClick={() => setEditingRegion(null)}
                    className="btn-filter-reset"
                    style={{ padding: '4px 8px', fontSize: '0.78rem' }}
                  >
                    Hủy
                  </button>
                </div>
              ) : (
                <div style={{ fontSize: '1.05rem', fontWeight: 700, color: '#1d4ed8' }}>
                  Sàn: {Number(reg.legalFloorHourly || 0).toLocaleString('vi-VN')} đ/giờ
                </div>
              )}

              {reg.description && (
                <div style={{ fontSize: '0.74rem', color: '#64748b', lineHeight: 1.35 }}>
                  {reg.description}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Thanh thống kê nhanh */}
      <div className="wage-matrix-stats-toolbar">
        <div className="wage-stats-group">
          <div className="wage-stat-pill">
            <span>Tổng cấu hình Region × JobTier:</span>
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

      {/* Bộ lọc */}
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
        regions={regions}
        onClose={handleCloseModal}
        onSubmit={handleSubmitModal}
        submitting={submitting}
      />
    </div>
  );
}
