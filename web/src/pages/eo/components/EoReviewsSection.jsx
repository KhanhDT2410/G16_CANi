import { useState, useEffect, useMemo } from 'react';
import { eoService } from '../../../services/eoService';
import './EoProfileTab.css';

const CRITERIA_CONFIG = [
  { key: 'criteriaWorkEnvironment', label: 'Môi trường làm việc an toàn & tôn trọng' },
  { key: 'criteriaSupport', label: 'Hỗ trợ & hướng dẫn chu đáo' },
  { key: 'criteriaPaymentTiming', label: 'Thanh toán lương đúng hẹn' },
];

/**
 * Hiển thị danh sách nhận xét & đánh giá sao thực tế từ Người lao động (Worker) dành cho EO.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export default function EoReviewsSection() {
  const [reviews, setReviews] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filterStar, setFilterStar] = useState('ALL');

  useEffect(() => {
    let isMounted = true;
    const fetchReviews = async () => {
      try {
        const data = await eoService.getEoReviews();
        if (isMounted) setReviews(Array.isArray(data) ? data : []);
      } catch {
        if (isMounted) setReviews([]);
      } finally {
        if (isMounted) setLoading(false);
      }
    };
    fetchReviews();
    return () => { isMounted = false; };
  }, []);

  const stats = useMemo(() => {
    if (!reviews.length) return null;
    const total = reviews.length;
    const avg = (reviews.reduce((sum, r) => sum + (r.rating || 0), 0) / total).toFixed(1);

    const criteriaAvg = {};
    CRITERIA_CONFIG.forEach(({ key }) => {
      const valid = reviews.filter((r) => r[key] != null);
      criteriaAvg[key] = valid.length
        ? (valid.reduce((sum, r) => sum + r[key], 0) / valid.length).toFixed(1)
        : null;
    });

    const starCounts = { 5: 0, 4: 0, 3: 0, 2: 0, 1: 0 };
    reviews.forEach((r) => {
      const st = r.rating || 5;
      if (starCounts[st] !== undefined) starCounts[st]++;
    });

    return { total, avg, criteriaAvg, starCounts };
  }, [reviews]);

  const filteredReviews = useMemo(() => {
    if (filterStar === 'ALL') return reviews;
    if (filterStar === 3) return reviews.filter((r) => (r.rating || 0) <= 3);
    return reviews.filter((r) => (r.rating || 0) === Number(filterStar));
  }, [reviews, filterStar]);

  if (loading) {
    return (
      <div className="profile-card" style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>
        <div className="cani-spinner" style={{ margin: '0 auto 1rem' }} />
        <span>Đang tải danh sách đánh giá từ nhân sự...</span>
      </div>
    );
  }

  return (
    <div className="profile-card animate-fade-in">
      <div className="profile-card-header">
        <div className="profile-card-header-left">
          <h3>Đánh Giá & Nhận Xét Từ Nhân Sự</h3>
          <p>Phản hồi thực tế từ người lao động đã hoàn thành ca làm việc tại các sự kiện của bạn.</p>
        </div>
      </div>

      {stats && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '1.25rem', padding: '1.25rem', background: '#f8fafc', borderRadius: 12, margin: '1rem 0 1.5rem', border: '1px solid #e2e8f0' }}>
          <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
            <span style={{ fontSize: '0.8rem', color: '#64748b', fontWeight: 700, textTransform: 'uppercase' }}>Điểm uy tín trung bình</span>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.5rem', marginTop: '0.25rem' }}>
              <span style={{ fontSize: '2.5rem', fontWeight: 800, color: '#0f172a', lineHeight: 1 }}>{stats.avg}</span>
              <span style={{ fontSize: '1.1rem', color: '#94a3b8', fontWeight: 600 }}>/ 5.0</span>
            </div>
            <span style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '0.35rem' }}>Dựa trên <strong>{stats.total}</strong> lượt đánh giá</span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', justifyContent: 'center' }}>
            {CRITERIA_CONFIG.map(({ key, label }) => (
              <div key={key} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.825rem' }}>
                <span style={{ color: '#334155' }}>{label}:</span>
                <strong style={{ color: '#0f172a' }}>{stats.criteriaAvg[key] ? `${stats.criteriaAvg[key]} / 5.0` : '5.0 / 5.0'}</strong>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Filter Chips */}
      {reviews.length > 0 && (
        <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.25rem', flexWrap: 'wrap' }}>
          {['ALL', 5, 4, 3].map((st) => (
            <button
              key={st}
              type="button"
              onClick={() => setFilterStar(st)}
              style={{
                padding: '4px 12px',
                borderRadius: 999,
                border: '1px solid',
                borderColor: filterStar === st ? '#2563eb' : '#cbd5e1',
                background: filterStar === st ? '#eff6ff' : '#ffffff',
                color: filterStar === st ? '#1d4ed8' : '#475569',
                fontSize: '0.78rem',
                fontWeight: filterStar === st ? 700 : 500,
                cursor: 'pointer',
              }}
            >
              {st === 'ALL' ? `Tất cả (${reviews.length})` : st === 3 ? `≤ 3 sao` : `${st} sao (${stats?.starCounts?.[st] || 0})`}
            </button>
          ))}
        </div>
      )}

      {filteredReviews.length === 0 ? (
        <div style={{ padding: '3rem 1rem', textAlign: 'center', color: '#64748b' }}>
          <p style={{ margin: 0, fontWeight: 700, fontSize: '0.95rem' }}>Chưa có đánh giá nào từ nhân sự</p>
          <span style={{ fontSize: '0.825rem', color: '#94a3b8' }}>Sau khi nhân sự hoàn thành ca làm và check-out, điểm số và nhận xét sẽ xuất hiện tại đây.</span>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {filteredReviews.map((r) => (
            <div
              key={r.reviewId || Math.random()}
              style={{
                border: '1px solid #e2e8f0',
                borderRadius: 10,
                padding: '1.15rem',
                background: '#ffffff',
                boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '0.5rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                  <div style={{ width: 36, height: 36, borderRadius: '50%', background: '#e0e7ff', color: '#4338ca', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: '0.95rem', overflow: 'hidden' }}>
                    {r.workerAvatarUrl ? (
                      <img src={r.workerAvatarUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                    ) : (
                      (r.reviewerWorkerName || 'N').charAt(0).toUpperCase()
                    )}
                  </div>
                  <div>
                    <strong style={{ fontSize: '0.92rem', color: '#0f172a' }}>{r.reviewerWorkerName || 'Nhân sự CANi'}</strong>
                    <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: 1 }}>
                      {r.createdAt ? new Date(r.createdAt).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }) : 'Vừa xong'}
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', background: '#fef9c3', border: '1px solid #fde047', padding: '3px 10px', borderRadius: 6, fontSize: '0.8rem', fontWeight: 800, color: '#854d0e' }}>
                  <span>{r.rating || 5} / 5 sao</span>
                </div>
              </div>

              {(r.eventName || r.shiftName) && (
                <div style={{ margin: '0.55rem 0', display: 'inline-flex', alignItems: 'center', gap: 4, background: '#eff6ff', color: '#1d4ed8', fontSize: '0.74rem', padding: '2px 8px', borderRadius: 4, fontWeight: 600 }}>
                  {r.eventName || 'Sự kiện'} {r.shiftName ? `• Ca: ${r.shiftName}` : ''}
                </div>
              )}

              {r.comment && (
                <p style={{ margin: '0.4rem 0', fontSize: '0.86rem', color: '#334155', lineHeight: 1.55 }}>
                  "{r.comment}"
                </p>
              )}

              {/* Sub-criteria mini chips */}
              <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', margin: '0.5rem 0' }}>
                {r.criteriaWorkEnvironment && <span style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 4, padding: '1px 6px', fontSize: '0.72rem', color: '#475569' }}>Môi trường: {r.criteriaWorkEnvironment}/5</span>}
                {r.criteriaSupport && <span style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 4, padding: '1px 6px', fontSize: '0.72rem', color: '#475569' }}>Hỗ trợ: {r.criteriaSupport}/5</span>}
                {r.criteriaPaymentTiming && <span style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 4, padding: '1px 6px', fontSize: '0.72rem', color: '#475569' }}>Thanh toán: {r.criteriaPaymentTiming}/5</span>}
              </div>

              {/* Tags */}
              {r.tags && r.tags.length > 0 && (
                <div style={{ display: 'flex', gap: '0.35rem', flexWrap: 'wrap', marginTop: '0.4rem' }}>
                  {r.tags.map((t, i) => (
                    <span key={i} style={{ fontSize: '0.72rem', fontWeight: 600, background: '#f1f5f9', border: '1px solid #cbd5e1', borderRadius: 4, padding: '1px 7px', color: '#334155' }}>
                      {t}
                    </span>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
