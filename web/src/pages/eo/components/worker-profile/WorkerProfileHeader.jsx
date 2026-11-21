import React from 'react';

/**
 * WorkerProfileHeader: Hiển thị định danh, huy hiệu cấp bậc và các chỉ số hiệu suất
 * Tiêu chuẩn < 200 dòng theo AGENTS.md
 */
export const WorkerProfileHeader = ({ profile }) => {
  if (!profile) return null;

  const {
    fullName,
    avatarUrl,
    phone,
    email,
    bio,
    age,
    yearsExperience,
    isOnline,
    skillTier = 'STARTER',
    totalSp = 0,
    reliabilityScore = 100,
    ratingAvg = 5.0,
    ratingCount = 0,
    totalShiftsCompleted = 0,
    isMyTeamMember,
    isFavorited,
    internalNote,
  } = profile;

  const tierKey = (skillTier || 'STARTER').toLowerCase();

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      {/* 1. Hero Identity Card */}
      <div className="worker-hero-card">
        <div className="worker-avatar-wrap">
          {avatarUrl ? (
            <img src={avatarUrl} alt={fullName} className="worker-avatar-img" />
          ) : (
            <div className="worker-avatar-placeholder">
              {(fullName || 'W').charAt(0).toUpperCase()}
            </div>
          )}
          <span
            className={`worker-online-indicator ${isOnline ? 'online' : 'offline'}`}
            title={isOnline ? 'Đang sẵn sàng nhận việc' : 'Ngoại tuyến'}
          />
        </div>

        <div className="worker-identity-info">
          <div className="worker-name-row">
            <h3 className="worker-full-name">{fullName || 'Nhân sự CANi'}</h3>
            <span className={`tier-badge ${tierKey}`}>
              Hạng {skillTier}
            </span>
            {isMyTeamMember && (
              <span style={{
                background: '#eff6ff',
                color: '#1d4ed8',
                fontSize: '0.725rem',
                fontWeight: 700,
                padding: '2px 8px',
                borderRadius: 4,
                border: '1px solid #bfdbfe',
              }}>
                {isFavorited ? '★ Nhân sự yêu thích' : '✓ Nhân sự quen thuộc'}
              </span>
            )}
          </div>

          <div className="worker-meta-details">
            {age && <span>Tuổi: <strong>{age}</strong></span>}
            {yearsExperience > 0 && <span>Kinh nghiệm: <strong>{yearsExperience} năm</strong></span>}
            {phone && <span>SĐT: <strong>{phone}</strong></span>}
            {email && <span>Email: {email}</span>}
          </div>

          {bio && <p className="worker-bio-text">"{bio}"</p>}
        </div>
      </div>

      {/* 2. Ghi chú nội bộ nếu đã trong My Team */}
      {isMyTeamMember && internalNote && (
        <div className="my-team-status-box">
          <div>
            <strong>Ghi chú nội bộ của bạn:</strong> {internalNote}
          </div>
        </div>
      )}

      {/* 3. Lưới 4 chỉ số năng lực cốt lõi */}
      <div className="worker-metrics-summary-grid">
        <div className="metric-stat-item">
          <span className="metric-stat-label">Điểm Uy Tín</span>
          <span className="metric-stat-val" style={{ color: reliabilityScore >= 80 ? '#16a34a' : '#d97706' }}>
            {reliabilityScore}/100
          </span>
        </div>

        <div className="metric-stat-item">
          <span className="metric-stat-label">Tổng Điểm SP</span>
          <span className="metric-stat-val" style={{ color: '#0066FF' }}>
            {totalSp} SP
          </span>
        </div>

        <div className="metric-stat-item">
          <span className="metric-stat-label">Đánh Giá</span>
          <span className="metric-stat-val" style={{ color: '#eab308' }}>
            ⭐ {Number(ratingAvg || 5.0).toFixed(1)} <small style={{ fontSize: '0.75rem', color: '#64748b' }}>({ratingCount})</small>
          </span>
        </div>

        <div className="metric-stat-item">
          <span className="metric-stat-label">Ca Hoàn Thành</span>
          <span className="metric-stat-val" style={{ color: '#059669' }}>
            {totalShiftsCompleted} ca
          </span>
        </div>
      </div>
    </div>
  );
};

export default WorkerProfileHeader;
