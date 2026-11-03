import { useState } from 'react';
import { UserManagementView } from '../../features/admin-users';
import { AdminDashboardView } from '../../features/admin-dashboard';
import { DisputeCenterView } from '../../features/disputes';
import { SupportTicketsCenterView } from '../../features/support-tickets';
import AdminEkycTab from './components/AdminEkycTab';
import AdminSkillsTab from './components/AdminSkillsTab';
import './AdminPage.css';

const AdminPage = () => {
  const [activeTab, setActiveTab] = useState('dashboard');
  const [ekycCount, setEkycCount] = useState(0);
  const [skillCount, setSkillCount] = useState(0);

  return (
    <div className="admin-container">
      <div className="admin-header">
        <h1>
          Cổng Quản Trị Hệ Thống
          <span className="admin-badge">ADMIN PORTAL</span>
        </h1>
        <p style={{ color: '#6b7280', margin: 0 }}>
          Giám sát vận hành, phê duyệt hồ sơ định danh eKYC và thẩm định kỹ năng AI
        </p>
      </div>

      {/* Tabs */}
      <div className="admin-tabs">
        <button
          className={`admin-tab-btn ${activeTab === 'dashboard' ? 'active' : ''}`}
          onClick={() => setActiveTab('dashboard')}
        >
          Bảng Điều Khiển
        </button>
        <button
          className={`admin-tab-btn ${activeTab === 'ekyc' ? 'active' : ''}`}
          onClick={() => setActiveTab('ekyc')}
        >
          Hàng Đợi eKYC Căn Cước {ekycCount > 0 ? `(${ekycCount})` : ''}
        </button>
        <button
          className={`admin-tab-btn ${activeTab === 'skills' ? 'active' : ''}`}
          onClick={() => setActiveTab('skills')}
        >
          Hàng Đợi Kỹ Năng AI {skillCount > 0 ? `(${skillCount})` : ''}
        </button>
        <button
          className={`admin-tab-btn ${activeTab === 'users' ? 'active' : ''}`}
          onClick={() => setActiveTab('users')}
        >
          Quản Trị Người Dùng
        </button>
        <button
          className={`admin-tab-btn ${activeTab === 'disputes' ? 'active' : ''}`}
          onClick={() => setActiveTab('disputes')}
        >
          Tranh Chấp & Khiếu Nại
        </button>
        <button
          className={`admin-tab-btn ${activeTab === 'tickets' ? 'active' : ''}`}
          onClick={() => setActiveTab('tickets')}
        >
          Hỗ Trợ Khách Hàng (Tickets)
        </button>
      </div>

      {/* Content */}
      {activeTab === 'dashboard' && <AdminDashboardView onNavigateTab={setActiveTab} />}
      {activeTab === 'ekyc' && <AdminEkycTab onCountChange={setEkycCount} />}
      {activeTab === 'skills' && <AdminSkillsTab onCountChange={setSkillCount} />}
      {activeTab === 'users' && <UserManagementView />}
      {activeTab === 'disputes' && <DisputeCenterView />}
      {activeTab === 'tickets' && <SupportTicketsCenterView />}
    </div>
  );
};

export default AdminPage;
