import React from 'react';
import {
  CITY_LABELS,
  TIME_SLOT_CONFIG,
  LEAD_TIME_CONFIG,
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
    if (val === null || val === undefined) return '0 đ/h';
    return `${Number(val).toLocaleString('vi-VN')} đ/h`;
  };

  return (
    <div className="wage-matrix-table-wrapper">
      {loading && items.length === 0 ? (
        <div className="wage-matrix-empty">Đang tải dữ liệu biểu thù lao...</div>
      ) : items.length === 0 ? (
        <div className="wage-matrix-empty">
          Không tìm thấy cấu hình thù lao nào phù hợp với bộ lọc.
        </div>
      ) : (
        <table className="wage-matrix-table">
          <thead>
            <tr>
              <th style={{ minWidth: '180px' }}>Vị Trí Công Việc</th>
              <th style={{ width: '150px' }}>Khu Vực</th>
              <th style={{ minWidth: '220px' }}>Khung Giờ Áp Dụng</th>
              <th style={{ width: '180px' }}>Độ Khẩn Cấp</th>
              <th style={{ width: '160px' }}>Mức Thù Lao Chuẩn</th>
              <th style={{ width: '130px', textAlign: 'center' }}>Trạng Thái</th>
              <th style={{ width: '140px', textAlign: 'center' }}>Thao Tác</th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => {
              const slotCfg = TIME_SLOT_CONFIG[item.timeSlot] || {
                label: item.timeSlot,
                color: '#475569',
                bg: '#f1f5f9',
                border: '#cbd5e1',
              };
              const leadCfg = LEAD_TIME_CONFIG[item.leadTime] || {
                label: item.leadTime,
                color: '#475569',
                bg: '#f1f5f9',
                border: '#cbd5e1',
              };

              const isToggling = togglingId === item.matrixId;

              return (
                <tr key={item.matrixId}>
                  <td>
                    <span className="wage-job-badge">{item.jobType}</span>
                  </td>
                  <td>
                    <span className="wage-city-text">{CITY_LABELS[item.city] || item.city}</span>
                  </td>
                  <td>
                    <span
                      className="wage-slot-badge"
                      style={{
                        color: slotCfg.color,
                        backgroundColor: slotCfg.bg,
                        borderColor: slotCfg.border,
                      }}
                    >
                      {slotCfg.label}
                    </span>
                  </td>
                  <td>
                    <span
                      className="wage-lead-badge"
                      style={{
                        color: leadCfg.color,
                        backgroundColor: leadCfg.bg,
                        borderColor: leadCfg.border,
                      }}
                    >
                      {leadCfg.label}
                    </span>
                  </td>
                  <td>
                    <strong className="wage-rate-text">{formatWage(item.wagePerHour)}</strong>
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

      {/* Phân trang */}
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
