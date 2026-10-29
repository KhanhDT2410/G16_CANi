import React from 'react';

/**
 * Thẻ KPI Thống Kê Ma Trận Phân Quyền RBAC (W-40)
 * Tuân thủ chuẩn DESIGN.md: Top accent line, bo góc 16px, ambient glow.
 */
export const RbacMetricsGrid = ({ metrics, loading }) => {
  if (loading) {
    return (
      <div className="rbac-metrics-grid">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="skeleton" style={{ height: '110px', borderRadius: '16px' }} />
        ))}
      </div>
    );
  }

  return (
    <div className="rbac-metrics-grid">
      {/* Metric 1: Tổng Vai Trò */}
      <div className="rbac-metric-card">
        <div className="rbac-metric-top">
          <span className="rbac-metric-title">Tổng Vai Trò Hệ Thống</span>
        </div>
        <div className="rbac-metric-val">{metrics.totalRoles}</div>
        <div className="rbac-metric-sub">
          <span className="rbac-badge-system">{metrics.systemRoles} Hệ thống</span>
          <span className="rbac-metric-sep">•</span>
          <span className="rbac-badge-custom">{metrics.customRoles} Tùy chỉnh</span>
        </div>
      </div>

      {/* Metric 2: Quyền Hạn Chi Tiết */}
      <div className="rbac-metric-card">
        <div className="rbac-metric-top">
          <span className="rbac-metric-title">Quyền Hạn Chi Tiết</span>
        </div>
        <div className="rbac-metric-val">{metrics.totalPermissions}</div>
        <div className="rbac-metric-sub">
          <span>Phân bố trên <strong>{metrics.totalModules}</strong> phân hệ chức năng</span>
        </div>
      </div>

      {/* Metric 3: Người Dùng Chịu Tác Động */}
      <div className="rbac-metric-card">
        <div className="rbac-metric-top">
          <span className="rbac-metric-title">Tài Khoản Phân Quyền</span>
        </div>
        <div className="rbac-metric-val">
          {metrics.affectedUsers.toLocaleString('vi-VN')}
        </div>
        <div className="rbac-metric-sub">
          <span className="rbac-metric-pulse" />
          <span>Áp dụng hiệu lực tức thời (Zero-downtime)</span>
        </div>
      </div>

      {/* Metric 4: Kiểm Toán Gần Nhất */}
      <div className="rbac-metric-card">
        <div className="rbac-metric-top">
          <span className="rbac-metric-title">Nhật Ký Kiểm Toán</span>
        </div>
        <div className="rbac-metric-audit-info">
          <div className="rbac-audit-time">{metrics.lastAudit.timestamp}</div>
          <div className="rbac-audit-author">{metrics.lastAudit.adminName}</div>
        </div>
        <div className="rbac-metric-sub truncate" title={metrics.lastAudit.summary}>
          {metrics.lastAudit.summary}
        </div>
      </div>
    </div>
  );
};

export default RbacMetricsGrid;
