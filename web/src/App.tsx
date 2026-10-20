import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import EOPage from './pages/eo/EOPage';
import AdminPage from './pages/admin/AdminPage';
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import WorkerDashboard from './pages/worker/WorkerDashboard';
import FinancePage from './pages/finance/FinancePage';
import CskhPage from './pages/cskh/CskhPage';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/" element={<MainLayout />}>
          <Route index element={<Navigate to="/worker" replace />} />
          <Route path="worker" element={<WorkerDashboard />} />
          <Route path="eo" element={<EOPage />} />
          <Route path="admin" element={<AdminPage />} />
          <Route path="finance" element={<FinancePage />} />
          <Route path="cskh" element={<CskhPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
