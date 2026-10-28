import MapModal from '../../../components/common/MapModal';
import EventCreateModal from './EventCreateModal';
import ShiftCreateModal from './ShiftCreateModal';
import PaymentDeclarationModal from './PaymentDeclarationModal';
import PublishEventModal from './PublishEventModal';
import ShiftQrModal from './ShiftQrModal';
import LiveAttendanceModal from './LiveAttendanceModal';
import EditEventModal from './EditEventModal';
import EditShiftModal from './EditShiftModal';
import { ROLES } from '../hooks/useShifts';

export default function EOModals({ events, shifts, payments, mapModal, setMapModal }) {
  return (
    <>
      <EventCreateModal
        isOpen={events.showEventModal}
        onClose={() => events.setShowEventModal(false)}
        newEvent={events.newEvent}
        setNewEvent={events.setNewEvent}
        locating={events.locating}
        onGetCurrentLocation={events.handleGetCurrentLocation}
        onSubmit={events.handleCreateEvent}
      />

      <ShiftCreateModal
        isOpen={shifts.showShiftModal}
        onClose={() => shifts.setShowShiftModal(false)}
        newShift={shifts.newShift}
        setNewShift={shifts.setNewShift}
        roles={ROLES}
        onRoleChange={shifts.handleRoleChange}
        shiftDurationHours={shifts.getShiftDurationHours()}
        calculateShiftTotal={shifts.calculateShiftTotal}
        onSubmit={shifts.handleAddShift}
      />

      <PaymentDeclarationModal
        isOpen={payments.showPaymentModal}
        onClose={() => payments.setShowPaymentModal(false)}
        selectedAssignment={payments.selectedAssignment}
        paymentForm={payments.paymentForm}
        setPaymentForm={payments.setPaymentForm}
        onSubmit={payments.handleDeclarePayment}
      />

      <PublishEventModal
        isOpen={events.confirmPublish.isOpen}
        onClose={() => events.setConfirmPublish({ isOpen: false, event: null })}
        event={events.confirmPublish.event}
        payMethod={events.publishPayMethod}
        setPayMethod={events.setPublishPayMethod}
        onExecutePublish={events.handleExecutePublish}
      />

      <ShiftQrModal
        isOpen={Boolean(shifts.selectedShiftForQr)}
        onClose={() => shifts.setSelectedShiftForQr(null)}
        qrData={shifts.qrModalData}
        loading={shifts.loadingQr}
      />

      <LiveAttendanceModal
        isOpen={Boolean(shifts.selectedShiftForAttendance)}
        onClose={() => shifts.setSelectedShiftForAttendance(null)}
        shift={shifts.selectedShiftForAttendance}
        onReloadEvents={events.loadEvents}
        onOpenPaymentDeclaration={payments.handleOpenPaymentDeclarationFromAttendance}
      />

      <EditEventModal
        isOpen={Boolean(events.editingEvent)}
        onClose={() => events.setEditingEvent(null)}
        event={events.editingEvent}
        submitting={events.submittingEditEvent}
        onSubmit={events.handleUpdateEvent}
      />

      <EditShiftModal
        isOpen={Boolean(shifts.editingShift)}
        onClose={() => shifts.setEditingShift(null)}
        shift={shifts.editingShift}
        roles={ROLES}
        submitting={shifts.submittingEditShift}
        onSubmit={shifts.handleUpdateShift}
      />

      <MapModal
        isOpen={mapModal.isOpen}
        onClose={() => setMapModal((prev) => ({ ...prev, isOpen: false }))}
        lat={mapModal.lat}
        lng={mapModal.lng}
        title={mapModal.eventName}
        address={mapModal.venueAddress}
        radiusKm={mapModal.radiusKm}
      />
    </>
  );
}
