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

export default function EOWorkerReviewsTab({ onReloadEvents }) {
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
      <header className="ev-page-heading">
        <div>
          <h1>Đánh giá nhân sự (Worker)</h1>
          <p>Theo dõi và chấm điểm năng lực, tác phong của nhân sự sau khi hoàn thành ca làm việc.</p>
        </div>
        <button type="button" className="eo-btn-secondary" onClick={loadOverview} disabled={loading}>
          {loading ? 'Đang tải...' : 'Làm mới'}
        </button>
      </header>

      {/* KPI Tổng quan */}
      <section className="ev-kpi-strip" aria-label="Thống kê đánh giá nhân sự">
        <div className="ev-kpi-card">
          <span className="ev-kpi-label">Chờ đánh giá</span>
          <strong className="ev-kpi-value" style={{ color: stats.pendingCount > 0 ? '#d97706' : '#0f172a' }}>
            {stats.pendingCount}
          </strong>
          <span className="ev-kpi-sub">lượt đã đủ điều kiện chấm điểm</span>
        </div>
        <div className="ev-kpi-card">
          <span className="ev-kpi-label">Đã đánh giá</span>
          <strong className="ev-kpi-value" style={{ color: '#059669' }}>{stats.reviewedCount}</strong>
          <span className="ev-kpi-sub">lượt đã lưu nhận xét</span>
        </div>
        <div className="ev-kpi-card">
          <span className="ev-kpi-label">Điểm trung bình đã chấm</span>
          <strong className="ev-kpi-value" style={{ color: '#2563eb' }}>{stats.avgGiven}</strong>
          <span className="ev-kpi-sub">trên thang 5.0 sao</span>
        </div>
        <div className="ev-kpi-card">
          <span className="ev-kpi-label">Tổng lượt tham gia ca</span>
          <strong className="ev-kpi-value">{stats.total}</strong>
          <span className="ev-kpi-sub">
            {stats.waitingCount > 0 ? `${stats.waitingCount} lượt chờ kết ca / xử lý` : 'Tất cả đã đối soát'}
          </span>
        </div>
      </section>

      {/* Bộ lọc */}
      <section className="ev-toolbar-card">
        <div className="ev-filter-pills" role="tablist" aria-label="Lọc trạng thái đánh giá">
          {[
            { key: 'ALL', label: `Tất cả (${items.length})` },
            { key: 'PENDING', label: `Chưa đánh giá (${stats.pendingCount + stats.waitingCount})` },
            { key: 'REVIEWED', label: `Đã đánh giá (${stats.reviewedCount})` },
          ].map((tab) => (
            <button
              key={tab.key}
              type="button"
              role="tab"
              aria-selected={statusFilter === tab.key}
              className={`ev-filter-pill ${statusFilter === tab.key ? 'is-active' : ''}`}
              onClick={() => setStatusFilter(tab.key)}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <div className="ev-search-controls">
          <input
            type="text"
            className="eo-form-input"
            placeholder="Tìm theo tên nhân sự, SĐT, vị trí ca..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{ minWidth: '230px' }}
          />
          <select
            className="eo-form-input"
            value={eventFilter}
            onChange={(e) => setEventFilter(e.target.value)}
            style={{ minWidth: '200px' }}
          >
            <option value="ALL">Tất cả sự kiện</option>
            {eventOptions.map((ev) => (
              <option key={ev.eventId} value={ev.eventId}>
                {ev.eventName}
              </option>
            ))}
          </select>
        </div>
      </section>

      {/* Nội dung danh sách */}
      <section className="eo-widget-card" style={{ padding: '1.25rem' }}>
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
            <p style={{ margin: '0 0 0.35rem', fontWeight: 700, color: '#0f172a', fontSize: '1rem' }}>
              Chưa có lượt làm việc nào phù hợp bộ lọc
            </p>
            <span style={{ fontSize: '0.85rem' }}>
              Sau khi nhân sự Check-out và hoàn thành ca làm việc, bạn có thể chấm điểm và nhận xét tại đây.
            </span>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
            {filteredItems.map((item) => {
              const isExpanded = selectedForReview?.assignmentId === item.assignmentId;
              return (
              <div
                key={item.assignmentId}
                style={{
                  border: isExpanded ? '1.5px solid #2563eb' : '1px solid #e2e8f0',
                  borderRadius: '12px',
                  padding: '1rem 1.15rem',
                  backgroundColor: '#ffffff',
                  transition: 'border-color 0.15s ease',
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'flex-start',
                    gap: '1rem',
                    flexWrap: 'wrap',
                  }}
                >
                <div style={{ display: 'flex', gap: '0.85rem', flex: '1 1 320px' }}>
                  <div
                    onClick={() => (item.workerId || item.userId) && workerProfile.openProfile(item.workerId || item.userId)}
                    style={{
                      width: 44,
                      height: 44,
                      borderRadius: '50%',
                      background: '#eff6ff',
                      color: '#2563eb',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 800,
                      fontSize: '1rem',
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

                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                      <button
                        type="button"
                        onClick={() => (item.workerId || item.userId) && workerProfile.openProfile(item.workerId || item.userId)}
                        style={{
                          background: 'none',
                          border: 'none',
                          padding: 0,
                          fontSize: '0.96rem',
                          fontWeight: 800,
                          color: '#0f172a',
                          cursor: 'pointer',
                        }}
                      >
                        {item.workerName || 'Nhân sự'}
                      </button>
                      <span
                        style={{
                          fontSize: '0.73rem',
                          fontWeight: 700,
                          padding: '2px 8px',
                          borderRadius: '999px',
                          background: '#eff6ff',
                          color: '#1d4ed8',
                        }}
                      >
                        {item.jobType}
                      </span>
                      <span style={{ fontSize: '0.78rem', color: '#64748b' }}>
                        Uy tín: <strong style={{ color: '#2563eb' }}>{item.workerReliabilityScore ?? 100}/100</strong>
                      </span>
                    </div>

                    <div style={{ fontSize: '0.82rem', color: '#475569', marginTop: '0.25rem' }}>
                      <strong>{item.eventName}</strong> • Kết ca: {formatDateTime(item.checkoutTime || item.shiftEndTime)}
                      {item.workerPhone && ` • SĐT: ${item.workerPhone}`}
                    </div>

                    {item.hasReviewedWorker && (
                      <div
                        style={{
                          marginTop: '0.6rem',
                          padding: '0.6rem 0.85rem',
                          borderRadius: '8px',
                          backgroundColor: '#f8fafc',
                          border: '1px solid #e2e8f0',
                          fontSize: '0.81rem',
                          color: '#334155',
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem', flexWrap: 'wrap', fontWeight: 700 }}>
                          <span style={{ color: '#b45309' }}>{'★'.repeat(item.rating || 5)} ({item.rating || 5}/5)</span>
                          {item.criteriaWorkCompletion != null && (
                            <span style={{ fontSize: '0.75rem', color: '#475569' }}>
                              Hoàn thành: {item.criteriaWorkCompletion}/5
                            </span>
                          )}
                          {item.criteriaPunctuality != null && (
                            <span style={{ fontSize: '0.75rem', color: '#475569' }}>
                              Đúng giờ: {item.criteriaPunctuality}/5
                            </span>
                          )}
                          {item.criteriaProfessionalism != null && (
                            <span style={{ fontSize: '0.75rem', color: '#475569' }}>
                              Tác phong: {item.criteriaProfessionalism}/5
                            </span>
                          )}
                        </div>
                        {item.comment && <div style={{ marginTop: '0.3rem', fontStyle: 'italic' }}>"{item.comment}"</div>}
                        {Array.isArray(item.tags) && item.tags.length > 0 && (
                          <div style={{ display: 'flex', gap: '0.35rem', flexWrap: 'wrap', marginTop: '0.4rem' }}>
                            {item.tags.map((t, idx) => (
                              <span
                                key={idx}
                                style={{
                                  fontSize: '0.72rem',
                                  fontWeight: 600,
                                  background: '#ffffff',
                                  border: '1px solid #cbd5e1',
                                  borderRadius: '999px',
                                  padding: '1px 8px',
                                  color: '#334155',
                                }}
                              >
                                {t}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                  {(item.workerId || item.userId) && (
                    <button
                      type="button"
                      className="eo-btn-secondary"
                      onClick={() => workerProfile.openProfile(item.workerId || item.userId)}
                      style={{ fontSize: '0.78rem', padding: '0.4rem 0.75rem' }}
                    >
                      Hồ sơ
                    </button>
                  )}

                  {item.hasReviewedWorker ? (
                    <span
                      style={{
                        padding: '2px 8px',
                        borderRadius: '4px',
                        background: '#ecfdf5',
                        border: '1px solid #a7f3d0',
                        color: '#047857',
                        fontSize: '0.78rem',
                        fontWeight: 500,
                      }}
                    >
                      Đã đánh giá
                    </span>
                  ) : item.canReview ? (
                    <button
                      type="button"
                      className={isExpanded ? 'eo-btn-secondary' : 'eo-btn-primary'}
                      onClick={() => setSelectedForReview((prev) => (prev?.assignmentId === item.assignmentId ? null : item))}
                      style={{ fontSize: '0.82rem', padding: '0.45rem 0.95rem', fontWeight: 700 }}
                    >
                      {isExpanded ? 'Thu gọn' : 'Đánh giá ngay'}
                    </button>
                  ) : (
                    <span
                      title={item.reviewBlockReason || ''}
                      style={{
                        padding: '0.4rem 0.8rem',
                        borderRadius: '8px',
                        background: '#f1f5f9',
                        border: '1px solid #cbd5e1',
                        color: '#64748b',
                        fontSize: '0.78rem',
                        fontWeight: 600,
                      }}
                    >
                      {item.reviewBlockReason || 'Chưa đủ điều kiện'}
                    </span>
                  )}
                </div>
                </div>

                {isExpanded && (
                  <div
                    style={{
                      marginTop: '0.95rem',
                      padding: '0.95rem 1rem',
                      borderRadius: '10px',
                      backgroundColor: '#f8fafc',
                      border: '1px solid #dbeafe',
                    }}
                  >
                    <WorkerReviewForm
                      assignment={item}
                      submitting={submittingReview}
                      onCancel={() => setSelectedForReview(null)}
                      onSubmitReview={handleSubmitReview}
                      layout="inline"
                    />
                  </div>
                )}
              </div>
              );
            })}
          </div>
        )}
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
