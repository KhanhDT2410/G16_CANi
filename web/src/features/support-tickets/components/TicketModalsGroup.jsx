import CreateTicketModal from './CreateTicketModal';
import TicketDetailModal from './TicketDetailModal';
import TransferTicketModal from './TransferTicketModal';
import CloseTicketModal from './CloseTicketModal';

export const TicketModalsGroup = ({
  isCreateOpen,
  onCreateClose,
  onCreateSubmit,
  isDetailOpen,
  onDetailClose,
  selectedTicket,
  isStaff,
  onReply,
  onAssign,
  onOpenTransferModal,
  onOpenCloseModal,
  onResolve,
  detailLoading,
  isTransferOpen,
  onTransferClose,
  onTransferConfirm,
  activeTicket,
  ccStaff,
  isCloseOpen,
  onCloseClose,
  onCloseConfirm,
  loading,
}) => {
  return (
    <>
      <CreateTicketModal
        isOpen={isCreateOpen}
        onClose={onCreateClose}
        onSubmit={onCreateSubmit}
        loading={loading}
      />

      <TicketDetailModal
        isOpen={isDetailOpen}
        onClose={onDetailClose}
        ticket={selectedTicket}
        isStaff={isStaff}
        onReply={onReply}
        onAssign={onAssign}
        onOpenTransferModal={onOpenTransferModal}
        onOpenCloseModal={onOpenCloseModal}
        onResolve={onResolve}
        loading={detailLoading}
      />

      <TransferTicketModal
        isOpen={isTransferOpen}
        onClose={onTransferClose}
        onConfirm={onTransferConfirm}
        ticket={activeTicket}
        staff={ccStaff}
        loading={loading}
      />

      <CloseTicketModal
        isOpen={isCloseOpen}
        onClose={onCloseClose}
        onConfirm={onCloseConfirm}
        ticket={activeTicket}
        loading={loading}
      />
    </>
  );
};

export default TicketModalsGroup;

