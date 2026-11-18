import { useState, useRef, useEffect, useCallback } from 'react';
import { useAuth } from '../../auth';
import { supportChatApi } from '../api/supportChatApi';
import './SupportChatWidget.css';

const QUICK_FAQS = [
  { q: 'Không quét được mã QR điểm danh ca', a: 'Nếu QR ca sự kiện bị mờ hoặc lỗi GPS, bạn hãy báo Quản lý ca (EO) quét điểm danh thủ công hoặc gửi ảnh chứng minh vào đây để CSKH hỗ trợ nhé!' },
  { q: 'Bao lâu thì nhận được thù lao ca làm?', a: 'Thù lao sẽ được chuyển khoản trực tiếp sau khi Nhà tổ chức hoàn tất đối soát ca (chậm nhất 24 giờ sau khi kết thúc sự kiện).' },
  { q: 'Làm thế nào để kháng cáo điểm uy tín?', a: 'Bạn vào mục "Kháng cáo điểm uy tín" trong vòng 7 ngày kể từ khi bị trừ điểm, kèm ảnh chụp minh chứng bất khả kháng để được duyệt hoàn điểm.' },
  { q: 'Tôi muốn gặp chuyên viên CSKH trực tiếp', a: 'Chuyên viên CSKH của CANi đã nhận được yêu cầu của bạn. Vui lòng để lại tin nhắn chi tiết, chúng tôi sẽ phản hồi trong giây lát!' },
];

const STORAGE_KEY_POS = 'cani_support_chat_widget_pos';
const DRAG_THRESHOLD = 6; // pixels di chuyển tối thiểu để nhận diện là Drag thay vì Click

export const SupportChatWidget = () => {
  const { user } = useAuth();
  const isStaff = user?.role === 'ADMIN' || user?.role === 'CUSTOMER_CARE';

  // Trạng thái hiển thị 3 cấp độ:
  // 1. isOpen = false: Bong bóng chat nhỏ (FAB)
  // 2. isOpen = true, isMinimized = true: Thanh nổi thu gọn (Dock Pill)
  // 3. isOpen = true, isMinimized = false: Cửa sổ chat đầy đủ (Expanded Window)
  const [isOpen, setIsOpen] = useState(false);
  const [isMinimized, setIsMinimized] = useState(false);

  const [messages, setMessages] = useState([
    { id: 'welcome', isBot: true, text: `Xin chào ${user?.fullName || 'bạn'}! CANi có thể hỗ trợ gì cho bạn hôm nay?`, time: 'Vừa xong' },
  ]);
  const [inputText, setInputText] = useState('');
  const [sending, setSending] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [activeShiftContext, setActiveShiftContext] = useState(null);

  useEffect(() => {
    const handleOpenWithShift = (e) => {
      if (e.detail?.shiftId) {
        setActiveShiftContext(e.detail);
        setIsOpen(true);
        setIsMinimized(false);
      }
    };
    window.addEventListener('cani_open_chat_shift', handleOpenWithShift);
    return () => window.removeEventListener('cani_open_chat_shift', handleOpenWithShift);
  }, []);

  // Toạ độ kéo thả tự do
  const [position, setPosition] = useState(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY_POS);
      if (saved) return JSON.parse(saved);
    } catch { /* ignore */ }
    return null; // null = dùng toạ độ CSS mặc định (góc dưới bên phải)
  });

  const messagesEndRef = useRef(null);
  const dragRef = useRef({
    isDragging: false,
    startX: 0,
    startY: 0,
    elemStartX: 0,
    elemStartY: 0,
    hasMoved: false,
  });

  // Clamp toạ độ an toàn trong khung hình
  const clampPosition = useCallback((x, y, width = 380, height = 520) => {
    const maxX = Math.max(16, window.innerWidth - width - 16);
    const maxY = Math.max(16, window.innerHeight - height - 16);
    return {
      x: Math.min(Math.max(16, x), maxX),
      y: Math.min(Math.max(16, y), maxY),
    };
  }, []);

  // Tự động điều chỉnh toạ độ khi người dùng thay đổi kích thước cửa sổ trình duyệt
  useEffect(() => {
    const handleResize = () => {
      setPosition((prev) => {
        if (!prev) return null;
        return clampPosition(prev.x, prev.y, isMinimized ? 260 : 380, isMinimized ? 48 : 520);
      });
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [clampPosition, isMinimized]);

  // Đồng bộ tin nhắn hội thoại và Polling cập nhật tin nhắn mới mỗi 4s khi đang mở chat
  useEffect(() => {
    let isMounted = true;
    if (!user || isStaff || !isOpen) return;

    const fetchMessages = async () => {
      try {
        const res = await supportChatApi.getMyConversation();
        if (!isMounted || !res?.messages) return;

        setMessages((prev) => {
          const apiMessages = res.messages.map((m) => ({
            id: m.id,
            isBot: m.senderRole === 'CUSTOMER_CARE' || m.senderRole === 'ADMIN',
            text: m.messageText,
            time: new Date(m.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
          }));

          // Nếu có tin nhắn mới từ CSKH khi đang thu nhỏ, tăng badge thông báo
          if (isMinimized && apiMessages.length > prev.length) {
            setUnreadCount((c) => c + (apiMessages.length - prev.length));
          }
          return apiMessages.length > 0 ? apiMessages : prev;
        });
      } catch { /* Bỏ qua lỗi mạng nền */ }
    };

    fetchMessages();
    const interval = setInterval(fetchMessages, 4000);
    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, [user, isStaff, isOpen, isMinimized]);

  // Tự động cuộn xuống tin nhắn mới nhất khi đang mở toàn phần
  useEffect(() => {
    if (isOpen && !isMinimized) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isOpen, isMinimized]);

  // Quản lý kéo thả thông qua Pointer Events chuẩn công nghiệp
  const startDrag = (e, currentWidth, currentHeight) => {
    // Không kích hoạt kéo thả nếu người dùng click vào nút con (close, minimize, input, button)
    if (e.target.closest('button, input, textarea, a, .support-faq-chip')) return;

    e.preventDefault();
    const clientX = e.clientX;
    const clientY = e.clientY;

    let initX = position?.x;
    let initY = position?.y;

    if (initX == null || initY == null) {
      // Tính toạ độ hiện tại trên màn hình nếu đang dùng vị trí CSS mặc định
      const rect = e.currentTarget.getBoundingClientRect();
      initX = rect.left;
      initY = rect.top;
    }

    dragRef.current = {
      isDragging: true,
      startX: clientX,
      startY: clientY,
      elemStartX: initX,
      elemStartY: initY,
      hasMoved: false,
    };

    const onPointerMove = (moveEvent) => {
      if (!dragRef.current.isDragging) return;
      const dx = moveEvent.clientX - dragRef.current.startX;
      const dy = moveEvent.clientY - dragRef.current.startY;

      if (!dragRef.current.hasMoved && Math.hypot(dx, dy) >= DRAG_THRESHOLD) {
        dragRef.current.hasMoved = true;
      }

      if (dragRef.current.hasMoved) {
        const nextX = dragRef.current.elemStartX + dx;
        const nextY = dragRef.current.elemStartY + dy;
        const clamped = clampPosition(nextX, nextY, currentWidth, currentHeight);
        setPosition(clamped);
      }
    };

    const onPointerUp = () => {
      if (!dragRef.current.isDragging) return;
      dragRef.current.isDragging = false;
      window.removeEventListener('pointermove', onPointerMove);
      window.removeEventListener('pointerup', onPointerUp);

      if (dragRef.current.hasMoved) {
        setPosition((currentPos) => {
          if (currentPos) {
            try {
              localStorage.setItem(STORAGE_KEY_POS, JSON.stringify(currentPos));
            } catch { /* ignore */ }
          }
          return currentPos;
        });
      }
    };

    window.addEventListener('pointermove', onPointerMove);
    window.addEventListener('pointerup', onPointerUp);
  };

  if (isStaff) return null;

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

    try {
      if (user) {
        await supportChatApi.sendUserMessage({
          messageText: textToSend,
          shiftId: activeShiftContext?.shiftId || null,
        });
      }
    } catch { /* Bỏ qua lỗi mạng */ } finally {
      setSending(false);
    }
  };

  const handleQuickFaqClick = (faq) => {
    const now = new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
    setMessages((prev) => [
      ...prev,
      { id: String(Date.now()), isBot: false, text: faq.q, time: now },
      { id: String(Date.now() + 1), isBot: true, text: faq.a, time: now },
    ]);
  };

  // Tính style toạ độ tuỳ biến nếu đã kéo thả
  const positionStyle = position ? {
    position: 'fixed',
    left: `${position.x}px`,
    top: `${position.y}px`,
    right: 'auto',
    bottom: 'auto',
  } : {};

  return (
    <>
      {/* TRƯỜNG HỢP 1: BONG BÓNG CHAT NỔI (KHI ĐANG ĐÓNG) */}
      {!isOpen && (
        <button
          type="button"
          className="support-chat-bubble-btn animate-fade-in draggable-support-element"
          style={positionStyle}
          onPointerDown={(e) => startDrag(e, 180, 50)}
          onClick={(e) => {
            if (dragRef.current.hasMoved) {
              e.stopPropagation();
              return;
            }
            setIsOpen(true);
            setIsMinimized(false);
            setUnreadCount(0);
          }}
          title="Nhấn giữ để di chuyển · Nhấp để mở khung chat"
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
      )}

      {/* TRƯỜNG HỢP 2: THANH NỔI THU GỌN (MINIMIZED PILL DOCK) */}
      {isOpen && isMinimized && (
        <div
          className="support-chat-minimized-pill animate-scale-in draggable-support-element"
          style={positionStyle}
          onPointerDown={(e) => startDrag(e, 260, 48)}
          title="Nhấn giữ để di chuyển thanh hỗ trợ"
        >
          <div
            className="minimized-pill-body"
            onClick={(e) => {
              if (dragRef.current.hasMoved) {
                e.stopPropagation();
                return;
              }
              setIsMinimized(false);
              setUnreadCount(0);
            }}
          >
            <span className="support-status-dot" />
            <span className="minimized-pill-title">Hỗ Trợ CANi</span>
            {unreadCount > 0 && (
              <span className="minimized-pill-unread-badge">+{unreadCount}</span>
            )}
          </div>
          <div className="minimized-pill-actions">
            <button
              type="button"
              className="support-header-icon-btn"
              onClick={() => {
                setIsMinimized(false);
                setUnreadCount(0);
              }}
              title="Phóng to cửa sổ chat"
            >
              🗖
            </button>
            <button
              type="button"
              className="support-header-icon-btn"
              onClick={() => {
                setIsOpen(false);
                setIsMinimized(false);
              }}
              title="Đóng hẳn khung chat"
            >
              ✕
            </button>
          </div>
        </div>
      )}

      {/* TRƯỜNG HỢP 3: CỬA SỔ CHAT MỞ RỘNG TOÀN PHẦN (EXPANDED WINDOW) */}
      {isOpen && !isMinimized && (
        <div
          className="support-chat-window animate-scale-in"
          style={positionStyle}
        >
          {/* Thanh Header đóng vai trò tay cầm kéo thả (Drag Handle) */}
          <div
            className="support-chat-header draggable-header"
            onPointerDown={(e) => startDrag(e, 380, 520)}
            title="Nhấn giữ thanh tiêu đề để di chuyển cửa sổ chat"
          >
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

            <div className="support-header-actions">
              <button
                type="button"
                className="support-header-icon-btn"
                onClick={() => setIsMinimized(true)}
                title="Thu nhỏ khung chat xuống thanh nổi"
              >
                —
              </button>
              <button
                type="button"
                className="support-header-icon-btn"
                onClick={() => setIsOpen(false)}
                title="Đóng chat"
              >
                ✕
              </button>
            </div>
          </div>

          <div className="support-chat-body">
            {messages.map((m) => (
              <div key={m.id} className={`support-msg ${m.isBot ? 'bot' : 'user'}`}>
                <div className="support-msg-bubble">{m.text}</div>
                <span className="support-msg-time">{m.time}</span>
              </div>
            ))}

            {messages.length <= 2 && (
              <div className="support-quick-faqs">
                <span style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>Câu hỏi thường gặp:</span>
                {QUICK_FAQS.map((faq, idx) => (
                  <button key={idx} type="button" className="support-faq-chip" onClick={() => handleQuickFaqClick(faq)}>
                    💡 {faq.q}
                  </button>
                ))}
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {activeShiftContext && (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '4px 10px', background: '#eff6ff', borderTop: '1px solid #bfdbfe', fontSize: '0.74rem', color: '#1d4ed8' }}>
              <span>🏷️ Đang trao đổi về ca: <strong>{activeShiftContext.shiftCode || activeShiftContext.shiftName || 'Ca làm việc'}</strong></span>
              <button
                type="button"
                onClick={() => setActiveShiftContext(null)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#64748b', fontWeight: 'bold', fontSize: '0.85rem' }}
                title="Hủy gắn ca làm (chuyển sang chat tự do)"
              >
                ✕
              </button>
            </div>
          )}

          <form className="support-chat-footer" onSubmit={(e) => { e.preventDefault(); handleSend(); }}>
            <input
              type="text"
              className="support-chat-input"
              placeholder="Nhập tin nhắn cần hỗ trợ..."
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              disabled={sending}
            />
            <button type="submit" className="support-send-btn" disabled={!inputText.trim() || sending} title="Gửi tin nhắn">
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
