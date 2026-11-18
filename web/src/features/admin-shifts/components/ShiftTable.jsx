import React from 'react';
import { SHIFT_STATUS_CONFIG } from '../types/adminShift.constants';

/**
 * Bảng hiển thị danh sách ca làm việc cho Admin (Read-Only).
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const ShiftTable = ({
  shifts = [],
  loading,
  page,
  totalPages,
  totalElements,
  onPageChange,
  onViewDetail,
}) => {
  const formatDateTime = (isoStr) => {
    if (!isoStr) return '—';
    try {
      return new Date(isoStr).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
    } catch {
      return '—';
    }
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '—';
    try {
      return new Date(dateStr).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
    } catch {
      return '—';
    }
  };

  return (
    <div className="tickets-table-wrapper" style={{ marginTop: 0 }}>
      {loading && shifts.length === 0 ? (
        <div className="ticket-empty-state">Đang tải danh sách ca làm việc...</div>
      ) : shifts.length === 0 ? (
        <div className="ticket-empty-state">Không tìm thấy ca làm việc nào.</div>
      ) : (
        <table className="tickets-table">
          <thead>
            <tr>
              <th style={{ width: '110px' }}>Mã Ca</th>
              <th style={{ minWidth: '220px' }}>Sự Kiện & Ca Làm</th>
              <th style={{ minWidth: '180px' }}>Nhà Tổ Chức (EO)</th>
              <th style={{ width: '160px' }}>Khung Giờ & Ngày</th>
              <th style={{ minWidth: '180px' }}>Địa Điểm</th>
              <th style={{ width: '120px' }}>Nhân Sự</th>
              <th style={{ width: '130px' }}>Trạng Thái</th>
              <th style={{ textAlign: 'left', width: '120px' }}>Thao Tác</th>
            </tr>
          </thead>
          <tbody>
            {shifts.map((shift) => {
              const statusCfg = SHIFT_STATUS_CONFIG[shift.status] || {
                label: shift.status || 'Chưa rõ',
                bg: '#f1f5f9',
                color: '#475569',
              };

              return (
                <tr key={shift.shiftId}>
                  <td>
                    <span
                      style={{
                        fontWeight: 700,
                        color: '#2563eb',
                        background: '#eff6ff',
                        padding: '3px 8px',
                        borderRadius: 6,
                        fontSize: '0.82rem',
                        display: 'inline-block',
                      }}
                    >
                      {shift.shiftCode || 'SH-0000'}
                    </span>
                  </td>
                  <td>
                    <div style={{ fontWeight: 600, color: '#1e293b', fontSize: '0.9rem' }}>
                      {shift.eventName || 'Sự kiện'}
                    </div>
                    <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '2px' }}>
                      Vị trí: <strong>{shift.jobType}</strong>
                    </div>
                  </td>
                  <td>
                    <div style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.88rem' }}>
                      {shift.eoName || 'Nhà tổ chức'}
                    </div>
                    <div style={{ fontSize: '0.78rem', color: '#64748b' }}>
                      SĐT: {shift.eoPhone || '—'}
                    </div>
                    {shift.companyName && (
                      <div style={{ fontSize: '0.72rem', color: '#94a3b8' }}>
                        {shift.companyName}
                      </div>
                    )}
                  </td>
                  <td>
                    <div style={{ fontWeight: 600, color: '#1e293b', fontSize: '0.85rem' }}>
                      {formatDateTime(shift.startTime)} - {formatDateTime(shift.endTime)}
                    </div>
                    <div style={{ fontSize: '0.78rem', color: '#64748b' }}>
                      {formatDate(shift.eventDate || shift.startTime)}
                    </div>
                  </td>
                  <td>
                    <div
                      style={{
                        fontSize: '0.82rem',
                        color: '#334155',
                        maxWidth: '220px',
                        whiteSpace: 'nowrap',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                      }}
                      title={shift.venueAddress}
                    >
                      {shift.venueAddress || '—'}
                    </div>
                  </td>
                  <td>
                    <div style={{ fontWeight: 700, fontSize: '0.88rem', color: '#0f172a' }}>
                      {shift.confirmedWorkers || 0} / {shift.requiredWorkers || 0}
                    </div>
                    <div style={{ fontSize: '0.75rem', color: '#059669', fontWeight: 600 }}>
                      {Number(shift.hourlyWage || 0).toLocaleString()} đ/h
                    </div>
                  </td>
                  <td>
                    <span
                      style={{
                        padding: '3px 8px',
                        borderRadius: 12,
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        background: statusCfg.bg,
                        color: statusCfg.color,
                        display: 'inline-block',
                      }}
                    >
                      {statusCfg.label}
                    </span>
                  </td>
                  <td>
                    <button
                      type="button"
                      className="ticket-btn ticket-btn-secondary ticket-btn-sm"
                      onClick={() => onViewDetail(shift.shiftId)}
                      style={{ padding: '4px 10px', fontSize: '0.8rem' }}
                    >
                      Chi Tiết
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      {/* Phân trang */}
      {totalPages > 1 && (
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
      )}
    </div>
  );
};

export default ShiftTable;
