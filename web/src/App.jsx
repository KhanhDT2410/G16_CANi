import React, { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import RequireRole from './features/auth/components/RequireRole';

class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }
  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }
  componentDidCatch(error, errorInfo) {
    console.error('[CANi App Crash]', error, errorInfo);
  }
  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: '3rem 2rem', maxWidth: '640px', margin: '4rem auto', textAlign: 'center', fontFamily: 'system-ui, sans-serif' }}>
          <h2 style={{ color: '#dc2626', marginBottom: '1rem' }}>Đã xảy ra lỗi giao diện</h2>
          <pre style={{ background: '#f3f4f6', padding: '1rem', borderRadius: '8px', color: '#b91c1c', textAlign: 'left', overflowX: 'auto', fontSize: '0.85rem' }}>
            {this.state.error?.message || String(this.state.error)}
          </pre>
          <div style={{ marginTop: '1.5rem', display: 'flex', gap: '0.75rem', justifyContent: 'center' }}>
            <button
              onClick={() => window.location.reload()}
              style={{ padding: '0.6rem 1.25rem', background: '#2563eb', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 600 }}
            >
              Tải lại trang
            </button>
            <button
              onClick={() => { localStorage.clear(); window.location.href = '/login'; }}
              style={{ padding: '0.6rem 1.25rem', background: '#e5e7eb', color: '#374151', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 600 }}
            >
              Xóa Cache & Đăng nhập lại
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

const EOPage = lazy(() => import('./pages/eo/EOPage'));
const AdminPage = lazy(() => import('./pages/admin/AdminPage'));
const LoginPage = lazy(() => import('./pages/auth/LoginPage'));
const RegisterPage = lazy(() => import('./pages/auth/RegisterPage'));
const AuthCallbackPage = lazy(() => import('./pages/auth/AuthCallbackPage'));
const CustomerCarePage = lazy(() => import('./pages/customer-care/CustomerCarePage'));
const MobileShowcasePage = lazy(() => import('./pages/mobile-preview/MobileShowcasePage'));
const SupportPage = lazy(() => import('./pages/support/SupportPage'));

function PageFallback() {
  return (
    <div className="page-loading-fallback">
      <div className="cani-spinner" />
      <span>Đang tải dữ liệu CANi...</span>
    </div>
  );
}

function App() {
  // Tự động chuyển tiếp nếu Supabase fallback redirect về trang chủ kèm mã xác thực ?code=
  if (typeof window !== 'undefined') {
    const searchParams = new URLSearchParams(window.location.search);
    if (searchParams.has('code') && window.location.pathname !== '/auth/callback') {
      window.location.replace('/auth/callback' + window.location.search + window.location.hash);
      return null;
    }
  }

  return (
    <BrowserRouter>
      <Suspense fallback={<PageFallback />}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/auth/callback" element={<AuthCallbackPage />} />
          <Route path="/" element={<MainLayout />}>
            <Route index element={<Navigate to="/eo" replace />} />
            <Route
              path="eo"
              element={
                <RequireRole allowedRoles={['EVENT_ORGANIZER', 'ADMIN']}>
                  <EOPage />
                </RequireRole>
              }
            />
            {/* Worker chỉ hoạt động trên Mobile App, chuyển hướng về EO nếu truy cập trên Web */}
            <Route path="worker" element={<Navigate to="/login" replace />} />
            <Route path="mobile-preview" element={<MobileShowcasePage />} />
            <Route
              path="customer-care"
              element={
                <RequireRole allowedRoles={['CUSTOMER_CARE', 'ADMIN']}>
                  <CustomerCarePage />
                </RequireRole>
              }
            />
            <Route
              path="admin"
              element={
                <RequireRole allowedRoles={['ADMIN']}>
                  <AdminPage />
                </RequireRole>
              }
            />
            <Route path="support" element={<SupportPage />} />
            {/* Fallback route cho duong dan cu /cskh */}
            <Route path="cskh" element={<Navigate to="/customer-care" replace />} />
          </Route >
        </Routes >
      </Suspense >
    </BrowserRouter >
  );
}

export default App;
