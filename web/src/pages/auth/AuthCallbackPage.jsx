import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { supabase } from '../../features/auth/api/supabaseClient';
import { authApi } from '../../features/auth/api/authApi';

/**
 * Trang Đón Tiếp Callback OAuth Google (/auth/callback)
 * Tiếp nhận phiên đăng nhập từ Supabase -> Đồng bộ sang Backend CANi Spring Boot
 */
const AuthCallbackPage = () => {
  const navigate = useNavigate();
  const [status, setStatus] = useState('Đang hoàn tất xác thực với Google...');
  const [errorMessage, setErrorMessage] = useState('');
  const [isProcessing, setIsProcessing] = useState(true);

  useEffect(() => {
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
          setErrorMessage('Tài khoản của bạn là Nhân sự (Worker). Vui lòng đăng nhập trên ứng dụng CANi Mobile!');
          setIsProcessing(false);
          return;
        }

        // Lưu thông tin phiên đăng nhập
        if (loginData.token) {
          localStorage.setItem('cani_token', loginData.token);
        }
        if (loginData.refreshToken) {
          localStorage.setItem('cani_refresh_token', loginData.refreshToken);
        }
        if (loginData.user) {
          localStorage.setItem('cani_user', JSON.stringify(loginData.user));
        }

        setStatus('Đăng nhập thành công! Đang chuyển hướng...');
        setTimeout(() => {
          switch (loginData.user?.role) {
            case 'ADMIN':
              navigate('/admin', { replace: true });
              break;
            case 'CUSTOMER_CARE':
              navigate('/customer-care', { replace: true });
              break;
            case 'EVENT_ORGANIZER':
            default:
              navigate('/eo', { replace: true });
              break;
          }
        }, 600);
      } catch (err) {
        console.error('Lỗi đồng bộ Google Auth:', err);
        setErrorMessage(err.message || 'Đăng nhập bằng Google thất bại. Vui lòng thử lại.');
        setIsProcessing(false);
      }
    };

    // 1. Kiểm tra nếu có mã code từ PKCE OAuth (?code=...)
    const urlParams = new URLSearchParams(window.location.search);
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
