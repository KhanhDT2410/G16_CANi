import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { ACCOUNT_APPEAL_STATUS_CONFIG } from '../types/accountAppeal.constants';
import { showToast } from '../../../components/common/Toast';
import './AdminAccountAppeals.css';

export default function AdminAccountAppealReviewModal({ appeal, onClose, onReviewSuccess }) {
  const [decision, setDecision] = useState('APPROVED');
  const [adminNotes, setAdminNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!appeal) return;
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = prevOverflow;
    };
  }, [appeal, onClose]);

  if (!appeal || typeof document === 'undefined') return null;

  const cfg = ACCOUNT_APPEAL_STATUS_CONFIG[appeal.status] || ACCOUNT_APPEAL_STATUS_CONFIG.SUBMITTED;
  const isPending = appeal.status === 'SUBMITTED' || appeal.status === 'UNDER_REVIEW';

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!adminNotes.trim()) {
      showToast('Vui lòng nhập ghi chú thẩm định', 'error');
      return;
    }
    setSubmitting(true);
    try {
      await onReviewSuccess(appeal.id, {
        decision,
        adminNotes: adminNotes.trim(),
      });
      showToast(
        decision === 'APPROVED'
          ? 'Đã chấp thuận đơn giải trình & mở khóa tài khoản!'
          : 'Đã từ chối đơn giải trình tài khoản.',
        'success'
      );
      onClose();
    } catch (err) {
      showToast(err.response?.data?.message || 'Lỗi khi xử lý đơn giải trình', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const modalContent = (
    <div className="appeal-modal-backdrop" onClick={onClose} role="dialog" aria-modal="true">
      <div className="appeal-modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="appeal-modal-header">
          <h3>Thẩm Định Đơn Giải Trình Khóa Tài Khoản</h3>
          <button type="button" className="appeal-modal-close" onClick={onClose}>
            ✕
          </button>
        </div>

        <div className="appeal-modal-body">
          <div className="appeal-detail-grid">
            <div>
              <span className="appeal-detail-label">Người dùng / Đơn vị:</span>
              <strong className="appeal-detail-value">{appeal.companyName || appeal.fullName || appeal.email}</strong>
            </div>
            <div>
              <span className="appeal-detail-label">Vai trò tài khoản:</span>
              <span className={`appeal-role-badge ${appeal.userRole === 'EVENT_ORGANIZER' ? 'eo' : 'worker'}`}>
                {appeal.userRole === 'EVENT_ORGANIZER' ? 'Nhà tổ chức' : 'Người lao động'}
              </span>
            </div>
            <div>
              <span className="appeal-detail-label">Email tài khoản:</span>
              <span className="appeal-detail-value">{appeal.email}</span>
            </div>
            <div>
              <span className="appeal-detail-label">Trạng thái hiện tại:</span>
              <span className={`appeal-status-badge ${cfg.badgeClass}`}>
                {cfg.label}
              </span>
            </div>
          </div>

          <div className="appeal-detail-section">
            <label className="appeal-detail-label">Lý do tài khoản bị Admin khóa ban đầu:</label>
            <div className="appeal-lock-box">{appeal.lockReasonSnapshot || 'Vi phạm chính sách vận hành'}</div>
          </div>

          <div className="appeal-detail-section">
            <label className="appeal-detail-label">Nội dung giải trình từ Người dùng:</label>
            <div className="appeal-content-box">{appeal.appealContent}</div>
          </div>

          {appeal.attachmentUrls && (
            <div className="appeal-detail-section">
              <label className="appeal-detail-label">Tài liệu minh chứng đính kèm:</label>
              <div className="appeal-evidence-links">
                {appeal.attachmentUrls.split(',').map((url, i) => (
                  <a
                    key={i}
                    href={url.trim()}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="appeal-evidence-item"
                  >
                    Xem tài liệu minh chứng #{i + 1} ↗
                  </a>
                ))}
              </div>
            </div>
          )}

          {isPending ? (
            <form onSubmit={handleSubmit} className="appeal-review-form">
              <div className="appeal-decision-group">
                <label className="appeal-detail-label">Quyết định thẩm định:</label>
                <div className="appeal-radio-options">
                  <label className={`appeal-radio-label ${decision === 'APPROVED' ? 'selected-approve' : ''}`}>
                    <input
                      type="radio"
                      name="decision"
                      value="APPROVED"
                      checked={decision === 'APPROVED'}
                      onChange={() => setDecision('APPROVED')}
                    />
                    <span>Chấp Thuận &amp; Mở Khóa Tài Khoản</span>
                  </label>
                  <label className={`appeal-radio-label ${decision === 'REJECTED' ? 'selected-reject' : ''}`}>
                    <input
                      type="radio"
                      name="decision"
                      value="REJECTED"
                      checked={decision === 'REJECTED'}
                      onChange={() => setDecision('REJECTED')}
                    />
                    <span>Từ Chối &amp; Giữ Nguyên Khóa</span>
                  </label>
                </div>
              </div>

              <div className="appeal-detail-section">
                <label className="appeal-detail-label">Ghi chú / Lý do phản hồi cho Người dùng *</label>
                <textarea
                  className="appeal-textarea"
                  rows={3}
                  required
                  placeholder="Nhập lý do chấp thuận mở khóa hoặc nguyên nhân từ chối giải trình..."
                  value={adminNotes}
                  onChange={(e) => setAdminNotes(e.target.value)}
                />
              </div>

              <div className="appeal-modal-actions">
                <button type="button" className="btn-cancel" onClick={onClose} disabled={submitting}>
                  Đóng
                </button>
                <button
                  type="submit"
                  className={decision === 'APPROVED' ? 'btn-approve-confirm' : 'btn-reject-confirm'}
                  disabled={submitting}
                >
                  {submitting ? 'Đang xử lý...' : decision === 'APPROVED' ? 'Xác Nhận Mở Khóa' : 'Xác Nhận Từ Chối'}
                </button>
              </div>
            </form>
          ) : (
            <div className="appeal-resolved-info">
              <strong>Kết quả thẩm định:</strong> {appeal.status === 'APPROVED' ? 'Đã chấp thuận mở khóa' : 'Đã từ chối'}
              {appeal.adminNotes && <div><strong>Ghi chú Admin:</strong> {appeal.adminNotes}</div>}
            </div>
          )}
        </div>
      </div>
    </div>
  );

  return createPortal(modalContent, document.body);
}
