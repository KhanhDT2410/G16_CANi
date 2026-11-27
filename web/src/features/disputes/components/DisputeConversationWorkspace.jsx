import { useState } from 'react';
import DisputeChatThread from './DisputeChatThread';
import DisputeDualChatModal from './DisputeDualChatModal';
import DisputeSidebarInfo from './DisputeSidebarInfo';
import { useDisputeChat } from '../hooks/useDisputeChat';
import { DISPUTE_STATUS_CONFIG, DISPUTE_TYPE_LABELS } from '../types/dispute.constants';

const CASE_INFO_STORAGE_KEY = 'cani_dispute_show_case_info';

/**
 * Không gian xử lý tranh chấp dạng hội thoại tương tác đa bên (ADM-03 / FE-11).
 * Hỗ trợ ẩn/hiện cột 3 (Hồ sơ vụ việc) để tập trung vào hàng đợi và hội thoại.
 * Tuân thủ < 200 dòng theo AGENTS.md.
 */
export const DisputeConversationWorkspace = ({
  dispute,
  onBack,
  onAssign,
  onDecide,
  onEscalate,
  onTransfer,
  isArbitrator,
}) => {
  const [copiedCode, setCopiedCode] = useState(false);
  const [isDualModalOpen, setIsDualModalOpen] = useState(false);
  const [showCaseInfo, setShowCaseInfo] = useState(() => {
    try {
      return localStorage.getItem(CASE_INFO_STORAGE_KEY) === 'true';
    } catch {
      return false;
    }
  });

  const { chatData, isSending, sendMessage } = useDisputeChat(dispute?.id, isArbitrator);

  const toggleCaseInfo = () => {
    setShowCaseInfo((prev) => {
      const next = !prev;
      try {
        localStorage.setItem(CASE_INFO_STORAGE_KEY, String(next));
      } catch {
        // Bỏ qua nếu trình duyệt chặn storage
      }
      return next;
    });
  };

  const handleCopyId = () => {
    if (!dispute?.id) return;
    navigator.clipboard.writeText(dispute.id);
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2000);
  };

  if (!dispute) return null;

  const statusConfig = DISPUTE_STATUS_CONFIG[dispute.status] || {
    label: dispute.status,
    badgeClass: 'badge-status-open',
  };
  const typeLabel = DISPUTE_TYPE_LABELS[dispute.type] || dispute.type;

  const canAssign = isArbitrator && dispute.status === 'OPEN';
  const canDecide = isArbitrator && (dispute.status === 'INVESTIGATING' || dispute.status === 'ESCALATED');
  const canEscalate = !isArbitrator && (dispute.status === 'RESOLVED' || dispute.status === 'UNVERIFIED');
  const canTransfer = isArbitrator && (dispute.status === 'OPEN' || dispute.status === 'INVESTIGATING');

  return (
    <div className="dispute-workspace-container">
      {/* Top Header bar */}
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
              {!showCaseInfo && (dispute.complainantName || dispute.respondentName) && (
                <span className="workspace-parties-summary">
                  {dispute.complainantName || 'Người gửi'} · {dispute.respondentName || 'Đối tác'}
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Action buttons */}
        <div className="workspace-actions">
          <button
            type="button"
            className={`btn-ws-action btn-ws-info-toggle ${showCaseInfo ? 'active' : ''}`}
            onClick={toggleCaseInfo}
          >
            {showCaseInfo ? 'Ẩn hồ sơ' : 'Xem hồ sơ'}
          </button>
          {canAssign && (
            <button type="button" className="btn-ws-action btn-ws-assign" onClick={() => onAssign(dispute.id)}>
              Nhận xử lý ca này
            </button>
          )}
          {canDecide && (
            <button type="button" className="btn-ws-action btn-ws-decide" onClick={() => onDecide(dispute)}>
              Ra phán quyết
            </button>
          )}
          {canEscalate && (
            <button type="button" className="btn-ws-action btn-ws-escalate" onClick={() => onEscalate(dispute)}>
              Kháng nghị Admin
            </button>
          )}
          {canTransfer && onTransfer && (
            <button type="button" className="btn-ws-action btn-ws-transfer" onClick={() => onTransfer(dispute)}>
              Chuyển CSKH
            </button>
          )}
        </div>
      </div>

      {/* Main Body: 1 cột (Chat mở rộng) hoặc 2 cột (Chat + Hồ sơ vụ việc) */}
      <div className={`dispute-workspace-layout ${showCaseInfo ? '' : 'sidebar-hidden'}`}>
        <div className="dispute-workspace-chat">
          <DisputeChatThread
            dispute={dispute}
            chatData={chatData}
            onSendMessage={sendMessage}
            isSending={isSending}
            isArbitrator={isArbitrator}
            onOpenDualModal={() => setIsDualModalOpen(true)}
          />
        </div>

        {showCaseInfo && (
          <DisputeSidebarInfo
            dispute={dispute}
            copiedCode={copiedCode}
            onCopyId={handleCopyId}
          />
        )}
      </div>

      {/* Modal Kẹp Đôi Song Song Toàn Màn Hình */}
      <DisputeDualChatModal
        isOpen={isDualModalOpen}
        onClose={() => setIsDualModalOpen(false)}
        dispute={dispute}
        chatData={chatData}
        onSendMessage={sendMessage}
        isSending={isSending}
        isArbitrator={isArbitrator}
      />
    </div>
  );
};

export default DisputeConversationWorkspace;
