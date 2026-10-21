import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import EOPage from './pages/eo/EOPage';
import AdminPage from './pages/admin/AdminPage';
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import CustomerCarePage from './pages/customer-care/CustomerCarePage';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/" element={<MainLayout />}>
          <Route index element={<Navigate to="/eo" replace />} />
          <Route path="eo" element={<EOPage />} />
          <Route path="admin" element={<AdminPage />} />
          <Route path="customer-care" element={<CustomerCarePage />} />
          {/* Fallback route cho đường dẫn cũ /cskh */}
          <Route path="cskh" element={<Navigate to="/customer-care" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
