import React from 'react';

/**
 * Hiển thị thẻ thống kê KPI No-show (Clean, không icon)
 */
export const NoShowStatsCards = ({ stats }) => {
  if (!stats) return null;

  return (
    <div className="noshow-stats-grid">
      <div className="noshow-stat-card card-total">
        <span className="noshow-stat-label">Tổng ca vi phạm</span>
        <div className="noshow-stat-val">{stats.totalNoShows || 0}</div>
      </div>

      <div className="noshow-stat-card card-today">
        <span className="noshow-stat-label">Vi phạm hôm nay</span>
        <div className="noshow-stat-val">{stats.noShowsToday || 0}</div>
      </div>

      <div className="noshow-stat-card card-rs">
        <span className="noshow-stat-label">Tổng điểm RS bị trừ</span>
        <div className="noshow-stat-val">{stats.totalRsDeducted || 0}</div>
      </div>

      <div className="noshow-stat-card card-locked">
        <span className="noshow-stat-label">Tài khoản bị khóa</span>
        <div className="noshow-stat-val">{stats.totalAccountsLocked || 0}</div>
      </div>
    </div>
  );
};
