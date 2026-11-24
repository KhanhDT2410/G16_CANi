import React from 'react';
import { resolveShiftStatus } from '../types/adminShift.constants';
import { formatTimeOnly, formatDateOnly } from '../utils/shiftModalHelpers';
import ShiftPagination from './ShiftPagination';
import './ShiftDetailModal.css';

/**
 * Bảng hiển thị danh sách ca làm việc cho Admin.
 * Hiển thị rõ ràng 3 pha trạng thái: Đang làm, Đã hoàn thành, Chưa làm.
 * Đảm bảo minWidth cố định tránh co cụm/mất cột khi cuộn.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const ShiftTable = ({
  shifts = [], loading, page, totalPages, totalElements, onPageChange, onViewDetail, onQuickAction,
}) => {
  return (
    <div className="tickets-table-wrapper" style={{ marginTop: 0, overflowX: 'auto', width: '100%' }}>
      {loading && shifts.length === 0 ? (
        <div className="ticket-empty-state">Đang tải danh sách ca làm việc...</div>
      ) : shifts.length === 0 ? (
        <div className="ticket-empty-state">Không tìm thấy ca làm việc nào.</div>
      ) : (
        <table className="tickets-table" style={{ minWidth: '1180px', width: '100%' }}>
          <thead>
            <tr>
              <th style={{ width: '95px', minWidth: '95px' }}>Mã Ca</th>
              <th style={{ minWidth: '180px' }}>Sự Kiện & Ca Làm</th>
              <th style={{ minWidth: '160px' }}>Nhà Tổ Chức</th>
              <th style={{ width: '140px', minWidth: '140px' }}>Khung Giờ & Ngày</th>
              <th style={{ minWidth: '150px' }}>Địa Điểm</th>
              <th style={{ width: '95px', minWidth: '95px' }}>Nhân Sự</th>
              <th style={{ width: '130px', minWidth: '130px', whiteSpace: 'nowrap' }}>Trạng Thái</th>
              <th style={{
                width: '155px', minWidth: '155px', textAlign: 'center', position: 'sticky',
                right: 0, background: '#f8fafc', zIndex: 2, boxShadow: '-3px 0 6px rgba(0, 0, 0, 0.05)',
              }}>
                Thao Tác
              </th>
            </tr>
          </thead>
          <tbody>
            {shifts.map((shift) => {
              const statusCfg = resolveShiftStatus(shift);
              const isFinished = statusCfg.label === 'Hoàn Thành' || shift.status === 'COMPLETED';
              const canSuspend = !isFinished && shift.status !== 'CANCELLED' && shift.status !== 'SUSPENDED';

              return (
                <tr
                  key={shift.shiftId}
                  onClick={() => onViewDetail(shift.shiftId)}
                  style={{ cursor: 'pointer' }}
                  title="Nhấp để xem chi tiết ca làm việc"
                >
                  <td style={{ width: '95px', minWidth: '95px' }}>
                    <span className="shift-code-badge">
                      {shift.shiftCode || 'SH-0000'}
                    </span>
                  </td>
                  <td style={{ minWidth: '180px' }}>
                    <div style={{ fontWeight: 600, color: '#1e293b', fontSize: '0.9rem' }}>
                      {shift.eventName || 'Sự kiện'}
                    </div>
                    <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '2px' }}>
                      Vị trí: <strong>{shift.jobType}</strong>
                    </div>
                  </td>
                  <td style={{ minWidth: '160px' }}>
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
                  <td style={{ width: '140px', minWidth: '140px' }}>
                    <div style={{ fontWeight: 600, color: '#1e293b', fontSize: '0.85rem' }}>
                      {formatTimeOnly(shift.startTime)} - {formatTimeOnly(shift.endTime)}
                    </div>
                    <div style={{ fontSize: '0.78rem', color: '#64748b' }}>
                      {formatDateOnly(shift.eventDate || shift.startTime)}
                    </div>
                  </td>
                  <td style={{ minWidth: '150px' }}>
                    <div
                      style={{
                        fontSize: '0.82rem', color: '#334155', maxWidth: '200px',
                        whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                      }}
                      title={shift.venueAddress}
                    >
                      {shift.venueAddress || '—'}
                    </div>
                  </td>
                  <td style={{ width: '95px', minWidth: '95px' }}>
                    <div style={{ fontWeight: 700, fontSize: '0.88rem', color: '#0f172a' }}>
                      {shift.confirmedWorkers || 0} / {shift.requiredWorkers || 0}
                    </div>
                    <div style={{ fontSize: '0.75rem', color: '#059669', fontWeight: 600 }}>
                      {Number(shift.hourlyWage || 0).toLocaleString()} đ/h
                    </div>
                  </td>
                  <td style={{ width: '130px', minWidth: '130px', whiteSpace: 'nowrap' }}>
                    <span
                      className="shift-status-badge"
                      style={{
                        background: statusCfg.bg,
                        color: statusCfg.color,
                        borderColor: statusCfg.border || '#cbd5e1',
                      }}
                    >
                      {statusCfg.label}
                    </span>
                  </td>
                  <td
                    style={{
                      width: '155px', minWidth: '155px', position: 'sticky', right: 0,
                      background: '#ffffff', zIndex: 1, textAlign: 'center',
                      boxShadow: '-3px 0 6px rgba(0, 0, 0, 0.05)', whiteSpace: 'nowrap',
                    }}
                    onClick={(e) => e.stopPropagation()}
                  >
                    <div style={{ display: 'flex', gap: '0.35rem', justifyContent: 'center' }}>
                      <button
                        type="button"
                        className="ticket-btn ticket-btn-secondary ticket-btn-sm"
                        onClick={() => onViewDetail(shift.shiftId)}
                        style={{ padding: '3px 8px', fontSize: '0.78rem' }}
                      >
                        Chi Tiết
                      </button>
                      {shift.status === 'SUSPENDED' ? (
                        <button
                          type="button"
                          className="ticket-btn ticket-btn-primary ticket-btn-sm"
                          onClick={() => onQuickAction && onQuickAction(shift, 'RESUME')}
                          style={{ padding: '3px 8px', fontSize: '0.78rem' }}
                          title="Gỡ bỏ tạm đình chỉ, mở lại ca làm việc"
                        >
                          Mở Lại
                        </button>
                      ) : (
                        <button
                          type="button"
                          className="ticket-btn ticket-btn-warning ticket-btn-sm"
                          onClick={() => canSuspend && onQuickAction && onQuickAction(shift, 'SUSPEND')}
                          disabled={!canSuspend}
                          style={{
                            padding: '3px 8px', fontSize: '0.78rem',
                            opacity: canSuspend ? 1 : 0.45,
                            cursor: canSuspend ? 'pointer' : 'not-allowed',
                          }}
                          title={
                            isFinished
                              ? 'Ca làm việc đã kết thúc hoàn thành, không thể đình chỉ'
                              : shift.status === 'CANCELLED'
                                ? 'Ca làm việc đã bị hủy trước đó, không thể đình chỉ'
                                : 'Tạm đình chỉ ca làm việc này'
                          }
                        >
                          Đình Chỉ
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      <ShiftPagination
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onPageChange={onPageChange}
      />
    </div>
  );
};

export default ShiftTable;
