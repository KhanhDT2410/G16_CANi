import { useState, useRef, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { supportChatApi } from '../api/supportChatApi';
import { showToast } from '../../../components/common/Toast';
import SupportChatFaqModal from './SupportChatFaqModal';

/**
 * Cửa sổ chat Messenger thu nhỏ ở góc dưới màn hình dành cho CSKH / Quản trị viên.
 * Luôn ghim cố định ở góc dưới màn hình qua createPortal, không làm cuộn nhảy trang.
 */
export const StaffMessengerDock = ({ activeChat, onClose, onOpenTicket, onOpenShiftDetail }) => {
  const [messages, setMessages] = useState([]);
  const [inputText, setInputText] = useState('');
  const [isMinimized, setIsMinimized] = useState(false);
  const [convId, setConvId] = useState(null);
  const [showMenu, setShowMenu] = useState(false);
  const [showFaqModal, setShowFaqModal] = useState(false);
  const [pendingAttachment, setPendingAttachment] = useState(null);
  const [isUploading, setIsUploading] = useState(false);
  const [sending, setSending] = useState(false);

  const fileInputRef = useRef(null);
  const dockBodyRef = useRef(null);

  const formatMessage = (m) => ({
    id: m.id,
    sender: m.senderRole === 'CUSTOMER_CARE' || m.senderRole === 'ADMIN' ? 'staff' : 'user',
    senderName: m.senderName,
    text: m.messageText,
    attachmentUrl: m.attachmentUrl,
    shiftId: m.shiftId,
    shiftCode: m.shiftCode,
    shiftName: m.shiftName,
    time: new Date(m.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
  });

  useEffect(() => {
    let isMounted = true;
    if (!activeChat) {
      setConvId(null);
      setMessages([]);
      return;
    }

    setIsMinimized(false);
    const targetUserId = activeChat.userId;

    if (targetUserId) {
      supportChatApi.initiateStaffConversation(targetUserId)
        .then((res) => {
          if (!isMounted) return;
          setConvId(res?.conversation?.id);
          if (res?.messages) setMessages(res.messages.map(formatMessage));
        })
        .catch(() => { if (isMounted) setMessages(activeChat.chatHistory || []); });
    } else if (activeChat.id && !String(activeChat.id).startsWith('demo-')) {
      setConvId(activeChat.id);
      supportChatApi.getStaffConversationDetail(activeChat.id)
        .then((res) => {
          if (isMounted && res?.messages) {
            setMessages(res.messages.map(formatMessage));
            window.dispatchEvent(new CustomEvent('cani_staff_chat_refresh'));
          }
        })
        .catch(() => { if (isMounted) setMessages(activeChat.chatHistory || []); });
    } else {
      setMessages(activeChat.chatHistory || []);
    }
    return () => { isMounted = false; };
  }, [activeChat]);

  // Polling tin nhắn mới mỗi 4 giây (khi đang mở khung chat)
  useEffect(() => {
    if (!convId || isMinimized) return;
    const interval = setInterval(async () => {
      try {
        const res = await supportChatApi.getStaffConversationDetail(convId);
        if (res?.messages) setMessages(res.messages.map(formatMessage));
      } catch { /* ignore */ }
    }, 4000);
    return () => clearInterval(interval);
  }, [convId, isMinimized]);

  // Tự động cuộn xuống cuối bên trong khung tin nhắn
  useEffect(() => {
    if (!isMinimized && dockBodyRef.current) {
      dockBodyRef.current.scrollTop = dockBodyRef.current.scrollHeight;
    }
  }, [messages, isMinimized]);

  if (!activeChat || typeof document === 'undefined') return null;

  const handleFileSelect = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 10 * 1024 * 1024) {
      showToast('Dung lượng tệp không được vượt quá 10MB', 'error');
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }

    setIsUploading(true);
    try {
      const res = await supportChatApi.uploadAttachment(file);
      if (res?.url) {
        setPendingAttachment({ url: res.url, name: file.name });
        showToast('Tải tệp thành công!', 'success');
      }
    } catch (err) {
      showToast(err.response?.data?.message || 'Không thể tải tệp lên máy chủ', 'error');
    } finally {
      setIsUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleSendMessage = async (e) => {
    if (e) e.preventDefault();
    const text = inputText.trim();
    if ((!text && !pendingAttachment) || sending || isUploading) return;

    const attachmentToSend = pendingAttachment;
    const finalMsgText = text || (attachmentToSend ? `Đã gửi tệp đính kèm: ${attachmentToSend.name}` : '');

    setMessages((prev) => [
      ...prev,
      {
        id: String(Date.now()),
        sender: 'staff',
        senderName: 'CSKH CANi',
        text: finalMsgText,
        attachmentUrl: attachmentToSend?.url || null,
        time: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
      },
    ]);
    setInputText('');
    setPendingAttachment(null);
    setSending(true);

    if (convId) {
      try {
        await supportChatApi.sendStaffReply(convId, {
          messageText: finalMsgText,
          attachmentUrl: attachmentToSend?.url || null,
        });
        window.dispatchEvent(new CustomEvent('cani_staff_chat_refresh'));
      } catch (err) {
        showToast('Lỗi khi gửi phản hồi: ' + (err.response?.data?.message || err.message), 'error');
      } finally {
        setSending(false);
      }
    } else {
      setSending(false);
    }
  };

  const dockContent = isMinimized ? (
    <div className="staff-messenger-minimized animate-fade-in">
      <div className="minimized-info">
        <span className="minimized-dot" />
        <span className="minimized-name">{activeChat.userName || 'Khách hàng'}</span>
        <span className="minimized-phone">({activeChat.userPhone || activeChat.referenceNo || 'Tin nhắn'})</span>
      </div>
      <div className="minimized-actions">
        <button type="button" className="dock-text-btn" onClick={() => setIsMinimized(false)}>Mở Rộng</button>
        <button type="button" className="dock-text-btn" onClick={onClose}>Đóng</button>
      </div>
    </div>
  ) : (
    <div className="staff-messenger-dock animate-scale-in">
      <div className="dock-header">
        <div className="dock-customer-info">
          <div className="dock-customer-name-row">
            <span className="dock-customer-name">{activeChat.userName || 'Khách hàng'}</span>
            <span className="dock-online-badge">Trực tuyến</span>
          </div>
          <div className="dock-customer-field">
            <span className="dock-field-label">SĐT:</span>
            <span className="dock-field-val">{activeChat.userPhone || '—'}</span>
          </div>
          <div className="dock-customer-field">
            <span className="dock-field-label">Email:</span>
            <span className="dock-field-val">{activeChat.userEmail || '—'}</span>
          </div>
          {(activeChat.currentShiftCode || activeChat.shiftCode) && (
            <div className="dock-customer-field">
              <span className="dock-field-label">Ca làm:</span>
              <span className="dock-field-val">
                <button
                  type="button"
                  onClick={() => onOpenShiftDetail && onOpenShiftDetail(activeChat.currentShiftId || activeChat.shiftId)}
                  style={{
                    background: '#eff6ff',
                    color: '#1d4ed8',
                    border: '1px solid #bfdbfe',
                    borderRadius: '4px',
                    padding: '2px 8px',
                    fontSize: '0.78rem',
                    fontWeight: 500,
                    cursor: 'pointer',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '3px',
                    fontFamily: "var(--font-family, 'Be Vietnam Pro', -apple-system, sans-serif)",
                  }}
                  title="Bấm để xem chi tiết ca làm việc"
                >
                  {activeChat.currentShiftCode || activeChat.shiftCode} ↗
                </button>
              </span>
            </div>
          )}
          {activeChat.referenceNo && (
            <div className="dock-customer-field">
              <span className="dock-field-label">Mã phiếu:</span>
              <span className="dock-field-val dock-field-ref">{activeChat.referenceNo}</span>
            </div>
          )}
        </div>

        <div className="dock-header-actions-col">
          {onOpenTicket && activeChat.referenceNo && (
            <button
              type="button"
              className="dock-text-btn"
              onClick={() => onOpenTicket(activeChat)}
              title="Xem chi tiết phiếu hỗ trợ"
            >
              Xem Phiếu
            </button>
          )}
          <button type="button" className="dock-text-btn" onClick={() => setIsMinimized(true)}>Thu Nhỏ</button>
          <button type="button" className="dock-text-btn" onClick={onClose}>Đóng</button>
        </div>
      </div>

      <div className="dock-body" ref={dockBodyRef}>
        {messages.length === 0 ? (
          <div style={{ textAlign: 'center', color: '#94a3b8', fontSize: '0.82rem', marginTop: '2rem' }}>
            Bắt đầu cuộc trò chuyện trực tiếp với khách hàng...
          </div>
        ) : (
          messages.map((m) => (
            <div key={m.id} className={`dock-message-row ${m.sender === 'staff' ? 'from-staff' : 'from-user'}`}>
              <div className="dock-bubble-sender">
                <span>{m.senderName}</span>
                {m.shiftCode && (
                  <span
                    style={{
                      marginLeft: 6,
                      fontSize: '0.75rem',
                      background: '#eff6ff',
                      color: '#1d4ed8',
                      border: '1px solid #bfdbfe',
                      padding: '1px 6px',
                      borderRadius: 4,
                      fontWeight: 500,
                      fontFamily: "var(--font-family, 'Be Vietnam Pro', -apple-system, sans-serif)",
                      cursor: onOpenShiftDetail ? 'pointer' : 'default',
                    }}
                    onClick={() => m.shiftId && onOpenShiftDetail && onOpenShiftDetail(m.shiftId)}
                    title="Bấm để xem chi tiết ca làm này"
                  >
                    {m.shiftCode} ↗
                  </span>
                )}
              </div>
              <div className="dock-bubble-text">{m.text}</div>
              {m.attachmentUrl && (
                <div className="dock-attachment-wrap">
                  {/\.(png|jpe?g|webp|gif)$/i.test(m.attachmentUrl) ? (
                    <a href={m.attachmentUrl} target="_blank" rel="noopener noreferrer">
                      <img src={m.attachmentUrl} alt="Đính kèm" className="dock-attachment-thumb" />
                    </a>
                  ) : (
                    <a href={m.attachmentUrl} target="_blank" rel="noopener noreferrer" className="dock-attachment-doc">
                      Xem tệp đính kèm ↗
                    </a>
                  )}
                </div>
              )}
              <div className="dock-bubble-time">{m.time}</div>
            </div>
          ))
        )}
      </div>

      {pendingAttachment && (
        <div className="dock-pending-attachment-bar">
          <span className="pending-file-info">Đính kèm: <strong>{pendingAttachment.name}</strong></span>
          <button
            type="button"
            className="pending-file-remove"
            onClick={() => setPendingAttachment(null)}
            title="Hủy tệp này"
          >
            ✕
          </button>
        </div>
      )}

      <form className="dock-footer" onSubmit={handleSendMessage}>
        <div className="dock-menu-container">
          <button
            type="button"
            className="dock-menu-btn"
            onClick={() => setShowMenu((prev) => !prev)}
            title="Tác vụ (Đính kèm tệp / Bật FAQ)"
            aria-label="Menu tác vụ"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="3" y1="6" x2="21" y2="6" />
              <line x1="3" y1="12" x2="21" y2="12" />
              <line x1="3" y1="18" x2="21" y2="18" />
            </svg>
          </button>

          {showMenu && (
            <div className="dock-menu-popover animate-scale-in" onClick={(e) => e.stopPropagation()}>
              <button
                type="button"
                className="dock-menu-row"
                onClick={() => {
                  setShowMenu(false);
                  fileInputRef.current?.click();
                }}
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
                </svg>
                <span>Đính kèm tệp / hình ảnh</span>
              </button>
              <button
                type="button"
                className="dock-menu-row"
                onClick={() => {
                  setShowMenu(false);
                  setShowFaqModal(true);
                }}
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10" />
                  <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
                  <line x1="12" y1="17" x2="12.01" y2="17" />
                </svg>
                <span>Xem câu hỏi thường gặp (FAQ)</span>
              </button>
            </div>
          )}
        </div>

        <input
          type="file"
          ref={fileInputRef}
          style={{ display: 'none' }}
          accept="image/*,.pdf,.doc,.docx,.txt"
          onChange={handleFileSelect}
        />

        <input
          type="text"
          className="dock-input"
          placeholder={isUploading ? "Đang tải tệp lên..." : "Nhập tin nhắn phản hồi cho khách..."}
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
          disabled={sending || isUploading}
        />
        <button
          type="submit"
          className="dock-send-btn"
          disabled={(!inputText.trim() && !pendingAttachment) || sending || isUploading}
        >
          Gửi
        </button>
      </form>

      <SupportChatFaqModal
        isOpen={showFaqModal}
        onClose={() => setShowFaqModal(false)}
        onSelectFaq={(faq) => {
          setShowFaqModal(false);
          const answer = faq.answer || faq.a || '';
          setInputText((prev) => (prev.trim() ? `${prev}\n\n${answer}` : answer));
        }}
      />
    </div>
  );

  return createPortal(dockContent, document.body);
};

export default StaffMessengerDock;
