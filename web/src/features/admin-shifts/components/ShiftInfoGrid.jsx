import React from 'react';
import { formatDateTime } from '../utils/shiftModalHelpers';

/**
 * Lưới thông tin tổng quan của Ca làm việc & Nhà tổ chức
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const ShiftInfoGrid = ({ shift }) => {
  if (!shift) return null;

  return (
    <div className="shift-info-grid">
      <div>
        <div className="grid-label">Sự Kiện & Vị Trí</div>
        <div className="grid-main-val">{shift.eventName}</div>
        <div className="grid-sub-val" style={{ color: '#2563eb' }}>Vị trí: {shift.jobType}</div>
      </div>

      <div>
        <div className="grid-label">Nhà Tổ Chức (EO)</div>
        <div className="grid-main-val">{shift.eoName}</div>
        <div className="grid-sub-val">SĐT: {shift.eoPhone} • {shift.eoEmail}</div>
        {shift.companyName && <div className="grid-company-val">{shift.companyName}</div>}
      </div>

      <div>
        <div className="grid-label">Thời Gian & Địa Điểm</div>
        <div className="grid-main-val" style={{ fontSize: '0.85rem' }}>
          {formatDateTime(shift.startTime)} — {formatDateTime(shift.endTime)}
        </div>
        <div className="grid-sub-val">{shift.venueAddress || '—'}</div>
      </div>

      <div>
        <div className="grid-label">Nhân Sự & Thù Lao</div>
        <div className="grid-main-val" style={{ color: '#059669' }}>
          {Number(shift.hourlyWage || 0).toLocaleString()} đ / giờ
        </div>
        <div className="grid-sub-val">
          Tuyển: <strong>{shift.confirmedWorkers || 0}</strong> / {shift.requiredWorkers || 0} người
        </div>
        {shift.dressCode && <div className="grid-dress-val">Đồng phục: {shift.dressCode}</div>}
      </div>
    </div>
  );
};

export default ShiftInfoGrid;
