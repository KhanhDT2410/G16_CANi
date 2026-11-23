import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { supabase } from '../../features/auth/api/supabaseClient';
import { authApi } from '../../features/auth/api/authApi';
import { useAuth } from '../../features/auth';
import { eoService } from '../../services/eoService';

/**
 * Trang Đón Tiếp Callback OAuth Google (/auth/callback)
 * Tiếp nhận phiên đăng nhập từ Supabase -> Đồng bộ sang Backend CANi Spring Boot
 */
const AuthCallbackPage = () => {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [status, setStatus] = useState('Đang hoàn tất xác thực với Google...');
  const [errorMessage, setErrorMessage] = useState('');
  const [isProcessing, setIsProcessing] = useState(true);

  useEffect(() => {
    // 0. Phòng vệ an toàn: Nếu bằng cách nào đó URL thanh toán PayOS (?orderCode=...) bị lạc vào trang này,
    // lập tức điều hướng trở về cổng Quản lý Gói cước của Nhà tổ chức sự kiện (EO), tuyệt đối không xử lý như OAuth Google
    const urlParams = new URLSearchParams(window.location.search);
    const isPayOS =
      urlParams.has('orderCode') ||
      urlParams.has('payos') ||
      urlParams.has('cancel') ||
      urlParams.get('code') === '00' ||
      urlParams.get('status') === 'PAID';

    if (isPayOS) {
      navigate('/eo?tab=subscription&' + urlParams.toString(), { replace: true });
      return;
    }

    let isHandled = false;

    const processSession = async (session) => {
      if (isHandled || !session?.access_token) return;
      isHandled = true;

      try {
        setStatus('Đang đồng bộ tài khoản với hệ thống CANi...');
        const loginData = await authApi.syncGoogleAuth({
          supabaseAccessToken: session.access_token,
          targetRole: 'EVENT_ORGANIZER',
        });

        // Nếu tài khoản được gán là WORKER, từ chối trên Web Portal
        if (loginData.user && loginData.user.role === 'WORKER') {
          await authApi.logout();
          setErrorMessage('Tài khoản của bạn là Nhân sự. Vui lòng đăng nhập trên ứng dụng CANi Mobile!');
          setIsProcessing(false);
          return;
        }

        // Lưu thông tin phiên đăng nhập vào AuthContext & localStorage
        login(loginData.user, loginData.token, loginData.refreshToken);

        setStatus('Đang kiểm tra thông tin hồ sơ tài khoản...');
        let nextPath = '/eo';
        if (loginData.user?.role === 'ADMIN') {
          nextPath = '/admin';
        } else if (loginData.user?.role === 'CUSTOMER_CARE') {
          nextPath = '/customer-care';
        } else {
          // Bắt buộc kiểm tra hồ sơ EO: nếu thiếu SĐT, họ tên, tên đơn vị hoặc địa chỉ -> điều hướng vào trang bổ sung thông tin
          let needsCompletion = !loginData.user?.phone?.trim() || !loginData.user?.fullName?.trim();
          if (!needsCompletion) {
            try {
              const profile = await eoService.getEoProfile();
              if (
                !profile?.phone?.trim() ||
                !profile?.fullName?.trim() ||
                !profile?.companyName?.trim() ||
                !profile?.address?.trim()
              ) {
                needsCompletion = true;
              }
            } catch {
              needsCompletion = true;
            }
          }
          nextPath = needsCompletion ? '/complete-profile' : '/eo';
        }

        setStatus('Đăng nhập thành công! Đang chuyển hướng...');
        setTimeout(() => {
          navigate(nextPath, { replace: true });
        }, 400);
      } catch (err) {
        console.error('Lỗi đồng bộ Google Auth:', err);
        const errData = err.response?.data?.data;
        const isLocked =
          errData?.code === 'ACCOUNT_LOCKED' ||
          err.message?.toLowerCase().includes('khóa') ||
          err.response?.data?.message?.toLowerCase().includes('khóa');

        if (isLocked) {
          navigate('/account-locked', {
            state: {
              email: errData?.email || session?.user?.email || '',
              lockReason: errData?.lockReason || err.response?.data?.message || '',
              appealToken: errData?.appealToken || '',
              role: errData?.role || 'EVENT_ORGANIZER',
            },
            replace: true,
          });
          return;
        }
        setErrorMessage(err.message || 'Đăng nhập bằng Google thất bại. Vui lòng thử lại.');
        setIsProcessing(false);
      }
    };

    // 1. Kiểm tra nếu có mã code từ PKCE OAuth (?code=...)
    const authCode = urlParams.get('code');

    if (authCode) {
      supabase.auth.exchangeCodeForSession(authCode).then(({ data, error }) => {
        if (error) {
          console.warn('[AuthCallback] Lỗi exchangeCodeForSession:', error.message);
        } else if (data?.session) {
          processSession(data.session);
        }
      });
    }

    // 2. Kiểm tra session hiện có
    supabase.auth.getSession().then(({ data: { session }, error }) => {
      if (error) {
        setErrorMessage('Lỗi xác thực Supabase: ' + error.message);
        setIsProcessing(false);
        return;
      }
      if (session) {
        processSession(session);
      }
    });

    // 3. Lắng nghe sự kiện AuthStateChange (khi Supabase bóc tách hash #access_token=... từ Google)
    const { data: { subscription } } = supabase.auth.onAuthStateChange((event, session) => {
      if (session && (event === 'SIGNED_IN' || event === 'INITIAL_SESSION')) {
        processSession(session);
      }
    });

    // 3. Fallback timeout nếu sau 8s không nhận được token
    const timer = setTimeout(() => {
      if (!isHandled) {
        setErrorMessage('Không nhận được phản hồi đăng nhập từ Google hoặc phiên đã hết hạn.');
        setIsProcessing(false);
      }
    }, 8000);

    return () => {
      clearTimeout(timer);
      subscription?.unsubscribe();
    };
  }, [navigate]);

  return (
    <div
      style={{
        display: 'flex',
        minHeight: '100vh',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#f8fafc',
        fontFamily: "'Plus Jakarta Sans', sans-serif",
        padding: '1.5rem',
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: '440px',
          background: '#ffffff',
          borderRadius: '16px',
          padding: '2.5rem 2rem',
          boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.08), 0 8px 10px -6px rgba(0, 0, 0, 0.04)',
          textAlign: 'center',
          border: '1px solid #e2e8f0',
        }}
      >
        <div style={{ marginBottom: '1.5rem' }}>
          <span style={{ fontSize: '28px', fontWeight: 800, color: '#2563eb', letterSpacing: '-0.5px' }}>
            CANi
          </span>
          <p style={{ fontSize: '13px', color: '#64748b', marginTop: '4px' }}>
            Nền tảng Quản trị & Điều phối Nhân sự Sự kiện
          </p>
        </div>

        {isProcessing && !errorMessage ? (
          <div>
            <div
              style={{
                width: '44px',
                height: '44px',
                margin: '0 auto 1.25rem',
                border: '3px solid #e2e8f0',
                borderTopColor: '#2563eb',
                borderRadius: '50%',
                animation: 'spin 0.8s linear infinite',
              }}
            />
            <h3 style={{ fontSize: '16px', fontWeight: 600, color: '#1e293b', marginBottom: '0.5rem' }}>
              {status}
            </h3>
            <p style={{ fontSize: '13px', color: '#64748b' }}>
              Vui lòng không đóng trình duyệt trong giây lát...
            </p>
          </div>
        ) : (
          <div>
            <div
              style={{
                width: '48px',
                height: '48px',
                margin: '0 auto 1rem',
                backgroundColor: '#fef2f2',
                color: '#ef4444',
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '24px',
              }}
            >
              ✕
            </div>
            <h3 style={{ fontSize: '16px', fontWeight: 600, color: '#991b1b', marginBottom: '0.5rem' }}>
              Xác thực không thành công
            </h3>
            <p style={{ fontSize: '13px', color: '#64748b', marginBottom: '1.5rem', lineHeight: '1.5' }}>
              {errorMessage}
            </p>
            <button
              onClick={() => navigate('/login')}
              style={{
                width: '100%',
                padding: '0.75rem 1rem',
                backgroundColor: '#2563eb',
                color: '#ffffff',
                border: 'none',
                borderRadius: '10px',
                fontWeight: 600,
                fontSize: '14px',
                cursor: 'pointer',
                transition: 'background-color 0.2s',
              }}
              onMouseOver={(e) => (e.currentTarget.style.backgroundColor = '#1d4ed8')}
              onMouseOut={(e) => (e.currentTarget.style.backgroundColor = '#2563eb')}
            >
              Quay lại Đăng nhập
            </button>
          </div>
        )}

        <style>
          {`
            @keyframes spin {
              from { transform: rotate(0deg); }
              to { transform: rotate(360deg); }
            }
          `}
        </style>
      </div>
    </div>
  );
};

export default AuthCallbackPage;
