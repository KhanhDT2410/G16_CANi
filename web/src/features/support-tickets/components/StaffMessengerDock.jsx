import { useState, useRef, useEffect } from 'react';

/**
 * Cửa sổ chat Messenger thu nhỏ ở góc dưới màn hình dành cho CSKH / Quản trị viên.
 * Hoạt động song song với bảng Phiếu Hỗ Trợ Khách Hàng.
 * Bo góc 4 góc, header xanh nhạt trong, chữ đen, nút xếp dọc đồng màu xanh đậm hơn background.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const StaffMessengerDock = ({ activeChat, onClose, onOpenTicket }) => {
  const [messages, setMessages] = useState([]);
  const [inputText, setInputText] = useState('');
  const [isMinimized, setIsMinimized] = useState(false);
  const messagesEndRef = useRef(null);

  useEffect(() => {
    if (activeChat) {
      setMessages(activeChat.chatHistory || [
        {
          id: 'welcome-msg',
          sender: 'staff',
          senderName: 'Hệ Thống CSKH',
          text: `Đã kết nối phiên hỗ trợ trực tiếp với ${activeChat.userName || 'khách hàng'}.`,
          time: 'Vừa xong',
        },
      ]);
      setIsMinimized(false);
    }
  }, [activeChat]);

  useEffect(() => {
    if (!isMinimized) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isMinimized]);

  if (!activeChat) return null;

  const handleSendMessage = (e) => {
    e.preventDefault();
    const text = inputText.trim();
    if (!text) return;

    const newMsg = {
      id: String(Date.now()),
      sender: 'staff',
      senderName: 'CSKH CANi',
      text,
      time: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
    };

    setMessages((prev) => [...prev, newMsg]);
    setInputText('');
  };

  if (isMinimized) {
    return (
      <div className="staff-messenger-minimized animate-fade-in">
        <div className="minimized-info">
          <span className="minimized-dot" />
          <span className="minimized-name">{activeChat.userName || 'Khách hàng'}</span>
          <span className="minimized-phone">({activeChat.userPhone || activeChat.referenceNo})</span>
        </div>
        <div className="minimized-actions">
          <button
            type="button"
            className="dock-text-btn"
            onClick={() => setIsMinimized(false)}
            title="Mở rộng khung chat"
          >
            Mở Rộng
          </button>
          <button
            type="button"
            className="dock-text-btn"
            onClick={onClose}
            title="Đóng khung chat"
          >
            Đóng
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="staff-messenger-dock animate-scale-in">
      {/* Header Khung Chat - Màu xanh nhạt trong, chữ đen, nút xếp dọc bên phải */}
      <div className="dock-header">
        <div className="dock-customer-info">
          {/* Tên kèm trực tuyến nhỏ cạnh tên */}
          <div className="dock-customer-name-row">
            <span className="dock-customer-name">{activeChat.userName || 'Khách hàng'}</span>
            <span className="dock-online-badge">Trực tuyến</span>
          </div>

          {/* Email */}
          <div className="dock-customer-field">
            <span className="dock-field-label">Email:</span>
            <span className="dock-field-val">{activeChat.userEmail || '—'}</span>
          </div>

          {/* SĐT */}
          <div className="dock-customer-field">
            <span className="dock-field-label">SĐT:</span>
            <span className="dock-field-val">{activeChat.userPhone || '—'}</span>
          </div>

          {/* Mã phiếu */}
          {activeChat.referenceNo && (
            <div className="dock-customer-field">
              <span className="dock-field-label">Mã phiếu:</span>
              <span className="dock-field-val dock-field-ref">{activeChat.referenceNo}</span>
            </div>
          )}
        </div>

        {/* Các nút xếp dọc sát lề bên phải, đồng màu bo góc */}
        <div className="dock-header-actions-col">
          {onOpenTicket && (
            <button
              type="button"
              className="dock-text-btn"
              onClick={() => onOpenTicket(activeChat)}
              title="Xem thông tin chi tiết phiếu"
            >
              Xem Phiếu
            </button>
          )}
          <button
            type="button"
            className="dock-text-btn"
            onClick={() => setIsMinimized(true)}
            title="Thu nhỏ khung chat xuống góc"
          >
            Thu Nhỏ
          </button>
          <button
            type="button"
            className="dock-text-btn"
            onClick={onClose}
            title="Đóng phiên chat"
          >
            Đóng
          </button>
        </div>
      </div>

      {/* Thân Khung Chat - Dòng tin nhắn */}
      <div className="dock-body">
        {messages.map((m) => {
          const isStaff = m.sender === 'staff';
          return (
            <div key={m.id} className={`dock-message-row ${isStaff ? 'from-staff' : 'from-user'}`}>
              <div className="dock-bubble-sender">{m.senderName}</div>
              <div className="dock-bubble-text">{m.text}</div>
              <div className="dock-bubble-time">{m.time}</div>
            </div>
          );
        })}
        <div ref={messagesEndRef} />
      </div>

      {/* Footer Khung Chat - Nhập tin nhắn phản hồi */}
      <form className="dock-footer" onSubmit={handleSendMessage}>
        <input
          type="text"
          className="dock-input"
          placeholder="Nhập tin nhắn phản hồi cho khách..."
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
        />
        <button
          type="submit"
          className="dock-send-btn"
          disabled={!inputText.trim()}
        >
          Gửi
        </button>
      </form>
    </div>
  );
};

export default StaffMessengerDock;
