import MapModal from '../../../components/common/MapModal';
import ShiftCreateModal from './ShiftCreateModal';
import PaymentDeclarationModal from './PaymentDeclarationModal';
import PublishEventModal from './PublishEventModal';
import CancelEventModal from './CancelEventModal';
import ShiftQrModal from './ShiftQrModal';
import ShiftInviteModal from './ShiftInviteModal';
import DisputeModal from './DisputeModal';
import LiveAttendanceModal from './LiveAttendanceModal';
import EditEventModal from './EditEventModal';
import EditShiftModal from './EditShiftModal';
import { ROLES } from '../hooks/useShifts';

export default function EOModals({
  events,
  shifts,
  payments,
  mapModal,
  setMapModal,
  inviteModal,
  setInviteModal,
  disputeModal,
  setDisputeModal,
}) {
  const activeEvent =
    events?.eventsList?.find((e) => e.eventId === shifts?.selectedEventId) || null;

  return (
    <>
      <ShiftCreateModal
        isOpen={shifts.showShiftModal}
        onClose={() => shifts.setShowShiftModal(false)}
        newShift={shifts.newShift}
        setNewShift={shifts.setNewShift}
        roles={ROLES}
        onRoleChange={shifts.handleRoleChange}
        shiftDurationHours={shifts.getShiftDurationHours()}
        calculateShiftTotal={shifts.calculateShiftTotal}
        skillTaxonomy={shifts.skillTaxonomy}
        venueAddress={activeEvent?.venueAddress || ''}
        venueLat={activeEvent?.venueLat ?? null}
        venueLng={activeEvent?.venueLng ?? null}
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

      <CancelEventModal
        isOpen={Boolean(events.cancelModal?.isOpen)}
        event={events.cancelModal?.event}
        onClose={events.handleCloseCancelModal}
        onConfirm={events.handleConfirmCancelEvent}
        loading={events.cancelModal?.loading}
      />

      <ShiftQrModal
        isOpen={Boolean(shifts.selectedShiftForQr)}
        onClose={() => shifts.setSelectedShiftForQr(null)}
        qrData={shifts.qrModalData}
        loading={shifts.loadingQr}
      />

      <ShiftInviteModal
        isOpen={Boolean(inviteModal?.isOpen)}
        onClose={() => setInviteModal({ isOpen: false, shift: null, event: null })}
        shift={inviteModal?.shift}
        event={inviteModal?.event}
      />

      <DisputeModal
        isOpen={Boolean(disputeModal?.isOpen)}
        onClose={() => setDisputeModal({ isOpen: false, assignment: null })}
        assignment={disputeModal?.assignment}
        onSuccess={payments?.loadEoPayments}
      />

      <LiveAttendanceModal
        isOpen={Boolean(shifts.selectedShiftForAttendance)}
        onClose={() => shifts.setSelectedShiftForAttendance(null)}
        shift={shifts.selectedShiftForAttendance}
        skillTaxonomy={shifts.skillTaxonomy}
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
        skillTaxonomy={shifts.skillTaxonomy}
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
