import React, { useEffect } from 'react';
import { createPortal } from 'react-dom';
import WorkerReviewForm from './WorkerReviewForm';

const RateWorkerModal = ({
  isOpen,
  onClose,
  assignment,
  onSubmitReview,
  submitting,
}) => {
  useEffect(() => {
    if (!isOpen) return undefined;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const onKeyDown = (e) => {
      if (e.key === 'Escape') onClose?.();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => {
      document.body.style.overflow = prev;
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen || !assignment) return null;

  return createPortal(
    <div
      className="eo-modal-overlay"
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        width: '100vw',
        height: '100vh',
        backgroundColor: 'rgba(15, 23, 42, 0.65)',
        backdropFilter: 'blur(4px)',
        WebkitBackdropFilter: 'blur(4px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 99999,
        padding: '1rem',
        boxSizing: 'border-box',
      }}
    >
      <div
        className="eo-modal-box"
        onClick={(e) => e.stopPropagation()}
        style={{
          maxWidth: 540,
          width: '100%',
          maxHeight: '90vh',
          overflowY: 'auto',
          overflowX: 'hidden',
          margin: 'auto',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h3 style={{ margin: 0, fontSize: '1.2rem', fontWeight: 700, color: '#1e293b' }}>
            Đánh Giá Nhân Sự Sau Ca Làm
          </h3>
          <button
            type="button"
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              fontSize: '0.9rem',
              cursor: 'pointer',
              color: '#64748b',
              fontWeight: 600,
            }}
          >
            Đóng
          </button>
        </div>

        <div style={{ background: '#f8fafc', padding: '0.75rem 1rem', borderRadius: 8, marginBottom: '1rem', border: '1px solid #e2e8f0' }}>
          <div style={{ fontWeight: 600, color: '#1e293b' }}>{assignment.workerName || 'Nhân sự'}</div>
          <div style={{ fontSize: '0.84rem', color: '#64748b', marginTop: 2 }}>
            SĐT: {assignment.workerPhone || 'Chưa cập nhật'} • Điểm uy tín: <strong style={{ color: '#2563eb' }}>{assignment.workerReliabilityScore || 100}/100</strong>
            {(assignment.confirmedPerformedSkillName || assignment.performedSkillName) && (
              <> • Kỹ năng: <strong style={{ color: '#047857' }}>{assignment.confirmedPerformedSkillName || assignment.performedSkillName}</strong></>
            )}
          </div>
        </div>

        <WorkerReviewForm
          assignment={assignment}
          submitting={submitting}
          onCancel={onClose}
          onSubmitReview={onSubmitReview}
          layout="modal"
        />
      </div>
    </div>,
    document.body
  );
};

export default RateWorkerModal;
