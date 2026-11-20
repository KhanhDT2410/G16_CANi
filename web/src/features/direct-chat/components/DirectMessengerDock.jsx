import React, { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { directChatApi } from '../api/directChatApi';
import { QUICK_SHIFT_TEMPLATES } from '../types/directChat.constants';
import './DirectChat.css';

/**
 * Cửa sổ Pop-up Messenger Dock nổi ở góc màn hình dành cho EO chat với Worker.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const DirectMessengerDock = ({ activeChat, onClose, onOpenShiftDetail }) => {
  const [messages, setMessages] = useState([]);
  const [inputText, setInputText] = useState('');
  const [isMinimized, setIsMinimized] = useState(false);
  const [sending, setSending] = useState(false);
  const messagesEndRef = useRef(null);

  const convId = activeChat?.id;

  // Lấy tin nhắn và Polling cập nhật mỗi 3s
  useEffect(() => {
    let isMounted = true;
    if (!convId) return;

    const fetchDetail = async () => {
      try {
        const res = await directChatApi.getConversationDetail(convId);
        if (isMounted && res?.messages) {
          setMessages(res.messages);
        }
      } catch {
        /* Bỏ qua lỗi mạng */
      }
    };

    fetchDetail();
    const interval = setInterval(fetchDetail, 3000);
    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, [convId]);

  useEffect(() => {
    if (!isMinimized) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isMinimized]);

  if (!activeChat) return null;

  const handleSend = async (customText = null) => {
    const textToSend = (customText || inputText).trim();
    if (!textToSend || sending || !convId) return;

    const optimisticMsg = {
      id: `local-${Date.now()}`,
      senderId: 'me',
      senderName: 'Bạn',
      messageText: textToSend,
      createdAt: new Date().toISOString(),
      isMe: true,
    };

    setMessages((prev) => [...prev, optimisticMsg]);
    if (!customText) setInputText('');
    setSending(true);

    try {
      await directChatApi.sendMessage(convId, { messageText: textToSend });
    } catch {
      /* Bỏ qua */
    } finally {
      setSending(false);
    }
  };

  const dockContent = (
    <div className={`direct-messenger-dock ${isMinimized ? 'minimized' : ''}`}>
      {/* Header Dock */}
      <div className="dock-header" onClick={() => { if (isMinimized) setIsMinimized(false); }}>
        <div className="dock-header-user">
          <span style={{ fontSize: '1rem' }}>💬</span>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <span className="dock-header-title">{activeChat.targetUserName || activeChat.shiftName || 'Trao đổi nhân sự'}</span>
            {activeChat.shiftCode && !isMinimized && (
              <span style={{ fontSize: '0.68rem', color: '#bfdbfe' }}>Ca: {activeChat.shiftCode}</span>
            )}
          </div>
        </div>

        <div className="dock-header-actions" onClick={(e) => e.stopPropagation()}>
          {activeChat.shiftId && onOpenShiftDetail && !isMinimized && (
            <button type="button" className="dock-btn" onClick={() => onOpenShiftDetail(activeChat.shiftId)} title="Xem ca làm">↗</button>
          )}
          <button type="button" className="dock-btn" onClick={() => setIsMinimized(!isMinimized)} title={isMinimized ? 'Mở rộng' : 'Thu nhỏ'}>
            {isMinimized ? '🗖' : '—'}
          </button>
          <button type="button" className="dock-btn" onClick={onClose} title="Đóng chat">✕</button>
        </div>
      </div>

      {/* Body Tin nhắn (chỉ hiện khi không thu nhỏ) */}
      {!isMinimized && (
        <>
          <div className="dock-messages-body">
            {activeChat.shiftCode && (
              <div style={{ background: '#eff6ff', border: '1px solid #bfdbfe', borderRadius: 6, padding: '4px 8px', fontSize: '0.74rem', color: '#1d4ed8', textAlign: 'center' }}>
                🏷️ Cuộc trò chuyện gắn với ca: <strong>{activeChat.shiftCode}</strong>
              </div>
            )}

            {messages.map((m) => {
              const isMe = m.isMe || m.senderId === 'me';
              return (
                <div key={m.id} className={`dock-msg-item ${isMe ? 'me' : 'other'}`}>
                  {!isMe && <span style={{ fontSize: '0.68rem', color: '#64748b', fontWeight: 600, marginBottom: 2 }}>{m.senderName || 'Nhân sự'}</span>}
                  <div className="dock-msg-bubble">{m.messageText}</div>
                  <span className="dock-msg-time">{new Date(m.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}</span>
                </div>
              );
            })}

            {messages.length <= 1 && activeChat.type === 'SHIFT' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginTop: 4 }}>
                <span style={{ fontSize: '0.72rem', color: '#64748b' }}>Gợi ý tin nhắn ca:</span>
                {QUICK_SHIFT_TEMPLATES.map((tmpl, idx) => (
                  <button key={idx} type="button" onClick={() => handleSend(tmpl)} style={{ background: '#ffffff', border: '1px solid #cbd5e1', borderRadius: 4, padding: '4px 8px', fontSize: '0.72rem', color: '#1e293b', textAlign: 'left', cursor: 'pointer' }}>
                    💡 {tmpl}
                  </button>
                ))}
              </div>
            )}

            <div ref={messagesEndRef} />
          </div>

          <form className="dock-footer" onSubmit={(e) => { e.preventDefault(); handleSend(); }}>
            <input type="text" className="dock-input" placeholder="Nhập tin nhắn..." value={inputText} onChange={(e) => setInputText(e.target.value)} disabled={sending} />
            <button type="submit" className="dock-send-btn" disabled={!inputText.trim() || sending} title="Gửi tin nhắn">➤</button>
          </form>
        </>
      )}
    </div>
  );

  return createPortal(dockContent, document.body);
};

export default DirectMessengerDock;
