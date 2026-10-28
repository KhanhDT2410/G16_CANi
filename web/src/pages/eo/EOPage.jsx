import { useState } from 'react';
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
import EoEkycTab from './EoEkycTab';
import EOModals from './components/EOModals';

import './EOPage.css';

export default function EOPage() {
  const { user } = useAuth();
  const [activeNav, setActiveNav] = useState('overview');
  const [activeTab, setActiveTab] = useState('events');

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

  const handleNavClick = (navKey) => {
    setActiveNav(navKey);
    if (navKey === 'overview' || navKey === 'events') setActiveTab('events');
    else if (navKey === 'payments') setActiveTab('payments');
    else if (navKey === 'ekyc') setActiveTab('ekyc');
    else if (navKey === 'recruitment') {
      if (events.events.length > 0) {
        shifts.setSelectedEventId(events.events[0].eventId);
        shifts.setShowShiftModal(true);
      } else {
        events.setShowEventModal(true);
      }
    } else {
      showToast('Tính năng đang được tối ưu trong bản cập nhật kế tiếp!', 'info');
    }
  };

  const displayName = user?.fullName || user?.username || 'Duy Hưng';

  return (
    <div className="eo-dashboard-container">
      <div className="eo-dashboard-layout">
        <EOSidebar activeNav={activeNav} onNavClick={handleNavClick} />

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
            <button type="button" className={`pill-tab ${activeTab === 'events' ? 'active' : ''}`} onClick={() => setActiveTab('events')}>
              Sự Kiện Của Tôi ({events.events.length})
            </button>
            <button type="button" className={`pill-tab ${activeTab === 'payments' ? 'active' : ''}`} onClick={() => setActiveTab('payments')}>
              Thanh Toán & Khai Báo Lương ({payments.payments.length})
            </button>
            <button type="button" className={`pill-tab ${activeTab === 'ekyc' ? 'active' : ''}`} onClick={() => setActiveTab('ekyc')}>
              Định Danh Doanh Nghiệp (eKYC)
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
              onOpenConfirmPublish={events.openConfirmPublish}
              onOpenMap={setMapModal}
              onOpenQr={shifts.handleOpenShiftQr}
              onOpenAttendance={shifts.handleOpenAttendance}
              onEditShift={shifts.setEditingShift}
              onCancelShift={shifts.handleCancelShift}
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
            />
          )}

          {activeTab === 'ekyc' && <EoEkycTab />}
        </main>

        <EORightSidebar />
      </div>

      <EOModals
        events={events}
        shifts={shifts}
        payments={payments}
        mapModal={mapModal}
        setMapModal={setMapModal}
      />
    </div>
  );
}
