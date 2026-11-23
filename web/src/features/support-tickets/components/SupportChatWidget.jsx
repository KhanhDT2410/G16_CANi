import { useState, useRef, useEffect, useCallback } from 'react';
import { useAuth } from '../../auth';
import { supportChatApi } from '../api/supportChatApi';
import { showToast } from '../../../components/common/Toast';
import SupportChatFaqModal from './SupportChatFaqModal';
import './SupportChatWidget.css';

const QUICK_FAQS = [
  { q: 'Không quét được mã QR điểm danh ca', a: 'Nếu QR ca sự kiện bị mờ hoặc lỗi GPS, bạn hãy báo Quản lý ca quét điểm danh thủ công hoặc gửi ảnh chứng minh vào đây để CSKH hỗ trợ nhé!' },
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

  // Tùy chọn menu (Đính kèm tệp / Bật FAQ) & tệp đang chờ gửi
  const [showMenu, setShowMenu] = useState(false);
  const [showFaqModal, setShowFaqModal] = useState(false);
  const [pendingAttachment, setPendingAttachment] = useState(null);
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef(null);
  const localFaqMessagesRef = useRef([]);

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

  const chatBodyRef = useRef(null);
  const isAtBottomRef = useRef(true);
  const prevMsgLengthRef = useRef(0);

  const scrollToBottom = useCallback((smooth = true) => {
    const el = chatBodyRef.current;
    if (!el) return;
    if (smooth) {
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
    } else {
      el.scrollTop = el.scrollHeight;
    }
  }, []);

  const handleChatScroll = () => {
    const el = chatBodyRef.current;
    if (!el) return;
    isAtBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight <= 60;
  };
  const justDraggedRef = useRef(false);
  const dragRef = useRef({
    isDragging: false,
    startX: 0,
    startY: 0,
    elemStartX: 0,
    elemStartY: 0,
    width: 185,
    height: 48,
    hasMoved: false,
  });

  // Tính kích thước dự kiến của widget theo chế độ hiển thị & viewport
  const getActiveBounds = useCallback(() => {
    const vw = typeof window !== 'undefined' ? window.innerWidth : 1280;
    const vh = typeof window !== 'undefined' ? window.innerHeight : 800;
    if (!isOpen) {
      return { width: vw <= 640 ? 156 : 185, height: 48 };
    }
    if (isMinimized) {
      return { width: Math.min(260, Math.max(180, vw - 24)), height: 48 };
    }
    return {
      width: Math.min(375, Math.max(280, vw - 24)),
      height: Math.min(520, Math.max(360, vh - 32)),
    };
  }, [isOpen, isMinimized]);

  // Clamp toạ độ an toàn trong khung hình
  const clampPosition = useCallback((x, y, width = 375, height = 520) => {
    const margin = window.innerWidth <= 640 ? 12 : 16;
    const maxX = Math.max(margin, window.innerWidth - width - margin);
    const maxY = Math.max(margin, window.innerHeight - height - margin);
    return {
      x: Math.min(Math.max(margin, x), maxX),
      y: Math.min(Math.max(margin, y), maxY),
    };
  }, []);

  // Tự động điều chỉnh toạ độ khi người dùng thay đổi kích thước cửa sổ trình duyệt
  useEffect(() => {
    const handleResize = () => {
      const { width, height } = getActiveBounds();
      setPosition((prev) => {
        if (!prev) return null;
        return clampPosition(prev.x, prev.y, width, height);
      });
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [clampPosition, getActiveBounds]);

  // Đồng bộ tin nhắn hội thoại và Polling cập nhật tin nhắn mới mỗi 4s khi đang mở chat
  useEffect(() => {
    let isMounted = true;
    if (!user || isStaff || !isOpen) return;

    const fetchMessages = async () => {
      try {
        const res = await supportChatApi.getMyConversation();
        if (!isMounted || !res?.messages) return;

        setMessages((prev) => {
          // Chuẩn hóa kênh Hỗ trợ: Chỉ lấy tin nhắn của chính người dùng và phản hồi từ CSKH / Admin
          const supportOnlyMessages = (res.messages || []).filter((m) => {
            const isFromStaff = m.senderRole === 'CUSTOMER_CARE' || m.senderRole === 'ADMIN';
            const isFromMe = (user?.userId && m.senderId && String(m.senderId) === String(user.userId)) ||
              (m.senderRole && user?.role && m.senderRole === user.role && m.senderRole !== 'WORKER');
            return isFromStaff || isFromMe;
          });

          const apiMessages = supportOnlyMessages.map((m) => {
            const isFromStaff = m.senderRole === 'CUSTOMER_CARE' || m.senderRole === 'ADMIN';
            return {
              id: m.id,
              isBot: isFromStaff,
              senderLabel: isFromStaff ? (m.senderName || 'CSKH CANi') : null,
              shiftCode: m.shiftCode,
              text: m.messageText,
              attachmentUrl: m.attachmentUrl,
              time: new Date(m.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
            };
          });

          // Giữ lại các tin nhắn FAQ cục bộ (không bao giờ bị xóa khi polling)
          const localMessages = localFaqMessagesRef.current || [];
          const combined = [...apiMessages, ...localMessages];

          // Tránh set lại state gây re-render khi tin nhắn không đổi
          if (
            prev.length === combined.length &&
            prev[prev.length - 1]?.id === combined[combined.length - 1]?.id &&
            prev[prev.length - 1]?.text === combined[combined.length - 1]?.text
          ) {
            return prev;
          }

          // Nếu có tin nhắn mới từ CSKH khi đang thu nhỏ, tăng badge thông báo
          if (isMinimized && apiMessages.length > prev.filter((p) => !p.isLocal).length) {
            setUnreadCount((c) => c + (apiMessages.length - prev.filter((p) => !p.isLocal).length));
          }
          return combined.length > 0 ? combined : prev;
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

  // Tự động cuộn cục bộ trong khung chat khi có tin nhắn mới hoặc vừa mở
  useEffect(() => {
    if (isOpen && !isMinimized) {
      if (messages.length > prevMsgLengthRef.current) {
        if (isAtBottomRef.current || prevMsgLengthRef.current <= 2) {
          scrollToBottom(prevMsgLengthRef.current > 2);
        }
        prevMsgLengthRef.current = messages.length;
      }
    }
  }, [messages, isOpen, isMinimized, scrollToBottom]);

  useEffect(() => {
    if (isOpen && !isMinimized) {
      isAtBottomRef.current = true;
      setTimeout(() => scrollToBottom(false), 50);
    }
  }, [isOpen, isMinimized, scrollToBottom]);

  // Quản lý kéo thả thông qua Pointer Events (áp dụng cho cả bong bóng đóng, dock thu gọn và header mở rộng)
  const startDrag = (e, fallbackWidth = 185, fallbackHeight = 48) => {
    if (e.pointerType === 'mouse' && e.button !== 0) return;

    // Bỏ qua các phần tử tương tác con bên trong (ví dụ nút thu nhỏ/đóng trong pill hoặc header),
    // nhưng vẫn cho phép kéo trực tiếp chính nút bong bóng khi đóng (e.currentTarget).
    const interactive = e.target.closest('button, input, textarea, a, .support-faq-chip');
    if (interactive && interactive !== e.currentTarget) return;

    const rect = e.currentTarget.getBoundingClientRect();
    const dragWidth = rect.width || fallbackWidth;
    const dragHeight = rect.height || fallbackHeight;

    const currentClamped = position
      ? clampPosition(position.x, position.y, dragWidth, dragHeight)
      : { x: rect.left, y: rect.top };

    dragRef.current = {
      isDragging: true,
      startX: e.clientX,
      startY: e.clientY,
      elemStartX: currentClamped.x,
      elemStartY: currentClamped.y,
      width: dragWidth,
      height: dragHeight,
      hasMoved: false,
    };
    justDraggedRef.current = false;

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
        const clamped = clampPosition(nextX, nextY, dragRef.current.width, dragRef.current.height);
        setPosition(clamped);
      }
    };

    const onPointerEnd = () => {
      if (!dragRef.current.isDragging) return;
      dragRef.current.isDragging = false;
      window.removeEventListener('pointermove', onPointerMove);
      window.removeEventListener('pointerup', onPointerEnd);
      window.removeEventListener('pointercancel', onPointerEnd);

      if (dragRef.current.hasMoved) {
        justDraggedRef.current = true;
        setTimeout(() => {
          justDraggedRef.current = false;
        }, 250);
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
    window.addEventListener('pointerup', onPointerEnd);
    window.addEventListener('pointercancel', onPointerEnd);
  };

  if (isStaff) return null;

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
        showToast('Tải tệp thành công! Nhấn Gửi để chuyển tin nhắn.', 'success');
      }
    } catch (err) {
      showToast(err.response?.data?.message || 'Không thể tải tệp lên máy chủ', 'error');
    } finally {
      setIsUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleSend = async (customText = null) => {
    const textToSend = customText || inputText.trim();
    if ((!textToSend && !pendingAttachment) || sending || isUploading) return;

    const attachmentToSend = pendingAttachment;
    const finalMsgText = textToSend || (attachmentToSend ? `Đã gửi tệp đính kèm: ${attachmentToSend.name}` : '');

    const userMsg = {
      id: `local-${Date.now()}`,
      isBot: false,
      text: finalMsgText,
      attachmentUrl: attachmentToSend?.url || null,
      time: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
    };

    setMessages((prev) => [...prev, userMsg]);
    if (!customText) setInputText('');
    setPendingAttachment(null);
    setSending(true);
    isAtBottomRef.current = true;
    setTimeout(() => scrollToBottom(true), 50);

    try {
      if (user) {
        await supportChatApi.sendUserMessage({
          messageText: finalMsgText,
          attachmentUrl: attachmentToSend?.url || null,
          shiftId: activeShiftContext?.shiftId || null,
        });
      }
    } catch { /* Bỏ qua lỗi mạng */ } finally {
      setSending(false);
    }
  };

  const handleQuickFaqClick = (faq) => {
    const now = new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
    const questionText = faq.q || faq.question;
    const answerText = faq.a || faq.answer;

    const qMsg = {
      id: `local-faq-q-${Date.now()}`,
      isLocal: true,
      isBot: false,
      text: questionText,
      time: now,
    };
    const aMsg = {
      id: `local-faq-a-${Date.now() + 1}`,
      isLocal: true,
      isBot: true,
      senderLabel: 'Trợ lý FAQ CANi',
      text: answerText,
      time: now,
    };

    localFaqMessagesRef.current = [...localFaqMessagesRef.current, qMsg, aMsg];
    setMessages((prev) => [...prev, qMsg, aMsg]);
    setShowFaqModal(false);
    setShowMenu(false);
    isAtBottomRef.current = true;
    setTimeout(() => scrollToBottom(true), 50);
  };

  // Tính style toạ độ tuỳ biến nếu đã kéo thả, luôn clamp theo kích thước chế độ hiện tại
  const activeBounds = getActiveBounds();
  const displayPos = position
    ? clampPosition(position.x, position.y, activeBounds.width, activeBounds.height)
    : null;

  const positionStyle = displayPos ? {
    position: 'fixed',
    left: `${displayPos.x}px`,
    top: `${displayPos.y}px`,
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
          onPointerDown={(e) => startDrag(e, 185, 48)}
          onClick={(e) => {
            if (dragRef.current.hasMoved || justDraggedRef.current) {
              e.preventDefault();
              e.stopPropagation();
              dragRef.current.hasMoved = false;
              justDraggedRef.current = false;
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
              if (dragRef.current.hasMoved || justDraggedRef.current) {
                e.preventDefault();
                e.stopPropagation();
                dragRef.current.hasMoved = false;
                justDraggedRef.current = false;
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
              aria-label="Phóng to"
            >
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="15 3 21 3 21 9" />
                <polyline points="9 21 3 21 3 15" />
                <line x1="21" y1="3" x2="14" y2="10" />
                <line x1="3" y1="21" x2="10" y2="14" />
              </svg>
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
              <div className="support-avatar-circle">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                </svg>
              </div>
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

          <div className="support-chat-body" ref={chatBodyRef} onScroll={handleChatScroll}>
            {messages.map((m) => (
              <div key={m.id} className={`support-msg ${m.isBot ? 'bot' : 'user'}`}>
                {(m.senderLabel || m.shiftCode) && (
                  <div style={{ fontSize: '0.68rem', fontWeight: 700, color: '#64748b', marginBottom: 2, display: 'flex', alignItems: 'center', gap: 5 }}>
                    {m.senderLabel && <span>{m.senderLabel}</span>}
                    {m.shiftCode && (
                      <span style={{ background: '#e0f2fe', color: '#0369a1', padding: '1px 5px', borderRadius: 4, fontSize: '0.62rem' }}>
                        {m.shiftCode}
                      </span>
                    )}
                  </div>
                )}
                <div className="support-msg-bubble">{m.text}</div>
                {m.attachmentUrl && (
                  <div className="support-msg-attachment-wrap">
                    {/\.(png|jpe?g|webp|gif)$/i.test(m.attachmentUrl) ? (
                      <a href={m.attachmentUrl} target="_blank" rel="noopener noreferrer">
                        <img src={m.attachmentUrl} alt="Đính kèm" className="support-attachment-thumb" />
                      </a>
                    ) : (
                      <a href={m.attachmentUrl} target="_blank" rel="noopener noreferrer" className="support-attachment-doc">
                        Xem tệp đính kèm ↗
                      </a>
                    )}
                  </div>
                )}
                <span className="support-msg-time">{m.time}</span>
              </div>
            ))}

            {messages.length <= 2 && (
              <div className="support-quick-faqs">
                <span style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>Câu hỏi thường gặp:</span>
                {QUICK_FAQS.map((faq, idx) => (
                  <button key={idx} type="button" className="support-faq-chip" onClick={() => handleQuickFaqClick(faq)}>
                    {faq.q}
                  </button>
                ))}
              </div>
            )}
          </div>

          {activeShiftContext && (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '4px 10px', background: '#eff6ff', borderTop: '1px solid #bfdbfe', fontSize: '0.74rem', color: '#1d4ed8' }}>
              <span>Đang trao đổi về ca: <strong>{activeShiftContext.shiftCode || activeShiftContext.shiftName || 'Ca làm việc'}</strong></span>
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

          {pendingAttachment && (
            <div className="support-pending-attachment-bar">
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

          <form className="support-chat-footer" onSubmit={(e) => { e.preventDefault(); handleSend(); }}>
            <div className="support-chat-menu-container">
              <button
                type="button"
                className="support-chat-menu-btn"
                onClick={() => setShowMenu((prev) => !prev)}
                title="Tác vụ hỗ trợ (Đính kèm tệp / Bật FAQ)"
                aria-label="Menu hỗ trợ"
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <line x1="3" y1="6" x2="21" y2="6" />
                  <line x1="3" y1="12" x2="21" y2="12" />
                  <line x1="3" y1="18" x2="21" y2="18" />
                </svg>
              </button>

              {showMenu && (
                <div className="support-chat-menu-popover animate-scale-in" onClick={(e) => e.stopPropagation()}>
                  <button
                    type="button"
                    className="support-chat-menu-row"
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
                    className="support-chat-menu-row"
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
              className="support-chat-input"
              placeholder={isUploading ? "Đang tải tệp lên..." : "Nhập tin nhắn cần hỗ trợ..."}
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              disabled={sending || isUploading}
            />
            <button
              type="submit"
              className="support-send-btn"
              disabled={(!inputText.trim() && !pendingAttachment) || sending || isUploading}
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

      <SupportChatFaqModal
        isOpen={showFaqModal}
        onClose={() => setShowFaqModal(false)}
        onSelectFaq={handleQuickFaqClick}
      />
    </>
  );
};

export default SupportChatWidget;
