import { useState, useEffect, useRef, useCallback } from 'react';
import { useAuth } from '../../../features/auth';
import { supportChatApi } from '../../../features/support-tickets';
import { showToast } from '../../../components/common/Toast';
import './EOSupportChatPanel.css';

const EO_QUICK_PROMPTS = [
  'Cần hỗ trợ đối soát thanh toán lương ca',
  'Cần điều động nhân sự thay thế khẩn cấp',
  'Lỗi quét mã QR điểm danh tại sự kiện',
  'Yêu cầu gặp trực tiếp chuyên viên CSKH',
];

/**
 * Khung Hội Thoại Trực Tuyến CSKH CANi 24/7 dành cho Event Organizer.
 * Kết nối thời gian thực qua polling định kỳ 4s với API /v1/support/chat/my.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export default function EOSupportChatPanel() {
  const { user } = useAuth();
  const [messages, setMessages] = useState([
    {
      id: 'welcome',
      isBot: true,
      senderName: 'CSKH CANi',
      text: `Xin chào ${user?.fullName || 'Ban Tổ Chức'}! Đội ngũ Chăm Sóc Khách Hàng CANi đang trực tuyến. Chúng tôi có thể hỗ trợ gì cho sự kiện và ca làm việc của bạn hôm nay?`,
      time: 'Vừa xong',
    },
  ]);
  const [inputText, setInputText] = useState('');
  const [sending, setSending] = useState(false);
  const [loading, setLoading] = useState(true);
  const scrollContainerRef = useRef(null);
  const isAtBottomRef = useRef(true);
  const prevMsgLengthRef = useRef(1);

  // Cuộn cục bộ trong container của hộp chat (không scroll cả trang window)
  const scrollToBottom = useCallback((behavior = 'smooth') => {
    const el = scrollContainerRef.current;
    if (!el) return;
    if (behavior === 'auto') {
      el.scrollTop = el.scrollHeight;
    } else {
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
    }
  }, []);

  const handleScroll = () => {
    const el = scrollContainerRef.current;
    if (!el) return;
    isAtBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight <= 80;
  };

  const loadConversation = useCallback(async () => {
    try {
      const res = await supportChatApi.getMyConversation();
      if (!res?.messages) return;

      const formatted = res.messages.map((m) => {
        const isStaff = m.senderRole === 'CUSTOMER_CARE' || m.senderRole === 'ADMIN';
        return {
          id: m.id,
          isBot: isStaff,
          senderName: isStaff ? (m.senderName || 'CSKH CANi') : 'Tôi (Ban Tổ Chức)',
          shiftCode: m.shiftCode,
          shiftName: m.shiftName,
          text: m.messageText,
          time: m.createdAt
            ? new Date(m.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
            : '',
        };
      });

      if (formatted.length > 0) {
        setMessages((prev) => {
          // Tránh cập nhật state khi danh sách tin nhắn không có gì thay đổi
          if (
            prev.length === formatted.length &&
            prev[prev.length - 1]?.id === formatted[formatted.length - 1]?.id &&
            prev[prev.length - 1]?.text === formatted[formatted.length - 1]?.text
          ) {
            return prev;
          }
          return formatted;
        });
      }
    } catch {
      // Bỏ qua lỗi polling nền
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadConversation();
    const interval = setInterval(loadConversation, 4000);
    return () => clearInterval(interval);
  }, [loadConversation]);

  // Chỉ tự động cuộn khi có tin nhắn MỚI và người dùng đang ở đáy hộp chat
  useEffect(() => {
    if (messages.length > prevMsgLengthRef.current) {
      if (isAtBottomRef.current) {
        scrollToBottom('smooth');
      }
      prevMsgLengthRef.current = messages.length;
    }
  }, [messages, scrollToBottom]);

  // Cuộn tức thì khi tải xong lần đầu
  useEffect(() => {
    if (!loading && messages.length > 0) {
      scrollToBottom('auto');
    }
  }, [loading, scrollToBottom]);

  const handleSendMessage = async (textToSend) => {
    const text = (textToSend || inputText).trim();
    if (!text || sending) return;

    setSending(true);
    if (!textToSend) setInputText('');

    try {
      await supportChatApi.sendUserMessage({ messageText: text });
      await loadConversation();
      isAtBottomRef.current = true;
      scrollToBottom('smooth');
    } catch (err) {
      showToast('Không thể gửi tin nhắn: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setSending(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const handleOpenFloating = () => {
    window.dispatchEvent(new CustomEvent('cani_open_chat_shift', { detail: { shiftId: null } }));
    showToast('Đã mở bong bóng chat nổi ở góc màn hình!', 'info');
  };

  return (
    <div className="eo-chat-panel-container animate-fade-in-up">
      {/* Header */}
      <div className="eo-chat-panel-header">
        <div className="eo-chat-header-info">
          <div className="eo-chat-avatar-badge">
            🎧
            <span className="eo-chat-online-dot" />
          </div>
          <div className="eo-chat-header-text">
            <h4>Đội Ngũ Hỗ Trợ Khách Hàng CANi 24/7</h4>
            <p>
              <span className="eo-chat-status-pill" />
              Đang trực tuyến • Phản hồi trung bình &lt; 5 phút
            </p>
          </div>
        </div>

        <div className="eo-chat-header-actions">
          <button type="button" className="eo-chat-action-btn" onClick={handleOpenFloating} title="Mở chat dạng bong bóng nổi để vừa chat vừa duyệt ca">
            <span>↗</span> Mở cửa sổ nổi
          </button>
          <button type="button" className="eo-chat-action-btn" onClick={loadConversation} title="Tải lại tin nhắn">
            ↻ Làm mới
          </button>
        </div>
      </div>

      {/* Message history */}
      <div className="eo-chat-messages-scroll" ref={scrollContainerRef} onScroll={handleScroll}>
        <div className="eo-chat-date-divider">
          <span>Hôm nay</span>
        </div>

        {loading && messages.length <= 1 ? (
          <div style={{ textAlign: 'center', color: '#94a3b8', padding: '2rem', fontSize: '0.85rem' }}>
            Đang tải dữ liệu hội thoại...
          </div>
        ) : (
          messages.map((msg) => (
            <div key={msg.id} className={`eo-chat-row ${msg.isBot ? 'staff' : 'user'}`}>
              <div className="eo-chat-bubble-wrap">
                <span className="eo-chat-sender-name">{msg.senderName}</span>
                <div className="eo-chat-bubble">
                  {msg.shiftCode && (
                    <div className="eo-chat-shift-tag">
                      Ca: {msg.shiftCode} {msg.shiftName ? `• ${msg.shiftName}` : ''}
                    </div>
                  )}
                  {msg.text}
                </div>
                <span className="eo-chat-msg-time">{msg.time}</span>
              </div>
            </div>
          ))
        )}
      </div>

      {/* Quick Prompts */}
      <div className="eo-chat-quick-prompts">
        {EO_QUICK_PROMPTS.map((prompt) => (
          <button
            key={prompt}
            type="button"
            className="eo-chat-prompt-chip"
            onClick={() => handleSendMessage(prompt)}
            disabled={sending}
          >
            📌 {prompt}
          </button>
        ))}
      </div>

      {/* Input bar */}
      <div className="eo-chat-input-bar">
        <input
          type="text"
          className="eo-chat-input-field"
          placeholder="Nhập nội dung cần hỗ trợ với CSKH (nhấn Enter để gửi)..."
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={sending}
        />
        <button
          type="button"
          className="eo-chat-send-btn"
          onClick={() => handleSendMessage()}
          disabled={sending || !inputText.trim()}
        >
          {sending ? 'Đang gửi...' : 'Gửi tin nhắn ✈'}
        </button>
      </div>
    </div>
  );
}
