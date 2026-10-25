import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';

const EOPage = lazy(() => import('./pages/eo/EOPage'));
const AdminPage = lazy(() => import('./pages/admin/AdminPage'));
const LoginPage = lazy(() => import('./pages/auth/LoginPage'));
const RegisterPage = lazy(() => import('./pages/auth/RegisterPage'));
const AuthCallbackPage = lazy(() => import('./pages/auth/AuthCallbackPage'));
const CustomerCarePage = lazy(() => import('./pages/customer-care/CustomerCarePage'));
const MobileShowcasePage = lazy(() => import('./pages/mobile-preview/MobileShowcasePage'));

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
            <Route path="eo" element={<EOPage />} />
            {/* Worker chỉ hoạt động trên Mobile App, chuyển hướng về EO nếu truy cập trên Web */}
            <Route path="worker" element={<Navigate to="/eo" replace />} />
            <Route path="mobile-preview" element={<MobileShowcasePage />} />
            <Route path="customer-care" element={<CustomerCarePage />} />
            <Route path="admin" element={<AdminPage />} />
            {/* Fallback route cho duong dan cu /cskh */}
            <Route path="cskh" element={<Navigate to="/customer-care" replace />} />
          </Route>
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

export default App;
