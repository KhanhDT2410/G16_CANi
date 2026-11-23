import React, { useState, useEffect } from 'react';
import { useLocation, Link, useNavigate } from 'react-router-dom';
import CaniLogo from '../../components/common/CaniLogo';
import { accountAppealApi } from '../../features/account-appeals/api/accountAppealApi';
import AppealSubmissionForm from '../../features/account-appeals/components/AppealSubmissionForm';
import AppealTimelineStatus from '../../features/account-appeals/components/AppealTimelineStatus';
import './AccountLockedPage.css';

export default function AccountLockedPage() {
  const location = useLocation();
  const navigate = useNavigate();

  const stateEmail = location.state?.email || '';
  const stateLockReason = location.state?.lockReason || '';
  const appealToken = location.state?.appealToken || '';

  const [email, setEmail] = useState(stateEmail);
  const [inputEmail, setInputEmail] = useState('');
  const [lockReason, setLockReason] = useState(stateLockReason);
  const [accountInfo, setAccountInfo] = useState(null);
  const [appeal, setAppeal] = useState(null);
  const [loading, setLoading] = useState(false);
  const [showForm, setShowForm] = useState(false);

  // Tra cứu dữ liệu tài khoản khi có email
  useEffect(() => {
    if (!email) {
      setAccountInfo(null);
      setAppeal(null);
      return;
    }

    let isMounted = true;
    setLoading(true);

    const loadData = async () => {
      try {
        const [lockInfo, appealStatus] = await Promise.allSettled([
          accountAppealApi.getLockInfo(email),
          accountAppealApi.getAppealStatus(email),
        ]);

        if (!isMounted) return;

        if (lockInfo.status === 'fulfilled' && lockInfo.value) {
          setAccountInfo(lockInfo.value);
          if (lockInfo.value.lockReason) {
            setLockReason(lockInfo.value.lockReason);
          }
        }

        if (appealStatus.status === 'fulfilled' && appealStatus.value) {
          setAppeal(appealStatus.value);
        } else {
          setAppeal(null);
        }
      } catch (err) {
        console.warn('Lỗi tải thông tin giải trình:', err);
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    loadData();
    return () => { isMounted = false; };
  }, [email]);

  const handleSearchEmail = (e) => {
    e.preventDefault();
    if (!inputEmail.trim()) return;
    setEmail(inputEmail.trim());
  };

  return (
    <div className="account-locked-page">
      <header className="locked-page-header">
        <div className="locked-header-inner">
          <Link to="/" className="locked-logo-link">
            <CaniLogo variant="full" height={36} />
          </Link>
          <Link to="/login" className="locked-back-login">
            ← Quay lại Đăng nhập
          </Link>
        </div>
      </header>

      <main className="locked-page-main">
        <div className="locked-card">
          <div className="locked-hero-banner">
            <div className="locked-icon-wrapper">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
              </svg>
            </div>
            <div className="locked-hero-text">
              <h2>Tài Khoản Đang Bị Tạm Khóa Hoạt Động</h2>
              <p>
                Để bảo vệ quyền lợi cộng đồng nhân sự và an toàn vận hành, Ban Quản trị CANi đã tạm ngưng hoạt động của tài khoản này.
              </p>
            </div>
          </div>

          {!email ? (
            <div className="locked-email-prompt">
              <p>Vui lòng nhập địa chỉ email tài khoản để tra cứu lý do khóa và nộp đơn giải trình:</p>
              <form onSubmit={handleSearchEmail} className="locked-lookup-form">
                <input
                  type="email"
                  required
                  placeholder="name@company.com"
                  value={inputEmail}
                  onChange={(e) => setInputEmail(e.target.value)}
                  className="locked-input"
                />
                <button type="submit" className="locked-btn-lookup">
                  Tra Cứu
                </button>
              </form>
            </div>
          ) : (
            <div className="locked-content-area">
              <div className="locked-account-info-bar">
                <div className="locked-info-item">
                  <span className="info-muted">Email tài khoản:</span>
                  <strong>{email}</strong>
                </div>
                {lockReason && (
                  <div className="locked-info-item">
                    <span className="info-muted">Lý do khóa từ Ban Quản trị:</span>
                    <span className="lock-reason-text">{lockReason}</span>
                  </div>
                )}
              </div>

              {loading ? (
                <div style={{ padding: '3rem', textAlign: 'center' }}>
                  <div className="cani-spinner" style={{ margin: '0 auto' }} />
                  <p style={{ marginTop: '0.75rem', color: '#64748b' }}>Đang tra cứu hồ sơ giải trình...</p>
                </div>
              ) : accountInfo && accountInfo.found === false ? (
                <div className="appeal-status-notice-card card-warning">
                  <div className="notice-icon">⚠️</div>
                  <div className="notice-body">
                    <h4>Không tìm thấy thông tin tài khoản</h4>
                    <p>Địa chỉ email <strong>{email}</strong> chưa được đăng ký trong hệ thống CANi.</p>
                    <div className="notice-actions">
                      <button type="button" className="btn-secondary-link" onClick={() => setEmail('')}>
                        Nhập lại email khác
                      </button>
                      <Link to="/login" className="btn-primary-link">
                        Quay lại Đăng nhập
                      </Link>
                    </div>
                  </div>
                </div>
              ) : accountInfo && accountInfo.found === true && accountInfo.isLocked === false ? (
                <div className="appeal-status-notice-card card-info">
                  <div className="notice-icon">✓</div>
                  <div className="notice-body">
                    <h4>Tài khoản đang hoạt động bình thường</h4>
                    <p>
                      Tài khoản <strong>{email}</strong> hiện ở trạng thái <strong>{accountInfo.status || 'ACTIVE'}</strong> và không bị khóa. Bạn có thể đăng nhập bình thường vào hệ thống.
                    </p>
                    <div className="notice-actions">
                      <Link to="/login" className="btn-primary-link">
                        Đăng nhập ngay
                      </Link>
                    </div>
                  </div>
                </div>
              ) : appeal && !showForm ? (
                <AppealTimelineStatus
                  appeal={appeal}
                  onReappeal={() => setShowForm(true)}
                />
              ) : (
                <AppealSubmissionForm
                  email={email}
                  appealToken={appealToken}
                  onSubmitSuccess={(newAppeal) => {
                    setAppeal(newAppeal);
                    setShowForm(false);
                  }}
                />
              )}
            </div>
          )}

          <div className="locked-footer-help">
            <span>Cần trợ giúp khẩn cấp?</span>
            <a href="mailto:support@cani.vn">Liên hệ Bộ phận CSKH: support@cani.vn</a>
            <span>• Hotline: 1900 6868</span>
          </div>
        </div>
      </main>
    </div>
  );
}
