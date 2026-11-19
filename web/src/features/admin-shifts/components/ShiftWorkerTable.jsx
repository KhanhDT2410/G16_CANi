import React, { useState } from 'react';
import { WORKER_ASSIGNMENT_STATUS_CONFIG } from '../types/adminShift.constants';

/**
 * Bảng đối soát nhân sự & tiến độ công việc trong ca làm việc cho Admin.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const ShiftWorkerTable = ({ workers = [], templateChecklist = [] }) => {
  const [selectedWorkerId, setSelectedWorkerId] = useState(null);
  const [previewImage, setPreviewImage] = useState(null);

  const formatDateTime = (isoStr) => {
    if (!isoStr) return '—';
    try {
      return new Date(isoStr).toLocaleString('vi-VN', {
        hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit',
      });
    } catch {
      return '—';
    }
  };

  const parseProgress = (rawProgress) => {
    if (!templateChecklist || templateChecklist.length === 0) {
      return { total: 0, done: 0, percent: 0, items: [] };
    }
    const total = templateChecklist.length;
    let done = 0;
    let progressMap = {};

    try {
      if (rawProgress) {
        const parsed = typeof rawProgress === 'string' ? JSON.parse(rawProgress) : rawProgress;
        if (Array.isArray(parsed)) {
          parsed.forEach((p) => {
            if (p && typeof p === 'object') {
              progressMap[p.id] = { completed: p.completed !== false, completedAt: p.completedAt };
            } else if (typeof p === 'string') {
              progressMap[p] = { completed: true };
            }
          });
        } else if (typeof parsed === 'object') {
          Object.keys(parsed).forEach((k) => {
            const v = parsed[k];
            progressMap[k] = { completed: v === true || v?.completed === true, completedAt: v?.completedAt };
          });
        }
      }
    } catch (e) {
      console.warn('Lỗi phân tích checklist_progress:', e);
    }

    const items = templateChecklist.map((tpl) => {
      const prog = progressMap[tpl.id];
      const isDone = Boolean(prog && prog.completed);
      if (isDone) done++;
      return {
        id: tpl.id,
        title: tpl.title,
        completed: isDone,
        completedAt: prog?.completedAt || null,
      };
    });

    const percent = total > 0 ? Math.round((done / total) * 100) : 0;
    return { total, done, percent, items };
  };

  if (!workers || workers.length === 0) {
    return (
      <div className="shift-workers-empty">
        Chưa có nhân viên nào được duyệt vào ca này.
      </div>
    );
  }

  return (
    <div className="shift-workers-wrapper">
      <div className="shift-workers-table-card">
        <table className="tickets-table" style={{ margin: 0 }}>
          <thead>
            <tr>
              <th style={{ minWidth: '170px' }}>Nhân Viên</th>
              <th style={{ width: '120px' }}>SĐT</th>
              <th style={{ width: '110px' }}>Trạng Thái</th>
              <th style={{ width: '150px' }}>Tiến Độ Việc</th>
              <th style={{ minWidth: '170px' }}>Điểm Danh QR</th>
              <th style={{ width: '110px' }}>EO Duyệt</th>
            </tr>
          </thead>
          <tbody>
            {workers.map((w) => {
              const wStatus = WORKER_ASSIGNMENT_STATUS_CONFIG[w.status] || {
                label: w.status, color: '#475569', bg: '#f1f5f9',
              };
              const { total, done, percent, items } = parseProgress(w.checklistProgress);
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
                      <span className="worker-status-badge" style={{ background: wStatus.bg, color: wStatus.color }}>
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
                            onClick={() => setPreviewImage(w.proofImageUrl)}
                          >
                            📸 Xem ảnh check-in
                          </button>
                        )}
                      </div>
                    </td>
                    <td>
                      {w.eoConfirmed ? (
                        <span className="eo-confirmed-badge done">Đã duyệt</span>
                      ) : (
                        <span className="eo-confirmed-badge pending">Chờ duyệt</span>
                      )}
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
                              <div key={task.id} className={`worker-task-item ${task.completed ? 'is-done' : 'is-pending'}`}>
                                <span className="task-check-icon">{task.completed ? '✓' : '○'}</span>
                                <span className="task-title">{task.title}</span>
                                {task.completed && task.completedAt && (
                                  <span className="task-time">({formatDateTime(task.completedAt)})</span>
                                )}
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
              <span>Ảnh Bằng Chứng Điểm Danh</span>
              <button type="button" onClick={() => setPreviewImage(null)}>&times;</button>
            </div>
            <img src={previewImage} alt="Bằng chứng check-in" className="proof-full-img" />
          </div>
        </div>
      )}
    </div>
  );
};

export default ShiftWorkerTable;
