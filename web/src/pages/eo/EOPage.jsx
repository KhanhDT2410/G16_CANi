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
import EOPaymentsTab from './components/EOPaymentsTab';
import EOSubscriptionTab from './components/EOSubscriptionTab';
import EoProfileTab from './components/EoProfileTab';
import EOAnalyticsTab from './components/EOAnalyticsTab';
import EOSettingsTab from './components/EOSettingsTab';
import EOModals from './components/EOModals';
import EventDetailModal from './components/EventDetailModal';
import SettlementVoucherModal from './components/SettlementVoucherModal';
import EkycRequiredModal from './components/EkycRequiredModal';
import { eoService } from '../../services/eoService';

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
  const [activeTab, setActiveTab] = useState('overview');
  const [activeEventSubTab, setActiveEventSubTab] = useState('list');
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
        if (targetTab === 'recruitment') {
          setActiveTab('events');
          setActiveNav('events');
          setActiveEventSubTab('list');
        } else if (targetTab === 'analytics') {
          setActiveTab('overview');
          setActiveNav('overview');
        } else {
          setActiveTab(targetTab);
          setActiveNav(targetTab);
        }
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
        setActiveTab('events');
        setActiveNav('events');
        setActiveEventSubTab('list');
      }
    };
    window.addEventListener('cani_global_search', handleGlobalSearch);
    return () => window.removeEventListener('cani_global_search', handleGlobalSearch);
  }, [setSearchQuery]);

  // Kiểm tra nếu có redirect từ VNPay / PayOS (?tab=subscription hoặc chứa param vnp_, payos, orderCode) hoặc điều hướng tab (?tab=profile)
  useEffect(() => {
    const tabParam = searchParams.get('tab');
    if (
      tabParam === 'subscription' ||
      searchParams.has('vnp_ResponseCode') ||
      searchParams.has('payos') ||
      searchParams.has('orderCode')
    ) {
      setActiveTab('subscription');
      setActiveNav('subscription');
    } else if (tabParam === 'profile' || tabParam === 'ekyc') {
      setActiveTab('profile');
      setActiveNav('profile');
    } else if (tabParam === 'recruitment') {
      setActiveTab('events');
      setActiveNav('events');
      setActiveEventSubTab('list');
    } else if (tabParam === 'analytics' || tabParam === 'overview') {
      setActiveTab('overview');
      setActiveNav('overview');
    } else if (tabParam && ['events', 'payments', 'settings', 'support'].includes(tabParam)) {
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
  const [detailEvent, setDetailEvent] = useState(null);
  const [ekycStatus, setEkycStatus] = useState('APPROVED');
  const [showEkycModal, setShowEkycModal] = useState(false);

  // Đồng bộ detailEvent khi danh sách events được tải lại (thêm ca, xuất bản, sửa sự kiện)
  useEffect(() => {
    if (detailEvent?.eventId && Array.isArray(events.events)) {
      const updated = events.events.find((e) => e.eventId === detailEvent.eventId);
      if (updated) {
        setDetailEvent(updated);
      }
    }
  }, [events.events, detailEvent?.eventId]);

  const handleOpenEventDetailPage = (evt) => {
    if (!evt) return;
    setDetailEvent(evt);
    setActiveTab('events');
    setActiveNav('events');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  // Lấy trạng thái eKYC của Nhà tổ chức
  const fetchEkycStatus = async () => {
    try {
      const res = await eoService.getEkycStatus();
      if (res?.status) {
        setEkycStatus(res.status);
      }
    } catch (err) {
      console.warn('Lỗi kiểm tra eKYC status:', err);
    }
  };

  useEffect(() => {
    fetchEkycStatus();
    const handleEkycUpdated = () => fetchEkycStatus();
    window.addEventListener('cani_ekyc_updated', handleEkycUpdated);
    return () => window.removeEventListener('cani_ekyc_updated', handleEkycUpdated);
  }, []);

  const handleOpenCreateEvent = () => {
    if (ekycStatus !== 'APPROVED') {
      setShowEkycModal(true);
      return;
    }
    events.setShowEventModal(true);
  };

  const handleNavClick = (navKey) => {
    setDetailEvent(null);
    setActiveNav(navKey);
    let target = 'overview';
    if (navKey === 'overview' || navKey === 'analytics') {
      target = 'overview';
    } else if (navKey === 'events') {
      target = 'events';
    } else if (navKey === 'recruitment') {
      target = 'events';
      setActiveNav('events');
      setActiveEventSubTab('recruitment');
    } else if (navKey === 'payments') {
      target = 'payments';
    } else if (navKey === 'subscription') {
      target = 'subscription';
    } else if (navKey === 'profile' || navKey === 'ekyc') {
      target = 'profile';
    } else if (navKey === 'support') {
      target = 'support';
    } else if (navKey === 'settings') {
      target = 'settings';
    }

    setActiveTab(target);
    try {
      setSearchParams({ tab: target }, { replace: true });
    } catch (err) {
      console.warn('Lỗi setSearchParams:', err);
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const switchTab = (tabKey) => {
    if (tabKey === 'recruitment') {
      setActiveTab('events');
      setActiveNav('events');
      setActiveEventSubTab('list');
      try {
        setSearchParams({ tab: 'events' }, { replace: true });
      } catch (err) {
        console.warn('Lỗi setSearchParams:', err);
      }
      window.scrollTo({ top: 0, behavior: 'smooth' });
      return;
    }
    setActiveTab(tabKey);
    setActiveNav(tabKey);
    try {
      setSearchParams({ tab: tabKey }, { replace: true });
    } catch (err) {
      console.warn('Lỗi setSearchParams:', err);
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleEventSubTabChange = (subKey) => {
    if (subKey === 'detail') {
      const target = detailEvent || (Array.isArray(events.events) ? events.events.find((e) => e.status !== 'CANCELLED') || events.events[0] : null);
      setDetailEvent(target);
      setActiveEventSubTab('detail');
      return;
    }
    setDetailEvent(null);
    setActiveEventSubTab(subKey);
    if (subKey === 'cancelled') {
      events.setStatusFilter('CANCELLED');
    } else if (events.statusFilter === 'CANCELLED') {
      events.setStatusFilter('ALL');
    }
  };

  const displayName = user?.fullName || user?.username || 'Ban Tổ Chức';

  return (
    <div className="eo-dashboard-container">
      <div className={`eo-dashboard-layout ${isSidebarCollapsed ? 'sidebar-collapsed' : ''} tab-${activeTab}`}>
        <EOSidebar
          activeNav={activeNav}
          onNavClick={handleNavClick}
          activeEventSubTab={activeEventSubTab}
          onEventSubTabClick={handleEventSubTabChange}
          isCollapsed={isSidebarCollapsed}
          onToggleCollapse={toggleSidebarCollapse}
        />

        <main className="eo-main-stream">
          {/* TRANG TỔNG QUAN (Gộp Báo Cáo & Thống Kê Tài Chính + Uy Tín vào Tổng Quan) */}
          {activeTab === 'overview' && (
            <>
              <section className="eo-hero-banner-clean">
                <div className="hero-clean-left">
                  <h1 className="hero-clean-greeting">Chào mừng trở lại, {displayName}!</h1>
                  <p className="hero-clean-subtitle">
                    Tổng quan chỉ số vận hành, ngân sách và báo cáo quyết toán sự kiện trên CANi.
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

              {/* Banner cảnh báo khi tài khoản chưa hoàn tất eKYC */}
              {ekycStatus && ekycStatus !== 'APPROVED' && (
                <div className="eo-ekyc-warning-banner">
                  <div className="warning-banner-left">
                    <span className="warning-icon">⚠️</span>
                    <div>
                      <strong>Tài khoản chưa hoàn tất định danh eKYC</strong>
                      <p>
                        {ekycStatus === 'PENDING_REVIEW'
                          ? 'Hồ sơ eKYC của bạn đang trong quá trình thẩm định. Bạn cần được phê duyệt để mở khóa tạo sự kiện.'
                          : 'Bạn cần hoàn tất xác thực định danh (CCCD/ĐKKD) để được phép tạo sự kiện và tuyển dụng nhân sự trên CANi.'}
                      </p>
                    </div>
                  </div>
                  <button
                    type="button"
                    className="btn-verify-ekyc-now"
                    onClick={() => switchTab('profile')}
                  >
                    {ekycStatus === 'PENDING_REVIEW' ? 'Xem Trạng Thái eKYC →' : 'Xác Thực Định Danh Ngay →'}
                  </button>
                </div>
              )}

              <EOMetricsGrid
                metrics={events.metrics}
                onOpenActiveEventDetail={() => {
                  const active = events.events.find((e) => e.status === 'ACTIVE' || e.status === 'APPROVED') || events.events[0];
                  if (active) {
                    handleOpenEventDetailPage(active);
                  }
                }}
              />

              {/* Báo cáo & Thống kê Tài chính đã gộp vào Tổng quan (đã lược bỏ các chỉ số trùng lặp) */}
              <EOAnalyticsTab
                events={events.events}
                payments={payments.payments}
                onOpenVoucherModal={() => setIsVoucherModalOpen(true)}
              />
            </>
          )}

          {/* TRANG RIÊNG: QUẢN LÝ SỰ KIỆN HOẶC TRANG CHI TIẾT SỰ KIỆN */}
          {activeTab === 'events' && (
            (detailEvent || activeEventSubTab === 'detail') ? (
              <EventDetailModal
                isOpen={true}
                asPage={true}
                allEvents={events.events}
                onSelectEvent={(nextEvt) => setDetailEvent(nextEvt)}
                onClose={() => {
                  setDetailEvent(null);
                  handleEventSubTabChange('list');
                }}
                event={
                  detailEvent ||
                  (Array.isArray(events.events)
                    ? events.events.find((e) => e.status !== 'CANCELLED') || events.events[0]
                    : null)
                }
                onOpenAddShift={(id) => { shifts.setSelectedEventId(id); shifts.setShowShiftModal(true); }}
                onOpenInvite={(shift, event) => setInviteModal({ isOpen: true, shift, event })}
                onOpenConfirmPublish={(evt) => {
                  const shiftCount = evt.totalShifts || (Array.isArray(evt.shifts) ? evt.shifts.length : 0);
                  if (!shiftCount) {
                    showToast('Sự kiện này chưa có ca làm việc nào. Vui lòng thêm ca làm việc trước khi đăng tuyển!', 'warning');
                    shifts.setSelectedEventId(evt.eventId);
                    shifts.setShowShiftModal(true);
                    return;
                  }
                  events.openConfirmPublish(evt);
                }}
                onOpenEditEvent={events.setEditingEvent}
                onEditShift={shifts.setEditingShift}
                onCancelShift={shifts.handleCancelShift}
                onReloadEvents={events.loadEvents}
                onOpenPaymentDeclaration={payments.handleOpenPaymentDeclarationFromAttendance}
              />
            ) : (
              <EOEventsTab
                loading={events.loading}
                allEvents={events.events}
                filteredEvents={events.filteredEvents}
                searchQuery={events.searchQuery}
                setSearchQuery={events.setSearchQuery}
                statusFilter={events.statusFilter}
                setStatusFilter={events.setStatusFilter}
                sortBy={events.sortBy}
                setSortBy={events.setSortBy}
                expandedEventId={shifts.expandedEventId}
                setExpandedEventId={shifts.setExpandedEventId}
                activeSubTab={activeEventSubTab}
                onSubTabChange={handleEventSubTabChange}
                onOpenEventDetail={handleOpenEventDetailPage}
                onDeleteEvent={events.handleDeleteEvent}
                onOpenCreateEvent={handleOpenCreateEvent}
                onOpenAddShift={(id) => { shifts.setSelectedEventId(id); shifts.setShowShiftModal(true); }}
                onOpenEditEvent={events.setEditingEvent}
                onCancelEvent={events.handleCancelEvent}
                onOpenConfirmPublish={(evt) => {
                  const shiftCount = evt.totalShifts || (Array.isArray(evt.shifts) ? evt.shifts.length : 0);
                  if (!shiftCount) {
                    showToast('Sự kiện này chưa có ca làm việc nào. Vui lòng thêm ca làm việc trước khi kích hoạt tìm kiếm!', 'warning');
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
                onReloadEvents={events.loadEvents}
                onOpenPaymentDeclaration={payments.handleOpenPaymentDeclarationFromAttendance}
              />
            )
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

          {activeTab === 'settings' && (
            <EOSettingsTab onNavigateToSubscription={() => switchTab('subscription')} />
          )}
        </main>

        {activeTab === 'overview' && (
          <EORightSidebar
            metrics={events.metrics}
            events={events.events}
            onOpenQr={() => {
              handleNavClick('events');
              handleEventSubTabChange('attendance');
            }}
            onOpenRecruitment={() => {
              handleNavClick('events');
              handleEventSubTabChange('recruitment');
            }}
            onOpenSubscription={() => switchTab('subscription')}
            onViewAllEvents={() => {
              handleNavClick('events');
              handleEventSubTabChange('list');
            }}
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
        onEventCreated={async (createdEvt) => {
          const refreshed = await events.loadEvents();
          const list = Array.isArray(refreshed) ? refreshed : events.events;
          const found = list.find((e) => e.eventId === createdEvt?.eventId) || createdEvt;
          handleOpenEventDetailPage(found);
        }}
      />

      {/* Modal Xuất Phiếu Chi Thù Lao & Quyết Toán Kế Toán (Mẫu 02-TT / NĐ 52/2024/NĐ-CP) */}
      <SettlementVoucherModal
        isOpen={isVoucherModalOpen}
        onClose={() => setIsVoucherModalOpen(false)}
        events={events.events}
        payments={payments.payments}
        companyInfo={{
          companyName: user?.companyName || user?.fullName || 'Đơn vị tổ chức sự kiện',
          taxCode: user?.taxCode || 'Chưa cập nhật',
          address: user?.address || 'Chưa cập nhật địa chỉ',
          organizerName: user?.fullName || user?.username || 'Đại diện Ban Tổ Chức',
        }}
      />
      {/* Modal Cảnh Báo Yêu Cầu eKYC Trước Khi Tạo Sự Kiện */}
      <EkycRequiredModal
        isOpen={showEkycModal}
        onClose={() => setShowEkycModal(false)}
        onNavigateToEkyc={() => switchTab('profile')}
        ekycStatus={ekycStatus}
      />
    </div >
  );
}
