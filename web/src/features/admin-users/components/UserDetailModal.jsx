import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import {
  USER_ROLE_LABELS,
  USER_STATUS_LABELS,
  KYC_STATUS_LABELS,
  SKILL_STATUS_LABELS,
} from '../types/userManagement.constants';

const DetailItem = ({ label, value, color, badgeClass }) => (
  <div>
    <span className="detail-label">{label}:</span>{' '}
    {badgeClass ? (
      <span className={badgeClass}>{value}</span>
    ) : (
      <span className="detail-value" style={color ? { color } : undefined}>{value || '—'}</span>
    )}
  </div>
);

const UserDetailModal = ({ user, loading, onClose, onOpenStatusModal }) => {
  useEffect(() => {
    if (!user && !loading) return;
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
  }, [user, loading, onClose]);

  if (!user && !loading) return null;

  const roleInfo = user?.role ? (USER_ROLE_LABELS[user.role] || { label: user.role }) : null;
  const statusInfo = user?.status ? (USER_STATUS_LABELS[user.status] || { label: user.status }) : null;

  const modalNode = (
    <div className="user-modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="user-modal-box" onClick={(e) => e.stopPropagation()}>
        <div className="user-modal-header">
          <h3>Chi Tiết Tài Khoản Người Dùng</h3>
          <button type="button" className="btn-modal-close" onClick={onClose} aria-label="Đóng cửa sổ">
            &times;
          </button>
        </div>

        {loading ? (
          <div className="user-modal-body" style={{ padding: '3rem 1.5rem', textAlign: 'center' }}>
            <div className="cani-spinner" style={{ margin: '0 auto 1rem' }} />
            <span>Đang tải thông tin chi tiết...</span>
          </div>
        ) : (
          <>
            <div className="user-modal-body">
              {/* Thông tin cơ bản */}
              <div className="user-detail-section">
                <div className="detail-section-title">
                  <h4>Thông Tin Cơ Bản</h4>
                </div>
                <div className="detail-grid">
                  <DetailItem label="Họ và tên" value={user.fullName} />
                  <DetailItem label="Email" value={user.email} />
                  <DetailItem label="Số điện thoại" value={user.phone} />
                  <DetailItem label="Vai trò" value={roleInfo?.label} badgeClass={roleInfo?.badgeClass} />
                  <DetailItem label="Trạng thái" value={statusInfo?.label} badgeClass={statusInfo?.badgeClass} />
                  <DetailItem label="Email Verified" value={user.emailVerified ? 'Đã xác thực' : 'Chưa xác thực'} />
                  <DetailItem label="Ngày tạo" value={user.createdAt ? new Date(user.createdAt).toLocaleString('vi-VN') : null} />
                  <DetailItem label="Đăng nhập gần nhất" value={user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString('vi-VN') : null} />
                </div>
              </div>

              {/* Hồ sơ Worker nếu có */}
              {user.workerProfile && (
                <div className="user-detail-section">
                  <div className="detail-section-title">
                    <h4>Hồ Sơ Nhân Sự</h4>
                    <span className="detail-section-subtitle">(Worker Profile)</span>
                  </div>
                  <div className="detail-grid">
                    <DetailItem label="Số CCCD" value={user.workerProfile.cccdNumber} />
                    <DetailItem
                      label="Trạng thái eKYC"
                      value={KYC_STATUS_LABELS[user.workerProfile.kycStatus]?.label || user.workerProfile.kycStatus}
                      badgeClass={KYC_STATUS_LABELS[user.workerProfile.kycStatus]?.badgeClass}
                    />
                    <DetailItem
                      label="Trạng thái Skill"
                      value={SKILL_STATUS_LABELS[user.workerProfile.skillStatus]?.label || user.workerProfile.skillStatus || 'Chưa kê khai'}
                      badgeClass={SKILL_STATUS_LABELS[user.workerProfile.skillStatus]?.badgeClass || 'badge-status-pending'}
                    />
                    <DetailItem label="Điểm uy tín" value={`${user.workerProfile.reliabilityScore} / 100`} color="#16a34a" />
                    <DetailItem label="Đánh giá trung bình" value={`${user.workerProfile.ratingAvg} (${user.workerProfile.ratingCount || 0} lượt)`} color="#d97706" />
                    <DetailItem label="Ca hoàn thành" value={`${user.workerProfile.totalShiftsCompleted} ca`} />
                    <DetailItem label="Hủy ca / Bỏ ca" value={`${user.workerProfile.totalCancellations} hủy / ${user.workerProfile.totalNoshows} bỏ`} color="#dc2626" />
                  </div>
                </div>
              )}

              {/* Hồ sơ Nhà tổ chức nếu có */}
              {user.eoProfile && (
                <div className="user-detail-section">
                  <div className="detail-section-title">
                    <h4>Hồ Sơ Nhà Tổ Chức</h4>
                    <span className="detail-section-subtitle">(Event Organizer)</span>
                  </div>
                  <div className="detail-grid">
                    <DetailItem label="Tên công ty / Đơn vị" value={user.eoProfile.companyName} />
                    <DetailItem label="Mã số thuế" value={user.eoProfile.taxId} />
                    <DetailItem label="Địa chỉ" value={user.eoProfile.address} />
                    <DetailItem label="Kiểm duyệt doanh nghiệp" value={user.eoProfile.verificationStatus} />
                    <DetailItem label="Đánh giá trung bình" value={`${user.eoProfile.ratingAvg} (${user.eoProfile.ratingCount || 0} lượt)`} color="#d97706" />
                    <DetailItem label="Sự kiện đã hoàn tất" value={`${user.eoProfile.totalEventsCompleted} sự kiện`} />
                  </div>
                </div>
              )}
            </div>

            {/* Modal Footer Actions */}
            <div className="user-modal-footer">
              <button
                type="button"
                className="btn-filter-reset"
                onClick={onClose}
              >
                Đóng
              </button>
              {user.status === 'LOCKED' ? (
                <button
                  type="button"
                  className="btn-action-unlock"
                  style={{ padding: '0.55rem 1.25rem', fontSize: '0.85rem' }}
                  onClick={() => {
                    onClose();
                    if (onOpenStatusModal) onOpenStatusModal(user);
                  }}
                >
                  Mở Khóa Tài Khoản
                </button>
              ) : (
                <button
                  type="button"
                  className="btn-action-lock"
                  style={{ padding: '0.55rem 1.25rem', fontSize: '0.85rem' }}
                  onClick={() => {
                    onClose();
                    if (onOpenStatusModal) onOpenStatusModal(user);
                  }}
                >
                  Khóa Tài Khoản
                </button>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );

  return typeof document !== 'undefined' ? createPortal(modalNode, document.body) : modalNode;
};

export default UserDetailModal;
