import React, { useState } from 'react';

/**
 * Modal đánh dấu No-show thủ công cho một ca làm việc cụ thể
 */
export const ManualMarkNoShowModal = ({ isOpen, onClose, onSubmit, submitting }) => {
  const [assignmentId, setAssignmentId] = useState('');
  const [notes, setNotes] = useState('');

  if (!isOpen) return null;

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!assignmentId.trim()) return;
    onSubmit(assignmentId.trim(), { notes: notes.trim() });
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <form onSubmit={handleSubmit}>
          <div className="modal-header">
            <h3>Xác Nhận Đánh Dấu No-Show Thủ Công</h3>
            <button type="button" className="modal-close-btn" onClick={onClose}>&times;</button>
          </div>

          <div className="modal-body">
            <p style={{ fontSize: '0.875rem', color: '#6b7280', margin: 0 }}>
              Hành động này sẽ cập nhật trạng thái ca sang <b>NO_SHOW</b>, trừ <b>25 điểm uy tín (RS)</b> của nhân sự,
              tự động khóa tài khoản nếu RS &lt; 20 và kích hoạt điều phối nhân sự thay thế.
            </p>

            <div>
              <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '6px' }}>
                Mã Phân Công Ca (Assignment ID) <span style={{ color: '#dc2626' }}>*</span>
              </label>
              <input
                type="text"
                placeholder="Nhập UUID của Shift Assignment..."
                value={assignmentId}
                onChange={(e) => setAssignmentId(e.target.value)}
                required
                style={{ width: '100%', padding: '10px 12px', borderRadius: '8px', border: '1px solid #d1d5db', fontSize: '0.875rem' }}
              />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '6px' }}>
                Ghi chú lý do / Biên bản kiểm tra
              </label>
              <textarea
                placeholder="Mô tả lý do xác nhận vắng mặt..."
                rows={3}
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                style={{ width: '100%', padding: '10px 12px', borderRadius: '8px', border: '1px solid #d1d5db', fontSize: '0.875rem' }}
              />
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" className="btn-noshow-manual" onClick={onClose} disabled={submitting}>
              Hủy
            </button>
            <button
              type="submit"
              className="btn-noshow-scan"
              style={{ background: '#dc2626' }}
              disabled={submitting || !assignmentId.trim()}
            >
              {submitting ? 'Đang xử lý...' : 'Xác Nhận No-Show'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

