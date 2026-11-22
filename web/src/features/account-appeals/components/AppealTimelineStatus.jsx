import React from 'react';
import { ACCOUNT_APPEAL_STATUS_CONFIG } from '../types/accountAppeal.constants';

export default function AppealTimelineStatus({ appeal, onReappeal }) {
  if (!appeal) return null;

  const cfg = ACCOUNT_APPEAL_STATUS_CONFIG[appeal.status] || ACCOUNT_APPEAL_STATUS_CONFIG.SUBMITTED;
  const isRejected = appeal.status === 'REJECTED';
  const isApproved = appeal.status === 'APPROVED';

  return (
    <div className="appeal-status-card">
      <div className="appeal-status-header">
        <div className="appeal-status-badge" style={{ backgroundColor: cfg.bg, color: cfg.color }}>
          <span className="appeal-status-dot" style={{ backgroundColor: cfg.color }} />
          {cfg.label}
        </div>
        <span className="appeal-status-time">
          Nộp lúc: {new Date(appeal.createdAt).toLocaleString('vi-VN')}
        </span>
      </div>

      <div className="appeal-status-body">
        <div className="appeal-info-row">
          <span className="appeal-info-label">Lý do tài khoản bị khóa ban đầu:</span>
          <span className="appeal-info-val text-danger">{appeal.lockReasonSnapshot || 'Vi phạm chính sách vận hành'}</span>
        </div>

        <div className="appeal-info-row">
          <span className="appeal-info-label">Nội dung giải trình của bạn:</span>
          <div className="appeal-quote-box">{appeal.appealContent}</div>
        </div>

        {appeal.adminNotes && (
          <div className="appeal-info-row">
            <span className="appeal-info-label">Phản hồi từ Ban Quản trị:</span>
            <div className={`appeal-admin-notes ${isApproved ? 'is-approved' : 'is-rejected'}`}>
              <strong>{isApproved ? 'Chấp thuận mở khóa' : 'Từ chối giải trình'}:</strong> {appeal.adminNotes}
            </div>
          </div>
        )}

        <div className="appeal-desc-note">{cfg.desc}</div>
      </div>

      {isRejected && (
        <div className="appeal-reappeal-box">
          <p>Nếu bạn đã khắc phục bổ sung thêm chứng từ mới, bạn có thể nộp lại đơn giải trình mới.</p>
          <button type="button" className="btn-reappeal" onClick={onReappeal}>
            Nộp Lại Đơn Giải Trình Mới
          </button>
        </div>
      )}

      {isApproved && (
        <div className="appeal-approved-box">
          <p>Tài khoản của bạn đã được kích hoạt lại bình thường!</p>
          <a href="/login" className="btn-appeal-login">
            Đăng Nhập Lại Ngay →
          </a>
        </div>
      )}
    </div>
  );
}
