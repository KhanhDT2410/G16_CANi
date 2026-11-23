import { useState, useEffect, useRef, lazy, Suspense } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../../features/auth';
import { showToast } from '../../components/common/Toast';

import { useEvents } from './hooks/useEvents';
import { useShifts } from './hooks/useShifts';
import { usePayments } from './hooks/usePayments';

import EOSidebar from './components/EOSidebar';
import EORightSidebar from './components/EORightSidebar';
import EOEventDataState from './components/EOEventDataState';
import EOMetricsGrid from './components/EOMetricsGrid';
import EOOverviewAnalytics from './components/EOOverviewAnalytics';
import EOModals from './components/EOModals';
import EkycRequiredModal from './components/EkycRequiredModal';
import { eoService } from '../../services/eoService';
import { createDefaultNewEvent } from './utils/eventHelpers';
import { useDirectChat, DirectMessengerDock } from '../../features/direct-chat';
import EOMessagesTab from './components/EOMessagesTab';
import './EOPage.css';

// Lazy-load các tab & workspace nặng để giữ bundle ban đầu gọn nhẹ dưới 500 kB
const EOEventsTab = lazy(() => import('./components/EOEventsTab'));
const EOPaymentsTab = lazy(() => import('./components/EOPaymentsTab'));
const EOSubscriptionTab = lazy(() => import('./components/EOSubscriptionTab'));
const EoProfileTab = lazy(() => import('./components/EoProfileTab'));
const EOMyTeamTab = lazy(() => import('./components/EOMyTeamTab'));
const EventCreationWizardModal = lazy(() => import('./components/EventCreationWizardModal'));
const EventDetailModal = lazy(() => import('./components/EventDetailModal'));
const ShiftWorkspacePage = lazy(() => import('./components/ShiftWorkspacePage'));
const SettlementVoucherModal = lazy(() => import('./components/SettlementVoucherModal'));
const EOSupportTab = lazy(() => import('./components/EOSupportTab'));
const DisputeCenterView = lazy(() =>
  import('../../features/disputes').then((m) => ({ default: m.DisputeCenterView }))
);

function EOTabFallback() {
  return (
    <div className="page-loading-fallback" style={{ minHeight: '280px' }}>
      <div className="cani-spinner" />
      <span>Đang tải khu vực làm việc...</span>
    </div>
  );
}

export default function EOPage() {
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  // Custom Domain Hooks - Khởi tạo sớm trước effect và local state
  const events = useEvents();
  const shifts = useShifts({ onReloadEvents: events.loadEvents });
  const payments = usePayments();
  const directChat = useDirectChat();

  const [activeNav, setActiveNav] = useState('overview');
  const [activeTab, setActiveTab] = useState('overview');
  const [activeEventSubTab, setActiveEventSubTab] = useState('list');
  const [highlightAssignmentId, setHighlightAssignmentId] = useState(null);
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

  // Lắng nghe sự kiện chuyển tab từ Navbar hoặc Chuông thông báo (Deep Link)
  const eventsRef = useRef(events);
  eventsRef.current = events;

  useEffect(() => {
    const handleSwitch = (e) => {
      const targetTab = e.detail?.tab;
      const targetShiftId = e.detail?.shiftId;
      const targetEventId = e.detail?.eventId;
      const targetAssignmentId = e.detail?.assignmentId;

      if (targetAssignmentId) {
        setHighlightAssignmentId(targetAssignmentId);
      }

      if (targetTab) {
        if (targetTab === 'recruitment') {
          setActiveTab('events');
          setActiveNav('events');
          setActiveEventSubTab('list');
        } else if (targetTab === 'analytics') {
          setActiveTab('overview');
          setActiveNav('overview');
        } else if (targetTab === 'settings') {
          setActiveTab('profile');
          setActiveNav('profile');
        } else {
          setActiveTab(targetTab);
          setActiveNav(targetTab);
        }

        // Deep-link trực tiếp vào trang quản lý ca từ thông báo
        const currentEvents = eventsRef.current;
        const resolvedEvent =
          targetTab === 'events' && Array.isArray(currentEvents?.events)
            ? (targetEventId && currentEvents.events.find((ev) => ev.eventId === targetEventId)) ||
              (targetShiftId &&
                currentEvents.events.find((ev) => (ev.shifts || []).some((sh) => sh.shiftId === targetShiftId))) ||
              null
            : null;
        const resolvedEventId = targetEventId || resolvedEvent?.eventId || null;

        if (targetTab === 'events' && resolvedEvent) {
          setDetailEvent(resolvedEvent);
          if (targetShiftId) {
            setWorkspaceShiftId(targetShiftId);
          } else {
            setWorkspaceShiftId(null);
          }
          setActiveEventSubTab('detail');
          currentEvents?.loadEvents?.();
        } else if (targetTab === 'events' && (targetEventId || targetShiftId)) {
          currentEvents?.loadEvents?.();
        }

        try {
          const nextParams = { tab: targetTab };
          if (targetTab === 'events' && resolvedEventId) nextParams.eventId = String(resolvedEventId);
          if (targetTab === 'events' && targetShiftId) nextParams.shiftId = String(targetShiftId);
          setSearchParams(nextParams, { replace: true });
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

  const { loadEoPayments } = payments;
  useEffect(() => {
    if (activeTab === 'payments') {
      loadEoPayments();
    }
  }, [activeTab, loadEoPayments]);

  // Helper đẩy URL query params vào Browser History để nút Back quay lại đúng màn/bước trước
  const syncUrlRoute = (paramsObj, { replace = false } = {}) => {
    try {
      const next = {};
      Object.entries(paramsObj || {}).forEach(([k, v]) => {
        if (v !== undefined && v !== null && v !== '') {
          next[k] = String(v);
        }
      });
      setSearchParams(next, { replace });
    } catch (err) {
      console.warn('Lỗi setSearchParams:', err);
    }
  };

  // Đồng bộ state từ URL searchParams (hỗ trợ redirect PayOS/tab & nút Back/Forward trình duyệt)
  useEffect(() => {
    const tabParam = searchParams.get('tab');
    const viewParam = searchParams.get('view');
    const stepParam = Number(searchParams.get('step'));
    const eventIdParam = searchParams.get('eventId');
    const shiftIdParam = searchParams.get('shiftId');

    if (
      tabParam === 'subscription' ||
      searchParams.has('vnp_ResponseCode') ||
      searchParams.has('payos') ||
      searchParams.has('orderCode')
    ) {
      setActiveTab('subscription');
      setActiveNav('subscription');
    } else if (tabParam === 'profile' || tabParam === 'ekyc' || tabParam === 'settings') {
      setActiveTab('profile');
      setActiveNav('profile');
    } else if (tabParam === 'recruitment') {
      setActiveTab('events');
      setActiveNav('events');
      setActiveEventSubTab('list');
    } else if (tabParam === 'analytics' || tabParam === 'overview') {
      setActiveTab('overview');
      setActiveNav('overview');
    } else if (tabParam && ['events', 'payments', 'disputes', 'subscription', 'profile', 'my-team', 'messages', 'settings', 'support'].includes(tabParam)) {
      setActiveTab(tabParam);
      setActiveNav(tabParam);
    } else if (!tabParam) {
      setActiveTab('overview');
      setActiveNav('overview');
    }

    if (tabParam === 'events') {
      if (viewParam === 'create') {
        setActiveEventSubTab('create');
        setDetailEvent(null);
        setWorkspaceShiftId(null);
        if (stepParam >= 1 && stepParam <= 3) {
          setWizardStep(stepParam);
        }
      } else if (eventIdParam) {
        const matched = Array.isArray(events.events)
          ? events.events.find((e) => e.eventId === eventIdParam)
          : null;
        if (matched) setDetailEvent(matched);
        setWorkspaceShiftId(shiftIdParam || null);
        setActiveEventSubTab('detail');
      } else {
        setDetailEvent(null);
        setWorkspaceShiftId(null);
        setActiveEventSubTab(viewParam || 'list');
      }
    } else {
      setDetailEvent(null);
      setWorkspaceShiftId(null);
    }
  }, [searchParams, events.events]);

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
  const [detailInitialTab, setDetailInitialTab] = useState('SHIFTS');
  const [workspaceShiftId, setWorkspaceShiftId] = useState(null);
  const [wizardStep, setWizardStep] = useState(1);
  const [ekycStatus, setEkycStatus] = useState(null);
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

  const handleOpenEventDetailPage = (evt, initialTab = 'SHIFTS') => {
    if (!evt) return;
    setDetailEvent(evt);
    setWorkspaceShiftId(null);
    setDetailInitialTab(initialTab);
    setActiveTab('events');
    setActiveNav('events');
    setActiveEventSubTab('detail');
    syncUrlRoute({ tab: 'events', eventId: evt.eventId });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  // Lấy trạng thái eKYC của Nhà tổ chức (tránh bỏ sót xác thực khi API lỗi)
  const fetchEkycStatus = async () => {
    try {
      const res = await eoService.getEkycStatus();
      const nextStatus = res?.status || 'NOT_SUBMITTED';
      setEkycStatus(nextStatus);
      return nextStatus;
    } catch (err) {
      console.warn('Lỗi kiểm tra eKYC status:', err);
      setEkycStatus('NOT_SUBMITTED');
      return 'NOT_SUBMITTED';
    }
  };

  useEffect(() => {
    fetchEkycStatus();
    const handleEkycUpdated = () => fetchEkycStatus();
    window.addEventListener('cani_ekyc_updated', handleEkycUpdated);
    return () => window.removeEventListener('cani_ekyc_updated', handleEkycUpdated);
  }, []);

  const handleOpenCreateEvent = async () => {
    let resolvedStatus = ekycStatus;
    if (!resolvedStatus) {
      resolvedStatus = await fetchEkycStatus();
    }
    if (resolvedStatus !== 'APPROVED') {
      setShowEkycModal(true);
      return;
    }
    events.setNewEvent(createDefaultNewEvent());
    setDetailEvent(null);
    setWorkspaceShiftId(null);
    setWizardStep(1);
    setActiveTab('events');
    setActiveNav('events');
    setActiveEventSubTab('create');
    syncUrlRoute({ tab: 'events', view: 'create', step: 1 });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleNavClick = (navKey) => {
    setDetailEvent(null);
    setWorkspaceShiftId(null);
    setActiveNav(navKey);
    let target = 'overview';
    if (navKey === 'overview' || navKey === 'analytics') {
      target = 'overview';
    } else if (navKey === 'events') {
      target = 'events';
      if (events.statusFilter === 'CANCELLED') events.setStatusFilter('ALL');
    } else if (navKey === 'recruitment') {
      target = 'events';
      setActiveNav('events');
      setActiveEventSubTab('recruitment');
    } else if (navKey === 'payments') {
      target = 'payments';
    } else if (navKey === 'disputes') {
      target = 'disputes';
    } else if (navKey === 'subscription') {
      target = 'subscription';
    } else if (navKey === 'profile' || navKey === 'ekyc' || navKey === 'settings') {
      target = 'profile';
    } else if (navKey === 'my-team') {
      target = 'my-team';
    } else if (navKey === 'messages') {
      target = 'messages';
    } else if (navKey === 'support') {
      target = 'support';
    }

    setActiveTab(target);
    syncUrlRoute({ tab: target });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const switchTab = (tabKey) => {
    if (tabKey === 'recruitment') {
      setActiveTab('events');
      setActiveNav('events');
      setActiveEventSubTab('list');
      syncUrlRoute({ tab: 'events' });
      window.scrollTo({ top: 0, behavior: 'smooth' });
      return;
    }
    setActiveTab(tabKey);
    setActiveNav(tabKey);
    syncUrlRoute({ tab: tabKey });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleEventSubTabChange = (subKey) => {
    if (subKey === 'detail') {
      const target = detailEvent || (Array.isArray(events.events) ? events.events.find((e) => e.status !== 'CANCELLED') || events.events[0] : null);
      setDetailEvent(target);
      setDetailInitialTab('SHIFTS');
      setActiveEventSubTab('detail');
      syncUrlRoute(target?.eventId ? { tab: 'events', eventId: target.eventId } : { tab: 'events' });
      return;
    }
    setDetailEvent(null);
    setDetailInitialTab('SHIFTS');
    setActiveEventSubTab(subKey);
    if (subKey === 'cancelled') {
      events.setStatusFilter('CANCELLED');
    } else if (events.statusFilter === 'CANCELLED') {
      events.setStatusFilter('ALL');
    }
    syncUrlRoute(subKey && subKey !== 'list' ? { tab: 'events', view: subKey } : { tab: 'events' });
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
          unreadMessagesCount={directChat.counts.unread}
        />

        <main className="eo-main-stream">
          {/* TRANG TỔNG QUAN (Gộp Báo Cáo & Thống Kê Tài Chính + Uy Tín vào Tổng Quan) */}
          {activeTab === 'overview' && (
            <>
              <section className="eo-workspace-hero" aria-labelledby="eo-overview-title">
                <div className="eo-workspace-hero-copy">
                  <p>Xin chào, {displayName}</p>
                  <h1 id="eo-overview-title">Tổng quan điều hành</h1>
                  <div className="eo-workspace-actions">
                    <button type="button" className="eo-workspace-create" onClick={handleOpenCreateEvent}>
                      <span aria-hidden="true">+</span> Tạo sự kiện
                    </button>
                    <button type="button" className="eo-workspace-secondary" onClick={() => handleNavClick('events')}>
                      Xem sự kiện <span aria-hidden="true">→</span>
                    </button>
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
                          ? 'Hồ sơ eKYC cũ của bạn chưa được xác thực tự động. Vui lòng thực hiện xác thực định danh VNPT eKYC để mở khóa tạo sự kiện.'
                          : 'Bạn cần hoàn tất xác thực định danh tự động qua VNPT eKYC để được phép tạo sự kiện và tuyển dụng nhân sự trên CANi.'}
                      </p>
                    </div>
                  </div>
                  <button
                    type="button"
                    className="btn-verify-ekyc-now"
                    onClick={() => switchTab('profile')}
                  >
                    {ekycStatus === 'PENDING_REVIEW' ? 'Xác Thực Lại eKYC →' : 'Xác Thực Định Danh Ngay →'}
                  </button>
                </div>
              )}

              {events.loading || events.loadError ? (
                <EOEventDataState loading={events.loading} onRetry={events.loadEvents} />
              ) : (
                <>
                  <EOMetricsGrid
                    metrics={events.metrics}
                    onOpenActiveEventDetail={() => {
                      const active = events.events.find((event) => event.status === 'ACTIVE' || event.status === 'APPROVED');
                      if (active) handleOpenEventDetailPage(active);
                    }}
                  />
                  <EOOverviewAnalytics
                    events={events.events}
                    onSelectEvent={handleOpenEventDetailPage}
                  />
                </>
              )}
            </>
          )}

          {/* TRANG RIÊNG: TẠO SỰ KIỆN, QUẢN LÝ SỰ KIỆN, CHI TIẾT SỰ KIỆN HOẶC TRANG QUẢN LÝ TỪNG CA */}
          {activeTab === 'events' && (
            <Suspense fallback={<EOTabFallback />}>
              {(() => {
            if (activeEventSubTab === 'create') {
              return (
                <EventCreationWizardModal
                  isOpen={true}
                  asPage={true}
                  controlledStep={wizardStep}
                  onStepChange={(nextStep) => {
                    setWizardStep(nextStep);
                    syncUrlRoute({ tab: 'events', view: 'create', step: nextStep });
                  }}
                  onClose={() => handleEventSubTabChange('list')}
                  onSuccess={events.loadEvents}
                  onEventCreated={async (createdEvt) => {
                    const refreshed = await events.loadEvents();
                    const list = Array.isArray(refreshed) ? refreshed : events.events;
                    const found = list.find((e) => e.eventId === createdEvt?.eventId) || createdEvt;
                    handleOpenEventDetailPage(found);
                  }}
                  newEvent={events.newEvent}
                  setNewEvent={events.setNewEvent}
                  locating={events.locating}
                  onGetCurrentLocation={events.handleGetCurrentLocation}
                  skillTaxonomy={shifts.skillTaxonomy}
                />
              );
            }

            const resolvedEvent =
              detailEvent ||
              (activeEventSubTab === 'detail' && Array.isArray(events.events)
                ? events.events.find((e) => e.status !== 'CANCELLED') || events.events[0]
                : null);

            const workspaceShift =
              workspaceShiftId && resolvedEvent
                ? (resolvedEvent.shifts || []).find((s) => s.shiftId === workspaceShiftId)
                : null;

            if (workspaceShift && resolvedEvent) {
              return (
                <ShiftWorkspacePage
                  shift={workspaceShift}
                  event={resolvedEvent}
                  targetAssignmentId={highlightAssignmentId}
                  onSelectShift={(nextShift) => {
                    setWorkspaceShiftId(nextShift.shiftId);
                    syncUrlRoute({ tab: 'events', eventId: resolvedEvent.eventId, shiftId: nextShift.shiftId });
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                  }}
                  onBackToEvent={() => {
                    setWorkspaceShiftId(null);
                    syncUrlRoute({ tab: 'events', eventId: resolvedEvent.eventId });
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                  }}
                  onEditShift={shifts.setEditingShift}
                  onCancelShift={shifts.handleCancelShift}
                  onOpenInvite={(shift, event) => setInviteModal({ isOpen: true, shift, event })}
                  onReloadEvents={events.loadEvents}
                  onOpenPaymentDeclaration={payments.handleOpenPaymentDeclarationFromAttendance}
                  skillTaxonomy={shifts.skillTaxonomy}
                />
              );
            }

            if (resolvedEvent || activeEventSubTab === 'detail') {
              return (
                <EventDetailModal
                  isOpen={true}
                  asPage={true}
                  initialTab={detailInitialTab}
                  allEvents={events.events}
                  onSelectEvent={(nextEvt) => {
                    setWorkspaceShiftId(null);
                    setDetailEvent(nextEvt);
                    if (nextEvt?.eventId) {
                      syncUrlRoute({ tab: 'events', eventId: nextEvt.eventId });
                    }
                  }}
                  onClose={() => {
                    setDetailEvent(null);
                    setWorkspaceShiftId(null);
                    setDetailInitialTab('SHIFTS');
                    handleEventSubTabChange('list');
                  }}
                  event={resolvedEvent}
                  onOpenShiftWorkspace={(sh) => {
                    if (sh?.shiftId) {
                      setWorkspaceShiftId(sh.shiftId);
                      if (resolvedEvent?.eventId) {
                        syncUrlRoute({ tab: 'events', eventId: resolvedEvent.eventId, shiftId: sh.shiftId });
                      }
                      window.scrollTo({ top: 0, behavior: 'smooth' });
                    }
                  }}
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
                  skillTaxonomy={shifts.skillTaxonomy}
                />
              );
            }

            return (
              <EOEventsTab
                loading={events.loading}
                loadError={events.loadError}
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
                onOpenShiftWorkspace={(sh, evt) => {
                  if (evt) setDetailEvent(evt);
                  if (sh?.shiftId) setWorkspaceShiftId(sh.shiftId);
                  if (evt?.eventId && sh?.shiftId) {
                    syncUrlRoute({ tab: 'events', eventId: evt.eventId, shiftId: sh.shiftId });
                  }
                  window.scrollTo({ top: 0, behavior: 'smooth' });
                }}
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
                skillTaxonomy={shifts.skillTaxonomy}
              />
            );
          })()}
            </Suspense>
          )}

          {activeTab === 'payments' && (
            <Suspense fallback={<EOTabFallback />}>
              <EOPaymentsTab
                payments={payments.payments}
                events={events.events}
                loading={payments.loading}
                error={payments.error}
                onRefresh={payments.loadEoPayments}
                onOpenPaymentModal={payments.handleOpenPaymentDeclarationFromAttendance}
                onOpenDisputeModal={(p) => setDisputeModal({ isOpen: true, assignment: p })}
                onOpenVoucherModal={() => setIsVoucherModalOpen(true)}
                onNavigateToDisputes={() => handleNavClick('disputes')}
              />
            </Suspense>
          )}

          {activeTab === 'subscription' && (
            <Suspense fallback={<EOTabFallback />}>
              <EOSubscriptionTab />
            </Suspense>
          )}

          {/* Tranh chấp & Khiếu nại (Dispute Center - UC-96 -> UC-104) */}
          {activeTab === 'disputes' && (
            <Suspense fallback={<EOTabFallback />}>
              <div className="animate-fade-in-up" style={{ marginTop: '0.5rem' }}>
                <DisputeCenterView />
              </div>
            </Suspense>
          )}

          {activeTab === 'my-team' && (
            <Suspense fallback={<EOTabFallback />}>
              <EOMyTeamTab />
            </Suspense>
          )}

          {activeTab === 'messages' && (
            <EOMessagesTab
              directChatState={directChat}
              onOpenShiftDetail={(shiftId) => {
                const foundEvent = events.events?.find((e) =>
                  Array.isArray(e.shifts) && e.shifts.some((s) => s.shiftId === shiftId)
                );
                if (foundEvent) {
                  handleOpenEventDetailPage(foundEvent, 'SHIFTS');
                } else {
                  handleNavClick('events');
                }
              }}
            />
          )}

          {(activeTab === 'profile' || activeTab === 'ekyc') && (
            <Suspense fallback={<EOTabFallback />}>
              <EoProfileTab />
            </Suspense>
          )}

          {/* Trung tâm Trợ giúp & Câu hỏi thường gặp FAQ (SRS UC-105 / UC-110) */}
          {activeTab === 'support' && (
            <Suspense fallback={<EOTabFallback />}>
              <div className="animate-fade-in-up" style={{ marginTop: '0.5rem' }}>
                <EOSupportTab />
              </div>
            </Suspense>
          )}
        </main>

        {activeTab === 'overview' && !events.loading && !events.loadError && (
          <EORightSidebar
            metrics={events.metrics}
            events={events.events}
            onSelectEvent={(evt) => handleOpenEventDetailPage(evt)}
            onOpenQr={(targetEvt) => {
              const active = targetEvt?.eventId
                ? targetEvt
                : (events.events.find((e) => e.status === 'ACTIVE' || e.status === 'APPROVED') || events.events[0]);
              if (active) {
                handleOpenEventDetailPage(active, 'QR_ATTENDANCE');
              } else {
                handleNavClick('events');
                handleEventSubTabChange('list');
              }
            }}
            onOpenRecruitment={() => {
              handleNavClick('events');
              handleEventSubTabChange('list');
              events.setStatusFilter('RECRUITING');
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
      />

      {/* Modal Xuất Phiếu Chi Thù Lao & Quyết Toán Kế Toán (Mẫu 02-TT / NĐ 52/2024/NĐ-CP) */}
      {isVoucherModalOpen && (
        <Suspense fallback={null}>
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
        </Suspense>
      )}
      {/* Modal Cảnh Báo Yêu Cầu eKYC Trước Khi Tạo Sự Kiện */}
      <EkycRequiredModal
        isOpen={showEkycModal}
        onClose={() => setShowEkycModal(false)}
        onNavigateToEkyc={() => switchTab('profile')}
        ekycStatus={ekycStatus}
      />
      {/* Pop-up Messenger Dock trao đổi trực tiếp với Worker */}
      {directChat.activeChat && (
        <DirectMessengerDock
          activeChat={directChat.activeChat}
          onClose={() => directChat.setActiveChat(null)}
          onOpenShiftDetail={(shiftId) => {
            const foundEvent = events.events?.find((e) =>
              Array.isArray(e.shifts) && e.shifts.some((s) => s.shiftId === shiftId)
            );
            if (foundEvent) {
              handleOpenEventDetailPage(foundEvent, 'SHIFTS');
            }
          }}
        />
      )}
    </div >
  );
}
