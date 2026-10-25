import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import EOPage from './pages/eo/EOPage';
import AdminPage from './pages/admin/AdminPage';
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import AuthCallbackPage from './pages/auth/AuthCallbackPage';
import CustomerCarePage from './pages/customer-care/CustomerCarePage';

import WorkerPage from './pages/worker/WorkerPage';

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
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/auth/callback" element={<AuthCallbackPage />} />
        <Route path="/" element={<MainLayout />}>
          <Route index element={<Navigate to="/eo" replace />} />
          <Route path="eo" element={<EOPage />} />
          {/* Worker chỉ hoạt động trên Mobile App, chuyển hướng về EO nếu truy cập trên Web */}
          <Route path="worker" element={<Navigate to="/eo" replace />} />
          <Route path="customer-care" element={<CustomerCarePage />} />
          <Route path="admin" element={<AdminPage />} />
          {/* Fallback route cho duong dan cu /cskh */}
          <Route path="cskh" element={<Navigate to="/customer-care" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
