import { useState, useEffect, useCallback } from 'react';
import DisputeChatThread from './DisputeChatThread';
import DisputeDualChatModal from './DisputeDualChatModal';
import DisputeSidebarInfo from './DisputeSidebarInfo';
import { disputeApi } from '../api/disputeApi';
import { DISPUTE_STATUS_CONFIG, DISPUTE_TYPE_LABELS } from '../types/dispute.constants';

/**
 * Không gian xử lý tranh chấp dạng hội thoại tương tác đa bên (ADM-03 / FE-11).
 * Tự động polling thời gian thực (2.5s) và cập nhật tin nhắn tức thì (Optimistic UI).
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
  const [chatData, setChatData] = useState(null);
  const [isSending, setIsSending] = useState(false);

  const fetchChat = useCallback(async (isSilent = false) => {
    if (!dispute?.id) return;
    try {
      const data = await disputeApi.getDisputeChat(dispute.id);
      if (data) {
        setChatData((prev) => {
          if (!prev) return data;
          const oldW = prev.workerConversation?.messages?.length || 0;
          const newW = data.workerConversation?.messages?.length || 0;
          const oldE = prev.eoConversation?.messages?.length || 0;
          const newE = data.eoConversation?.messages?.length || 0;
          if (oldW === newW && oldE === newE) return prev;
          return data;
        });
      }
    } catch (err) {
      if (!isSilent) console.error('Lỗi tải dữ liệu chat:', err);
    }
  }, [dispute?.id]);

  useEffect(() => {
    fetchChat(false);
    const interval = setInterval(() => {
      fetchChat(true);
    }, 2500);
    return () => clearInterval(interval);
  }, [fetchChat]);

  const handleSendMessage = async (payload) => {
    if (!dispute?.id) return;
    const target = payload.targetParty || 'WORKER';
    const tempId = `temp-${Date.now()}`;
    const optimisticMsg = {
      id: tempId,
      conversationId: target === 'WORKER'
        ? chatData?.workerConversation?.conversationId
        : chatData?.eoConversation?.conversationId,
      senderId: 'me',
      senderName: isArbitrator ? 'Quản trị viên' : 'Tôi',
      senderRole: isArbitrator ? 'ADMIN' : 'USER',
      messageText: payload.messageText,
      attachmentUrl: payload.attachmentUrl,
      isMe: true,
      createdAt: new Date().toISOString(),
    };

    setChatData((prev) => {
      if (!prev) return prev;
      const key = target === 'WORKER' ? 'workerConversation' : 'eoConversation';
      const conv = prev[key] || { party: target, messages: [] };
      return { ...prev, [key]: { ...conv, messages: [...(conv.messages || []), optimisticMsg] } };
    });

    setIsSending(true);
    try {
      const sentMsg = await disputeApi.sendDisputeChatMessage(dispute.id, payload);
      if (sentMsg) {
        setChatData((prev) => {
          if (!prev) return prev;
          const key = target === 'WORKER' ? 'workerConversation' : 'eoConversation';
          const conv = prev[key];
          if (!conv) return prev;
          return {
            ...prev,
            [key]: {
              ...conv,
              messages: (conv.messages || []).map((m) => (m.id === tempId ? sentMsg : m)),
            },
          };
        });
      }
    } catch (err) {
      console.error('Lỗi gửi tin nhắn phân xử:', err);
    } finally {
      setIsSending(false);
    }
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
  const canEscalate = dispute.status === 'RESOLVED' || dispute.status === 'UNVERIFIED';
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
            </div>
          </div>
        </div>

        {/* Action buttons */}
        <div className="workspace-actions">
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

      {/* Main 2-column Body */}
      <div className="dispute-workspace-layout">
        <div className="dispute-workspace-chat">
          <DisputeChatThread
            dispute={dispute}
            chatData={chatData}
            onSendMessage={handleSendMessage}
            isSending={isSending}
            isArbitrator={isArbitrator}
            onOpenDualModal={() => setIsDualModalOpen(true)}
          />
        </div>

        <DisputeSidebarInfo
          dispute={dispute}
          copiedCode={copiedCode}
          onCopyId={handleCopyId}
        />
      </div>

      {/* Modal Kẹp Đôi Song Song Toàn Màn Hình */}
      <DisputeDualChatModal
        isOpen={isDualModalOpen}
        onClose={() => setIsDualModalOpen(false)}
        dispute={dispute}
        chatData={chatData}
        onSendMessage={handleSendMessage}
        isSending={isSending}
        isArbitrator={isArbitrator}
      />
    </div>
  );
};

export default DisputeConversationWorkspace;
