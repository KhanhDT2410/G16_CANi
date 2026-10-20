import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../../features/auth';
import './worker.css';

/**
 * Màn hình Cổng Thông Tin Nhân Sự Sự Kiện (Worker Portal)
 * Dành riêng cho người lao động quản lý ca làm, điểm tin cậy, và check-in QR
 */
const WorkerDashboard = () => {
  const navigate = useNavigate();

  const [workerUser, setWorkerUser] = useState({
    name: 'Nguyễn Văn Nhân Sự',
    email: 'worker@cani.vn',
    reliabilityScore: 100,
    skillTier: 'STARTER',
    ratingAvg: 4.95,
    completedShifts: 12,
  });

  const [isOnline, setIsOnline] = useState(true);

  // Danh sách các ca làm việc gợi ý (Dispatch Proposal - Matching)
  const [proposals, setProposals] = useState([
    {
      id: 'prop-1',
      jobType: 'Điều phối viên Check-in & Hướng dẫn',
      eventName: 'Hội Thảo Công Nghệ TechFest 2026',
      organizer: 'Sun Event Group',
      location: 'Trung tâm Hội nghị SECC, Quận 7, TP.HCM',
      time: '07:30 - 12:30 (5 giờ) • Ngày mai',
      wage: '65.000đ / giờ',
      matchScore: 98,
      status: 'PENDING',
    },
    {
      id: 'prop-2',
      jobType: 'Hỗ trợ Kỹ thuật Âm thanh & Ánh sáng',
      eventName: 'Đêm Nhạc Acoustic Mùa Thu',
      organizer: 'Vibe Music & Entertainment',
      location: 'Phố đi bộ Nguyễn Huệ, Quận 1, TP.HCM',
      time: '17:00 - 22:30 (5.5 giờ) • Thứ 7 tuần này',
      wage: '85.000đ / giờ',
      matchScore: 92,
      status: 'PENDING',
    },
    {
      id: 'prop-3',
      jobType: 'PG / PB Đón khách & Phát quà tặng',
      eventName: 'Khai Trương Showroom Xe Điện',
      organizer: 'VinFast Agency',
      location: 'Landmark 81, Bình Thạnh, TP.HCM',
      time: '08:00 - 16:00 (8 giờ) • Chủ nhật tuần này',
      wage: '75.000đ / giờ',
      matchScore: 89,
      status: 'PENDING',
    },
  ]);

  useEffect(() => {
    const savedUser = authApi.getCurrentUser();
    if (savedUser) {
      setWorkerUser((prev) => ({
        ...prev,
        name: savedUser.name || savedUser.fullName || prev.name,
        email: savedUser.email || prev.email,
        reliabilityScore: savedUser.reliabilityScore || prev.reliabilityScore,
        skillTier: savedUser.tier || prev.skillTier,
      }));
    }
  }, []);

  const handleToggleOnline = () => {
    setIsOnline((prev) => !prev);
  };

  const handleAcceptProposal = (id) => {
    setProposals((prev) =>
      prev.map((item) => (item.id === id ? { ...item, status: 'ACCEPTED' } : item))
    );
  };

  const handleDeclineProposal = (id) => {
    setProposals((prev) => prev.filter((item) => item.id !== id));
  };

  const handleLogout = async () => {
    await authApi.logout();
    navigate('/login');
  };

  return (
    <div className="worker-dashboard">
      {/* Profile Hero Banner */}
      <div className="worker-hero">
        <div className="worker-profile-info">
          <div className="worker-avatar">
            {workerUser.name.charAt(0)}
          </div>
          <div className="worker-details">
            <h1>{workerUser.name}</h1>
            <div className="worker-badges">
              <span className="tier-badge">
                ⭐ Hạng: {workerUser.skillTier}
              </span>
              <span className="reliability-badge">
                🛡️ Điểm tin cậy: {workerUser.reliabilityScore}/100
              </span>
              <span className="reliability-badge">
                {workerUser.email}
              </span>
            </div>
          </div>
        </div>

        <div className="worker-hero-actions">
          <button
            type="button"
            className="status-toggle-btn"
            onClick={handleToggleOnline}
            title="Bấm để đổi trạng thái nhận việc"
          >
            <span
              className="status-dot"
              style={{
                backgroundColor: isOnline ? '#22c55e' : '#94a3b8',
                boxShadow: isOnline ? '0 0 8px #22c55e' : 'none',
              }}
            />
            {isOnline ? 'Đang sẵn sàng nhận ca' : 'Tạm dừng nhận ca'}
          </button>
          <button type="button" className="btn-logout" onClick={handleLogout}>
            Đăng xuất
          </button>
        </div>
      </div>

      {/* Quick Stats Grid */}
      <div className="stats-grid">
        <div className="stat-card">
          <span className="stat-label">Điểm Tin Cậy (Reliability)</span>
          <span className="stat-value" style={{ color: '#16a34a' }}>
            {workerUser.reliabilityScore} / 100
          </span>
          <span className="stat-trend">✨ Mức tối đa (Chưa từng hủy ca/No-show)</span>
        </div>

        <div className="stat-card">
          <span className="stat-label">Đánh Giá Trung Bình</span>
          <span className="stat-value" style={{ color: '#ea580c' }}>
            {workerUser.ratingAvg} ★
          </span>
          <span className="stat-trend">Từ 12 nhà tổ chức sự kiện</span>
        </div>

        <div className="stat-card">
          <span className="stat-label">Ca Đã Hoàn Thành</span>
          <span className="stat-value">{workerUser.completedShifts}</span>
          <span className="stat-trend">Tỷ lệ đúng giờ 100%</span>
        </div>

        <div className="stat-card">
          <span className="stat-label">Thu Nhập Ước Tính Tháng Này</span>
          <span className="stat-value" style={{ color: '#2563eb' }}>
            4.850.000đ
          </span>
          <span className="stat-trend">Đã thanh toán đủ qua tài khoản</span>
        </div>
      </div>

      {/* Main Content Layout */}
      <div className="worker-main-grid">
        {/* Left Column: Job Proposals (Matching System) */}
        <div className="section-card">
          <div className="section-header">
            <h2 className="section-title">
              <span>🎯</span> Ca Làm Việc Đề Xuất Hôm Nay (Matching)
            </h2>
            <span style={{ fontSize: '0.8125rem', color: '#64748b' }}>
              Dựa trên kỹ năng & vị trí của bạn
            </span>
          </div>

          <div className="proposals-list">
            {proposals.length === 0 ? (
              <p style={{ textAlign: 'center', color: '#94a3b8', padding: '2rem' }}>
                Hiện chưa có đề xuất ca làm mới. Hãy bật trạng thái sẵn sàng để nhận thông báo!
              </p>
            ) : (
              proposals.map((item) => (
                <div
                  key={item.id}
                  className={`proposal-item ${item.status === 'ACCEPTED' ? 'accepted' : ''}`}
                >
                  <div className="proposal-header">
                    <div>
                      <h3 className="job-name">{item.jobType}</h3>
                      <span className="event-organizer-tag">
                        {item.eventName} • <strong>{item.organizer}</strong>
                      </span>
                    </div>
                    <span className="wage-badge">{item.wage}</span>
                  </div>

                  <div className="proposal-meta">
                    <div className="meta-item">
                      <span>📍</span> {item.location}
                    </div>
                    <div className="meta-item">
                      <span>🕒</span> {item.time}
                    </div>
                    <div className="meta-item" style={{ color: '#2563eb', fontWeight: '600' }}>
                      <span>⚡</span> Độ phù hợp: {item.matchScore}%
                    </div>
                  </div>

                  {item.status === 'ACCEPTED' ? (
                    <div
                      style={{
                        padding: '0.5rem',
                        backgroundColor: '#dcfce7',
                        color: '#15803d',
                        borderRadius: '0.375rem',
                        textAlign: 'center',
                        fontWeight: '600',
                        fontSize: '0.875rem',
                      }}
                    >
                      ✓ Bạn đã đồng ý nhận ca làm này. Vui lòng có mặt đúng giờ!
                    </div>
                  ) : (
                    <div className="proposal-actions">
                      <button
                        type="button"
                        className="btn-accept"
                        onClick={() => handleAcceptProposal(item.id)}
                      >
                        Đồng Ý Nhận Ca
                      </button>
                      <button
                        type="button"
                        className="btn-decline"
                        onClick={() => handleDeclineProposal(item.id)}
                      >
                        Bỏ qua
                      </button>
                    </div>
                  )}
                </div>
              ))
            )}
          </div>
        </div>

        {/* Right Column: QR Check-in & Availability */}
        <div className="sidebar-col">
          {/* Card: Mã QR Check-in */}
          <div className="section-card">
            <div className="section-header">
              <h3 className="section-title">
                <span>📱</span> Mã QR Điểm Danh Ca
              </h3>
            </div>
            <div className="qr-box">
              <div className="qr-placeholder">
                <span>📷</span>
              </div>
              <strong style={{ fontSize: '0.875rem', color: '#0f172a' }}>
                Mã QR Check-in Cá Nhân
              </strong>
              <p style={{ fontSize: '0.75rem', color: '#64748b', margin: '0.25rem 0 0.75rem 0' }}>
                Đưa mã này cho Trưởng ban tổ chức (EO) quét khi đến địa điểm sự kiện.
              </p>
              <span
                style={{
                  fontSize: '0.75rem',
                  backgroundColor: '#f1f5f9',
                  padding: '0.2rem 0.5rem',
                  borderRadius: '0.25rem',
                  color: '#475569',
                }}
              >
                Hash: #CN-WK-{workerUser.reliabilityScore}
              </span>
            </div>
          </div>

          {/* Card: Lịch rảnh trong tuần */}
          <div className="section-card">
            <div className="section-header">
              <h3 className="section-title">
                <span>🗓️</span> Lịch Rảnh (Availability)
              </h3>
            </div>
            <div className="availability-list">
              <div className="availability-item">
                <span>Thứ 2 - Thứ 6:</span>
                <strong style={{ color: '#2563eb' }}>18:00 - 23:00</strong>
              </div>
              <div className="availability-item">
                <span>Thứ 7:</span>
                <strong style={{ color: '#16a34a' }}>Cả ngày (07:00 - 23:00)</strong>
              </div>
              <div className="availability-item">
                <span>Chủ Nhật:</span>
                <strong style={{ color: '#16a34a' }}>Cả ngày (07:00 - 23:00)</strong>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default WorkerDashboard;
