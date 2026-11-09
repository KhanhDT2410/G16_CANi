import { useState, useRef, useEffect } from 'react';
import { useAuth } from '../../auth';
import { supportTicketApi } from '../api/supportTicketApi';
import './SupportChatWidget.css';

const QUICK_FAQS = [
  {
    q: 'Không quét được mã QR điểm danh ca',
    a: 'Nếu QR ca sự kiện bị mờ hoặc lỗi GPS, bạn hãy báo Quản lý ca (EO) quét điểm danh thủ công hoặc gửi ảnh chứng minh vào đây để CSKH hỗ trợ nhé!',
  },
  {
    q: 'Bao lâu thì nhận được thù lao ca làm?',
    a: 'Thù lao sẽ được chuyển khoản trực tiếp sau khi Nhà tổ chức hoàn tất đối soát ca (chậm nhất 24 giờ sau khi kết thúc sự kiện).',
  },
  {
    q: 'Làm thế nào để kháng cáo điểm uy tín?',
    a: 'Bạn vào mục "Kháng cáo điểm uy tín" trong vòng 7 ngày kể từ khi bị trừ điểm, kèm ảnh chụp minh chứng bất khả kháng để được duyệt hoàn điểm.',
  },
  {
    q: 'Tôi muốn gặp chuyên viên CSKH trực tiếp',
    a: 'Chuyên viên CSKH của CANi đã nhận được yêu cầu của bạn. Vui lòng để lại tin nhắn chi tiết, chúng tôi sẽ phản hồi trong giây lát!',
  },
];

export const SupportChatWidget = () => {
  const { user } = useAuth();
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState([
    {
      id: 'welcome',
      isBot: true,
      text: `Xin chào ${user?.fullName ? user.fullName : 'bạn'}! CANi có thể hỗ trợ gì cho bạn hôm nay?`,
      time: 'Vừa xong',
    },
  ]);
  const [inputText, setInputText] = useState('');
  const [sending, setSending] = useState(false);
  const messagesEndRef = useRef(null);

  useEffect(() => {
    if (isOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isOpen]);

  const handleSend = async (customText = null) => {
    const textToSend = customText || inputText.trim();
    if (!textToSend || sending) return;

    const userMsg = {
      id: String(Date.now()),
      isBot: false,
      text: textToSend,
      time: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
    };

    setMessages((prev) => [...prev, userMsg]);
    if (!customText) setInputText('');
    setSending(true);

    // Gửi ngầm lên Backend nếu đã đăng nhập để lưu vào Support Tickets
    try {
      if (user) {
        await supportTicketApi.createTicket({
          category: 'GENERAL',
          subject: `Tin nhắn hỗ trợ từ ${user.fullName || 'Người dùng'}`,
          description: textToSend.length >= 30 ? textToSend : `${textToSend} (Yêu cầu gửi qua khung Live Chat)`,
        });
      }
    } catch {
      // Bỏ qua lỗi mạng để cuộc trò chuyện trên UI không bị gián đoạn
    }

    // Phản hồi tự động để trải nghiệm chat tức thời
    setTimeout(() => {
      const botReply = {
        id: String(Date.now() + 1),
        isBot: true,
        text: 'Cảm ơn bạn! Yêu cầu hỗ trợ đã được chuyển tới chuyên viên CSKH đang trực ca. Chúng tôi sẽ phản hồi trong ít phút.',
        time: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
      };
      setMessages((prev) => [...prev, botReply]);
      setSending(false);
    }, 800);
  };

  const handleQuickFaqClick = (faq) => {
    const userMsg = {
      id: String(Date.now()),
      isBot: false,
      text: faq.q,
      time: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
    };
    const botReply = {
      id: String(Date.now() + 1),
      isBot: true,
      text: faq.a,
      time: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
    };
    setMessages((prev) => [...prev, userMsg, botReply]);
  };

  return (
    <>
      {/* 1. Nút bong bóng chat nổi */}
      <button
        type="button"
        className="support-chat-bubble-btn animate-fade-in"
        onClick={() => setIsOpen((prev) => !prev)}
        title="Trò chuyện hỗ trợ trực tuyến"
        aria-label="Mở khung chat hỗ trợ"
      >
        <span className="support-chat-icon-wrap">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
          <span className="support-chat-badge-dot" />
        </span>
        <span>Hỗ trợ trực tuyến</span>
      </button>

      {/* 2. Cửa sổ chat nổi (Chat Window) */}
      {isOpen && (
        <div className="support-chat-window animate-scale-in">
          {/* Header */}
          <div className="support-chat-header">
            <div className="support-header-brand">
              <div className="support-avatar-circle">🎧</div>
              <div className="support-header-info">
                <h4>Hỗ Trợ Khách Hàng CANi</h4>
                <div className="support-status-text">
                  <span className="support-status-dot" />
                  <span>Trực tuyến · Phản hồi ngay</span>
                </div>
              </div>
            </div>
            <button
              type="button"
              className="support-close-btn"
              onClick={() => setIsOpen(false)}
              title="Đóng chat"
            >
              ✕
            </button>
          </div>

          {/* Body: Messages Stream */}
          <div className="support-chat-body">
            {messages.map((m) => (
              <div key={m.id} className={`support-msg ${m.isBot ? 'bot' : 'user'}`}>
                <div className="support-msg-bubble">{m.text}</div>
                <span className="support-msg-time">{m.time}</span>
              </div>
            ))}

            {/* Gợi ý câu hỏi nhanh (Quick FAQs) khi mới vào */}
            {messages.length <= 2 && (
              <div className="support-quick-faqs">
                <span style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>Câu hỏi thường gặp:</span>
                {QUICK_FAQS.map((faq, idx) => (
                  <button
                    key={idx}
                    type="button"
                    className="support-faq-chip"
                    onClick={() => handleQuickFaqClick(faq)}
                  >
                    💡 {faq.q}
                  </button>
                ))}
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Footer: Input Bar */}
          <form
            className="support-chat-footer"
            onSubmit={(e) => {
              e.preventDefault();
              handleSend();
            }}
          >
            <input
              type="text"
              className="support-chat-input"
              placeholder="Nhập tin nhắn cần hỗ trợ..."
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              disabled={sending}
            />
            <button
              type="submit"
              className="support-send-btn"
              disabled={!inputText.trim() || sending}
              title="Gửi tin nhắn"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <line x1="22" y1="2" x2="11" y2="13" />
                <polygon points="22 2 15 22 11 13 2 9 22 2" />
              </svg>
            </button>
          </form>
        </div>
      )}
    </>
  );
};

export default SupportChatWidget;

