import React from 'react';
import './EkycRequiredModal.css';

export default function EkycRequiredModal({ isOpen, onClose, onNavigateToEkyc, ekycStatus = 'NOT_SUBMITTED' }) {
  if (!isOpen) return null;

  const getStatusText = () => {
    switch (ekycStatus) {
      case 'PENDING_REVIEW':
        return {
          title: 'Hồ sơ eKYC đang chờ duyệt',
          desc: 'Hồ sơ định danh của bạn đã được gửi và đang được đội ngũ kiểm duyệt xem xét. Vui lòng quay lại sau ít phút hoặc kiểm tra lại hồ sơ.',
          badgeColor: '#b45309',
          badgeBg: '#fef3c7',
        };
      case 'REJECTED':
        return {
          title: 'Hồ sơ eKYC trước đó bị từ chối',
          desc: 'Hình ảnh CCCD hoặc chân dung chưa đạt yêu cầu rõ nét. Vui lòng chụp lại ảnh mới để tiếp tục mở khóa tạo sự kiện.',
          badgeColor: '#b91c1c',
          badgeBg: '#fee2e2',
        };
      case 'NOT_SUBMITTED':
      default:
        return {
          title: 'Chưa nộp hồ sơ xác thực định danh',
          desc: 'Theo Nghị định 13/2023/NĐ-CP và bảo vệ an toàn cho nhân sự, Nhà tổ chức cần hoàn tất định danh eKYC trước khi tạo sự kiện và đăng tuyển.',
          badgeColor: '#475569',
          badgeBg: '#f1f5f9',
        };
    }
  };

  const statusInfo = getStatusText();

  return (
    <div className="ekyc-required-backdrop" onClick={onClose}>
      <div className="ekyc-required-card" onClick={(e) => e.stopPropagation()}>
        <button
          type="button"
          className="ekyc-required-close"
          onClick={onClose}
          title="Đóng"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>

        <div className="ekyc-required-icon-wrapper">
          <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
            <line x1="12" y1="8" x2="12" y2="12"></line>
            <line x1="12" y1="16" x2="12.01" y2="16"></line>
          </svg>
        </div>

        <h3 className="ekyc-required-title">Yêu Cầu Xác Thực Định Danh</h3>
        <p className="ekyc-required-desc">
          Để tạo sự kiện, điều phối ca làm và xuất bản tin tuyển dụng nhân sự trên CANi, bạn cần hoàn tất định danh eKYC điện tử.
        </p>

        <div className="ekyc-status-pill-box">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.35rem' }}>
            <strong style={{ margin: 0 }}>Trạng thái tài khoản:</strong>
            <span style={{
              fontSize: '0.75rem',
              fontWeight: 700,
              padding: '0.2rem 0.55rem',
              borderRadius: '9999px',
              backgroundColor: statusInfo.badgeBg,
              color: statusInfo.badgeColor,
            }}>
              {statusInfo.title}
            </span>
          </div>
          <span>{statusInfo.desc}</span>
        </div>

        <div className="ekyc-required-actions">
          <button
            type="button"
            className="ekyc-btn-dismiss"
            onClick={onClose}
          >
            Để sau
          </button>
          <button
            type="button"
            className="ekyc-btn-primary"
            onClick={() => {
              onClose();
              if (onNavigateToEkyc) onNavigateToEkyc();
            }}
          >
            <span>Xác Thực eKYC Ngay</span>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <polyline points="9 18 15 12 9 6"></polyline>
            </svg>
          </button>
        </div>
      </div>
    </div>
  );
}

