import React from 'react';

/**
 * Thanh phân trang cho bảng danh sách ca làm việc
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const ShiftPagination = ({
  page,
  totalPages,
  totalElements,
  onPageChange,
}) => {
  if (totalPages <= 1) return null;

  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '1rem', borderTop: '1px solid #f1f5f9' }}>
      <span style={{ fontSize: '0.85rem', color: '#64748b' }}>
        Tổng cộng <strong>{totalElements}</strong> ca làm việc
      </span>
      <div style={{ display: 'flex', gap: '0.5rem' }}>
        <button
          type="button"
          className="ticket-btn ticket-btn-secondary ticket-btn-sm"
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
        >
          Trang Trước
        </button>
        <span style={{ display: 'flex', alignItems: 'center', fontSize: '0.85rem', color: '#1e293b', fontWeight: 600, padding: '0 0.5rem' }}>
          Trang {page + 1} / {totalPages}
        </span>
        <button
          type="button"
          className="ticket-btn ticket-btn-secondary ticket-btn-sm"
          disabled={page >= totalPages - 1}
          onClick={() => onPageChange(page + 1)}
        >
          Trang Sau
        </button>
      </div>
    </div>
  );
};

export default ShiftPagination;
