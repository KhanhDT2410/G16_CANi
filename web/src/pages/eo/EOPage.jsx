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

import './EOPage.css';

export default function EOPage() {
  const { user } = useAuth();
  const [searchParams] = useSearchParams();
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

  const events = useEvents();
  const shifts = useShifts({ onReloadEvents: events.loadEvents });
  const payments = usePayments();

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

  const handleNavClick = (navKey) => {
    setActiveNav(navKey);
    if (navKey === 'overview' || navKey === 'events') setActiveTab('events');
    else if (navKey === 'recruitment') setActiveTab('recruitment');
    else if (navKey === 'payments') setActiveTab('payments');
    else if (navKey === 'subscription') setActiveTab('subscription');
    else if (navKey === 'profile' || navKey === 'ekyc') setActiveTab('profile');
    else if (navKey === 'analytics') setActiveTab('analytics');
    else if (navKey === 'settings') setActiveTab('settings');
  };

  const switchTab = (tabKey) => {
    setActiveTab(tabKey);
    setActiveNav(tabKey);
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

          <div className="eo-pill-tabs">
            <button type="button" className={`pill-tab ${activeTab === 'events' ? 'active' : ''}`} onClick={() => switchTab('events')}>
              Sự Kiện Của Tôi ({events.events.length})
            </button>
            <button type="button" className={`pill-tab ${activeTab === 'recruitment' ? 'active' : ''}`} onClick={() => switchTab('recruitment')}>
              Tuyển Dụng & Matching
            </button>
            <button type="button" className={`pill-tab ${activeTab === 'payments' ? 'active' : ''}`} onClick={() => switchTab('payments')}>
              Thanh Toán & Khai Báo Lương ({payments.payments.length})
            </button>
            <button type="button" className={`pill-tab ${activeTab === 'subscription' ? 'active' : ''}`} onClick={() => switchTab('subscription')}>
              Gói Dịch Vụ & Quota (W-06)
            </button>
            <button type="button" className={`pill-tab ${activeTab === 'profile' || activeTab === 'ekyc' ? 'active' : ''}`} onClick={() => switchTab('profile')}>
              Hồ Sơ & eKYC
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
            />
          )}

          {activeTab === 'subscription' && <EOSubscriptionTab />}

          {(activeTab === 'profile' || activeTab === 'ekyc') && <EoProfileTab />}

          {activeTab === 'analytics' && (
            <EOAnalyticsTab events={events.events} payments={payments.payments} />
          )}

          {activeTab === 'settings' && (
            <EOSettingsTab onNavigateToSubscription={() => switchTab('subscription')} />
          )}
        </main>

        <EORightSidebar />
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
    </div>
  );
}
