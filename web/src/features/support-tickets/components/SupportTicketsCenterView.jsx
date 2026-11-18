import { useState, useMemo, useEffect } from 'react';
import { useAuth } from '../../auth';
import { useSupportTickets } from '../hooks/useSupportTickets';
import { supportChatApi } from '../api/supportChatApi';
import TicketStatsBar from './TicketStatsBar';
import TicketToolbar from './TicketToolbar';
import TicketQueueTable from './TicketQueueTable';
import MyTicketsTable from './MyTicketsTable';
import TicketModalsGroup from './TicketModalsGroup';
import StaffMessengerDock from './StaffMessengerDock';
import LiveChatInboxTable from './LiveChatInboxTable';
import './SupportTickets.css';

/**
 * Trung Tâm Phiếu Hỗ Trợ & Tin Nhắn Trực Tuyến (ADM-02).
 * Phân tách rõ ràng giữa Hàng Đợi Phiếu Hỗ Trợ và Cửa Sổ Tin Nhắn Trực Tiếp qua Sidebar.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const SupportTicketsCenterView = ({
  activeFilter: propFilter,
  onFilterChange: propOnFilterChange,
  refreshKey,
}) => {
  const { user } = useAuth();
  const isStaff = user?.role === 'ADMIN' || user?.role === 'CUSTOMER_CARE';

  const {
    queue, myQueue, ccStaff, myTickets, selectedTicket, loading, detailLoading,
    loadQueue, loadMyQueue, loadCcStaff, loadMyTickets, loadTicketDetail,
    createTicket, replyTicket, assignTicket, closeTicket, resolveTicket, setSelectedTicket,
  } = useSupportTickets(isStaff ? 'queue' : 'my');

  const [chatConversations, setChatConversations] = useState([]);
  const [chatLoading, setChatLoading] = useState(false);
  const [internalFilter, setInternalFilter] = useState('ALL');
  const activeFilter = propFilter !== undefined ? propFilter : internalFilter;
  const handleFilterChange = (f) => { setInternalFilter(f); if (propOnFilterChange) propOnFilterChange(f); };

  const isChatMode = isStaff && activeFilter === 'CHAT';

  const [searchTerm, setSearchTerm] = useState('');
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [createPrefill, setCreatePrefill] = useState(null);
  const [isCloseModalOpen, setIsCloseModalOpen] = useState(false);
  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);
  const [isTransferModalOpen, setIsTransferModalOpen] = useState(false);
  const [activeTicket, setActiveTicket] = useState(null);
  const [activeChatTicket, setActiveChatTicket] = useState(null);

  const loadChat = async () => {
    if (!isStaff) return;
    setChatLoading(true);
    try {
      const data = await supportChatApi.getStaffConversations();
      setChatConversations(data || []);
    } catch { /* ignore */ } finally { setChatLoading(false); }
  };

  useEffect(() => {
    if (isStaff) loadChat();
  }, [isStaff, isChatMode]);

  useEffect(() => {
    if (refreshKey) handleRefresh();
  }, [refreshKey]);

  useEffect(() => {
    const handleOpenChatEvent = (e) => {
      if (e.detail?.conversation) {
        setActiveChatTicket(e.detail.conversation);
      }
    };
    window.addEventListener('cani_open_chat', handleOpenChatEvent);
    return () => window.removeEventListener('cani_open_chat', handleOpenChatEvent);
  }, []);

  const stats = useMemo(() => ({
    total: queue.length, myCount: myQueue.length,
    openCount: queue.filter((t) => t.status === 'OPEN').length,
    inProgressCount: queue.filter((t) => t.status === 'IN_PROGRESS').length,
    riskCount: queue.filter((t) => t.slaAtRisk).length,
  }), [queue, myQueue]);

  const filteredQueue = useMemo(() => {
    if (!isStaff || isChatMode) return [];
    const src = activeFilter === 'MINE' ? myQueue : queue;
    return src.filter((t) => {
      if (activeFilter === 'OPEN' && t.status !== 'OPEN') return false;
      if (activeFilter === 'IN_PROGRESS' && t.status !== 'IN_PROGRESS') return false;
      if (activeFilter === 'SLA_RISK' && !t.slaAtRisk) return false;
      if (searchTerm.trim()) {
        const term = searchTerm.toLowerCase();
        return t.referenceNo?.toLowerCase().includes(term) || t.subject?.toLowerCase().includes(term);
      }
      return true;
    });
  }, [queue, myQueue, isStaff, isChatMode, activeFilter, searchTerm]);

  const handleOpenDetail = async (id) => {
    const detail = await loadTicketDetail(id);
    if (detail) setIsDetailModalOpen(true);
  };

  const handleRefresh = () => {
    if (isStaff) {
      if (isChatMode) loadChat();
      else { loadQueue(); loadMyQueue(); loadCcStaff(); }
    } else { loadMyTickets(); }
  };

  return (
    <div className="tickets-container">
      {!isStaff && (
        <div className="tickets-header">
          <div>
            <div className="tickets-title">Phiếu Hỗ Trợ Của Tôi</div>
            <div className="tickets-subtitle">Gửi yêu cầu hỗ trợ đến ban quản trị và theo dõi tiến độ xử lý</div>
          </div>
          <div className="tickets-header-actions">
            <button type="button" className="ticket-btn ticket-btn-secondary" onClick={handleRefresh} disabled={loading}>
              Làm Mới
            </button>
            <button type="button" className="ticket-btn ticket-btn-primary" onClick={() => { setCreatePrefill(null); setIsCreateModalOpen(true); }}>
              Tạo Yêu Cầu Hỗ Trợ
            </button>
          </div>
        </div>
      )}

      {isChatMode ? (
        <LiveChatInboxTable
          conversations={chatConversations}
          loading={chatLoading}
          onOpenChat={(c) => setActiveChatTicket(c)}
          onRefresh={loadChat}
        />
      ) : (
        <>
          {isStaff && <TicketStatsBar stats={stats} />}
          {isStaff && (
            <TicketToolbar activeFilter={activeFilter} onFilterChange={handleFilterChange} searchTerm={searchTerm} onSearchChange={setSearchTerm} stats={stats} />
          )}
          {isStaff ? (
            <TicketQueueTable
              tickets={filteredQueue}
              onOpenDetail={handleOpenDetail}
              onOpenChat={(t) => setActiveChatTicket(t)}
              onAssign={(id) => assignTicket(id)}
              onOpenTransferModal={(t) => { setActiveTicket(t); setIsTransferModalOpen(true); }}
              loading={loading}
            />
          ) : (
            <MyTicketsTable tickets={myTickets} onOpenDetail={handleOpenDetail} loading={loading} />
          )}
        </>
      )}

      <TicketModalsGroup
        isCreateOpen={isCreateModalOpen}
        onCreateClose={() => { setIsCreateModalOpen(false); setCreatePrefill(null); }}
        onCreateSubmit={createTicket}
        createPrefill={createPrefill}
        isDetailOpen={isDetailModalOpen}
        onDetailClose={() => { setIsDetailModalOpen(false); setSelectedTicket(null); }}
        selectedTicket={selectedTicket}
        isStaff={isStaff}
        onReply={replyTicket}
        onAssign={assignTicket}
        onOpenTransferModal={(t) => { setActiveTicket(t); setIsTransferModalOpen(true); }}
        onOpenCloseModal={(t) => { setActiveTicket(t); setIsCloseModalOpen(true); }}
        onResolve={resolveTicket}
        detailLoading={detailLoading}
        isTransferOpen={isTransferModalOpen}
        onTransferClose={() => { setIsTransferModalOpen(false); setActiveTicket(null); }}
        onTransferConfirm={async (id, target) => { const r = await assignTicket(id, target); if (r && isDetailModalOpen) setIsDetailModalOpen(false); return r; }}
        activeTicket={activeTicket}
        ccStaff={ccStaff}
        isCloseOpen={isCloseModalOpen}
        onCloseClose={() => { setIsCloseModalOpen(false); setActiveTicket(null); }}
        onCloseConfirm={async (id, rationale) => { const r = await closeTicket(id, rationale); if (r && isDetailModalOpen) setIsDetailModalOpen(false); return r; }}
        loading={loading}
      />

      {isStaff && (
        <StaffMessengerDock
          activeChat={activeChatTicket}
          onClose={() => { setActiveChatTicket(null); if (isChatMode) loadChat(); }}
          onOpenTicket={(t) => t.referenceNo && handleOpenDetail(t.id)}
        />
      )}
    </div>
  );
};

export default SupportTicketsCenterView;
