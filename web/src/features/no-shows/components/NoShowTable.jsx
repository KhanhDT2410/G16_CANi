import React from 'react';
import {
  NO_SHOW_DETECTED_BY_LABELS,
  NO_SHOW_DETECTED_BY_BADGE,
  APPEAL_STATUS_LABELS,
  APPEAL_STATUS_BADGE,
} from '../types/noShow.constants';

/**
 * Bảng hiển thị danh sách các ca No-show
 */
export const NoShowTable = ({ items, loading, onSelectDetail }) => {
  if (loading) {
    return <div className="empty-state">Đang tải danh sách vi phạm...</div>;
  }

  if (!items || items.length === 0) {
    return <div className="empty-state">Không có ca vi phạm No-show nào trong danh sách.</div>;
  }

  return (
    <div className="noshow-table-container">
      <table className="noshow-table">
        <thead>
          <tr>
            <th>Nhân Sự (Worker)</th>
            <th>Sự Kiện & Ca Trực</th>
            <th>Thời Gian Ca</th>
            <th>Mức Phạt RS</th>
            <th>Nguồn Phát Hiện</th>
            <th>Kháng Cáo</th>
            <th style={{ textAlign: 'center' }}>Thao Tác</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.id}>
              <td>
                <div className="worker-cell">
                  <div className="worker-avatar">
                    {item.workerAvatar ? (
                      <img src={item.workerAvatar} alt={item.workerName} style={{ width: '100%', height: '100%', borderRadius: '50%' }} />
                    ) : (
                      (item.workerName || 'W').charAt(0).toUpperCase()
                    )}
                  </div>
                  <div className="worker-info-meta">
                    <span className="worker-name">{item.workerName}</span>
                    <span className="worker-email">{item.workerEmail}</span>
                  </div>
                </div>
              </td>
              <td>
                <div style={{ fontWeight: 600, color: '#111827' }}>{item.eventName || 'Sự kiện'}</div>
                <div style={{ fontSize: '0.8125rem', color: '#6b7280' }}>{item.shiftRole}</div>
              </td>
              <td>
                <div style={{ fontSize: '0.8125rem' }}>
                  {item.shiftStartTime ? new Date(item.shiftStartTime).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' }) : 'N/A'}
                </div>
              </td>
              <td>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <span className="penalty-tag">-{item.penaltyAmount || 25} RS</span>
                  {item.isAccountLocked && <span className="badge-lock">KHÓA TK</span>}
                </div>
                <div className="rs-score-change">
                  {item.reliabilityScoreBefore !== null && item.reliabilityScoreAfter !== null
                    ? `${item.reliabilityScoreBefore} → ${item.reliabilityScoreAfter}`
                    : ''}
                </div>
              </td>
              <td>
                <span className={`badge ${NO_SHOW_DETECTED_BY_BADGE[item.detectedBy] || 'badge-sys'}`}>
                  {NO_SHOW_DETECTED_BY_LABELS[item.detectedBy] || item.detectedBy}
                </span>
              </td>
              <td>
                {item.appealId ? (
                  <span className={`badge ${APPEAL_STATUS_BADGE[item.appealStatus] || 'badge-warning'}`}>
                    {APPEAL_STATUS_LABELS[item.appealStatus] || item.appealStatus}
                  </span>
                ) : (
                  <span style={{ fontSize: '0.8125rem', color: '#9ca3af' }}>Chưa kháng cáo</span>
                )}
              </td>
              <td style={{ textAlign: 'center' }}>
                <button
                  type="button"
                  className="btn-view-detail"
                  onClick={() => onSelectDetail(item)}
                >
                  Chi tiết
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

