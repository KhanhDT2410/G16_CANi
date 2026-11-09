import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import RequireRole from './features/auth/components/RequireRole';
import ErrorBoundary from './components/common/ErrorBoundary';

const EOPage = lazy(() => import('./pages/eo/EOPage'));
const AdminPage = lazy(() => import('./pages/admin/AdminPage'));
const LoginPage = lazy(() => import('./pages/auth/LoginPage'));
const RegisterPage = lazy(() => import('./pages/auth/RegisterPage'));
const AuthCallbackPage = lazy(() => import('./pages/auth/AuthCallbackPage'));
const CustomerCarePage = lazy(() => import('./pages/customer-care/CustomerCarePage'));
const LandingPage = lazy(() => import('./pages/landing/LandingPage'));

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
    <ErrorBoundary>
      <BrowserRouter>
        <Suspense fallback={<PageFallback />}>
          <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/auth/callback" element={<AuthCallbackPage />} />
          <Route element={<MainLayout />}>
            <Route
              path="eo"
              element={
                <RequireRole allowedRoles={['EVENT_ORGANIZER']}>
                  <EOPage />
                </RequireRole>
              }
            />
            {/* Direct routes to EO Profile */}
            <Route path="profile" element={<Navigate to="/eo?tab=profile" replace />} />
            <Route path="eo/profile" element={<Navigate to="/eo?tab=profile" replace />} />
            {/* Worker & Mobile Simulator không khả dụng trên Web Portal, tự động điều hướng về Trang chủ */}
            <Route path="worker" element={<Navigate to="/" replace />} />
            <Route path="mobile-preview" element={<Navigate to="/" replace />} />
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
            {/* Fallback route cho duong dan cu /cskh */}
            <Route path="cskh" element={<Navigate to="/customer-care" replace />} />
          </Route>
        </Routes>
      </Suspense>
    </BrowserRouter>
  </ErrorBoundary>
  );
}

export default App;
