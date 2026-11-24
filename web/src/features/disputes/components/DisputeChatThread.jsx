import { useState, useEffect } from 'react';
import DisputeSingleChatPane from './DisputeSingleChatPane';
import { disputeApi } from '../api/disputeApi';

/**
 * Khung điều phối kênh hội thoại phân xử tranh chấp (ADM-03 / FE-11).
 * Hỗ trợ chuyển đổi tab giữa Worker và EO cho Admin/CSKH,
 * hiển thị số tin nhắn mới trên từng tab và tự động xóa badge khi bấm.
 * Tuân thủ < 200 dòng theo AGENTS.md.
 */
export const DisputeChatThread = ({
  dispute,
  chatData,
  onSendMessage,
  isSending = false,
  isArbitrator = false,
  onOpenDualModal,
}) => {
  const [activeTab, setActiveTab] = useState('WORKER');

  const workerChat = chatData?.workerConversation;
  const eoChat = chatData?.eoConversation;
  const isClosed =
    dispute?.status === 'FINAL_RESOLVED' ||
    dispute?.status === 'RESOLVED' ||
    dispute?.status === 'UNVERIFIED' ||
    chatData?.disputeStatus === 'FINAL_RESOLVED' ||
    chatData?.disputeStatus === 'RESOLVED' ||
    chatData?.disputeStatus === 'UNVERIFIED';

  // Tự động đánh dấu đọc tin khi người dùng hoặc trọng tài mở hội thoại
  useEffect(() => {
    if (!dispute?.id) return;
    if (!isArbitrator) {
      disputeApi.markDisputeChatRead(dispute.id).catch(() => {});
      window.dispatchEvent(new Event('cani:dispute_chat_updated'));
    } else {
      disputeApi.markDisputeChatRead(dispute.id, activeTab).catch(() => {});
      if (activeTab === 'WORKER' && workerChat) workerChat.unreadCount = 0;
      if (activeTab === 'EO' && eoChat) eoChat.unreadCount = 0;
      window.dispatchEvent(new Event('cani:dispute_chat_updated'));
    }
  }, [dispute?.id, activeTab, isArbitrator]);

  if (!dispute) return null;

  // Đối với bên tham gia (Worker hoặc EO): Chỉ hiển thị hội thoại 1-1 với Admin
  if (!isArbitrator) {
    const singleChat = workerChat || eoChat;
    return (
      <div className="dispute-chat-thread-container">
        <DisputeSingleChatPane
          partyChat={singleChat}
          partyTitle="Trọng tài CSKH & Quản trị viên"
          partyRoleLabel="Ban Quản Trị"
          onSendMessage={onSendMessage}
          isSending={isSending}
          isArbitrator={false}
          isClosed={isClosed}
          placeholder="Nhập ý kiến giải trình hoặc phản hồi gửi Ban Quản Trị..."
        />
      </div>
    );
  }

  // Đối với Trọng tài (Admin / CSKH): Tab chọn bên trao đổi + nút Toàn màn hình
  const workerName = workerChat?.userName || dispute.complainantName || 'Nhân sự';
  const eoName = eoChat?.userName || dispute.respondentName || 'Nhà tổ chức';

  const workerUnread = workerChat?.unreadCount || 0;
  const eoUnread = eoChat?.unreadCount || 0;

  const handleSelectTab = (tab) => {
    setActiveTab(tab);
    if (dispute?.id) {
      disputeApi.markDisputeChatRead(dispute.id, tab).catch(() => {});
      if (tab === 'WORKER' && workerChat) workerChat.unreadCount = 0;
      if (tab === 'EO' && eoChat) eoChat.unreadCount = 0;
      window.dispatchEvent(new Event('cani:dispute_chat_updated'));
    }
  };

  return (
    <div className="dispute-chat-thread-container">
      {/* Thanh điều hướng 3 nút đều nhau cho Admin */}
      <div className="dispute-thread-top-nav">
        <button
          type="button"
          className={`thread-nav-btn ${activeTab === 'WORKER' ? 'active' : ''}`}
          onClick={() => handleSelectTab('WORKER')}
        >
          <span className="nav-btn-role">Nhân sự</span>
          <span className="nav-btn-name">{workerName}</span>
          {workerUnread > 0 && (
            <span className="tab-unread-dot" title={`${workerUnread} tin nhắn mới`} />
          )}
        </button>

        <button
          type="button"
          className={`thread-nav-btn ${activeTab === 'EO' ? 'active' : ''}`}
          onClick={() => handleSelectTab('EO')}
        >
          <span className="nav-btn-role">Nhà tổ chức</span>
          <span className="nav-btn-name">{eoName}</span>
          {eoUnread > 0 && (
            <span className="tab-unread-dot" title={`${eoUnread} tin nhắn mới`} />
          )}
        </button>

        {onOpenDualModal && (
          <button
            type="button"
            className="thread-nav-btn btn-fullscreen"
            onClick={onOpenDualModal}
            title="Mở song song 2 bên trên màn hình rộng"
          >
            <span className="nav-btn-role">Chế độ xem</span>
            <span className="nav-btn-name">Toàn màn hình</span>
          </button>
        )}
      </div>

      {/* Nội dung hội thoại của tab đang chọn */}
      <div className="dispute-thread-active-content">
        {activeTab === 'WORKER' ? (
          <DisputeSingleChatPane
            partyChat={workerChat}
            targetParty="WORKER"
            partyRolePrefix="Nhân sự"
            partyName={workerName}
            partyRoleLabel="Người lao động"
            onSendMessage={onSendMessage}
            isSending={isSending}
            isArbitrator={true}
            isClosed={isClosed}
            placeholder="Chỉ đạo hoặc yêu cầu bổ sung chứng cứ gửi Nhân sự..."
          />
        ) : (
          <DisputeSingleChatPane
            partyChat={eoChat}
            targetParty="EO"
            partyRolePrefix="Nhà tổ chức"
            partyName={eoName}
            partyRoleLabel="Nhà tổ chức"
            onSendMessage={onSendMessage}
            isSending={isSending}
            isArbitrator={true}
            isClosed={isClosed}
            placeholder="Chỉ đạo hoặc yêu cầu đối soát gửi Nhà tổ chức..."
          />
        )}
      </div>
    </div>
  );
};

export default DisputeChatThread;
