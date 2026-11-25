import { useState } from 'react';
import { createPortal } from 'react-dom';
import { supportChatApi } from '../api/supportChatApi';
import { showToast } from '../../../components/common/Toast';

export const EscalateChatModal = ({
  isOpen,
  onClose,
  convId,
  conversationId,
  customerName,
  onSuccess,
  onEscalated,
}) => {
  const targetConvId = convId || conversationId;
  const handleSuccess = onSuccess || onEscalated;

  const [category, setCategory] = useState('TECHNICAL');
  const [priority, setPriority] = useState('MEDIUM');
  const [subject, setSubject] = useState(`Hỗ trợ vấn đề từ cuộc trò chuyện - ${customerName || 'Khách hàng'}`);
  const [customDescription, setCustomDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (!isOpen || typeof document === 'undefined') return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!targetConvId) {
      showToast('Không xác định được mã cuộc trò chuyện', 'error');
      return;
    }
    if (!subject.trim()) {
      showToast('Vui lòng nhập tiêu đề phiếu hỗ trợ', 'error');
      return;
    }

    setSubmitting(true);
    try {
      const res = await supportChatApi.escalateToTicket(targetConvId, {
        category,
        priority,
        subject: subject.trim(),
        customDescription: customDescription.trim() || undefined,
      });

      showToast(`Đã tạo thành công phiếu hỗ trợ #${res.referenceNo}`, 'success');
      if (handleSuccess) handleSuccess(res);
      onClose();
    } catch (err) {
      showToast(err.response?.data?.message || 'Không thể tạo phiếu hỗ trợ từ cuộc trò chuyện', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return createPortal(
    <div
      className="ticket-modal-overlay"
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.65)',
        backdropFilter: 'blur(4px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 100000,
        padding: '1rem',
      }}
    >
      <div
        className="ticket-modal animate-scale-up"
        onClick={(e) => e.stopPropagation()}
        style={{
          width: '100%',
          maxWidth: '520px',
          background: '#ffffff',
          borderRadius: '16px',
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
          overflow: 'hidden',
        }}
      >
        <div style={{ padding: '1.25rem 1.5rem', borderBottom: '1px solid #f1f5f9', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 800, color: '#0f172a' }}>
              Chuyển Sang Phiếu Hỗ Trợ
            </h3>
            <p style={{ margin: '3px 0 0', fontSize: '0.8rem', color: '#64748b' }}>
              Chuyển cuộc trò chuyện trực tuyến sang quy trình hỗ trợ có cam kết thời gian xử lý
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            style={{ background: 'transparent', border: 'none', fontSize: '1.2rem', cursor: 'pointer', color: '#94a3b8' }}
          >
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ padding: '1.5rem' }}>
          <div style={{ marginBottom: '1rem' }}>
            <label style={{ display: 'block', fontSize: '0.82rem', fontWeight: 700, color: '#334155', marginBottom: '0.4rem' }}>
              Danh mục sự cố *
            </label>
            <select
              value={category}
              onChange={(e) => {
                setCategory(e.target.value);
                if (e.target.value === 'BILLING' || e.target.value === 'ACCOUNT') {
                  setPriority('HIGH');
                }
              }}
              style={{ width: '100%', padding: '0.6rem 0.75rem', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '0.85rem' }}
            >
              <option value="ACCOUNT">Tài khoản và định danh</option>
              <option value="BILLING">Thù lao và thanh toán</option>
              <option value="TECHNICAL">Lỗi kỹ thuật và ứng dụng</option>
              <option value="GENERAL">Thông tin chung</option>
              <option value="OTHER">Vấn đề khác</option>
            </select>
          </div>

          <div style={{ marginBottom: '1rem' }}>
            <label style={{ display: 'block', fontSize: '0.82rem', fontWeight: 700, color: '#334155', marginBottom: '0.4rem' }}>
              Mức độ ưu tiên *
            </label>
            <select
              value={priority}
              onChange={(e) => setPriority(e.target.value)}
              style={{ width: '100%', padding: '0.6rem 0.75rem', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '0.85rem' }}
            >
              <option value="HIGH">Cao - Phản hồi trong 4 giờ</option>
              <option value="MEDIUM">Trung bình - Phản hồi trong 24 giờ</option>
              <option value="LOW">Thấp - Phản hồi trong 72 giờ</option>
            </select>
          </div>

          <div style={{ marginBottom: '1rem' }}>
            <label style={{ display: 'block', fontSize: '0.82rem', fontWeight: 700, color: '#334155', marginBottom: '0.4rem' }}>
              Tiêu đề phiếu hỗ trợ *
            </label>
            <input
              type="text"
              required
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
              style={{ width: '100%', padding: '0.6rem 0.75rem', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '0.85rem', boxSizing: 'border-box' }}
            />
          </div>

          <div style={{ marginBottom: '1.25rem' }}>
            <label style={{ display: 'block', fontSize: '0.82rem', fontWeight: 700, color: '#334155', marginBottom: '0.4rem' }}>
              Nội dung ghi chú ban đầu
            </label>
            <textarea
              rows={3}
              placeholder="Để trống nếu muốn hệ thống tự động trích xuất các tin nhắn gần nhất..."
              value={customDescription}
              onChange={(e) => setCustomDescription(e.target.value)}
              style={{ width: '100%', padding: '0.6rem 0.75rem', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '0.85rem', boxSizing: 'border-box', resize: 'vertical' }}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
            <button
              type="button"
              disabled={submitting}
              onClick={onClose}
              style={{ padding: '0.55rem 1.1rem', borderRadius: '8px', border: '1px solid #cbd5e1', background: '#ffffff', color: '#475569', fontSize: '0.85rem', fontWeight: 600, cursor: 'pointer' }}
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={submitting}
              style={{ padding: '0.55rem 1.25rem', borderRadius: '8px', border: 'none', background: '#4f46e5', color: '#ffffff', fontSize: '0.85rem', fontWeight: 700, cursor: 'pointer' }}
            >
              {submitting ? 'Đang xử lý...' : 'Xác Nhận Tạo Phiếu Hỗ Trợ'}
            </button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
};

export default EscalateChatModal;
