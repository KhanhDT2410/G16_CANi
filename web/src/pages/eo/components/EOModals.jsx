import { lazy, Suspense } from 'react';
import { useEmergencyAutoPrompt } from '../hooks/useEmergencyAutoPrompt';
import { ROLES } from '../hooks/useShifts';

const MapModal = lazy(() => import('../../../components/common/MapModal'));
const ShiftCreateModal = lazy(() => import('./ShiftCreateModal'));
const PaymentDeclarationModal = lazy(() => import('./PaymentDeclarationModal'));
const PublishEventModal = lazy(() => import('./PublishEventModal'));
const CancelEventModal = lazy(() => import('./CancelEventModal'));
const ShiftQrModal = lazy(() => import('./ShiftQrModal'));
const ShiftInviteModal = lazy(() => import('./ShiftInviteModal'));
const DisputeModal = lazy(() => import('./DisputeModal'));
const LiveAttendanceModal = lazy(() => import('./LiveAttendanceModal'));
const EditEventModal = lazy(() => import('./EditEventModal'));
const EditShiftModal = lazy(() => import('./EditShiftModal'));
const EmergencyDispatchConfirmModal = lazy(() => import('./EmergencyDispatchConfirmModal'));

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
  const eventList = events?.eventsList || events?.events || [];
  const activeEvent =
    eventList.find((e) => e.eventId === shifts?.selectedEventId) || null;
  const editingShiftEvent = shifts?.editingShift
    ? eventList.find(
        (e) =>
          e.eventId === shifts.editingShift.eventId ||
          (Array.isArray(e.shifts) && e.shifts.some((s) => s.shiftId === shifts.editingShift.shiftId))
      ) || null
    : null;

  const {
    promptTarget,
    isSubmitting: isAutoPromptSubmitting,
    handleAccept: handleAutoPromptAccept,
    handleDecline: handleAutoPromptDecline,
  } = useEmergencyAutoPrompt({
    events: events?.events || events?.eventsList || [],
    onReloadEvents: events?.loadEvents,
  });

  return (
    <>
      {Boolean(shifts.showShiftModal) && (
        <Suspense fallback={null}>
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
            existingShifts={activeEvent?.shifts || []}
            onSubmit={shifts.handleAddShift}
          />
        </Suspense>
      )}

      {Boolean(payments.showPaymentModal) && (
        <Suspense fallback={null}>
          <PaymentDeclarationModal
            isOpen={payments.showPaymentModal}
            onClose={() => payments.setShowPaymentModal(false)}
            selectedAssignment={payments.selectedAssignment}
            paymentForm={payments.paymentForm}
            setPaymentForm={payments.setPaymentForm}
            onSubmit={payments.handleDeclarePayment}
          />
        </Suspense>
      )}

      {Boolean(events.confirmPublish?.isOpen) && (
        <Suspense fallback={null}>
          <PublishEventModal
            isOpen={events.confirmPublish.isOpen}
            onClose={() => events.setConfirmPublish({ isOpen: false, event: null })}
            event={events.confirmPublish.event}
            payMethod={events.publishPayMethod}
            setPayMethod={events.setPublishPayMethod}
            onExecutePublish={events.handleExecutePublish}
          />
        </Suspense>
      )}

      {Boolean(events.cancelModal?.isOpen) && (
        <Suspense fallback={null}>
          <CancelEventModal
            isOpen={Boolean(events.cancelModal?.isOpen)}
            event={events.cancelModal?.event}
            onClose={events.handleCloseCancelModal}
            onConfirm={events.handleConfirmCancelEvent}
            loading={events.cancelModal?.loading}
          />
        </Suspense>
      )}

      {Boolean(shifts.selectedShiftForQr) && (
        <Suspense fallback={null}>
          <ShiftQrModal
            isOpen={Boolean(shifts.selectedShiftForQr)}
            onClose={() => shifts.setSelectedShiftForQr(null)}
            qrData={shifts.qrModalData}
            loading={shifts.loadingQr}
          />
        </Suspense>
      )}

      {Boolean(inviteModal?.isOpen) && (
        <Suspense fallback={null}>
          <ShiftInviteModal
            isOpen={Boolean(inviteModal?.isOpen)}
            onClose={() => setInviteModal({ isOpen: false, shift: null, event: null })}
            shift={inviteModal?.shift}
            event={inviteModal?.event}
          />
        </Suspense>
      )}

      {Boolean(disputeModal?.isOpen) && (
        <Suspense fallback={null}>
          <DisputeModal
            isOpen={Boolean(disputeModal?.isOpen)}
            onClose={() => setDisputeModal({ isOpen: false, assignment: null })}
            assignment={disputeModal?.assignment}
            onSuccess={payments?.loadEoPayments}
          />
        </Suspense>
      )}

      {Boolean(shifts.selectedShiftForAttendance) && (
        <Suspense fallback={null}>
          <LiveAttendanceModal
            isOpen={Boolean(shifts.selectedShiftForAttendance)}
            onClose={() => shifts.setSelectedShiftForAttendance(null)}
            shift={shifts.selectedShiftForAttendance}
            skillTaxonomy={shifts.skillTaxonomy}
            onReloadEvents={events.loadEvents}
            onOpenPaymentDeclaration={payments.handleOpenPaymentDeclarationFromAttendance}
          />
        </Suspense>
      )}

      {Boolean(events.editingEvent) && (
        <Suspense fallback={null}>
          <EditEventModal
            isOpen={Boolean(events.editingEvent)}
            onClose={() => events.setEditingEvent(null)}
            event={events.editingEvent}
            submitting={events.submittingEditEvent}
            onSubmit={events.handleUpdateEvent}
          />
        </Suspense>
      )}

      {Boolean(shifts.editingShift) && (
        <Suspense fallback={null}>
          <EditShiftModal
            isOpen={Boolean(shifts.editingShift)}
            onClose={() => shifts.setEditingShift(null)}
            shift={shifts.editingShift}
            event={editingShiftEvent}
            roles={ROLES}
            submitting={shifts.submittingEditShift}
            skillTaxonomy={shifts.skillTaxonomy}
            existingShifts={editingShiftEvent?.shifts || []}
            onSubmit={shifts.handleUpdateShift}
          />
        </Suspense>
      )}

      {Boolean(mapModal?.isOpen) && (
        <Suspense fallback={null}>
          <MapModal
            isOpen={mapModal.isOpen}
            onClose={() => setMapModal((prev) => ({ ...prev, isOpen: false }))}
            lat={mapModal.lat}
            lng={mapModal.lng}
            eventName={mapModal.eventName}
            title={mapModal.eventName || 'Tọa Độ Sự Kiện & Bán Kính Điều Phối'}
            venueAddress={mapModal.venueAddress}
            address={mapModal.venueAddress}
            radiusKm={mapModal.radiusKm}
          />
        </Suspense>
      )}

      {promptTarget && (
        <Suspense fallback={null}>
          <EmergencyDispatchConfirmModal
            isOpen={Boolean(promptTarget)}
            shift={promptTarget.shift}
            event={promptTarget.event}
            onClose={() => handleAutoPromptDecline(promptTarget.shift.shiftId)}
            onConfirm={({ radiusKm }) => handleAutoPromptAccept(promptTarget.shift.shiftId, radiusKm)}
            isSubmitting={isAutoPromptSubmitting}
            isAutoPrompt={true}
          />
        </Suspense>
      )}
    </>
  );
}
