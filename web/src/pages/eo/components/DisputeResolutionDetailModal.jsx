import React, { useEffect } from 'react';
import { createPortal } from 'react-dom';

const OUTCOME_CONFIG = {
  EO_FAULT: {
    label: 'Lỗi thuộc về Nhà tổ chức',
    color: '#b91c1c',
    bg: '#fef2f2',
    border: '#fca5a5',
    instruction: 'CSKH xác định Nhà tổ chức chưa thanh toán đủ thù lao cho Nhân sự. Bạn cần hoàn tất thanh toán thù lao bổ sung theo phán quyết.',
  },
  WORKER_FAULT: {
    label: 'Lỗi thuộc về Nhân sự',
    color: '#047857',
    bg: '#ecfdf5',
    border: '#a7f3d0',
    instruction: 'CSKH xác định Nhân sự vi phạm quy chế ca làm việc và bị trừ điểm uy tín. Nhà tổ chức được bảo lưu / miễn trả thù lao tranh chấp.',
  },
  PARTIAL: {
    label: 'Chia sẻ trách nhiệm hai bên',
    color: '#b45309',
    bg: '#fffbeb',
    border: '#fde68a',
    instruction: 'Hai bên cùng chịu một phần trách nhiệm. Khoản thanh toán được quyết toán theo tỷ lệ phân bổ của CSKH.',
  },
  UNVERIFIED: {
    label: 'Không đủ chứng cứ xác minh',
    color: '#475569',
    bg: '#f8fafc',
    border: '#cbd5e1',
    instruction: 'Không đủ chứng cứ để kết luận bên vi phạm. Giữ nguyên tình trạng đối soát hiện hành.',
  },
  UPHOLD: {
    label: 'Giữ nguyên phán quyết sơ bộ',
    color: '#4338ca',
    bg: '#eef2ff',
    border: '#c7d2fe',
    instruction: 'Quản trị viên đã thẩm tra lại và giữ nguyên phán quyết trước đó của bộ phận CSKH.',
  },
};

export default function DisputeResolutionDetailModal({
  isOpen,
  onClose,
  payment,
  onNavigateToDisputes,
  onOpenPaymentDeclaration,
}) {
  useEffect(() => {
    if (!isOpen) return;
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const handleKeyDown = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      document.body.style.overflow = originalOverflow;
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen || !payment) return null;

  const outcomeInfo = payment.disputeOutcome ? OUTCOME_CONFIG[payment.disputeOutcome] : null;
  const isResolved = payment.disputeStatus === 'RESOLVED' || payment.disputeStatus === 'FINAL_RESOLVED';
  const isEoFault = payment.disputeOutcome === 'EO_FAULT';

  const modalContent = (
    <div
      className="eo-modal-overlay"
      onClick={onClose}
      style={{
        position: 'fixed', inset: 0, width: '100vw', height: '100vh',
        backgroundColor: 'rgba(15, 23, 42, 0.65)', backdropFilter: 'blur(6px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        zIndex: 99999, padding: '1rem', boxSizing: 'border-box',
      }}
    >
      <div
        className="eo-modal-box animate-scale-up"
        onClick={(e) => e.stopPropagation()}
        style={{
          backgroundColor: '#ffffff', borderRadius: 16, width: '100%', maxWidth: 580,
          maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          margin: 'auto', position: 'relative', padding: '1.75rem', boxSizing: 'border-box',
          border: '1px solid #e2e8f0',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.25rem' }}>
          <div>
            <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 800, color: '#0f172a' }}>
              {isResolved ? 'Kết Quả Phân Xử Tranh Chấp' : 'Tiến Độ Giải Quyết Tranh Chấp'}
            </h3>
            <p style={{ margin: '0.25rem 0 0', fontSize: '0.85rem', color: '#64748b' }}>
              Đối soát 3 bên giữa Nhà tổ chức, Nhân sự và Chăm sóc khách hàng CANi
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            style={{ background: 'none', border: 'none', fontSize: '1.5rem', cursor: 'pointer', color: '#94a3b8' }}
            title="Đóng cửa sổ"
          >
            ✕
          </button>
        </div>

        <div style={{ backgroundColor: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 10, padding: '0.85rem 1rem', marginBottom: '1.25rem', fontSize: '0.85rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.35rem' }}>
            <span style={{ color: '#64748b' }}>Mã ca phân công:</span>
            <span style={{ fontWeight: 700, fontFamily: 'monospace', color: '#0f172a' }}>{payment.assignmentId}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.35rem' }}>
            <span style={{ color: '#64748b' }}>Nhân sự liên quan:</span>
            <span style={{ fontWeight: 700, color: '#0f172a' }}>{payment.workerName} ({payment.jobType || 'Nhân sự'})</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: '#64748b' }}>Thù lao thỏa thuận / Đã trả:</span>
            <span style={{ fontWeight: 700, color: '#059669' }}>
              {(payment.agreedAmount || 0).toLocaleString()} đ / {(payment.declaredAmount || 0).toLocaleString()} đ
            </span>
          </div>
        </div>

        {outcomeInfo ? (
          <div style={{ backgroundColor: outcomeInfo.bg, border: `1px solid ${outcomeInfo.border}`, borderRadius: 10, padding: '1rem', marginBottom: '1.25rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
              <span style={{ fontSize: '0.75rem', fontWeight: 800, padding: '3px 8px', borderRadius: 4, backgroundColor: outcomeInfo.color, color: '#ffffff' }}>
                PHÁN QUYẾT
              </span>
              <span style={{ fontSize: '0.9rem', fontWeight: 700, color: outcomeInfo.color }}>
                {outcomeInfo.label}
              </span>
            </div>
            <p style={{ margin: 0, fontSize: '0.84rem', color: '#1e293b', lineHeight: 1.5 }}>
              {outcomeInfo.instruction}
            </p>
          </div>
        ) : (
          <div style={{ backgroundColor: '#fff7ed', border: '1px solid #fed7aa', borderRadius: 10, padding: '1rem', marginBottom: '1.25rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.4rem' }}>
              <span style={{ fontSize: '0.75rem', fontWeight: 800, padding: '3px 8px', borderRadius: 4, backgroundColor: '#ea580c', color: '#ffffff' }}>
                ĐANG XỬ LÝ
              </span>
              <span style={{ fontSize: '0.9rem', fontWeight: 700, color: '#c2410c' }}>
                Hồ sơ đang điều tra đối soát (SLA 7 ngày)
              </span>
            </div>
            <p style={{ margin: 0, fontSize: '0.84rem', color: '#431407', lineHeight: 1.5 }}>
              Bộ phận Chăm sóc khách hàng đang thu thập bằng chứng check-in GPS QR và giải trình thù lao từ hai bên.
            </p>
          </div>
        )}

        {payment.disputeRationale && (
          <div style={{ marginBottom: '1.25rem' }}>
            <label style={{ display: 'block', fontSize: '0.82rem', fontWeight: 700, color: '#475569', marginBottom: '0.4rem' }}>
              Căn cứ & Lý do phân xử từ Chuyên viên CSKH:
            </label>
            <div style={{ backgroundColor: '#f1f5f9', border: '1px solid #cbd5e1', borderRadius: 8, padding: '0.75rem 1rem', fontSize: '0.84rem', color: '#1e293b', lineHeight: 1.5, maxHeight: 120, overflowY: 'auto' }}>
              {payment.disputeRationale}
            </div>
          </div>
        )}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.6rem', marginTop: '1.5rem', paddingTop: '1rem', borderTop: '1px solid #e2e8f0', flexWrap: 'wrap' }}>
          {isEoFault && (
            <button
              type="button"
              className="btn-primary"
              style={{ backgroundColor: '#059669', color: '#ffffff', border: 'none', borderRadius: 8, padding: '0.55rem 1.15rem', fontSize: '0.85rem', fontWeight: 700, cursor: 'pointer' }}
              onClick={() => { onClose(); if (onOpenPaymentDeclaration) onOpenPaymentDeclaration(payment); }}
            >
              Khai Báo Trả Lương Bổ Sung
            </button>
          )}

          <button
            type="button"
            style={{ backgroundColor: '#eff6ff', color: '#1d4ed8', border: '1px solid #bfdbfe', borderRadius: 8, padding: '0.55rem 1.15rem', fontSize: '0.85rem', fontWeight: 700, cursor: 'pointer' }}
            onClick={() => { onClose(); if (onNavigateToDisputes) onNavigateToDisputes(payment.disputeId); }}
          >
            Đến Trung Tâm Tranh Chấp (Disputes) &rarr;
          </button>

          <button
            type="button"
            onClick={onClose}
            style={{ backgroundColor: '#f1f5f9', color: '#475569', border: '1px solid #cbd5e1', borderRadius: 8, padding: '0.55rem 1rem', fontSize: '0.85rem', fontWeight: 600, cursor: 'pointer' }}
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );

  return createPortal(modalContent, document.body);
}
