import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import DisputeSingleChatPane from './DisputeSingleChatPane';
import { disputeApi } from '../api/disputeApi';

/**
 * Giao diện kẹp đôi toàn màn hình (True Full-Screen Dual Chat).
 * Render qua createPortal trực tiếp vào document.body để luôn chiếm trọn vẹn viewport
 * bất kể vị trí cuộn trang hiện tại.
 * Tuân thủ < 200 dòng theo AGENTS.md.
 */
export const DisputeDualChatModal = ({
  isOpen,
  onClose,
  dispute,
  chatData,
  onSendMessage,
  isSending,
  isArbitrator,
}) => {
  const workerChat = chatData?.workerConversation;
  const eoChat = chatData?.eoConversation;

  useEffect(() => {
    if (isOpen) {
      const prevOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';
      if (dispute?.id) {
        disputeApi.markDisputeChatRead(dispute.id).catch(() => {});
        if (workerChat) workerChat.unreadCount = 0;
        if (eoChat) eoChat.unreadCount = 0;
        window.dispatchEvent(new Event('cani:dispute_chat_updated'));
      }
      return () => {
        document.body.style.overflow = prevOverflow;
      };
    }
  }, [isOpen, dispute?.id]);

  if (!isOpen) return null;

  const disputeCode = (chatData?.disputeId || dispute?.id || 'HOSO').slice(0, 8).toUpperCase();
  const shiftTitle = chatData?.shiftTitle || dispute?.jobType || 'Ca làm việc';
  const eventName = chatData?.eventName || dispute?.eventName || 'Sự kiện liên quan';

  const workerName = workerChat?.userName || dispute?.complainantName || 'Nhân sự';
  const eoName = eoChat?.userName || dispute?.respondentName || 'Nhà tổ chức';

  const isClosed =
    chatData?.disputeStatus === 'FINAL_RESOLVED' ||
    chatData?.disputeStatus === 'RESOLVED' ||
    chatData?.disputeStatus === 'UNVERIFIED' ||
    dispute?.status === 'FINAL_RESOLVED' ||
    dispute?.status === 'RESOLVED' ||
    dispute?.status === 'UNVERIFIED';

  const modalNode = (
    <div className="dispute-dual-fullscreen-overlay">
      <div className="dispute-dual-fullscreen-view">
        {/* Fullscreen Header Bar */}
        <div className="dispute-dual-fullscreen-header">
          <div className="modal-header-titles">
            <h3 className="modal-title">Phân Xử Đối Chất Song Song (Kẹp Đôi)</h3>
            <div className="modal-shift-meta">
              <span className="shift-pill">Ca: {shiftTitle}</span>
              {eventName && <span className="event-pill">{eventName}</span>}
              <span className="dispute-pill">#{disputeCode}</span>
            </div>
          </div>
          <button
            type="button"
            className="btn-dual-modal-close"
            onClick={onClose}
            title="Thoát chế độ toàn màn hình"
          >
            Thu nhỏ
          </button>
        </div>

        {/* Fullscreen 2-column Split Workspace */}
        <div className="dispute-dual-fullscreen-body">
          <div className="dual-pane-column left-pane">
            <DisputeSingleChatPane
              partyChat={workerChat}
              targetParty="WORKER"
              partyTitle={`Nhân sự: ${workerName}`}
              partyRoleLabel="Người lao động"
              onSendMessage={onSendMessage}
              isSending={isSending}
              isArbitrator={isArbitrator}
              isClosed={isClosed}
              placeholder="Chỉ đạo / yêu cầu giải trình gửi Nhân sự..."
            />
          </div>

          <div className="dual-pane-column right-pane">
            <DisputeSingleChatPane
              partyChat={eoChat}
              targetParty="EO"
              partyTitle={`Nhà tổ chức: ${eoName}`}
              partyRoleLabel="Nhà tổ chức"
              onSendMessage={onSendMessage}
              isSending={isSending}
              isArbitrator={isArbitrator}
              isClosed={isClosed}
              placeholder="Chỉ đạo / yêu cầu đối soát gửi Nhà tổ chức..."
            />
          </div>
        </div>
      </div>
    </div>
  );

  return typeof document !== 'undefined' ? createPortal(modalNode, document.body) : modalNode;
};

export default DisputeDualChatModal;
