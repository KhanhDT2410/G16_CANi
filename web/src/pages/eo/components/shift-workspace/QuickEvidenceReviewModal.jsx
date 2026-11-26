import React, { useState, useEffect, useMemo } from 'react';
import { createPortal } from 'react-dom';
import { resolveAssetUrl } from '../../utils/resolveAssetUrl';
import {
  canEoConfirmWorker,
  formatTimeShort,
  getWorkerChecklistSummary,
  parseWorkerChecklistProgressMap,
} from './shiftWorkspaceHelpers';

/**
 * Quick Evidence Review Modal (Rà minh chứng nhanh 40+ worker theo hàng đợi)
 * EO lướt qua từng worker, xem ảnh minh chứng bên trái, checklist bên phải và duyệt liên hoàn.
 */
export default function QuickEvidenceReviewModal({
  isOpen,
  queue = [],
  initialIndex = 0,
  shiftChecklistTasks = [],
  verifyingId,
  onClose,
  onVerify,
  onReportIncomplete,
}) {
  const [index, setIndex] = useState(initialIndex);
  const [activePhotoIdx, setActivePhotoIdx] = useState(0);

  useEffect(() => {
    if (isOpen) {
      setIndex(Math.min(Math.max(0, initialIndex), Math.max(0, queue.length - 1)));
      setActivePhotoIdx(0);
    }
  }, [isOpen, initialIndex, queue.length]);

  useEffect(() => {
    setActivePhotoIdx(0);
  }, [index]);

  const currentItem = queue[index] || null;

  const summary = useMemo(
    () => (currentItem ? getWorkerChecklistSummary(currentItem, shiftChecklistTasks) : { evidenceItems: [], total: 0, done: 0, missingEvidence: 0 }),
    [currentItem, shiftChecklistTasks]
  );

  const photos = summary.evidenceItems || [];
  const activePhoto = photos[activePhotoIdx] || photos[0] || null;

  useEffect(() => {
    if (!isOpen) return undefined;
    const onKeyDown = (e) => {
      if (e.key === 'Escape') {
        onClose?.();
      } else if (e.key === 'ArrowLeft' && index > 0) {
        setIndex((i) => Math.max(0, i - 1));
      } else if (e.key === 'ArrowRight' && index < queue.length - 1) {
        setIndex((i) => Math.min(queue.length - 1, i + 1));
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [isOpen, index, queue.length, onClose]);

  if (!isOpen || !currentItem) return null;

  const canVerify = canEoConfirmWorker(currentItem, shiftChecklistTasks);
  const progressMap = parseWorkerChecklistProgressMap(currentItem.checklistRawProgress);
  const isBusy = verifyingId === currentItem.assignmentId;

  const handleApproveAndNext = async () => {
    const ok = await onVerify?.(currentItem);
    if (ok !== false) {
      if (index < queue.length - 1) {
        setIndex((i) => Math.min(queue.length - 1, i + 1));
      } else {
        onClose?.();
      }
    }
  };

  return createPortal(
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.78)',
        backdropFilter: 'blur(4px)',
        zIndex: 10020,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '1rem',
      }}
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label="Rà minh chứng nhanh"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          width: 'min(980px, 96vw)',
          maxHeight: '90vh',
          background: '#ffffff',
          borderRadius: '16px',
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 24px 48px rgba(15, 23, 42, 0.35)',
        }}
      >
        {/* Top bar */}
        <div
          style={{
            padding: '0.9rem 1.25rem',
            borderBottom: '1px solid #e2e8f0',
            background: '#f8fafc',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: '0.75rem',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
            <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 800, color: '#0f172a' }}>
              Rà minh chứng &amp; Duyệt nhanh
            </h3>
            <span
              style={{
                padding: '2px 10px',
                borderRadius: 999,
                background: '#eff6ff',
                color: '#1d4ed8',
                border: '1px solid #bfdbfe',
                fontSize: '0.78rem',
                fontWeight: 700,
              }}
            >
              Nhân sự {index + 1} / {queue.length}
            </span>
          </div>
          <button
            type="button"
            onClick={onClose}
            style={{
              background: '#ffffff',
              border: '1px solid #cbd5e1',
              borderRadius: 8,
              width: 32,
              height: 32,
              cursor: 'pointer',
              color: '#334155',
            }}
          >
            ✕
          </button>
        </div>

        {/* 2-Column Content */}
        <div
          style={{
            flex: 1,
            overflow: 'hidden',
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 340px), 1fr))',
          }}
        >
          {/* Left: Evidence Viewer */}
          <div
            style={{
              background: '#0f172a',
              padding: '1rem',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              minHeight: '340px',
              maxHeight: '65vh',
              gap: '0.75rem',
            }}
          >
            {activePhoto ? (
              <>
                <img
                  src={resolveAssetUrl(activePhoto.url)}
                  alt={activePhoto.label}
                  style={{
                    maxWidth: '100%',
                    maxHeight: '48vh',
                    objectFit: 'contain',
                    borderRadius: 10,
                    background: '#ffffff',
                  }}
                />
                <div style={{ color: '#e2e8f0', fontSize: '0.8rem', fontWeight: 600, textAlign: 'center' }}>
                  {activePhoto.label}
                  {activePhoto.submittedAt ? ` • ${formatTimeShort(activePhoto.submittedAt)}` : ''}
                </div>
                {photos.length > 1 && (
                  <div style={{ display: 'flex', gap: '0.45rem', flexWrap: 'wrap', justifyContent: 'center' }}>
                    {photos.map((p, idx) => (
                      <button
                        key={p.id || idx}
                        type="button"
                        onClick={() => setActivePhotoIdx(idx)}
                        style={{
                          padding: 2,
                          borderRadius: 6,
                          border: idx === activePhotoIdx ? '2px solid #3b82f6' : '1px solid #475569',
                          background: '#1e293b',
                          cursor: 'pointer',
                        }}
                      >
                        <img
                          src={resolveAssetUrl(p.url)}
                          alt={p.label}
                          style={{ width: 44, height: 44, objectFit: 'cover', borderRadius: 4, display: 'block' }}
                        />
                      </button>
                    ))}
                  </div>
                )}
              </>
            ) : (
              <div style={{ color: '#94a3b8', textAlign: 'center', padding: '1.5rem' }}>
                <div style={{ fontWeight: 700, color: '#e2e8f0', marginBottom: 6 }}>
                  Không có ảnh minh chứng đính kèm
                </div>
                <div style={{ fontSize: '0.82rem' }}>
                  {summary.missingEvidence > 0
                    ? `Nhân sự đang thiếu ${summary.missingEvidence} ảnh bắt buộc.`
                    : 'Ca làm việc này không yêu cầu ảnh minh chứng.'}
                </div>
              </div>
            )}
          </div>

          {/* Right: Worker Info & Checklist */}
          <div
            style={{
              padding: '1.15rem 1.35rem',
              overflowY: 'auto',
              maxHeight: '65vh',
              display: 'flex',
              flexDirection: 'column',
              gap: '0.9rem',
            }}
          >
            <div>
              <div style={{ fontSize: '1.1rem', fontWeight: 800, color: '#0f172a' }}>
                {currentItem.workerName}
              </div>
              <div style={{ fontSize: '0.82rem', color: '#64748b', marginTop: 3 }}>
                {currentItem.workerPhone || 'Chưa có SĐT'} • Vào: {formatTimeShort(currentItem.checkinTime)}{' '}
                {currentItem.isOnTime === false ? '(Trễ giờ)' : '(Đúng giờ)'} • Ra: {formatTimeShort(currentItem.checkoutTime)}
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
              <span className={`table-status-pill ${currentItem.eoConfirmed ? 'green' : canVerify ? 'blue' : 'yellow'}`}>
                <span className="status-dot-solid" />
                {currentItem.eoConfirmed
                  ? 'Đã duyệt công'
                  : canVerify
                    ? 'Đủ điều kiện duyệt'
                    : 'Chưa đủ điều kiện duyệt'}
              </span>
              <span style={{ fontSize: '0.8rem', color: '#475569', fontWeight: 600 }}>
                Hoàn thành: {summary.done}/{summary.total} đầu việc
              </span>
            </div>

            {shiftChecklistTasks.length > 0 ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                {shiftChecklistTasks.map((t) => {
                  const prog = progressMap[t.id] || progressMap[t.label] || {};
                  const isDone = Boolean(prog.isCompleted || currentItem.isChecklistCompleted);
                  const hasImg = Boolean(prog.evidenceImageUrl && String(prog.evidenceImageUrl).trim());
                  return (
                    <div
                      key={t.id}
                      style={{
                        padding: '0.5rem 0.7rem',
                        borderRadius: 8,
                        border: '1px solid #e2e8f0',
                        background: '#f8fafc',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        fontSize: '0.8rem',
                        gap: '0.5rem',
                      }}
                    >
                      <span style={{ fontWeight: 600, color: isDone ? '#0f172a' : '#64748b' }}>
                        {isDone ? '✓' : '○'} {t.label}
                      </span>
                      {t.isRequiredEvidence && (
                        <span style={{ fontSize: '0.72rem', fontWeight: 700, color: hasImg ? '#059669' : '#dc2626' }}>
                          {hasImg ? 'Có ảnh' : 'Thiếu ảnh'}
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>
            ) : (
              <div style={{ fontSize: '0.82rem', color: '#64748b', fontStyle: 'italic' }}>
                Ca không có danh mục đầu việc mẫu.
              </div>
            )}
          </div>
        </div>

        {/* Footer Actions */}
        <div
          style={{
            padding: '0.9rem 1.25rem',
            borderTop: '1px solid #e2e8f0',
            background: '#f8fafc',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '0.65rem',
          }}
        >
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button
              type="button"
              className="eo-btn-secondary"
              disabled={index <= 0 || isBusy}
              onClick={() => setIndex((i) => Math.max(0, i - 1))}
              style={{ fontSize: '0.82rem', padding: '0.45rem 0.85rem' }}
            >
              ← Trước
            </button>
            <button
              type="button"
              className="eo-btn-secondary"
              disabled={index >= queue.length - 1 || isBusy}
              onClick={() => setIndex((i) => Math.min(queue.length - 1, i + 1))}
              style={{ fontSize: '0.82rem', padding: '0.45rem 0.85rem' }}
            >
              Bỏ qua →
            </button>
          </div>

          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            <button
              type="button"
              className="eo-btn-secondary"
              disabled={isBusy}
              onClick={() => onReportIncomplete?.(currentItem)}
              style={{ fontSize: '0.82rem', padding: '0.45rem 0.9rem', color: '#b45309', borderColor: '#fde68a' }}
            >
              Báo lỗi công việc
            </button>

            {!currentItem.eoConfirmed && (
              <button
                type="button"
                className="eo-btn-primary"
                disabled={!canVerify || isBusy}
                onClick={handleApproveAndNext}
                style={{
                  fontSize: '0.84rem',
                  padding: '0.48rem 1.1rem',
                  opacity: !canVerify || isBusy ? 0.55 : 1,
                  cursor: !canVerify || isBusy ? 'not-allowed' : 'pointer',
                }}
              >
                {isBusy
                  ? 'Đang duyệt...'
                  : index < queue.length - 1
                    ? '✓ Duyệt & Sang người tiếp theo →'
                    : '✓ Duyệt công & Hoàn tất'}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>,
    document.body
  );
}
