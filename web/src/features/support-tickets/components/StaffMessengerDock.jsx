import { useState, useRef, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { supportChatApi } from '../api/supportChatApi';

/**
 * Cửa sổ chat Messenger thu nhỏ ở góc dưới màn hình dành cho CSKH / Quản trị viên.
 * Luôn ghim cố định ở góc dưới màn hình qua createPortal, không làm cuộn nhảy trang.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const StaffMessengerDock = ({ activeChat, onClose, onOpenTicket }) => {
  const [messages, setMessages] = useState([]);
  const [inputText, setInputText] = useState('');
  const [isMinimized, setIsMinimized] = useState(false);
  const [convId, setConvId] = useState(null);
  const dockBodyRef = useRef(null);

  const formatMessage = (m) => ({
    id: m.id,
    sender: m.senderRole === 'CUSTOMER_CARE' || m.senderRole === 'ADMIN' ? 'staff' : 'user',
    senderName: m.senderName,
    text: m.messageText,
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
          if (isMounted && res?.messages) setMessages(res.messages.map(formatMessage));
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

  // Tự động cuộn xuống cuối bên trong khung tin nhắn (không cuộn trang web cha)
  useEffect(() => {
    if (!isMinimized && dockBodyRef.current) {
      dockBodyRef.current.scrollTop = dockBodyRef.current.scrollHeight;
    }
  }, [messages, isMinimized]);

  if (!activeChat || typeof document === 'undefined') return null;

  const handleSendMessage = async (e) => {
    e.preventDefault();
    const text = inputText.trim();
    if (!text) return;

    setMessages((prev) => [
      ...prev,
      {
        id: String(Date.now()),
        sender: 'staff',
        senderName: 'CSKH CANi',
        text,
        time: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
      },
    ]);
    setInputText('');

    if (convId) {
      try {
        await supportChatApi.sendStaffReply(convId, { messageText: text });
      } catch (err) {
        console.error('Lỗi khi gửi phản hồi:', err);
      }
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
            <span className="dock-field-label">Email:</span>
            <span className="dock-field-val">{activeChat.userEmail || '—'}</span>
          </div>
          <div className="dock-customer-field">
            <span className="dock-field-label">SĐT:</span>
            <span className="dock-field-val">{activeChat.userPhone || '—'}</span>
          </div>
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
              <div className="dock-bubble-sender">{m.senderName}</div>
              <div className="dock-bubble-text">{m.text}</div>
              <div className="dock-bubble-time">{m.time}</div>
            </div>
          ))
        )}
      </div>

      <form className="dock-footer" onSubmit={handleSendMessage}>
        <input
          type="text"
          className="dock-input"
          placeholder="Nhập tin nhắn phản hồi cho khách..."
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
        />
        <button type="submit" className="dock-send-btn" disabled={!inputText.trim()}>
          Gửi
        </button>
      </form>
    </div>
  );

  return createPortal(dockContent, document.body);
};

export default StaffMessengerDock;
