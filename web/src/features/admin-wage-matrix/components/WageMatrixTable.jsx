import React from 'react';
import {
  REGION_CONFIG,
  JOB_TIER_CONFIG,
} from '../types/wageMatrix.constants';

export const WageMatrixTable = ({
  items = [],
  loading,
  page,
  totalPages,
  totalElements,
  onPageChange,
  onEdit,
  onDelete,
  onToggleActive,
  togglingId,
}) => {
  const formatWage = (val) => {
    if (val === null || val === undefined) return '—';
    return `${Number(val).toLocaleString('vi-VN')}đ`;
  };

  return (
    <div className="wage-matrix-table-wrapper">
      {loading && items.length === 0 ? (
        <div className="wage-matrix-empty">Đang tải dữ liệu ma trận Region × JobTier...</div>
      ) : items.length === 0 ? (
        <div className="wage-matrix-empty">
          Không tìm thấy cấu hình thù lao nào phù hợp với bộ lọc.
        </div>
      ) : (
        <table className="wage-matrix-table">
          <thead>
            <tr>
              <th style={{ width: '130px' }}>Vùng</th>
              <th style={{ minWidth: '220px' }}>Nhóm Công Việc (Job Tier)</th>
              <th style={{ width: '190px' }}>Hệ Số Bảng A (Min · ĐX · Max)</th>
              <th style={{ width: '210px' }}>Khung Lương Giờ Tham Chiếu</th>
              <th style={{ width: '140px' }}>Giá Ghim Cố Định</th>
              <th style={{ width: '125px', textAlign: 'center' }}>Trạng Thái</th>
              <th style={{ width: '135px', textAlign: 'center' }}>Thao Tác</th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => {
              const regionCfg = REGION_CONFIG[item.regionCode] || {
                label: item.regionName || item.regionCode,
                color: '#475569',
                bg: '#f1f5f9',
                border: '#cbd5e1',
              };
              const tierCfg = JOB_TIER_CONFIG[item.jobTier] || {
                label: item.tierLabel || item.jobTier,
                color: '#475569',
                bg: '#f1f5f9',
                border: '#cbd5e1',
              };
              const isToggling = togglingId === item.matrixId;

              return (
                <tr key={item.matrixId}>
                  <td>
                    <span
                      className="wage-slot-badge"
                      style={{
                        color: regionCfg.color,
                        backgroundColor: regionCfg.bg,
                        borderColor: regionCfg.border,
                      }}
                    >
                      {item.regionName || regionCfg.label}
                    </span>
                    <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '0.2rem' }}>
                      Sàn: {formatWage(item.legalFloorHourly)}
                    </div>
                  </td>
                  <td>
                    <span
                      className="wage-lead-badge"
                      style={{
                        color: tierCfg.color,
                        backgroundColor: tierCfg.bg,
                        borderColor: tierCfg.border,
                        marginBottom: '0.25rem',
                      }}
                    >
                      {tierCfg.label}
                    </span>
                    <div style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.85rem' }}>
                      {item.tierLabel}
                    </div>
                    {item.description && (
                      <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '0.15rem' }}>
                        {item.description}
                      </div>
                    )}
                  </td>
                  <td>
                    <div style={{ fontWeight: 600, color: '#1e293b', fontSize: '0.85rem' }}>
                      {Number(item.minMultiplier).toFixed(2)}x ·{' '}
                      <span style={{ color: '#2563eb' }}>{Number(item.recommendedMultiplier).toFixed(2)}x</span> ·{' '}
                      {Number(item.maxMultiplier).toFixed(2)}x
                    </div>
                  </td>
                  <td>
                    <div style={{ fontSize: '0.83rem', color: '#334155' }}>
                      {formatWage(item.computedMinWage)} – {formatWage(item.computedMaxWage)}/h
                    </div>
                    <div style={{ fontSize: '0.78rem', color: '#2563eb', fontWeight: 700, marginTop: '0.15rem' }}>
                      Đề xuất: {formatWage(item.computedSuggestedWage)}/h
                    </div>
                  </td>
                  <td>
                    {item.overrideBaseWage ? (
                      <strong className="wage-rate-text" style={{ color: '#b45309' }}>
                        {formatWage(item.overrideBaseWage)}/h
                      </strong>
                    ) : (
                      <span style={{ fontSize: '0.8rem', color: '#94a3b8' }}>Theo hệ số</span>
                    )}
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    <button
                      type="button"
                      disabled={isToggling}
                      onClick={() => onToggleActive(item)}
                      className={`wage-status-pill ${item.isActive ? 'active' : 'inactive'}`}
                      title="Bấm để bật/tắt kích hoạt"
                    >
                      <span className="status-dot" />
                      {item.isActive ? 'Áp dụng' : 'Tạm ngưng'}
                    </button>
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    <div className="wage-table-actions">
                      <button
                        type="button"
                        onClick={() => onEdit(item)}
                        className="btn-action-edit"
                        title="Chỉnh sửa định mức"
                      >
                        Sửa
                      </button>
                      <button
                        type="button"
                        onClick={() => onDelete(item.matrixId)}
                        className="btn-action-delete"
                        title="Xóa cấu hình"
                      >
                        Xóa
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      {totalPages > 1 && (
        <div className="wage-matrix-pagination">
          <span className="pagination-info">
            Trang <strong>{page + 1}</strong> / {totalPages} (Tổng cộng{' '}
            <strong>{totalElements}</strong> cấu hình)
          </span>
          <div className="pagination-controls">
            <button
              type="button"
              disabled={page <= 0 || loading}
              onClick={() => onPageChange(page - 1)}
              className="btn-page-nav"
            >
              Trước
            </button>
            <button
              type="button"
              disabled={page >= totalPages - 1 || loading}
              onClick={() => onPageChange(page + 1)}
              className="btn-page-nav"
            >
              Sau
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
