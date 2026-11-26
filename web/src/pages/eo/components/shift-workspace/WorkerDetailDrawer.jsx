import React, { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { formatVndWhole } from '../../utils/eventHelpers';
import { resolveAssetUrl, buildWorkerVietQrUrl } from '../../utils/resolveAssetUrl';
import WorkerReviewForm from '../WorkerReviewForm';
import {
  canEoConfirmWorker,
  formatTimeShort,
  getSourceLabel,
  parseWorkerChecklistProgressMap,
} from './shiftWorkspaceHelpers';

/**
 * Slide-over detail drawer for a single worker assignment inside ShiftWorkspacePage.
 * Keeps the main 40+ worker table clean while preserving full checklist, payment, review, and moderation actions.
 */
export default function WorkerDetailDrawer({
  item,
  shift,
  event,
  shiftChecklistTasks = [],
  verifyingId,
  submittingReview,
  onClose,
  onOpenProfile,
  onPreviewImage,
  onVerify,
  onOpenPayment,
  onReportIncomplete,
  onReportNoShow,
  onBlockWorker,
  onSubmitReview,
  onMessageWorker,
}) {
  useEffect(() => {
    if (!item) return undefined;
    const onKeyDown = (e) => {
      if (e.key === 'Escape') onClose?.();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [item, onClose]);

  if (!item) return null;

  const isNoShow = item.assignmentStatus === 'NO_SHOW';
  const isCheckedIn = item.assignmentStatus === 'CHECKED_IN' || Boolean(item.checkinTime);
  const isCompleted = item.assignmentStatus === 'COMPLETED' || item.checkoutTime != null;
  const canVerify = canEoConfirmWorker(item, shiftChecklistTasks);
  const progressMap = parseWorkerChecklistProgressMap(item.checklistRawProgress);
  const workerQrUrl = buildWorkerVietQrUrl(item);
  const sourceLabel = getSourceLabel(item.source);
  const hasDeclared =
    Boolean(item.paymentStatus && item.paymentStatus !== 'NOT_DECLARED') ||
    item.declaredAmount != null;
  const isTransfer = item.paymentMethod === 'BANK_TRANSFER';
  const isCash = item.paymentMethod === 'CASH';

  return createPortal(
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.45)',
        zIndex: 9990,
        display: 'flex',
        justifyContent: 'flex-end',
      }}
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label={`Chi tiết nhân sự ${item.workerName}`}
    >
      <aside
        onClick={(e) => e.stopPropagation()}
        style={{
          width: 'min(520px, 100vw)',
          height: '100dvh',
          background: '#ffffff',
          boxShadow: '-12px 0 36px rgba(15, 23, 42, 0.18)',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        {/* Header */}
        <div
          style={{
            padding: '1.25rem 1.35rem',
            borderBottom: '1px solid #e2e8f0',
            background: '#f8fafc',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
            gap: '0.75rem',
            flexShrink: 0,
          }}
        >
          <div style={{ minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
              <button
                type="button"
                onClick={() => item.workerId && onOpenProfile?.(item.workerId)}
                style={{
                  background: 'none',
                  border: 'none',
                  padding: 0,
                  fontSize: '1.1rem',
                  fontWeight: 800,
                  color: '#1d4ed8',
                  cursor: 'pointer',
                  textAlign: 'left',
                }}
              >
                {item.workerName}
              </button>
              {sourceLabel && (
                <span
                  style={{
                    fontSize: '0.7rem',
                    fontWeight: 600,
                    padding: '2px 8px',
                    borderRadius: 999,
                    background: '#ffffff',
                    border: '1px solid #cbd5e1',
                    color: '#475569',
                  }}
                >
                  {sourceLabel}
                </span>
              )}
            </div>
            <div style={{ fontSize: '0.82rem', color: '#475569', marginTop: 4 }}>
              {item.workerPhone || 'Chưa có SĐT'} • Đánh giá {item.workerRatingAvg || 5.0} ★ • Uy tín {item.workerReliabilityScore || 100}/100
            </div>
            <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: 4 }}>
              Vào ca: <strong style={{ color: '#0f172a' }}>{formatTimeShort(item.checkinTime)}</strong> • Kết ca:{' '}
              <strong style={{ color: '#0f172a' }}>{formatTimeShort(item.checkoutTime)}</strong>
              {item.hoursWorked != null && ` (${item.hoursWorked} giờ)`}
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng chi tiết"
            style={{
              background: '#ffffff',
              border: '1px solid #cbd5e1',
              borderRadius: '8px',
              width: 32,
              height: 32,
              cursor: 'pointer',
              color: '#334155',
              fontSize: '1rem',
              flexShrink: 0,
            }}
          >
            ✕
          </button>
        </div>

        {/* Body */}
        <div
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '1.25rem 1.35rem 5rem',
            display: 'flex',
            flexDirection: 'column',
            gap: '1.15rem',
          }}
        >
          {/* Quick Comm & Safety */}
          <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '0.5rem' }}>
            <button
              type="button"
              className="eo-btn-secondary"
              style={{ minHeight: '34px', fontSize: '0.78rem', padding: '0 0.85rem', borderRadius: 6 }}
              onClick={() => onMessageWorker?.(item)}
            >
              Nhắn tin trực tiếp
            </button>

            {item.workerId && (
              <button
                type="button"
                className="eo-btn-secondary"
                style={{ minHeight: '34px', fontSize: '0.78rem', padding: '0 0.85rem', borderRadius: 6 }}
                onClick={() => onOpenProfile?.(item.workerId)}
              >
                Xem hồ sơ năng lực
              </button>
            )}

            {!isCheckedIn && !isCompleted && !isNoShow && item.assignmentStatus !== 'CANCELLED' && (
              <button
                type="button"
                onClick={() => onReportNoShow?.(item)}
                style={{
                  minHeight: '34px',
                  display: 'inline-flex',
                  alignItems: 'center',
                  background: '#fef2f2',
                  color: '#b91c1c',
                  border: '1px solid #fecaca',
                  borderRadius: 6,
                  padding: '0 0.85rem',
                  fontSize: '0.78rem',
                  fontWeight: 700,
                  cursor: 'pointer',
                }}
              >
                Báo vắng (No-Show)
              </button>
            )}

            <button
              type="button"
              onClick={() => onBlockWorker?.(item)}
              style={{
                minHeight: '34px',
                display: 'inline-flex',
                alignItems: 'center',
                background: '#ffffff',
                color: '#dc2626',
                border: '1px solid #fecaca',
                borderRadius: 6,
                padding: '0 0.85rem',
                fontSize: '0.78rem',
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              Chặn nhân sự
            </button>
          </div>

          {/* 1. Checklist & Minh chứng */}
          <section style={{ border: '1px solid #e2e8f0', borderRadius: 12, padding: '1rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
              <h4 style={{ margin: 0, fontSize: '0.88rem', fontWeight: 700, color: '#0f172a' }}>
                Tiến độ đầu việc &amp; Minh chứng
              </h4>
              <span className={`table-status-pill ${item.isChecklistCompleted ? 'green' : 'gray'}`}>
                <span className="status-dot-solid" />
                {item.isChecklistCompleted ? 'Đã xong 100%' : (item.checklistProgress || 'Chưa xong')}
              </span>
            </div>

            {shiftChecklistTasks.length > 0 ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.45rem' }}>
                {shiftChecklistTasks.map((t) => {
                  const prog = progressMap[t.id] || progressMap[t.label] || {};
                  const isTaskDone = Boolean(prog.isCompleted || item.isChecklistCompleted);
                  const hasEvidence = Boolean(prog.evidenceImageUrl && String(prog.evidenceImageUrl).trim().length > 0);

                  return (
                    <div
                      key={t.id}
                      style={{
                        padding: '0.55rem 0.75rem',
                        borderRadius: 8,
                        background: '#f8fafc',
                        border: '1px solid #e2e8f0',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        gap: '0.5rem',
                        fontSize: '0.8rem',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
                        <span
                          style={{
                            fontSize: '0.68rem',
                            fontWeight: 700,
                            padding: '2px 6px',
                            borderRadius: 4,
                            background: isTaskDone ? '#ecfdf5' : '#e2e8f0',
                            color: isTaskDone ? '#065f46' : '#475569',
                            flexShrink: 0,
                          }}
                        >
                          {isTaskDone ? 'Xong' : 'Chưa'}
                        </span>
                        <span style={{ fontWeight: 600, color: '#1e293b' }}>{t.label}</span>
                      </div>

                      {t.isRequiredEvidence ? (
                        hasEvidence ? (
                          <button
                            type="button"
                            onClick={() =>
                              onPreviewImage?.({
                                url: prog.evidenceImageUrl,
                                title: `Minh chứng "${t.label}" - ${item.workerName}`,
                              })
                            }
                            style={{
                              background: '#eff6ff',
                              color: '#1d4ed8',
                              border: '1px solid #bfdbfe',
                              borderRadius: 6,
                              padding: '3px 8px',
                              fontSize: '0.73rem',
                              fontWeight: 700,
                              cursor: 'pointer',
                              flexShrink: 0,
                            }}
                          >
                            Xem ảnh
                          </button>
                        ) : (
                          <span style={{ color: '#dc2626', fontSize: '0.73rem', fontWeight: 700, flexShrink: 0 }}>
                            Thiếu ảnh
                          </span>
                        )
                      ) : (
                        <span style={{ color: '#94a3b8', fontSize: '0.72rem', flexShrink: 0 }}>Không cần ảnh</span>
                      )}
                    </div>
                  );
                })}
              </div>
            ) : (
              <div style={{ fontSize: '0.8rem', color: '#64748b', fontStyle: 'italic' }}>
                Ca làm việc không cấu hình checklist mẫu.
              </div>
            )}

            {item.proofImageUrl && (
              <button
                type="button"
                className="eo-btn-secondary"
                style={{ marginTop: '0.65rem', fontSize: '0.76rem', padding: '0.35rem 0.75rem' }}
                onClick={() =>
                  onPreviewImage?.({
                    url: item.proofImageUrl,
                    title: `Ảnh hoàn thành ca chung - ${item.workerName}`,
                  })
                }
              >
                Xem ảnh nộp ca chung
              </button>
            )}

            {!item.eoConfirmed && !isNoShow && (
              <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginTop: '0.9rem', paddingTop: '0.8rem', borderTop: '1px solid #f1f5f9' }}>
                <button
                  type="button"
                  disabled={!canVerify || verifyingId === item.assignmentId}
                  onClick={() => onVerify?.(item)}
                  className="eo-btn-primary"
                  style={{
                    fontSize: '0.8rem',
                    padding: '0.45rem 0.95rem',
                    opacity: !canVerify || verifyingId === item.assignmentId ? 0.55 : 1,
                    cursor: !canVerify || verifyingId === item.assignmentId ? 'not-allowed' : 'pointer',
                  }}
                >
                  {verifyingId === item.assignmentId ? 'Đang duyệt...' : 'Duyệt công'}
                </button>
                {(isCheckedIn || isCompleted) && (
                  <button
                    type="button"
                    onClick={() => onReportIncomplete?.(item)}
                    className="eo-btn-secondary"
                    style={{ fontSize: '0.8rem', padding: '0.45rem 0.85rem', color: '#b45309', borderColor: '#fde68a' }}
                  >
                    Báo thiếu việc / lỗi
                  </button>
                )}
                {!canVerify && (
                  <span style={{ fontSize: '0.74rem', color: '#64748b', alignSelf: 'center' }}>
                    {!isCheckedIn ? 'Cần check-in trước khi duyệt' : 'Cần đủ đầu việc & ảnh minh chứng'}
                  </span>
                )}
              </div>
            )}
          </section>

          {/* 2. Thanh toán & Chứng từ */}
          <section style={{ border: '1px solid #e2e8f0', borderRadius: 12, padding: '1rem', background: '#f8fafc' }}>
            <h4 style={{ margin: '0 0 0.65rem', fontSize: '0.88rem', fontWeight: 700, color: '#0f172a' }}>
              Thù lao &amp; Quyết toán ({event?.name || 'Sự kiện'})
            </h4>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem', fontSize: '0.82rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#64748b' }}>Thù lao thỏa thuận:</span>
                <strong style={{ color: '#0f172a' }}>{formatVndWhole(item.agreedWage || shift?.salary)}</strong>
              </div>

              {item.declaredAmount != null && (
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ color: '#64748b' }}>Đã khai báo:</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <strong style={{ color: '#047857' }}>{formatVndWhole(item.declaredAmount)}</strong>
                    {(isTransfer || isCash) && (
                      <span style={{ fontSize: '0.7rem', padding: '1px 6px', borderRadius: 4, background: '#ffffff', border: '1px solid #cbd5e1', color: '#334155', fontWeight: 600 }}>
                        {isTransfer ? 'Chuyển khoản' : 'Tiền mặt'}
                      </span>
                    )}
                  </div>
                </div>
              )}

              {item.externalRef && (
                <div style={{ fontSize: '0.76rem', color: '#475569', fontFamily: 'monospace' }}>
                  Mã GD: <strong>{item.externalRef}</strong>
                </div>
              )}
            </div>

            {item.workerConfirmStatus === 'RECEIVED_PARTIAL' && (
              <div style={{ marginTop: 8, background: '#fffbeb', color: '#b45309', border: '1px solid #fde68a', padding: '6px 8px', borderRadius: 6, fontSize: '0.76rem' }}>
                <strong>Worker nhận thiếu:</strong> {item.workerComment || 'Chưa ghi chú'}
              </div>
            )}

            {item.workerConfirmStatus === 'NOT_RECEIVED' && (
              <div style={{ marginTop: 8, background: '#fef2f2', color: '#b91c1c', border: '1px solid #fecaca', padding: '6px 8px', borderRadius: 6, fontSize: '0.76rem' }}>
                <strong>Worker chưa nhận được:</strong> {item.workerComment || 'Chưa ghi chú'}
              </div>
            )}

            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem', marginTop: '0.85rem' }}>
              {item.eoConfirmed && (!item.paymentStatus || item.paymentStatus === 'NOT_DECLARED') && (
                <button
                  type="button"
                  className="eo-btn-primary"
                  style={{ fontSize: '0.8rem', padding: '0.45rem 0.95rem' }}
                  onClick={() => onOpenPayment?.(item)}
                >
                  Thanh toán &amp; QR
                </button>
              )}

              {item.eoConfirmed && item.paymentStatus && item.paymentStatus !== 'NOT_DECLARED' && item.paymentStatus !== 'CONFIRMED_FULL' && (
                <button
                  type="button"
                  className="eo-btn-secondary"
                  style={{ fontSize: '0.8rem', padding: '0.45rem 0.95rem' }}
                  onClick={() => onOpenPayment?.(item)}
                >
                  Cập nhật chứng từ
                </button>
              )}

              {item.paymentProofUrl && (
                <button
                  type="button"
                  className="eo-btn-secondary"
                  style={{ fontSize: '0.78rem', padding: '0.4rem 0.75rem' }}
                  onClick={() =>
                    onPreviewImage?.({
                      url: resolveAssetUrl(item.paymentProofUrl),
                      title: `Chứng từ thanh toán - ${item.workerName}`,
                    })
                  }
                >
                  Xem biên lai
                </button>
              )}

              {workerQrUrl && (
                <button
                  type="button"
                  className="eo-btn-secondary"
                  style={{ fontSize: '0.78rem', padding: '0.4rem 0.75rem' }}
                  onClick={() =>
                    onPreviewImage?.({
                      url: workerQrUrl,
                      title: `Mã VietQR nhận lương - ${item.workerName}`,
                    })
                  }
                >
                  Mã QR nhận lương
                </button>
              )}

              {!item.eoConfirmed && !hasDeclared && (
                <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                  Mở thanh toán sau khi duyệt công cho nhân sự.
                </span>
              )}
            </div>
          </section>

          {/* 3. Đánh giá sau ca */}
          {isCompleted && (
            <section style={{ border: '1px solid #e2e8f0', borderRadius: 12, padding: '1rem' }}>
              <h4 style={{ margin: '0 0 0.65rem', fontSize: '0.88rem', fontWeight: 700, color: '#0f172a' }}>
                Đánh giá sau ca làm việc
              </h4>
              {item.hasReviewedWorker ? (
                <span className="table-status-pill green">
                  <span className="status-dot-solid" />
                  Đã gửi đánh giá cho nhân sự này
                </span>
              ) : (
                <WorkerReviewForm
                  assignment={item}
                  submitting={submittingReview}
                  onSubmitReview={onSubmitReview}
                  layout="inline"
                />
              )}
            </section>
          )}
        </div>
      </aside>
    </div>,
    document.body
  );
}
