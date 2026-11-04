import { useState, useMemo } from 'react';
import { useAuth } from '../../auth';
import { useSupportTickets } from '../hooks/useSupportTickets';
import TicketStatsBar from './TicketStatsBar';
import TicketToolbar from './TicketToolbar';
import TicketQueueTable from './TicketQueueTable';
import MyTicketsTable from './MyTicketsTable';
import TicketModalsGroup from './TicketModalsGroup';
import './SupportTickets.css';

export const SupportTicketsCenterView = () => {
  const { user } = useAuth();
  const isStaff = user?.role === 'ADMIN' || user?.role === 'CUSTOMER_CARE';

  const {
    queue,
    myQueue,
    ccStaff,
    myTickets,
    selectedTicket,
    loading,
    detailLoading,
    loadQueue,
    loadMyQueue,
    loadCcStaff,
    loadMyTickets,
    loadTicketDetail,
    createTicket,
    replyTicket,
    assignTicket,
    closeTicket,
    resolveTicket,
    setSelectedTicket,
  } = useSupportTickets(isStaff ? 'queue' : 'my');

  const [activeFilter, setActiveFilter] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isCloseModalOpen, setIsCloseModalOpen] = useState(false);
  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);
  const [isTransferModalOpen, setIsTransferModalOpen] = useState(false);
  const [activeTicket, setActiveTicket] = useState(null);

  const stats = useMemo(() => {
    const total = queue.length;
    const myCount = myQueue.length;
    const openCount = queue.filter((t) => t.status === 'OPEN').length;
    const inProgressCount = queue.filter((t) => t.status === 'IN_PROGRESS').length;
    const riskCount = queue.filter((t) => t.slaAtRisk).length;
    return { total, myCount, openCount, inProgressCount, riskCount };
  }, [queue, myQueue]);

  const filteredQueue = useMemo(() => {
    if (!isStaff) return [];
    const sourceList = activeFilter === 'MINE' ? myQueue : queue;
    return sourceList.filter((t) => {
      if (activeFilter === 'OPEN' && t.status !== 'OPEN') return false;
      if (activeFilter === 'IN_PROGRESS' && t.status !== 'IN_PROGRESS') return false;
      if (activeFilter === 'SLA_RISK' && !t.slaAtRisk) return false;

      if (searchTerm.trim()) {
        const term = searchTerm.toLowerCase();
        return t.referenceNo?.toLowerCase().includes(term) || t.subject?.toLowerCase().includes(term);
      }
      return true;
    });
  }, [queue, myQueue, isStaff, activeFilter, searchTerm]);

  const handleOpenDetail = async (id) => {
    const detail = await loadTicketDetail(id);
    if (detail) setIsDetailModalOpen(true);
  };

  const handleOpenCloseModal = (ticket) => {
    setActiveTicket(ticket);
    setIsCloseModalOpen(true);
  };

  const handleOpenTransferModal = (ticket) => {
    setActiveTicket(ticket);
    setIsTransferModalOpen(true);
  };

  const handleRefresh = () => {
    if (isStaff) {
      loadQueue();
      loadMyQueue();
      loadCcStaff();
    } else {
      loadMyTickets();
    }
  };

  return (
    <div className="tickets-container">
      <div className="tickets-header">
        <div>
          <div className="tickets-title">Trung Tâm Phiếu Hỗ Trợ Khách Hàng</div>
          <div className="tickets-subtitle">
            {isStaff
              ? 'Hàng đợi tiếp nhận và giải quyết yêu cầu kỹ thuật, thanh toán và tài khoản'
              : 'Gửi yêu cầu hỗ trợ đến ban quản trị và theo dõi tiến độ xử lý'}
          </div>
        </div>
        <div className="tickets-header-actions">
          <button
            type="button"
            className="ticket-btn ticket-btn-secondary"
            onClick={handleRefresh}
            disabled={loading}
          >
            Làm Mới
          </button>
          {!isStaff && (
            <button
              type="button"
              className="ticket-btn ticket-btn-primary"
              onClick={() => setIsCreateModalOpen(true)}
            >
              Tạo Yêu Cầu Hỗ Trợ
            </button>
          )}
        </div>
      </div>

      {isStaff && <TicketStatsBar stats={stats} />}

      {isStaff && (
        <TicketToolbar
          activeFilter={activeFilter}
          onFilterChange={setActiveFilter}
          searchTerm={searchTerm}
          onSearchChange={setSearchTerm}
          stats={stats}
        />
      )}

      {isStaff ? (
        <TicketQueueTable
          tickets={filteredQueue}
          onOpenDetail={handleOpenDetail}
          onAssign={(id) => assignTicket(id)}
          onOpenTransferModal={handleOpenTransferModal}
          loading={loading}
        />
      ) : (
        <MyTicketsTable
          tickets={myTickets}
          onOpenDetail={handleOpenDetail}
          loading={loading}
        />
      )}

      <TicketModalsGroup
        isCreateOpen={isCreateModalOpen}
        onCreateClose={() => setIsCreateModalOpen(false)}
        onCreateSubmit={createTicket}
        isDetailOpen={isDetailModalOpen}
        onDetailClose={() => {
          setIsDetailModalOpen(false);
          setSelectedTicket(null);
        }}
        selectedTicket={selectedTicket}
        isStaff={isStaff}
        onReply={replyTicket}
        onAssign={assignTicket}
        onOpenTransferModal={handleOpenTransferModal}
        onOpenCloseModal={handleOpenCloseModal}
        onResolve={resolveTicket}
        detailLoading={detailLoading}
        isTransferOpen={isTransferModalOpen}
        onTransferClose={() => {
          setIsTransferModalOpen(false);
          setActiveTicket(null);
        }}
        onTransferConfirm={async (id, targetCcUserId) => {
          const res = await assignTicket(id, targetCcUserId);
          if (res && isDetailModalOpen) setIsDetailModalOpen(false);
          return res;
        }}
        activeTicket={activeTicket}
        ccStaff={ccStaff}
        isCloseOpen={isCloseModalOpen}
        onCloseClose={() => {
          setIsCloseModalOpen(false);
          setActiveTicket(null);
        }}
        onCloseConfirm={async (id, rationale) => {
          const res = await closeTicket(id, rationale);
          if (res && isDetailModalOpen) setIsDetailModalOpen(false);
          return res;
        }}
        loading={loading}
      />
    </div>
  );
};

export default SupportTicketsCenterView;
