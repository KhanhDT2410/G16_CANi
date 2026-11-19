import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import WorkerProfileHeader from './WorkerProfileHeader';
import WorkerSkillsSection from './WorkerSkillsSection';
import WorkerReviewsSection from './WorkerReviewsSection';
import './WorkerProfileModal.css';

/**
 * WorkerProfileModal: Cửa sổ xem toàn diện hồ sơ năng lực Worker cho EO
 * Lấy dữ liệu thật 100% từ Database CANi qua backend API
 * Tiêu chuẩn < 200 dòng theo AGENTS.md
 */
export const WorkerProfileModal = ({
  isOpen,
  loading,
  error,
  profile,
  onClose,
  onAddToMyTeam,
}) => {
  const [activeTab, setActiveTab] = useState('skills');

  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = originalOverflow;
    };
  }, [isOpen, onClose]);

  if (!isOpen || typeof document === 'undefined') return null;

  const modalContent = (
    <div className="worker-modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="worker-modal-box" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="worker-modal-header">
          <div>
            <h3 style={{ margin: 0, fontSize: '1.15rem', fontWeight: 800, color: '#0f172a' }}>
              Hồ Sơ Năng Lực Nhân Sự
            </h3>
            <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
              Dữ liệu đối soát thời gian thực từ sàn CANi
            </span>
          </div>
          <button
            type="button"
            className="worker-modal-close-btn"
            onClick={onClose}
            aria-label="Đóng cửa sổ"
          >
            &times;
          </button>
        </div>

        {/* Body */}
        <div className="worker-modal-body">
          {loading ? (
            <div style={{ padding: '3.5rem 0', textAlign: 'center', color: '#64748b', fontSize: '0.9rem' }}>
              ⏳ Đang tải dữ liệu hồ sơ từ Database CANi...
            </div>
          ) : error ? (
            <div style={{ padding: '2.5rem 0', textAlign: 'center', color: '#dc2626', fontSize: '0.9rem' }}>
              ⚠️ {error}
            </div>
          ) : profile ? (
            <>
              {/* Định danh & 4 chỉ số hiệu suất */}
              <WorkerProfileHeader profile={profile} />

              {/* Thanh chuyển tab Kỹ năng / Đánh giá */}
              <div className="worker-profile-tabs">
                <button
                  type="button"
                  className={`profile-tab-btn ${activeTab === 'skills' ? 'active' : ''}`}
                  onClick={() => setActiveTab('skills')}
                >
                  Kỹ Năng & Bằng Cấp ({profile.skills?.length || 0})
                </button>
                <button
                  type="button"
                  className={`profile-tab-btn ${activeTab === 'reviews' ? 'active' : ''}`}
                  onClick={() => setActiveTab('reviews')}
                >
                  Đánh Giá Từ Các EO ({profile.reviews?.length || 0})
                </button>
              </div>

              {/* Nội dung Tab */}
              {activeTab === 'skills' ? (
                <WorkerSkillsSection skills={profile.skills} />
              ) : (
                <WorkerReviewsSection reviews={profile.reviews} />
              )}
            </>
          ) : null}
        </div>

        {/* Footer */}
        <div className="worker-modal-footer">
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            {profile?.phone && (
              <a
                href={`tel:${profile.phone}`}
                className="eo-btn-secondary"
                style={{
                  textDecoration: 'none',
                  fontSize: '0.825rem',
                  padding: '0.45rem 0.85rem',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 4,
                }}
              >
                📞 Gọi {profile.phone}
              </a>
            )}
            {profile && !profile.isMyTeamMember && onAddToMyTeam && (
              <button
                type="button"
                className="eo-btn-primary"
                style={{ fontSize: '0.825rem', padding: '0.45rem 0.85rem' }}
                onClick={() => onAddToMyTeam(profile)}
              >
                + Thêm vào Thợ Quen
              </button>
            )}
          </div>

          <button
            type="button"
            className="eo-btn-secondary"
            style={{ fontSize: '0.825rem', padding: '0.45rem 1rem' }}
            onClick={onClose}
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );

  return createPortal(modalContent, document.body);
};

export default WorkerProfileModal;
