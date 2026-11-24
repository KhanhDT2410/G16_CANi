import React, { useState } from 'react';
import { WORKER_ASSIGNMENT_STATUS_CONFIG } from '../types/adminShift.constants';
import { formatDateTime, parseWorkerChecklistProgress } from '../utils/shiftModalHelpers';

/**
 * Bảng đối soát nhân sự & tiến độ công việc trong ca làm việc cho Admin.
 * Hiển thị nút xem ảnh minh chứng & căn chỉnh thời gian sát lề phải chuẩn xác.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const ShiftWorkerTable = ({ workers = [], templateChecklist = [] }) => {
  const [selectedWorkerId, setSelectedWorkerId] = useState(null);
  const [previewImage, setPreviewImage] = useState(null);

  if (!workers || workers.length === 0) {
    return <div className="shift-workers-empty">Chưa có nhân viên nào được duyệt vào ca này.</div>;
  }

  return (
    <div className="shift-workers-wrapper">
      <div className="shift-workers-table-card">
        <table className="tickets-table" style={{ margin: 0, minWidth: '860px', width: '100%' }}>
          <thead>
            <tr>
              <th style={{ minWidth: '180px' }}>Nhân Viên</th>
              <th style={{ width: '120px', minWidth: '120px' }}>SĐT</th>
              <th style={{ width: '120px', minWidth: '120px' }}>Trạng Thái</th>
              <th style={{ width: '180px', minWidth: '180px' }}>Tiến Độ Việc</th>
              <th style={{ minWidth: '180px' }}>Điểm Danh QR</th>
              <th style={{ width: '110px', minWidth: '110px' }}>EO Duyệt</th>
            </tr>
          </thead>
          <tbody>
            {workers.map((w) => {
              const wStatus = WORKER_ASSIGNMENT_STATUS_CONFIG[w.status] || {
                label: w.status, color: '#475569', bg: '#f1f5f9',
              };
              const { total, done, percent, items } = parseWorkerChecklistProgress(templateChecklist, w.checklistProgress);
              const isExpanded = selectedWorkerId === w.assignmentId;

              return (
                <React.Fragment key={w.assignmentId}>
                  <tr>
                    <td>
                      <div className="worker-name-cell">
                        <strong>{w.workerName}</strong>
                        {w.isGroupLeader && <span className="leader-pill">Trưởng nhóm</span>}
                      </div>
                      <div className="worker-email-sub">{w.workerEmail}</div>
                    </td>
                    <td>{w.workerPhone || '—'}</td>
                    <td>
                      <span
                        className="worker-status-badge"
                        style={{ background: wStatus.bg, color: wStatus.color, borderColor: wStatus.border || '#cbd5e1' }}
                      >
                        {wStatus.label}
                      </span>
                    </td>
                    <td>
                      {total > 0 ? (
                        <div
                          className="worker-progress-box"
                          onClick={() => setSelectedWorkerId(isExpanded ? null : w.assignmentId)}
                          title="Bấm để xem chi tiết từng đầu việc"
                        >
                          <div className="progress-bar-bg">
                            <div className="progress-bar-fill" style={{ width: `${percent}%` }} />
                          </div>
                          <span className="progress-text">
                            {done}/{total} việc ({percent}%) {isExpanded ? '▴' : '▾'}
                          </span>
                        </div>
                      ) : (
                        <span className="text-muted-sm">Chưa có checklist</span>
                      )}
                    </td>
                    <td>
                      <div className="att-info-box">
                        <div>Vào: {w.checkinTime ? formatDateTime(w.checkinTime) : 'Chưa điểm danh'}</div>
                        {w.checkoutTime && <div>Ra: {formatDateTime(w.checkoutTime)}</div>}
                        {w.proofImageUrl && (
                          <button
                            type="button"
                            className="proof-img-link"
                            onClick={() => setPreviewImage({
                              url: w.proofImageUrl,
                              title: `Ảnh điểm danh QR check-in - ${w.workerName}`,
                              time: w.checkinTime,
                            })}
                          >
                            Xem ảnh check-in
                          </button>
                        )}
                      </div>
                    </td>
                    <td>
                      <span className={`eo-confirmed-badge ${w.eoConfirmed ? 'done' : 'pending'}`}>
                        {w.eoConfirmed ? 'Đã duyệt' : 'Chờ duyệt'}
                      </span>
                    </td>
                  </tr>

                  {isExpanded && (
                    <tr className="worker-checklist-expand-row">
                      <td colSpan={6}>
                        <div className="worker-checklist-details">
                          <div className="worker-checklist-title">
                            Chi tiết đầu việc của <strong>{w.workerName}</strong>:
                          </div>
                          <div className="worker-tasks-list">
                            {items.map((task) => (
                              <div
                                key={task.id}
                                className={`worker-task-item ${task.completed ? 'is-done' : 'is-pending'}`}
                                style={{
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'space-between',
                                  gap: '0.65rem',
                                  padding: '0.4rem 0.75rem',
                                }}
                              >
                                {/* Cột trái: Checkbox icon & Tên đầu việc */}
                                <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem', flex: 1, minWidth: 0 }}>
                                  <span className="task-check-icon">{task.completed ? '✓' : '○'}</span>
                                  <span className="task-title">{task.title}</span>
                                </div>

                                {/* Cột phải: Nút minh chứng & Thời gian luôn bám sát lề phải */}
                                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexShrink: 0, marginLeft: 'auto' }}>
                                  {task.isRequiredEvidence && !task.evidenceImageUrl && (
                                    <span
                                      style={{
                                        fontSize: '0.72rem',
                                        fontWeight: 600,
                                        padding: '2px 6px',
                                        borderRadius: 4,
                                        background: '#fef2f2',
                                        color: '#dc2626',
                                        border: '1px solid #fecaca',
                                        whiteSpace: 'nowrap',
                                      }}
                                    >
                                      Cần minh chứng
                                    </span>
                                  )}

                                  {task.evidenceImageUrl && (
                                    <button
                                      type="button"
                                      className="proof-img-link"
                                      onClick={() => setPreviewImage({
                                        url: task.evidenceImageUrl,
                                        title: `Ảnh minh chứng: ${task.title} (${w.workerName})`,
                                        time: task.evidenceSubmittedAt || task.completedAt,
                                      })}
                                      style={{
                                        fontSize: '0.75rem',
                                        fontWeight: 600,
                                        padding: '2px 8px',
                                        background: '#eff6ff',
                                        color: '#1d4ed8',
                                        border: '1px solid #bfdbfe',
                                        borderRadius: 4,
                                        cursor: 'pointer',
                                        whiteSpace: 'nowrap',
                                      }}
                                    >
                                      Xem ảnh minh chứng
                                    </button>
                                  )}

                                  {task.completed && task.completedAt && (
                                    <span
                                      style={{
                                        fontSize: '0.75rem',
                                        color: '#64748b',
                                        whiteSpace: 'nowrap',
                                        minWidth: '115px',
                                        textAlign: 'right',
                                      }}
                                    >
                                      ({formatDateTime(task.completedAt)})
                                    </span>
                                  )}
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              );
            })}
          </tbody>
        </table>
      </div>

      {previewImage && (
        <div className="proof-lightbox-overlay" onClick={() => setPreviewImage(null)}>
          <div className="proof-lightbox-card" onClick={(e) => e.stopPropagation()}>
            <div className="proof-lightbox-header">
              <div>
                <div style={{ fontWeight: 700, fontSize: '0.92rem', color: '#0f172a' }}>
                  {previewImage.title || 'Ảnh Bằng Chứng'}
                </div>
                {previewImage.time && (
                  <div style={{ fontSize: '0.78rem', color: '#64748b', marginTop: '2px', fontWeight: 500 }}>
                    Thời gian nộp: {formatDateTime(previewImage.time)}
                  </div>
                )}
              </div>
              <button type="button" onClick={() => setPreviewImage(null)} aria-label="Đóng">✕</button>
            </div>
            <img src={previewImage.url || previewImage} alt="Bằng chứng" className="proof-full-img" />
          </div>
        </div>
      )}
    </div>
  );
};

export default ShiftWorkerTable;
