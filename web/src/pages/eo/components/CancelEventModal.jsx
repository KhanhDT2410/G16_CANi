import React, { useState, useEffect } from 'react';

const CancelEventModal = ({ isOpen, event, onClose, onConfirm, loading = false }) => {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (isOpen) {
      setReason('');
      setError('');
    }
  }, [isOpen]);

  if (!isOpen || !event) return null;

  const charCount = reason.trim().length;
  const isReasonValid = charCount >= 10;

  const handleConfirm = () => {
    if (!isReasonValid) {
      setError('Vui lòng nhập lý do hủy cụ thể (tối thiểu 10 ký tự).');
      return;
    }
    setError('');
    onConfirm(reason.trim());
  };

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box animate-fade-in-up" style={{ maxWidth: '540px' }}>
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '0.75rem', marginBottom: '1.25rem' }}>
          <div
            style={{
              width: '42px',
              height: '42px',
              borderRadius: '10px',
              backgroundColor: '#fee2e2',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
              color: '#dc2626',
            }}
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10" />
              <line x1="15" y1="9" x2="9" y2="15" />
              <line x1="9" y1="9" x2="15" y2="15" />
            </svg>
          </div>
          <div>
            <h3 style={{ margin: '0 0 0.25rem', fontSize: '1.2rem', fontWeight: 700, color: '#991b1b' }}>
              Xác Nhận Hủy Sự Kiện
            </h3>
            <p style={{ margin: 0, fontSize: '0.85rem', color: '#64748b' }}>
              Thao tác này sẽ dừng toàn bộ hoạt động điều phối nhân sự của sự kiện.
            </p>
          </div>
        </div>

        {/* Thông tin sự kiện */}
        <div
          style={{
            backgroundColor: '#f8fafc',
            border: '1px solid #e2e8f0',
            borderRadius: '10px',
            padding: '0.85rem 1rem',
            marginBottom: '1rem',
          }}
        >
          <div style={{ fontWeight: 700, fontSize: '0.95rem', color: '#1e293b', marginBottom: '0.35rem' }}>
            {event.name}
          </div>
          <div style={{ fontSize: '0.8rem', color: '#64748b', display: 'flex', gap: '1.25rem' }}>
            <span>Số ca: <strong>{event.totalShifts || 0}</strong></span>
            <span>Tổng nhân sự: <strong>{event.totalRequiredWorkers || 0}</strong> người</span>
            <span>Trạng thái: <strong>{event.status}</strong></span>
          </div>
        </div>

        {/* Cảnh báo */}
        <div
          style={{
            backgroundColor: '#fffbeb',
            border: '1px solid #fef3c7',
            borderRadius: '8px',
            padding: '0.75rem 0.9rem',
            marginBottom: '1.25rem',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '0.5rem',
          }}
        >
          <span style={{ color: '#d97706', fontSize: '1rem', lineHeight: 1 }}>⚠️</span>
          <p style={{ margin: 0, fontSize: '0.82rem', color: '#92400e', lineHeight: 1.45 }}>
            <strong>Lưu ý:</strong> Tất cả ca làm việc sẽ bị hủy. Những nhân sự đã được giao ca hoặc chấp nhận lời mời sẽ nhận được thông báo hủy kèm lý do bên dưới. Sự kiện sau khi hủy sẽ được lưu trữ trong danh sách <em>"Sự kiện đã hủy"</em> để đối soát.
          </p>
        </div>

        {/* Form nhập lý do */}
        <div className="eo-form-group" style={{ marginBottom: '1.25rem' }}>
          <label className="eo-form-label" style={{ fontWeight: 600, color: '#334155', display: 'flex', justifyContent: 'space-between' }}>
            <span>Lý do hủy sự kiện <strong style={{ color: '#dc2626' }}>*</strong></span>
            <span style={{ fontSize: '0.785rem', color: charCount >= 10 ? '#16a34a' : '#94a3b8' }}>
              {charCount}/10 ký tự tối thiểu
            </span>
          </label>
          <textarea
            rows="3"
            className="eo-form-textarea"
            style={{
              borderColor: error ? '#ef4444' : undefined,
              boxShadow: error ? '0 0 0 1px #ef4444' : undefined,
            }}
            placeholder="Ví dụ: Sự kiện bị hoãn do điều kiện thời tiết bão lũ, ban tổ chức điều chỉnh lịch sang tháng sau..."
            value={reason}
            onChange={(e) => {
              setReason(e.target.value);
              if (error) setError('');
            }}
            disabled={loading}
          />
          {error && (
            <div style={{ color: '#ef4444', fontSize: '0.8rem', marginTop: '0.35rem', fontWeight: 600 }}>
              {error}
            </div>
          )}
        </div>

        {/* Footer Actions */}
        <div className="eo-modal-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
          <button
            type="button"
            className="eo-btn-secondary"
            onClick={onClose}
            disabled={loading}
            style={{ padding: '0.6rem 1.25rem', borderRadius: '8px', cursor: 'pointer' }}
          >
            Đóng / Quay lại
          </button>
          <button
            type="button"
            style={{
              background: isReasonValid && !loading ? '#dc2626' : '#fca5a5',
              color: '#ffffff',
              padding: '0.6rem 1.5rem',
              borderRadius: '8px',
              fontWeight: 700,
              cursor: isReasonValid && !loading ? 'pointer' : 'not-allowed',
              border: 'none',
              transition: 'background 0.2s ease',
            }}
            onClick={handleConfirm}
            disabled={!isReasonValid || loading}
          >
            {loading ? 'Đang Hủy Sự Kiện...' : 'Xác Nhận Hủy Sự Kiện'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default CancelEventModal;
