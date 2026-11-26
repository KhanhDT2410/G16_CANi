import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';
import WorkerReviewForm from './WorkerReviewForm';
import { WorkerProfileModal } from './worker-profile';
import { useWorkerProfile } from '../hooks/useWorkerProfile';
import './EOEventsTab.css';

function formatDateTime(iso) {
  if (!iso) return '--';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '--';
  return `${d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })} • ${d.toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })}`;
}

export default function EOWorkerReviewsTab({ onReloadEvents, embedded = false }) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [statusFilter, setStatusFilter] = useState('ALL'); // 'ALL' | 'PENDING' | 'REVIEWED'
  const [eventFilter, setEventFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedForReview, setSelectedForReview] = useState(null);
  const [submittingReview, setSubmittingReview] = useState(false);
  const workerProfile = useWorkerProfile();

  const loadOverview = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await eoService.getWorkerReviewOverview();
      setItems(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Không thể tải danh sách đánh giá nhân sự.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadOverview();
  }, [loadOverview]);

  const eventOptions = useMemo(() => {
    const map = new Map();
    items.forEach((it) => {
      if (it?.eventId && !map.has(it.eventId)) {
        map.set(it.eventId, it.eventName || 'Sự kiện');
      }
    });
    return Array.from(map.entries()).map(([eventId, eventName]) => ({ eventId, eventName }));
  }, [items]);

  const stats = useMemo(() => {
    const pendingCount = items.filter((it) => !it.hasReviewedWorker && it.canReview).length;
    const waitingCount = items.filter((it) => !it.hasReviewedWorker && !it.canReview).length;
    const reviewedItems = items.filter((it) => it.hasReviewedWorker);
    const avgGiven = reviewedItems.length
      ? (reviewedItems.reduce((sum, it) => sum + Number(it.rating || 0), 0) / reviewedItems.length).toFixed(1)
      : '—';
    return {
      total: items.length,
      pendingCount,
      waitingCount,
      reviewedCount: reviewedItems.length,
      avgGiven,
    };
  }, [items]);

  const filteredItems = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return items.filter((it) => {
      if (eventFilter !== 'ALL' && it.eventId !== eventFilter) return false;
      if (statusFilter === 'PENDING' && it.hasReviewedWorker) return false;
      if (statusFilter === 'REVIEWED' && !it.hasReviewedWorker) return false;
      if (!q) return true;
      return (
        (it.workerName || '').toLowerCase().includes(q) ||
        (it.workerPhone || '').toLowerCase().includes(q) ||
        (it.jobType || '').toLowerCase().includes(q) ||
        (it.eventName || '').toLowerCase().includes(q)
      );
    });
  }, [items, eventFilter, statusFilter, searchQuery]);

  const handleSubmitReview = async (reviewData) => {
    setSubmittingReview(true);
    try {
      await eoService.rateWorker(reviewData.assignmentId, reviewData);
      showToast('Đã gửi đánh giá nhân sự thành công! Điểm uy tín đã được cập nhật.', 'success');
      setSelectedForReview(null);
      await Promise.allSettled([
        loadOverview(),
        onReloadEvents ? Promise.resolve(onReloadEvents()) : Promise.resolve(),
      ]);
    } catch (err) {
      showToast('Không thể gửi đánh giá: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setSubmittingReview(false);
    }
  };

  return (
    <div className="ev-page-stack animate-fade-in-up">
      {!embedded && (
        <header className="ev-page-heading">
          <div>
            <h1>Đánh giá nhân sự</h1>
            <p>Chấm điểm nhanh tác phong và mức độ hoàn thành của nhân sự sau khi kết ca.</p>
          </div>
          <button type="button" className="eo-btn-secondary" onClick={loadOverview} disabled={loading}>
            {loading ? 'Đang tải...' : 'Làm mới'}
          </button>
        </header>
      )}

      {/* KPI Tổng quan đồng bộ chuẩn EOEventsTab.css */}
      <section className="ev-kpi-grid" aria-label="Thống kê đánh giá nhân sự">
        <div className="ev-kpi-card">
          <span className="ev-kpi-label">Chờ đánh giá</span>
          <strong className={`ev-kpi-val ${stats.pendingCount > 0 ? 'is-amber' : ''}`}>
            {stats.pendingCount}
          </strong>
          <span className="ev-kpi-sub">lượt đã đủ điều kiện chấm điểm</span>
        </div>
        <div className="ev-kpi-card">
          <span className="ev-kpi-label">Đã đánh giá</span>
          <strong className="ev-kpi-val">{stats.reviewedCount}</strong>
          <span className="ev-kpi-sub">lượt đã lưu nhận xét</span>
        </div>
        <div className="ev-kpi-card">
          <span className="ev-kpi-label">Điểm trung bình đã chấm</span>
          <strong className="ev-kpi-val is-blue">{stats.avgGiven}</strong>
          <span className="ev-kpi-sub">trên thang 5.0 sao</span>
        </div>
        <div className="ev-kpi-card">
          <span className="ev-kpi-label">Tổng lượt tham gia ca</span>
          <strong className="ev-kpi-val">{stats.total}</strong>
          <span className="ev-kpi-sub">
            {stats.waitingCount > 0 ? `${stats.waitingCount} lượt chờ kết ca` : 'Tất cả đã đối soát'}
          </span>
        </div>
      </section>

      {/* Bảng danh sách gọn */}
      <section className="eo-events-section-card animate-fade-in-up">
        <div className="table-card-header-bar">
          <div className="table-header-title-group">
            <h3 className="table-card-heading">Danh sách lượt làm việc</h3>
            <span className="ev-result-count">{filteredItems.length} kết quả</span>
          </div>

          <div className="table-header-actions-group">
            <div className="table-search-input-wrapper">
              <input
                type="text"
                className="table-search-input"
                placeholder="Tìm theo tên, SĐT, vị trí ca..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                aria-label="Tìm nhân sự cần đánh giá"
              />
            </div>

            <div className="table-select-wrapper">
              <select
                className="table-filter-select"
                value={eventFilter}
                onChange={(e) => setEventFilter(e.target.value)}
                aria-label="Lọc theo sự kiện"
              >
                <option value="ALL">Tất cả sự kiện</option>
                {eventOptions.map((ev) => (
                  <option key={ev.eventId} value={ev.eventId}>
                    {ev.eventName}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

        <div className="ev-filter-chips-bar" role="tablist" aria-label="Lọc trạng thái đánh giá">
          {[
            { key: 'ALL', label: `Tất cả (${items.length})` },
            { key: 'PENDING', label: `Chưa đánh giá (${stats.pendingCount + stats.waitingCount})` },
            { key: 'REVIEWED', label: `Đã đánh giá (${stats.reviewedCount})` },
          ].map((tab) => {
            const isActive = statusFilter === tab.key;
            return (
              <button
                key={tab.key}
                type="button"
                role="tab"
                aria-selected={isActive}
                className={`ev-filter-chip ${isActive ? 'is-active' : ''}`}
                onClick={() => setStatusFilter(tab.key)}
              >
                {tab.label}
              </button>
            );
          })}
        </div>

        <div className="table-responsive-wrapper">
          {loading ? (
            <div style={{ padding: '3rem 1rem', textAlign: 'center', color: '#64748b' }}>
              <div className="cani-spinner" style={{ margin: '0 auto 0.75rem' }} />
              <span>Đang tải danh sách nhân sự...</span>
            </div>
          ) : error ? (
            <div style={{ padding: '2.5rem 1rem', textAlign: 'center', color: '#dc2626' }}>
              <p style={{ margin: '0 0 0.75rem', fontWeight: 700 }}>{error}</p>
              <button type="button" className="eo-btn-primary" onClick={loadOverview}>
                Thử lại
              </button>
            </div>
          ) : filteredItems.length === 0 ? (
            <div style={{ padding: '3rem 1rem', textAlign: 'center', color: '#64748b' }}>
              <p style={{ margin: '0 0 0.35rem', fontWeight: 700, color: '#0f172a', fontSize: '0.95rem' }}>
                Chưa có lượt làm việc nào phù hợp bộ lọc
              </p>
              <span style={{ fontSize: '0.84rem' }}>
                Sau khi nhân sự kết ca, bạn có thể chấm điểm và nhận xét tại đây.
              </span>
            </div>
          ) : (
            <table className="cani-modern-table">
              <thead>
                <tr>
                  <th style={{ width: '26%' }}>Nhân sự</th>
                  <th style={{ width: '28%' }}>Sự kiện &amp; Ca làm</th>
                  <th style={{ width: '28%' }}>Kết quả đánh giá</th>
                  <th style={{ width: '18%', textAlign: 'right' }}>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {filteredItems.map((item) => {
                  const isExpanded = selectedForReview?.assignmentId === item.assignmentId;
                  return (
                    <React.Fragment key={item.assignmentId}>
                      <tr>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
                            <div
                              onClick={() => (item.workerId || item.userId) && workerProfile.openProfile(item.workerId || item.userId)}
                              style={{
                                width: 34,
                                height: 34,
                                borderRadius: '50%',
                                background: '#eff6ff',
                                color: '#2563eb',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                fontWeight: 700,
                                fontSize: '0.85rem',
                                overflow: 'hidden',
                                flexShrink: 0,
                                cursor: 'pointer',
                              }}
                              title="Xem hồ sơ nhân sự"
                            >
                              {item.avatarUrl ? (
                                <img src={item.avatarUrl} alt={item.workerName} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                              ) : (
                                (item.workerName || 'W').charAt(0).toUpperCase()
                              )}
                            </div>
                            <div style={{ minWidth: 0 }}>
                              <button
                                type="button"
                                onClick={() => (item.workerId || item.userId) && workerProfile.openProfile(item.workerId || item.userId)}
                                style={{
                                  background: 'none',
                                  border: 'none',
                                  padding: 0,
                                  fontSize: '0.88rem',
                                  fontWeight: 700,
                                  color: '#0f172a',
                                  cursor: 'pointer',
                                  textAlign: 'left',
                                }}
                              >
                                {item.workerName || 'Nhân sự'}
                              </button>
                              <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                                {item.workerPhone || 'Chưa có SĐT'} • Uy tín {item.workerReliabilityScore ?? 100}/100
                              </div>
                            </div>
                          </div>
                        </td>

                        <td>
                          <div style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.84rem' }}>{item.eventName}</div>
                          <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: 2 }}>
                            {item.jobType} • Kết ca: {formatDateTime(item.checkoutTime || item.shiftEndTime)}
                          </div>
                        </td>

                        <td>
                          {item.hasReviewedWorker ? (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem' }}>
                              <span style={{ fontSize: '0.82rem', fontWeight: 700, color: '#0f172a' }}>
                                <span style={{ color: '#f59e0b' }}>★</span> {item.rating || 5}/5
                                <span style={{ color: '#059669', fontWeight: 600, marginLeft: 6, fontSize: '0.74rem' }}>
                                  • Đã đánh giá
                                </span>
                              </span>
                              {item.comment && (
                                <span style={{ fontSize: '0.76rem', color: '#64748b', fontStyle: 'italic' }}>
                                  "{item.comment}"
                                </span>
                              )}
                            </div>
                          ) : item.canReview ? (
                            <span className="table-status-pill yellow">
                              <span className="status-dot-solid" />
                              Chờ đánh giá
                            </span>
                          ) : (
                            <span className="table-status-pill gray" title={item.reviewBlockReason || ''}>
                              <span className="status-dot-solid" />
                              {item.reviewBlockReason || 'Chưa đủ điều kiện'}
                            </span>
                          )}
                        </td>

                        <td style={{ textAlign: 'right' }}>
                          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}>
                            {(item.workerId || item.userId) && (
                              <button
                                type="button"
                                className="eo-btn-secondary"
                                onClick={() => workerProfile.openProfile(item.workerId || item.userId)}
                                style={{ fontSize: '0.76rem', padding: '0.35rem 0.65rem' }}
                              >
                                Hồ sơ
                              </button>
                            )}
                            {!item.hasReviewedWorker && item.canReview && (
                              <button
                                type="button"
                                className={isExpanded ? 'eo-btn-secondary' : 'eo-btn-primary'}
                                onClick={() => setSelectedForReview((prev) => (prev?.assignmentId === item.assignmentId ? null : item))}
                                style={{ fontSize: '0.76rem', padding: '0.35rem 0.75rem', fontWeight: 700 }}
                              >
                                {isExpanded ? 'Đóng' : 'Đánh giá'}
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>

                      {isExpanded && (
                        <tr>
                          <td colSpan={4} style={{ backgroundColor: '#f8fafc', padding: '1rem 1.25rem' }}>
                            <WorkerReviewForm
                              assignment={item}
                              submitting={submittingReview}
                              onCancel={() => setSelectedForReview(null)}
                              onSubmitReview={handleSubmitReview}
                              layout="inline"
                            />
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      </section>

      <WorkerProfileModal
        isOpen={workerProfile.isOpen}
        loading={workerProfile.loading}
        error={workerProfile.error}
        profile={workerProfile.profile}
        onClose={workerProfile.closeProfile}
        onRetry={workerProfile.retry}
      />
    </div>
  );
}
