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

import { SupportTicketsCenterView } from '../../features/support-tickets';
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
    else if (navKey === 'support') target = 'support';
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
              <section className="eo-hero-banner-clean">
                <div className="hero-clean-left">
                  <h1 className="hero-clean-greeting">Chào mừng trở lại, {displayName}!</h1>
                  <p className="hero-clean-subtitle">
                    Quản lý sự kiện, tuyển dụng và điều phối nhân sự dễ dàng trên CANi.
                  </p>
                </div>
                <div className="hero-clean-right">
                  <div className="hero-3d-calendar-stage">
                    <div className="hero-3d-calendar-card">
                      <div className="hero-calendar-spiral-top">
                        <span className="spiral-ring" />
                        <span className="spiral-ring" />
                        <span className="spiral-ring" />
                      </div>
                      <div className="hero-calendar-body">
                        <div className="hero-calendar-check-circle">✓</div>
                      </div>
                    </div>
                    <div className="hero-coordinator-badge">
                      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2">
                        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                        <circle cx="12" cy="7" r="4"></circle>
                      </svg>
                    </div>
                  </div>
                </div>
              </section>

              <EOMetricsGrid metrics={events.metrics} />
            </>
          )}

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

          {/* Hỗ trợ kỹ thuật & Phiếu yêu cầu (UC-105 / UC-106) */}
          {activeTab === 'support' && (
            <div className="animate-fade-in-up" style={{ marginTop: '0.5rem' }}>
              <SupportTicketsCenterView />
            </div>
          )}

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

        {(activeTab === 'events' || activeNav === 'overview') && (
          <EORightSidebar
            metrics={events.metrics}
            events={events.events}
            onOpenQr={() => {
              if (events.events.length > 0 && events.events[0].totalShifts > 0) {
                shifts.handleOpenShiftQr(events.events[0].eventId, events.events[0].name);
              } else {
                showToast('Chưa có ca làm việc nào để tạo mã QR điểm danh.', 'warning');
              }
            }}
            onOpenRecruitment={() => switchTab('recruitment')}
            onOpenSubscription={() => switchTab('subscription')}
          />
        )}
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
    </div >
  );
}
