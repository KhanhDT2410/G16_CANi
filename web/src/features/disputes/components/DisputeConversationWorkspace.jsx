import { useState } from 'react';
import DisputeChatThread from './DisputeChatThread';
import { DISPUTE_STATUS_CONFIG, DISPUTE_TYPE_LABELS } from '../types/dispute.constants';

const formatShiftTime = (start, end) => {
  if (!start) return null;
  const startDate = new Date(start);
  const timeStr = startDate.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  const dateStr = startDate.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
  if (!end) return `${timeStr}, ${dateStr}`;
  const endTimeStr = new Date(end).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  return `${timeStr} - ${endTimeStr}, ${dateStr}`;
};

const formatRoleLabel = (role) => {
  if (role === 'EVENT_ORGANIZER') return 'Nhà tổ chức';
  if (role === 'WORKER') return 'Nhân sự';
  if (role === 'CUSTOMER_CARE') return 'CSKH';
  if (role === 'ADMIN') return 'Quản trị viên';
  return role || '';
};

/**
 * Không gian xử lý tranh chấp dạng hội thoại tương tác đa bên (ADM-03).
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const DisputeConversationWorkspace = ({
  dispute,
  onBack,
  onAssign,
  onDecide,
  onEscalate,
  onTransfer,
  onSendMessage,
  isSending,
  isArbitrator,
}) => {
  const [copiedCode, setCopiedCode] = useState(false);

  if (!dispute) return null;

  const handleCopyId = () => {
    if (!dispute.id) return;
    navigator.clipboard.writeText(dispute.id);
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2000);
  };

  const statusConfig = DISPUTE_STATUS_CONFIG[dispute.status] || {
    label: dispute.status,
    badgeClass: 'badge-status-open',
  };
  const typeLabel = DISPUTE_TYPE_LABELS[dispute.type] || dispute.type;

  const canAssign = isArbitrator && dispute.status === 'OPEN';
  const canDecide = isArbitrator && (dispute.status === 'INVESTIGATING' || dispute.status === 'ESCALATED');
  const canEscalate = dispute.status === 'RESOLVED' || dispute.status === 'UNVERIFIED';
  const canTransfer = isArbitrator && (dispute.status === 'OPEN' || dispute.status === 'INVESTIGATING');

  return (
    <div className="dispute-workspace-container">
      {/* Top Header bar with Back button & Status */}
      <div className="dispute-workspace-topbar">
        <div className="workspace-title-box">
          <button type="button" className="btn-workspace-back" onClick={onBack}>
            Quay lại hàng đợi
          </button>
          <div className="workspace-main-title">
            <h2>{dispute.title}</h2>
            <div className="workspace-tags-row">
              <span className={`dispute-badge ${statusConfig.badgeClass}`}>{statusConfig.label}</span>
              <span className="workspace-type-tag">Phân loại: {typeLabel}</span>
              {dispute.slaDeadline && (
                <span className="workspace-sla-tag">
                  Hạn SLA: {new Date(dispute.slaDeadline).toLocaleString('vi-VN')}
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Action buttons */}
        <div className="workspace-actions">
          {canAssign && (
            <button
              type="button"
              className="btn-ws-action btn-ws-assign"
              onClick={() => onAssign(dispute.id)}
            >
              Nhận xử lý ca này
            </button>
          )}

          {canDecide && (
            <button
              type="button"
              className="btn-ws-action btn-ws-decide"
              onClick={() => onDecide(dispute)}
            >
              Ra phán quyết
            </button>
          )}

          {canEscalate && (
            <button
              type="button"
              className="btn-ws-action btn-ws-escalate"
              onClick={() => onEscalate(dispute)}
            >
              Kháng nghị Admin
            </button>
          )}

          {canTransfer && onTransfer && (
            <button
              type="button"
              className="btn-ws-action btn-ws-transfer"
              onClick={() => onTransfer(dispute)}
            >
              Chuyển CSKH
            </button>
          )}
        </div>
      </div>

      {/* Main 2-column Body */}
      <div className="dispute-workspace-layout">
        {/* Left Column: Conversational Chat Thread */}
        <div className="dispute-workspace-chat">
          <DisputeChatThread
            dispute={dispute}
            onSendMessage={onSendMessage}
            isSending={isSending}
            isArbitrator={isArbitrator}
          />
        </div>

        {/* Right Column: Case Details & Parties Info */}
        <div className="dispute-workspace-sidebar">
          <div className="sidebar-info-card">
            <div className="sidebar-card-header">
              <h4 className="sidebar-card-title">Hồ Sơ Vụ Việc</h4>
              <button
                type="button"
                className="case-ref-chip"
                onClick={handleCopyId}
                title="Bấm để sao chép mã khiếu nại"
              >
                <span>#{dispute.id ? dispute.id.slice(0, 8).toUpperCase() : 'HỒ SƠ'}</span>
                <span className="copy-hint">{copiedCode ? 'Đã sao chép' : 'Sao chép'}</span>
              </button>
            </div>

            {/* Thông tin ca & sự kiện */}
            <div className="sidebar-info-section">
              <span className="info-label">Ca làm việc liên quan:</span>
              <div className="info-value-primary">
                {dispute.eventName ? (
                  <>
                    <div className="font-semibold text-main">{dispute.eventName}</div>
                    {dispute.jobType && <div className="text-muted text-sm">{dispute.jobType}</div>}
                  </>
                ) : (
                  <span className="font-semibold">{dispute.jobType || `Ca #${dispute.assignmentId ? dispute.assignmentId.slice(0, 8).toUpperCase() : 'N/A'}`}</span>
                )}
              </div>
              {dispute.shiftStartTime && (
                <div className="info-subtext text-xs text-muted">
                  Thời gian: {formatShiftTime(dispute.shiftStartTime, dispute.shiftEndTime)}
                </div>
              )}
              {dispute.shiftLocation && (
                <div className="info-subtext text-xs text-muted">
                  Địa điểm: {dispute.shiftLocation}
                </div>
              )}
            </div>

            {/* Các bên liên quan */}
            <div className="sidebar-info-section">
              <div className="sidebar-parties-grid">
                <div className="party-box">
                  <span className="info-label">Người gửi khiếu nại:</span>
                  <div className="party-name">{dispute.complainantName || 'Người gửi đơn'}</div>
                  {dispute.complainantRole && (
                    <span className="party-role-tag">{formatRoleLabel(dispute.complainantRole)}</span>
                  )}
                </div>
                <div className="party-box">
                  <span className="info-label">Đối tác liên quan:</span>
                  <div className="party-name">{dispute.respondentName || 'Đối tác trong ca'}</div>
                  {dispute.respondentRole && (
                    <span className="party-role-tag">{formatRoleLabel(dispute.respondentRole)}</span>
                  )}
                </div>
              </div>
            </div>

            {/* Phụ trách & Tiến độ */}
            <div className="sidebar-info-item">
              <span className="info-label">Chuyên viên CSKH:</span>
              <span className="info-text">
                {dispute.assignedToName ? (
                  <strong className="text-primary">{dispute.assignedToName}</strong>
                ) : (
                  <span className="badge-unassigned">Đang chờ chuyên viên tiếp nhận</span>
                )}
              </span>
            </div>

            <div className="sidebar-info-item">
              <span className="info-label">Thời gian gửi:</span>
              <span className="info-text">{new Date(dispute.createdAt).toLocaleString('vi-VN')}</span>
            </div>

            {dispute.slaDeadline && (
              <div className="sidebar-info-item">
                <span className="info-label">Hạn xử lý (SLA):</span>
                <span className="info-text font-semibold" style={{ color: '#d97706' }}>
                  {new Date(dispute.slaDeadline).toLocaleDateString('vi-VN')}
                </span>
              </div>
            )}

            <div className="sidebar-info-item">
              <span className="info-label">Tài liệu minh chứng:</span>
              <span className="info-text font-bold">{dispute.evidences?.length || 0} tệp đã nạp</span>
            </div>
          </div>

          {/* Quy tắc giải quyết tranh chấp tóm tắt */}
          <div className="sidebar-rules-card">
            <h4 className="sidebar-card-title">Quy Tắc Hòa Giải</h4>
            <ul className="sidebar-rules-list">
              <li>Mỗi bên có quyền nộp tài liệu giải trình, hóa đơn và hình ảnh đối soát.</li>
              <li>CSKH giữ vai trò trung gian xác minh chứng cứ và đưa ra phán quyết độc lập.</li>
              <li>Trường hợp bên có lỗi là Worker, hệ thống tự động khấu trừ điểm Uy Tín.</li>
              <li>Nếu không đồng ý phán quyết, các bên có quyền Kháng nghị lên Ban Quản Trị trong vòng 7 ngày.</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DisputeConversationWorkspace;
