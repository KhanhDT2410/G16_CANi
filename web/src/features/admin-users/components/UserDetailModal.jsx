import { USER_ROLE_LABELS, USER_STATUS_LABELS } from '../types/userManagement.constants';

const UserDetailModal = ({ user, loading, onClose, onOpenStatusModal }) => {
  if (!user && !loading) return null;

  const roleInfo = user?.role ? (USER_ROLE_LABELS[user.role] || { label: user.role }) : null;
  const statusInfo = user?.status ? (USER_STATUS_LABELS[user.status] || { label: user.status }) : null;

  return (
    <div className="user-modal-overlay" onClick={onClose}>
      <div className="user-modal-box" onClick={(e) => e.stopPropagation()}>
        <div className="user-modal-header">
          <h3>Chi Tiết Tài Khoản Người Dùng</h3>
          <button type="button" className="btn-modal-close" onClick={onClose}>
            &times;
          </button>
        </div>

        {loading ? (
          <div style={{ padding: '2rem', textAlign: 'center' }}>
            <div className="cani-spinner" style={{ margin: '0 auto 1rem' }} />
            <span>Đang tải thông tin chi tiết...</span>
          </div>
        ) : (
          <div>
            {/* Thông tin định danh */}
            <div className="detail-section">
              <h4>Thông Tin Cơ Bản</h4>
              <div className="detail-grid">
                <div>
                  <span className="detail-label">Họ và tên:</span>{' '}
                  <span className="detail-value">{user.fullName}</span>
                </div>
                <div>
                  <span className="detail-label">Email:</span>{' '}
                  <span className="detail-value">{user.email}</span>
                </div>
                <div>
                  <span className="detail-label">Số điện thoại:</span>{' '}
                  <span className="detail-value">{user.phone || '—'}</span>
                </div>
                <div>
                  <span className="detail-label">Vai trò:</span>{' '}
                  <span className={roleInfo?.badgeClass}>{roleInfo?.label}</span>
                </div>
                <div>
                  <span className="detail-label">Trạng thái:</span>{' '}
                  <span className={statusInfo?.badgeClass}>{statusInfo?.label}</span>
                </div>
                <div>
                  <span className="detail-label">Email Verified:</span>{' '}
                  <span className="detail-value">{user.emailVerified ? 'Đã xác thực' : 'Chưa xác thực'}</span>
                </div>
                <div>
                  <span className="detail-label">Ngày tạo:</span>{' '}
                  <span className="detail-value">{user.createdAt ? new Date(user.createdAt).toLocaleString('vi-VN') : '—'}</span>
                </div>
                <div>
                  <span className="detail-label">Đăng nhập gần nhất:</span>{' '}
                  <span className="detail-value">{user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString('vi-VN') : '—'}</span>
                </div>
              </div>
            </div>

            {/* Hồ sơ Worker nếu có */}
            {user.workerProfile && (
              <div className="detail-section">
                <h4>Hồ Sơ Nhân Sự (Worker Profile)</h4>
                <div className="detail-grid">
                  <div>
                    <span className="detail-label">Số CCCD:</span>{' '}
                    <span className="detail-value">{user.workerProfile.cccdNumber || '—'}</span>
                  </div>
                  <div>
                    <span className="detail-label">Trạng thái eKYC:</span>{' '}
                    <span className="detail-value">{user.workerProfile.kycStatus || '—'}</span>
                  </div>
                  <div>
                    <span className="detail-label">Điểm uy tín:</span>{' '}
                    <span className="detail-value" style={{ color: '#16a34a' }}>
                      {user.workerProfile.reliabilityScore} / 100
                    </span>
                  </div>
                  <div>
                    <span className="detail-label">Đánh giá trung bình:</span>{' '}
                    <span className="detail-value" style={{ color: '#d97706' }}>
                      ★ {user.workerProfile.ratingAvg} ({user.workerProfile.ratingCount || 0} lượt)
                    </span>
                  </div>
                  <div>
                    <span className="detail-label">Ca hoàn thành:</span>{' '}
                    <span className="detail-value">{user.workerProfile.totalShiftsCompleted} ca</span>
                  </div>
                  <div>
                    <span className="detail-label">Hủy ca / Bỏ ca:</span>{' '}
                    <span className="detail-value" style={{ color: '#dc2626' }}>
                      {user.workerProfile.totalCancellations} hủy / {user.workerProfile.totalNoshows} bỏ
                    </span>
                  </div>
                </div>
              </div>
            )}

            {/* Hồ sơ Nhà tổ chức nếu có */}
            {user.eoProfile && (
              <div className="detail-section">
                <h4>Hồ Sơ Nhà Tổ Chức (Event Organizer)</h4>
                <div className="detail-grid">
                  <div>
                    <span className="detail-label">Tên công ty / Đơn vị:</span>{' '}
                    <span className="detail-value">{user.eoProfile.companyName || '—'}</span>
                  </div>
                  <div>
                    <span className="detail-label">Mã số thuế:</span>{' '}
                    <span className="detail-value">{user.eoProfile.taxId || '—'}</span>
                  </div>
                  <div>
                    <span className="detail-label">Địa chỉ:</span>{' '}
                    <span className="detail-value">{user.eoProfile.address || '—'}</span>
                  </div>
                  <div>
                    <span className="detail-label">Kiểm duyệt doanh nghiệp:</span>{' '}
                    <span className="detail-value">{user.eoProfile.verificationStatus || '—'}</span>
                  </div>
                  <div>
                    <span className="detail-label">Đánh giá trung bình:</span>{' '}
                    <span className="detail-value" style={{ color: '#d97706' }}>
                      ★ {user.eoProfile.ratingAvg} ({user.eoProfile.ratingCount || 0} lượt)
                    </span>
                  </div>
                  <div>
                    <span className="detail-label">Sự kiện đã hoàn tất:</span>{' '}
                    <span className="detail-value">{user.eoProfile.totalEventsCompleted} sự kiện</span>
                  </div>
                </div>
              </div>
            )}

            {/* Modal Footer Actions */}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.25rem', borderTop: '1px solid #e5e7eb', paddingTop: '1rem' }}>
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
          </div>
        )}
      </div>
    </div>
  );
};

export default UserDetailModal;
