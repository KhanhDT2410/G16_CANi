import DisputeChatThread from './DisputeChatThread';
import { DISPUTE_STATUS_CONFIG, DISPUTE_TYPE_LABELS } from '../types/dispute.constants';

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
  if (!dispute) return null;

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
          />
        </div>

        {/* Right Column: Case Details & Parties Info */}
        <div className="dispute-workspace-sidebar">
          <div className="sidebar-info-card">
            <h4 className="sidebar-card-title">Hồ Sơ Vụ Việc</h4>

            <div className="sidebar-info-item">
              <span className="info-label">Mã tranh chấp:</span>
              <code className="info-code">{dispute.id}</code>
            </div>

            <div className="sidebar-info-item">
              <span className="info-label">Ca làm việc:</span>
              <code className="info-code">{dispute.assignmentId || 'N/A'}</code>
            </div>

            <div className="sidebar-info-item">
              <span className="info-label">Người phụ trách (CSKH):</span>
              <code className="info-code">{dispute.assignedTo || 'Chưa phân công'}</code>
            </div>

            <div className="sidebar-info-item">
              <span className="info-label">Bên khiếu nại (Nguyên đơn):</span>
              <code className="info-code">{dispute.complainantId || 'N/A'}</code>
            </div>

            <div className="sidebar-info-item">
              <span className="info-label">Bên bị khiếu nại (Bị đơn):</span>
              <code className="info-code">{dispute.respondentId || 'N/A'}</code>
            </div>

            <div className="sidebar-info-item">
              <span className="info-label">Ngày khởi tạo:</span>
              <span className="info-text">{new Date(dispute.createdAt).toLocaleString('vi-VN')}</span>
            </div>

            <div className="sidebar-info-item">
              <span className="info-label">Số chứng cứ đã nạp:</span>
              <span className="info-text font-bold">{dispute.evidences?.length || 0} tệp</span>
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
