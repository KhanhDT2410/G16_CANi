import React, { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import RequireRole from './features/auth/components/RequireRole';
import { AuthProvider } from './features/auth';
import ErrorBoundary from './components/common/ErrorBoundary';
import { PromptDialogHost } from './components/common/PromptModal';

const EOPage = lazy(() => import('./pages/eo/EOPage'));
const AdminPage = lazy(() => import('./pages/admin/AdminPage'));
const LoginPage = lazy(() => import('./pages/auth/LoginPage'));
const RegisterPage = lazy(() => import('./pages/auth/RegisterPage'));
const AuthCallbackPage = lazy(() => import('./pages/auth/AuthCallbackPage'));
const CompleteProfilePage = lazy(() => import('./pages/auth/CompleteProfilePage'));
const CustomerCarePage = lazy(() => import('./pages/customer-care/CustomerCarePage'));
const LandingPage = lazy(() => import('./pages/landing/LandingPage'));
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
  // Tự động chuyển tiếp nếu Supabase fallback redirect về trang chủ kèm mã xác thực OAuth Google (?code=...)
  // Chú ý: Tuyệt đối không chuyển tiếp nếu đây là callback thanh toán PayOS (?orderCode=... hoặc ?status=PAID hoặc code=00)
  if (typeof window !== 'undefined') {
    const searchParams = new URLSearchParams(window.location.search);
    const isPayOSReturn =
      searchParams.has('orderCode') ||
      searchParams.has('payos') ||
      searchParams.has('cancel') ||
      searchParams.get('code') === '00' ||
      searchParams.get('status') === 'PAID' ||
      searchParams.get('status') === 'CANCELLED';

    // Chỉ chuyển hướng về /auth/callback nếu là mã OAuth thực sự (không phải PayOS) và người dùng đang ở trang chủ '/'
    if (!isPayOSReturn && searchParams.has('code') && window.location.pathname === '/') {
      window.location.replace('/auth/callback' + window.location.search + window.location.hash);
      return null;
    }
  }

  return (
    <ErrorBoundary>
      <BrowserRouter>
        <AuthProvider>
          <Suspense fallback={<PageFallback />}>
            <Routes>
            <Route path="/" element={<LandingPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/auth/callback" element={<AuthCallbackPage />} />
            <Route path="/complete-profile" element={<CompleteProfilePage />} />
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
              {/* Worker chỉ hoạt động trên Mobile App, chuyển hướng về Login nếu truy cập trên Web */}
              <Route path="worker" element={<Navigate to="/login" replace />} />
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
              <Route path="support" element={<SupportPage />} />
              {/* Fallback route cho duong dan cu /cskh */}
              <Route path="cskh" element={<Navigate to="/customer-care" replace />} />
            </Route>
            {/* Catch-all fallback về Landing Page */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Suspense>
        </AuthProvider>
      </BrowserRouter>
      <PromptDialogHost />
    </ErrorBoundary>
  );
}

export default App;
