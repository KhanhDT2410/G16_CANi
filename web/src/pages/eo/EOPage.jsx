import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../../features/auth';
import { showToast } from '../../components/common/Toast';

import { useEvents } from './hooks/useEvents';
import { useShifts } from './hooks/useShifts';
import { usePayments } from './hooks/usePayments';

import EOSidebar from './components/EOSidebar';
import EORightSidebar from './components/EORightSidebar';
import EOMetricsGrid from './components/EOMetricsGrid';
import EOEventsTab from './components/EOEventsTab';
import EORecruitmentTab from './components/EORecruitmentTab';
import EOPaymentsTab from './components/EOPaymentsTab';
import EOSubscriptionTab from './components/EOSubscriptionTab';
import EoProfileTab from './components/EoProfileTab';
import EOAnalyticsTab from './components/EOAnalyticsTab';
import EOSettingsTab from './components/EOSettingsTab';
import EOModals from './components/EOModals';
import SettlementVoucherModal from './components/SettlementVoucherModal';

import './EOPage.css';

export default function EOPage() {
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  // Custom Domain Hooks - Khởi tạo sớm trước effect và local state
  const events = useEvents();
  const shifts = useShifts({ onReloadEvents: events.loadEvents });
  const payments = usePayments();

  const [activeNav, setActiveNav] = useState('overview');
  const [activeTab, setActiveTab] = useState('events');
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(() => {
    try {
      return localStorage.getItem('cani_eo_sidebar_collapsed') === 'true';
    } catch {
      return false;
    }
  });

  const toggleSidebarCollapse = () => {
    setIsSidebarCollapsed((prev) => {
      const next = !prev;
      try {
        localStorage.setItem('cani_eo_sidebar_collapsed', String(next));
      } catch (e) {
        console.warn('Lỗi lưu trạng thái sidebar:', e);
      }
      return next;
    });
  };

  // Lắng nghe sự kiện chuyển tab từ Navbar (Header Avatar click)
  useEffect(() => {
    const handleSwitch = (e) => {
      const targetTab = e.detail?.tab;
      if (targetTab) {
        setActiveTab(targetTab);
        setActiveNav(targetTab);
        try {
          setSearchParams({ tab: targetTab }, { replace: true });
        } catch (err) {
          console.warn('Lỗi setSearchParams:', err);
        }
        window.scrollTo({ top: 0, behavior: 'smooth' });
      }
    };
    window.addEventListener('cani_switch_tab', handleSwitch);
    return () => window.removeEventListener('cani_switch_tab', handleSwitch);
  }, [setSearchParams]);

  // Lắng nghe tìm kiếm toàn cục từ Header Search Box
  const { setSearchQuery } = events;
  useEffect(() => {
    const handleGlobalSearch = (e) => {
      const q = e.detail?.query ?? '';
      setSearchQuery(q);
      if (q.trim()) {
        setActiveTab((prev) => (prev !== 'events' && prev !== 'recruitment' ? 'events' : prev));
        setActiveNav((prev) => (prev !== 'events' && prev !== 'recruitment' ? 'events' : prev));
      }
    };
    window.addEventListener('cani_global_search', handleGlobalSearch);
    return () => window.removeEventListener('cani_global_search', handleGlobalSearch);
  }, [setSearchQuery]);

  // Kiểm tra nếu có redirect từ VNPay (?tab=subscription hoặc chứa param vnp_) hoặc điều hướng tab (?tab=profile)
  useEffect(() => {
    const tabParam = searchParams.get('tab');
    if (tabParam === 'subscription' || searchParams.has('vnp_ResponseCode')) {
      setActiveTab('subscription');
      setActiveNav('subscription');
    } else if (tabParam === 'profile' || tabParam === 'ekyc') {
      setActiveTab('profile');
      setActiveNav('profile');
    } else if (tabParam && ['events', 'recruitment', 'payments', 'analytics', 'settings'].includes(tabParam)) {
      setActiveTab(tabParam);
      setActiveNav(tabParam);
    }
  }, [searchParams]);

  const [mapModal, setMapModal] = useState({
    isOpen: false,
    eventName: '',
    venueAddress: '',
    lat: 10.7328,
    lng: 106.7214,
    radiusKm: 15,
  });

  const [inviteModal, setInviteModal] = useState({
    isOpen: false,
    shift: null,
    event: null,
  });

  const [disputeModal, setDisputeModal] = useState({
    isOpen: false,
    assignment: null,
  });

  const [isVoucherModalOpen, setIsVoucherModalOpen] = useState(false);

  const handleNavClick = (navKey) => {
    setActiveNav(navKey);
    let target = 'events';
    if (navKey === 'overview' || navKey === 'events') target = 'events';
    else if (navKey === 'recruitment') target = 'recruitment';
    else if (navKey === 'payments') target = 'payments';
    else if (navKey === 'subscription') target = 'subscription';
    else if (navKey === 'profile' || navKey === 'ekyc') target = 'profile';
    else if (navKey === 'analytics') target = 'analytics';
    else if (navKey === 'settings') target = 'settings';
    
    setActiveTab(target);
    try {
      setSearchParams({ tab: target }, { replace: true });
    } catch (err) {
      console.warn('Lỗi setSearchParams:', err);
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const switchTab = (tabKey) => {
    setActiveTab(tabKey);
    setActiveNav(tabKey);
    try {
      setSearchParams({ tab: tabKey }, { replace: true });
    } catch (err) {
      console.warn('Lỗi setSearchParams:', err);
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const displayName = user?.fullName || user?.username || 'Duy Hưng';

  return (
    <div className="eo-dashboard-container">
      <div className={`eo-dashboard-layout ${isSidebarCollapsed ? 'sidebar-collapsed' : ''} tab-${activeTab}`}>
        <EOSidebar
          activeNav={activeNav}
          onNavClick={handleNavClick}
          isCollapsed={isSidebarCollapsed}
          onToggleCollapse={toggleSidebarCollapse}
        />

        <main className="eo-main-stream">
          {/* Hero Banner & Metrics Grid chỉ hiển thị ở tab Sự kiện / Tổng quan */}
          {(activeTab === 'events' || activeNav === 'overview') && (
            <>
              <section className="eo-hero-banner">
                <div className="hero-content">
                  <h1 className="hero-greeting">Chào mừng bạn quay trở lại, {displayName}!</h1>
                  <p className="hero-subtitle">
                    Hôm nay bạn có <strong>{events.events.length} sự kiện</strong> sắp diễn ra. Hãy kiểm tra danh sách điểm danh và phân công ca làm nhé.
                  </p>
                  <button type="button" className="hero-action-btn" onClick={() => events.setShowEventModal(true)}>
                    <span className="btn-plus-icon">+</span> Tạo Sự Kiện Mới
                  </button>
                </div>
              </section>

              <EOMetricsGrid metrics={events.metrics} />
            </>
          )}

          <div className="eo-pill-tabs">
            <button type="button" className={`pill-tab ${activeTab === 'events' ? 'active' : ''}`} onClick={() => switchTab('events')}>
              Sự Kiện Của Tôi
            </button>
            <button type="button" className={`pill-tab ${activeTab === 'recruitment' ? 'active' : ''}`} onClick={() => switchTab('recruitment')}>
              Tuyển Dụng & Điều Phối
            </button>
            <button type="button" className={`pill-tab ${activeTab === 'payments' ? 'active' : ''}`} onClick={() => switchTab('payments')}>
              Thanh Toán & Khai Báo Lương
            </button>
            <button type="button" className={`pill-tab ${activeTab === 'subscription' ? 'active' : ''}`} onClick={() => switchTab('subscription')}>
              Gói Dịch Vụ & Quota
            </button>
            <button type="button" className={`pill-tab ${activeTab === 'profile' || activeTab === 'ekyc' ? 'active' : ''}`} onClick={() => switchTab('profile')}>
              Hồ Sơ
            </button>
            <button type="button" className={`pill-tab ${activeTab === 'analytics' ? 'active' : ''}`} onClick={() => switchTab('analytics')}>
              Báo Cáo & Thống Kê
            </button>
            <button type="button" className={`pill-tab ${activeTab === 'settings' ? 'active' : ''}`} onClick={() => switchTab('settings')}>
              Cài Đặt Hệ Thống
            </button>
          </div>

          {activeTab === 'events' && (
            <EOEventsTab
              loading={events.loading}
              filteredEvents={events.filteredEvents}
              searchQuery={events.searchQuery}
              setSearchQuery={events.setSearchQuery}
              statusFilter={events.statusFilter}
              setStatusFilter={events.setStatusFilter}
              sortBy={events.sortBy}
              setSortBy={events.setSortBy}
              expandedEventId={shifts.expandedEventId}
              setExpandedEventId={shifts.setExpandedEventId}
              onOpenCreateEvent={() => events.setShowEventModal(true)}
              onOpenAddShift={(id) => { shifts.setSelectedEventId(id); shifts.setShowShiftModal(true); }}
              onOpenEditEvent={events.setEditingEvent}
              onCancelEvent={events.handleCancelEvent}
              onOpenConfirmPublish={(evt) => {
                if (!evt.totalShifts || evt.totalShifts === 0) {
                  showToast('Sự kiện này chưa có ca làm việc nào. Vui lòng thêm ca làm việc trước khi kích hoạt Matching!', 'warning');
                  shifts.setSelectedEventId(evt.eventId);
                  shifts.setShowShiftModal(true);
                  return;
                }
                events.openConfirmPublish(evt);
              }}
              onOpenMap={setMapModal}
              onOpenQr={shifts.handleOpenShiftQr}
              onOpenInvite={(shift, event) => setInviteModal({ isOpen: true, shift, event })}
              onOpenAttendance={shifts.handleOpenAttendance}
              onEditShift={shifts.setEditingShift}
              onCancelShift={shifts.handleCancelShift}
            />
          )}

          {activeTab === 'recruitment' && (
            <EORecruitmentTab
              events={events.events}
              onOpenAddShift={(id) => { shifts.setSelectedEventId(id); shifts.setShowShiftModal(true); }}
              onOpenQr={shifts.handleOpenShiftQr}
              onOpenInvite={(shift, event) => setInviteModal({ isOpen: true, shift, event })}
              onOpenAttendance={shifts.handleOpenAttendance}
              onOpenCreateEvent={() => events.setShowEventModal(true)}
              onReloadEvents={events.loadEvents}
            />
          )}

          {activeTab === 'payments' && (
            <EOPaymentsTab
              payments={payments.payments}
              onOpenPaymentModal={(p) => {
                payments.setSelectedAssignment(p);
                payments.setPaymentForm((prev) => ({ ...prev, assignmentId: p.assignmentId, declaredAmount: p.agreedAmount }));
                payments.setShowPaymentModal(true);
              }}
              onOpenDisputeModal={(p) => setDisputeModal({ isOpen: true, assignment: p })}
              onOpenVoucherModal={() => setIsVoucherModalOpen(true)}
            />
          )}

          {activeTab === 'subscription' && <EOSubscriptionTab />}

          {(activeTab === 'profile' || activeTab === 'ekyc') && <EoProfileTab />}

          {activeTab === 'analytics' && (
            <EOAnalyticsTab
              events={events.events}
              payments={payments.payments}
              onOpenVoucherModal={() => setIsVoucherModalOpen(true)}
            />
          )}

          {activeTab === 'settings' && (
            <EOSettingsTab onNavigateToSubscription={() => switchTab('subscription')} />
          )}
        </main>

        {(activeTab === 'events' || activeNav === 'overview') && <EORightSidebar />}
      </div>

      <EOModals
        events={events}
        shifts={shifts}
        payments={payments}
        mapModal={mapModal}
        setMapModal={setMapModal}
        inviteModal={inviteModal}
        setInviteModal={setInviteModal}
        disputeModal={disputeModal}
        setDisputeModal={setDisputeModal}
      />

      {/* Modal Xuất Phiếu Chi Thù Lao & Quyết Toán Kế Toán (Mẫu 02-TT / NĐ 52/2024/NĐ-CP) */}
      <SettlementVoucherModal
        isOpen={isVoucherModalOpen}
        onClose={() => setIsVoucherModalOpen(false)}
        events={events.events}
        payments={payments.payments}
        companyInfo={{
          companyName: user?.companyName || user?.fullName || 'HUNG COMPANY',
          taxCode: user?.taxCode || '0108923489',
          address: user?.address || 'Tòa nhà FPT, Khu CNC Hòa Lạc, Thạch Thất, Hà Nội',
          organizerName: user?.fullName || 'Duy Hưng Phạm',
        }}
      />
    </div>
  );
}
