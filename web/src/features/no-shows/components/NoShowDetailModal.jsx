import React from 'react';
import {
  NO_SHOW_DETECTED_BY_LABELS,
  APPEAL_STATUS_LABELS,
} from '../types/noShow.constants';

/**
 * Modal hiển thị chi tiết 1 ca No-show
 */
export const NoShowDetailModal = ({ item, onClose, onNavigateAppeal }) => {
  if (!item) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>Hồ Sơ Vi Phạm No-Show</h3>
          <button type="button" className="modal-close-btn" onClick={onClose}>&times;</button>
        </div>

        <div className="modal-body">
          <div className="detail-row">
            <span className="label">Nhân sự vi phạm</span>
            <span className="value">{item.workerName} ({item.workerEmail})</span>
          </div>

          <div className="detail-row">
            <span className="label">Số điện thoại</span>
            <span className="value">{item.workerPhone || 'Chưa cập nhật'}</span>
          </div>

          <div className="detail-row">
            <span className="label">Sự kiện</span>
            <span className="value">{item.eventName}</span>
          </div>

          <div className="detail-row">
            <span className="label">Vị trí ca trực</span>
            <span className="value">{item.shiftRole}</span>
          </div>

          <div className="detail-row">
            <span className="label">Thời gian bắt đầu ca</span>
            <span className="value">
              {item.shiftStartTime ? new Date(item.shiftStartTime).toLocaleString('vi-VN') : 'N/A'}
            </span>
          </div>

          <div className="detail-row">
            <span className="label">Thời điểm ghi nhận</span>
            <span className="value">
              {item.detectedAt ? new Date(item.detectedAt).toLocaleString('vi-VN') : 'N/A'}
            </span>
          </div>

          <div className="detail-row">
            <span className="label">Hình thức phát hiện</span>
            <span className="value">{NO_SHOW_DETECTED_BY_LABELS[item.detectedBy] || item.detectedBy}</span>
          </div>

          <div className="detail-row">
            <span className="label">Mức phạt điểm uy tín (RS)</span>
            <span className="value" style={{ color: '#dc2626' }}>
              -{item.penaltyAmount} RS ({item.reliabilityScoreBefore} → {item.reliabilityScoreAfter})
            </span>
          </div>

          <div className="detail-row">
            <span className="label">Khóa tài khoản</span>
            <span className="value" style={{ color: item.isAccountLocked ? '#dc2626' : '#166534' }}>
              {item.isAccountLocked ? 'Đã tự động khóa tài khoản (RS < 20)' : 'Không khóa'}
            </span>
          </div>

          <div className="detail-row">
            <span className="label">Điều phối thay thế (Emergency Dispatch)</span>
            <span className="value">
              {item.emergencyDispatched ? 'Đã kích hoạt gửi đề xuất tìm nhân sự mới' : 'Chưa điều phối'}
            </span>
          </div>

          <div className="detail-row">
            <span className="label">Trạng thái Kháng cáo (UC-94)</span>
            <span className="value">
              {item.appealId ? (
                <span style={{ fontWeight: 700, color: '#4f46e5' }}>
                  {APPEAL_STATUS_LABELS[item.appealStatus] || item.appealStatus}
                </span>
              ) : (
                'Chưa nộp đơn kháng cáo'
              )}
            </span>
          </div>

          {item.notes && (
            <div style={{ marginTop: '8px' }}>
              <span className="label" style={{ display: 'block', marginBottom: '4px' }}>Ghi chú vi phạm:</span>
              <div style={{ padding: '10px 12px', background: '#f9fafb', borderRadius: '8px', fontSize: '0.875rem', color: '#374151' }}>
                {item.notes}
              </div>
            </div>
          )}
        </div>

        <div className="modal-footer">
          {item.appealId && onNavigateAppeal && (
            <button
              type="button"
              className="btn-noshow-manual"
              style={{ background: '#4f46e5', color: '#fff', border: 'none' }}
              onClick={() => onNavigateAppeal(item.appealId)}
            >
              Xem Đơn Kháng Cáo
            </button>
          )}
          <button type="button" className="btn-noshow-manual" onClick={onClose}>
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
};

