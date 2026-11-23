import { useState, useMemo, useEffect } from 'react';
import { UserManagementView } from '../../features/admin-users';
import { AdminShiftView } from '../../features/admin-shifts';
import { AdminDashboardView } from '../../features/admin-dashboard';
import { DisputeCenterView } from '../../features/disputes';
import { SupportTicketsCenterView } from '../../features/support-tickets';
import { SystemConfigView } from '../../features/system-configs';
import { FaqView } from '../../features/faqs';
import { RevenueDashboardView } from '../../features/revenue-dashboard';
import { RsAppealsView } from '../../features/rs-appeals';
import { AdminAccountAppealsTable } from '../../features/account-appeals';
import { NoShowView } from '../../features/no-shows';
import { AdminPlansView } from '../../features/admin-plans';
import { WageMatrixView } from '../../features/admin-wage-matrix';
import AdminSidebar from './components/AdminSidebar';
import './AdminPage.css';

const TAB_METADATA = {
  dashboard: {
    title: 'Bảng Điều Khiển & Chỉ Số',
    subtitle: 'Tổng hợp số liệu người dùng và hoạt động vận hành thời gian thực',
  },
  revenue: {
    title: 'Doanh Thu & Tài Chính',
    subtitle: 'Theo dõi dòng tiền, phân tích gói đăng ký doanh nghiệp và lịch sử giao dịch',
  },
  users: {
    title: 'Quản Trị Người Dùng',
    subtitle: 'Tra cứu danh sách, kiểm tra hồ sơ chi tiết và phân quyền khóa hoặc mở tài khoản',
  },
  shifts: {
    title: 'Danh Sách Ca Làm Việc',
    subtitle: 'Tra cứu thông tin sự kiện, ca làm việc thực tế và đối soát nhân sự tham gia',
  },
  noshows: {
    title: 'Xử Lý Vi Phạm Vắng Mặt',
    subtitle: 'Giám sát vắng mặt không phép, tự động trừ điểm uy tín và điều động nhân sự thay thế',
  },
  appeals: {
    title: 'Kháng Cáo Điểm Uy Tín',
    subtitle: 'Thẩm định bằng chứng và xét duyệt khiếu nại hoàn trả điểm uy tín cho người lao động',
  },
  account_appeals: {
    title: 'Giải Trình Khóa Tài Khoản',
    subtitle: 'Thẩm định hồ sơ giải trình và chứng từ khắc phục từ Nhà tổ chức (EO) hoặc Người lao động (Worker) bị khóa',
  },
  tickets: {
    title: 'Phiếu Hỗ Trợ Khách Hàng',
    subtitle: 'Hàng đợi tiếp nhận phiếu, phản hồi trực tiếp và ghi chú nội bộ bảo mật',
  },
  disputes: {
    title: 'Tranh Chấp & Khiếu Nại',
    subtitle: 'Tiếp nhận đơn khiếu nại hợp đồng ca làm việc, phân công và ra phán quyết cuối cùng',
  },
  faqs: {
    title: 'Câu Hỏi Thường Gặp',
    subtitle: 'Quản lý kho tri thức trợ giúp, kiểm soát trạng thái xuất bản và đo lường giảm tải',
  },
  config_matching: {
    title: 'Thuật Toán Ghép Nối & Matching',
    subtitle: 'Tinh chỉnh trọng số khoảng cách, đánh giá sao, kinh nghiệm và công thức phân phối đề xuất ca',
  },
  config_operation: {
    title: 'Vận Hành & Điểm Uy Tín',
    subtitle: 'Thiết lập ngưỡng thời gian vắng mặt (No-show), mức trừ điểm uy tín và thời hạn đánh giá',
  },
  config_financial: {
    title: 'Tài Chính & Hoa Hồng',
    subtitle: 'Quản trị tỷ lệ hoa hồng nền tảng và thời hạn đối soát chuyển khoản thù lao trực tiếp',
  },
  plans: {
    title: 'Thiết Lập Giá & Hạn Mức Gói Dịch Vụ',
    subtitle: 'Quy định đơn giá (VNĐ), hạn mức sự kiện, ca làm, nhân sự và tính năng Matching cho Ban tổ chức sự kiện',
  },
  wage_matrix: {
    title: 'Biểu Ma Trận Thù Lao Chuẩn',
    subtitle: 'Thiết lập định mức thù lao thị trường theo khu vực, thời điểm ca và mức độ khẩn cấp',
  },
  config_general: {
    title: 'Hệ Thống & Tài Khoản',
    subtitle: 'Quy định độ tuổi lao động tối thiểu, phiên bản ứng dụng di động bắt buộc và hộp thư CSKH',
  },
  configs: {
    title: 'Tham Số Hệ Thống',
    subtitle: 'Cấu hình linh hoạt các tham số thuật toán ghép ca, ngưỡng vi phạm và quy tắc nền tảng',
  },
};

const AdminPage = () => {
  const [activeTab, setActiveTab] = useState('dashboard');
  const [ticketFilter, setTicketFilter] = useState('ALL');
  const [ticketRefreshKey, setTicketRefreshKey] = useState(0);
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem('cani_admin_sidebar_collapsed') === 'true';
    } catch {
      return false;
    }
  });

  // Xử lý sự kiện click vào User block trên Header để trở về Bảng điều khiển chính, mở chat hoặc deep-link thông báo
  useEffect(() => {
    const handleNavHome = () => setActiveTab('dashboard');
    const handleOpenStaffChat = () => {
      setActiveTab('tickets');
      setTicketFilter('CHAT');
    };
    const handleSwitchTab = (e) => {
      const tab = e.detail?.tab;
      if (tab === 'support' || tab === 'tickets') {
        setActiveTab('tickets');
      } else if (tab === 'disputes') {
        setActiveTab('disputes');
      } else if (tab === 'events' || tab === 'shifts') {
        setActiveTab('shifts');
      } else if (tab === 'payments' || tab === 'revenue') {
        setActiveTab('revenue');
      } else if (tab === 'appeals') {
        setActiveTab('appeals');
      } else if (tab === 'noshows') {
        setActiveTab('noshows');
      }
    };
    if (window.__cani_pending_chat) {
      setActiveTab('tickets');
      setTicketFilter('CHAT');
    }
    window.addEventListener('cani_admin_navigate_home', handleNavHome);
    window.addEventListener('cani_open_staff_chat', handleOpenStaffChat);
    window.addEventListener('cani_switch_tab', handleSwitchTab);
    return () => {
      window.removeEventListener('cani_admin_navigate_home', handleNavHome);
      window.removeEventListener('cani_open_staff_chat', handleOpenStaffChat);
      window.removeEventListener('cani_switch_tab', handleSwitchTab);
    };
  }, []);

  const handleToggleCollapse = () => {
    setCollapsed((prev) => {
      const next = !prev;
      try {
        localStorage.setItem('cani_admin_sidebar_collapsed', String(next));
      } catch {
        // ignore storage errors
      }
      return next;
    });
  };

  const currentMeta = useMemo(() => {
    if (activeTab === 'tickets' && ticketFilter === 'CHAT') {
      return {
        title: 'Tin Nhắn Trực Tiếp',
        subtitle: 'Hộp thư hội thoại trực tiếp với Nhà tổ chức sự kiện và Nhân viên',
      };
    }
    return TAB_METADATA[activeTab] || {
      title: 'Cổng Quản Trị Hệ Thống',
      subtitle: 'Giám sát vận hành, quản trị người dùng và cấu hình hệ thống',
    };
  }, [activeTab, ticketFilter]);

  return (
    <div className="admin-layout animate-fade-in">
      {/* Sidebar bên trái thu gọn/mở rộng */}
      <AdminSidebar
        activeTab={activeTab}
        onSelectTab={setActiveTab}
        activeTicketFilter={ticketFilter}
        onSelectTicketFilter={setTicketFilter}
        collapsed={collapsed}
        onToggleCollapse={handleToggleCollapse}
      />

      {/* Khu vực nội dung chính */}
      <main className="admin-main-wrapper">
        <header className="admin-topbar">
          <div className="admin-topbar-left">
            <h1 className="admin-topbar-title">{currentMeta.title}</h1>
            <p className="admin-topbar-subtitle">{currentMeta.subtitle}</p>
          </div>
          {activeTab === 'tickets' && (
            <button
              type="button"
              className="ticket-btn ticket-btn-secondary"
              onClick={() => setTicketRefreshKey((k) => k + 1)}
            >
              Làm Mới
            </button>
          )}
        </header>

        <section className="admin-content-card">
          {activeTab === 'dashboard' && <AdminDashboardView onNavigateTab={setActiveTab} />}
          {activeTab === 'revenue' && <RevenueDashboardView />}
          {activeTab === 'appeals' && <RsAppealsView />}
          {activeTab === 'noshows' && <NoShowView onNavigateAppeal={() => setActiveTab('appeals')} />}
          {activeTab === 'users' && <UserManagementView />}
          {activeTab === 'account_appeals' && <AdminAccountAppealsTable />}
          {activeTab === 'shifts' && <AdminShiftView />}
          {activeTab === 'disputes' && <DisputeCenterView />}
          {activeTab === 'tickets' && (
            <SupportTicketsCenterView
              activeFilter={ticketFilter}
              onFilterChange={setTicketFilter}
              refreshKey={ticketRefreshKey}
            />
          )}
          {activeTab === 'faqs' && <FaqView />}
          {activeTab === 'config_matching' && <SystemConfigView activeCategory="MATCHING" />}
          {activeTab === 'config_operation' && <SystemConfigView activeCategory="OPERATION" />}
          {activeTab === 'config_financial' && <SystemConfigView activeCategory="FINANCIAL" />}
          {activeTab === 'plans' && <AdminPlansView />}
          {activeTab === 'wage_matrix' && <WageMatrixView />}
          {activeTab === 'config_general' && <SystemConfigView activeCategory="GENERAL" />}
          {activeTab === 'configs' && <SystemConfigView activeCategory="ALL" />}
        </section>
      </main>
    </div>
  );
};

export default AdminPage;
