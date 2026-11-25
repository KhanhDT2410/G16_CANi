import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../features/auth';
import { eoService } from '../../services/eoService';
import { showToast } from '../../components/common/Toast';
import CaniLogo from '../../components/common/CaniLogo';
import '../../features/auth/auth.css';

const PHONE_REGEX = /^(\+84|0)[0-9]{8,10}$/;

/**
 * Trang Bổ Sung Thông Tin Bắt Buộc Sau Khi Đăng Nhập Google (dành cho Nhà Tổ Chức Sự Kiện)
 * Người dùng thiếu thông tin bắt buộc (SĐT, tên đơn vị, địa chỉ...) bắt buộc hoàn tất ở đây
 * trước khi được phép truy cập vào cổng quản trị chính (/eo).
 */
export default function CompleteProfilePage() {
  const navigate = useNavigate();
  const { user, isAuthenticated, isLoading: authLoading, refreshUser, logout } = useAuth();

  const [formData, setFormData] = useState({
    fullName: '',
    phone: '',
    companyName: '',
    address: '',
  });
  const [fieldErrors, setFieldErrors] = useState({});
  const [loadingProfile, setLoadingProfile] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    if (authLoading) return;
    if (!isAuthenticated) {
      navigate('/login', { replace: true });
      return;
    }
    if (user?.role === 'ADMIN') {
      navigate('/admin', { replace: true });
      return;
    }
    if (user?.role === 'CUSTOMER_CARE') {
      navigate('/customer-care', { replace: true });
      return;
    }

    let cancelled = false;
    const loadCurrentProfile = async () => {
      setLoadingProfile(true);
      try {
        const profile = await eoService.getEoProfile();
        if (cancelled) return;
        setFormData({
          fullName: profile?.fullName || user?.fullName || '',
          phone: profile?.phone || user?.phone || '',
          companyName: profile?.companyName || '',
          address: profile?.address || '',
        });
      } catch {
        if (!cancelled) {
          setFormData((prev) => ({
            ...prev,
            fullName: user?.fullName || '',
            phone: user?.phone || '',
          }));
        }
      } finally {
        if (!cancelled) {
          setLoadingProfile(false);
        }
      }
    };

    loadCurrentProfile();
    return () => {
      cancelled = true;
    };
  }, [authLoading, isAuthenticated, user?.role, user?.fullName, user?.phone, navigate]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
    if (fieldErrors[name]) {
      setFieldErrors((prev) => ({ ...prev, [name]: '' }));
    }
    if (errorMessage) setErrorMessage('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const errors = {};

    if (!formData.fullName.trim()) {
      errors.fullName = 'Vui lòng nhập họ và tên người đại diện.';
    }

    const cleanPhone = formData.phone.trim().replace(/\s+/g, '');
    if (!cleanPhone) {
      errors.phone = 'Số điện thoại liên hệ là bắt buộc.';
    } else if (!PHONE_REGEX.test(cleanPhone)) {
      errors.phone = 'Số điện thoại không hợp lệ (VD: 0912345678 hoặc +84912345678).';
    }

    if (!formData.companyName.trim()) {
      errors.companyName = 'Vui lòng nhập tên đơn vị / tổ chức sự kiện.';
    }

    if (!formData.address.trim()) {
      errors.address = 'Vui lòng nhập địa chỉ liên hệ hoặc văn phòng.';
    }

    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setErrorMessage('Vui lòng điền đầy đủ các thông tin còn thiếu bên dưới.');
      return;
    }

    setSubmitting(true);
    setErrorMessage('');

    try {
      await eoService.updateEoProfile({
        fullName: formData.fullName.trim(),
        phone: cleanPhone,
        companyName: formData.companyName.trim(),
        address: formData.address.trim(),
      });

      await refreshUser();

      let targetUrl = '/eo';
      try {
        const ekyc = await eoService.getEkycStatus();
        if (ekyc?.status !== 'APPROVED') {
          targetUrl = '/eo?tab=ekyc';
        }
      } catch {
        targetUrl = '/eo?tab=ekyc';
      }

      showToast('Đã cập nhật đầy đủ thông tin tài khoản Nhà Tổ Chức!', 'success');
      navigate(targetUrl, { replace: true });
    } catch (err) {
      setErrorMessage(err.response?.data?.message || err.message || 'Không thể lưu thông tin. Vui lòng thử lại.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSwitchAccount = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  if (authLoading || loadingProfile) {
    return (
      <div className="auth-container">
        <div className="auth-card" style={{ textAlign: 'center', padding: '3rem 2rem' }}>
          <div className="cani-spinner" style={{ margin: '0 auto 1rem' }} />
          <p style={{ color: '#64748b', fontSize: '0.9rem' }}>Đang kiểm tra thông tin tài khoản Google...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-container">
      <div className="auth-card auth-card-wide">
        <div className="auth-header" style={{ marginBottom: '1.25rem' }}>
          <CaniLogo variant="full" height={48} className="auth-logo-center" />
          <h2 className="auth-title" style={{ marginTop: '0.85rem', fontSize: '1.5rem' }}>
            Bổ Sung Thông Tin Tài Khoản
          </h2>
          <p className="auth-subtitle">
            Bạn vừa đăng nhập qua Google. Vui lòng hoàn thiện các thông tin còn thiếu trước khi vào hệ thống quản trị.
          </p>
        </div>

        {/* Thẻ xác nhận Email Google */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '0.75rem',
            padding: '0.75rem 1rem',
            backgroundColor: '#f0fdf4',
            border: '1px solid #bbf7d0',
            borderRadius: '0.65rem',
            marginBottom: '1.15rem',
          }}
        >
          <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: '0.75rem', color: '#15803d', fontWeight: 700 }}>
              Email Google đã xác thực
            </div>
            <div
              style={{
                fontSize: '0.875rem',
                fontWeight: 600,
                color: '#0f172a',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {user?.email}
            </div>
          </div>
          <span
            style={{
              backgroundColor: '#dcfce7',
              color: '#166534',
              fontSize: '0.75rem',
              fontWeight: 700,
              padding: '0.25rem 0.6rem',
              borderRadius: '999px',
              whiteSpace: 'nowrap',
            }}
          >
            Đã liên kết
          </span>
        </div>

        {errorMessage && (
          <div className="auth-error-banner" role="alert" style={{ marginBottom: '1rem' }}>
            {errorMessage}
          </div>
        )}

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="form-group">
            <label className="form-label" htmlFor="fullName">
              Họ và tên người đại diện *
            </label>
            <input
              id="fullName"
              name="fullName"
              type="text"
              className={`form-input ${fieldErrors.fullName ? 'input-error' : ''}`}
              placeholder="Nguyễn Văn A"
              value={formData.fullName}
              onChange={handleChange}
              disabled={submitting}
              required
            />
            {fieldErrors.fullName && (
              <div className="field-error-text">{fieldErrors.fullName}</div>
            )}
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="phone">
              Số điện thoại liên hệ *{' '}
              {!formData.phone?.trim() && (
                <span style={{ color: '#ea580c', fontSize: '0.75rem', fontWeight: 700 }}>
                  (Còn thiếu)
                </span>
              )}
            </label>
            <input
              id="phone"
              name="phone"
              type="tel"
              className={`form-input ${fieldErrors.phone ? 'input-error' : ''}`}
              placeholder="0912345678"
              value={formData.phone}
              onChange={handleChange}
              disabled={submitting}
              autoFocus={!formData.phone?.trim()}
              required
            />
            {fieldErrors.phone && (
              <div className="field-error-text">{fieldErrors.phone}</div>
            )}
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="companyName">
              Tên đơn vị / nhóm tổ chức sự kiện *{' '}
              {!formData.companyName?.trim() && (
                <span style={{ color: '#ea580c', fontSize: '0.75rem', fontWeight: 700 }}>
                  (Còn thiếu)
                </span>
              )}
            </label>
            <input
              id="companyName"
              name="companyName"
              type="text"
              className={`form-input ${fieldErrors.companyName ? 'input-error' : ''}`}
              placeholder="Công ty Sự Kiện / CLB Tổ Chức..."
              value={formData.companyName}
              onChange={handleChange}
              disabled={submitting}
              required
            />
            {fieldErrors.companyName && (
              <div className="field-error-text">{fieldErrors.companyName}</div>
            )}
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="address">
              Địa chỉ liên hệ / văn phòng *{' '}
              {!formData.address?.trim() && (
                <span style={{ color: '#ea580c', fontSize: '0.75rem', fontWeight: 700 }}>
                  (Còn thiếu)
                </span>
              )}
            </label>
            <input
              id="address"
              name="address"
              type="text"
              className={`form-input ${fieldErrors.address ? 'input-error' : ''}`}
              placeholder="Số nhà, Đường, Phường/Xã, Quận/Huyện, Tỉnh/TP"
              value={formData.address}
              onChange={handleChange}
              disabled={submitting}
              required
            />
            {fieldErrors.address && (
              <div className="field-error-text">{fieldErrors.address}</div>
            )}
          </div>

          <button type="submit" className="btn-submit" disabled={submitting}>
            {submitting ? (
              <>
                <span className="spinner" />
                Đang lưu thông tin...
              </>
            ) : (
              'Hoàn tất thông tin & Tiếp tục →'
            )}
          </button>
        </form>

        <div className="auth-footer" style={{ marginTop: '1.25rem', paddingTop: '1rem', borderTop: '1px solid #f3f4f6' }}>
          <button
            type="button"
            onClick={handleSwitchAccount}
            disabled={submitting}
            style={{
              background: 'none',
              border: 'none',
              color: '#64748b',
              fontSize: '0.85rem',
              fontWeight: 600,
              cursor: 'pointer',
              textDecoration: 'underline',
            }}
          >
            Đăng xuất & Đổi tài khoản khác
          </button>
        </div>
      </div>
    </div>
  );
}
