import { useState, useMemo } from 'react';
import { DisputeCenterView } from '../../features/disputes';
import { SupportTicketsCenterView } from '../../features/support-tickets';
import { FaqView } from '../../features/faqs';
import { RsAppealsView } from '../../features/rs-appeals';
import { NoShowView } from '../../features/no-shows';
import CustomerCareSidebar from './components/CustomerCareSidebar';
import './CustomerCarePage.css';

const TAB_METADATA = {
  tickets: {
    title: 'Phiếu Hỗ Trợ Khách Hàng',
    subtitle: 'Hàng đợi tiếp nhận phiếu, phản hồi hỗ trợ người dùng và kiểm soát thời hạn cam kết',
  },
  disputes: {
    title: 'Tranh Chấp & Khiếu Nại',
    subtitle: 'Xử lý khiếu nại ca làm việc, đối soát chứng cứ và phân giải cấp độ 1 giữa các bên',
  },
  faqs: {
    title: 'Câu Hỏi Thường Gặp & Trợ Giúp',
    subtitle: 'Quản lý kho tri thức trung tâm trợ giúp và theo dõi tỷ lệ giải đáp tự động',
  },
  appeals: {
    title: 'Kháng Cáo Điểm Uy Tín',
    subtitle: 'Thẩm định bằng chứng và xét duyệt khiếu nại hoàn trả điểm uy tín cho người lao động',
  },
  noshows: {
    title: 'Xử Lý Vi Phạm Vắng Mặt',
    subtitle: 'Theo dõi vắng mặt không phép, chế tài điểm uy tín và hỗ trợ điều động nhân sự thay thế',
  },
};

export const CustomerCarePage = () => {
  const [activeTab, setActiveTab] = useState('tickets');
  const [ticketFilter, setTicketFilter] = useState('ALL');
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem('cani_cc_sidebar_collapsed') === 'true';
    } catch {
      return false;
    }
  });

  const handleToggleCollapse = () => {
    setCollapsed((prev) => {
      const next = !prev;
      try {
        localStorage.setItem('cani_cc_sidebar_collapsed', String(next));
      } catch {
        // ignore storage errors
      }
      return next;
    });
  };

  const currentMeta = useMemo(() => {
    return TAB_METADATA[activeTab] || {
      title: 'Cổng Chăm Sóc Khách Hàng',
      subtitle: 'Tiếp nhận yêu cầu, giải quyết tranh chấp và hỗ trợ người dùng nền tảng',
    };
  }, [activeTab]);

  return (
    <div className="cc-layout animate-fade-in">
      {/* Sidebar bên trái thu gọn/mở rộng */}
      <CustomerCareSidebar
        activeTab={activeTab}
        onSelectTab={setActiveTab}
        activeTicketFilter={ticketFilter}
        onSelectTicketFilter={setTicketFilter}
        collapsed={collapsed}
        onToggleCollapse={handleToggleCollapse}
      />

      {/* Khu vực nội dung chính */}
      <main className="cc-main-wrapper">
        <header className="cc-topbar">
          <div className="cc-topbar-left">
            <h1 className="cc-topbar-title">{currentMeta.title}</h1>
            <p className="cc-topbar-subtitle">{currentMeta.subtitle}</p>
          </div>
        </header>

        <section className="cc-content-card">
          {activeTab === 'tickets' && (
            <SupportTicketsCenterView
              activeFilter={ticketFilter}
              onFilterChange={setTicketFilter}
            />
          )}
          {activeTab === 'disputes' && <DisputeCenterView />}
          {activeTab === 'faqs' && <FaqView />}
          {activeTab === 'appeals' && <RsAppealsView />}
          {activeTab === 'noshows' && <NoShowView onNavigateAppeal={() => setActiveTab('appeals')} />}
        </section>
      </main>
    </div>
  );
};

export default CustomerCarePage;
