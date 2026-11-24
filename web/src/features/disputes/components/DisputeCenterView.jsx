import { useState, useMemo, useEffect } from 'react';
import { useAuth } from '../../auth';
import { useDisputes } from '../hooks/useDisputes';
import DisputeQueueSidebar from './DisputeQueueSidebar';
import DisputeConversationWorkspace from './DisputeConversationWorkspace';
import DisputeQueueTable from './DisputeQueueTable';
import DisputeTopHeader from './DisputeTopHeader';
import CreateDisputeWizard from './CreateDisputeWizard';
import DisputeDecisionModal from './DisputeDecisionModal';
import EscalateModal from './EscalateModal';
import TransferDisputeModal from './TransferDisputeModal';
import './Disputes.css';

/**
 * Màn hình Trung tâm Xử lý Tranh chấp & Khiếu nại (ADM-03).
 * Mặc định hiển thị giao diện đối thoại trực tiếp kết nối cơ sở dữ liệu thật.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const DisputeCenterView = () => {
  const { user } = useAuth();
  const isArbitrator = user?.role === 'ADMIN' || user?.role === 'CUSTOMER_CARE';

  const {
    queue, myQueue, resolvedQueue, myDisputes, ccStaff, loading,
    loadQueue, loadResolvedQueue, loadMyDisputes, loadCcStaff, loadDisputeDetail,
    createDispute, addEvidence, assignDispute, issueDecision, escalateDispute,
  } = useDisputes(isArbitrator ? 'queue' : 'my');

  const [activeDisputeId, setActiveDisputeId] = useState(null);
  const [selectedDisputeDetail, setSelectedDisputeDetail] = useState(null);
  const [viewMode, setViewMode] = useState('SPLIT');
  const [modalType, setModalType] = useState(null);
  const [modalDispute, setModalDispute] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [queueFilter, setQueueFilter] = useState('ALL');
  const [queueScope, setQueueScope] = useState('ALL');

  const scopedList = !isArbitrator ? myDisputes : queueScope === 'MINE' ? myQueue : queueScope === 'RESOLVED' ? resolvedQueue : queue;
  const riskCount = useMemo(() => scopedList.filter((item) => item.slaAtRisk).length, [scopedList]);
  const unreadTotalCount = useMemo(
    () => scopedList.filter((i) => (i.unreadCount || 0) > 0 || (i.workerUnreadCount || 0) > 0 || (i.eoUnreadCount || 0) > 0).length,
    [scopedList]
  );
  const handleRefreshDisputes = () => {
    if (!isArbitrator) return loadMyDisputes();
    loadQueue();
    loadResolvedQueue();
  };

  const filteredQueue = useMemo(() => {
    let list = scopedList;
    if (queueFilter === 'UNREAD') {
      list = list.filter((i) => (i.unreadCount || 0) > 0 || (i.workerUnreadCount || 0) > 0 || (i.eoUnreadCount || 0) > 0);
    } else if (queueFilter !== 'ALL') {
      if (queueFilter === 'SLA_RISK') list = list.filter((i) => i.slaAtRisk);
      else list = list.filter((i) => i.status === queueFilter);
    }

    if (searchTerm.trim()) {
      const q = searchTerm.toLowerCase();
      list = list.filter((i) => i.title?.toLowerCase().includes(q) || i.id?.toLowerCase().includes(q));
    }

    return [...list].sort((a, b) => {
      const aUnread = (a.unreadCount || 0) > 0 || (a.workerUnreadCount || 0) > 0 || (a.eoUnreadCount || 0) > 0;
      const bUnread = (b.unreadCount || 0) > 0 || (b.workerUnreadCount || 0) > 0 || (b.eoUnreadCount || 0) > 0;
      if (aUnread !== bUnread) return aUnread ? -1 : 1;
      const aMsg = a.lastMessageAt ? new Date(a.lastMessageAt).getTime() : 0;
      const bMsg = b.lastMessageAt ? new Date(b.lastMessageAt).getTime() : 0;
      if (aMsg !== bMsg) return bMsg - aMsg;
      if (a.slaAtRisk !== b.slaAtRisk) return a.slaAtRisk ? -1 : 1;
      return (b.createdAt ? new Date(b.createdAt).getTime() : 0) - (a.createdAt ? new Date(a.createdAt).getTime() : 0);
    });
  }, [scopedList, queueFilter, searchTerm]);

  useEffect(() => {
    const timer = setInterval(() => {
      if (typeof document === 'undefined' || document.visibilityState === 'visible') {
        if (isArbitrator) {
          loadQueue();
          if (queueScope === 'RESOLVED') loadResolvedQueue();
        } else {
          loadMyDisputes();
        }
      }
    }, 4000);
    return () => clearInterval(timer);
  }, [isArbitrator, queueScope, loadQueue, loadResolvedQueue, loadMyDisputes]);

  useEffect(() => {
    if (filteredQueue.length > 0 && (!activeDisputeId || !filteredQueue.some((i) => i.id === activeDisputeId))) {
      setActiveDisputeId(filteredQueue[0].id);
    }
  }, [filteredQueue, activeDisputeId]);

  const currentDispute = useMemo(() => {
    if (!activeDisputeId) return null;
    return selectedDisputeDetail?.id === activeDisputeId
      ? selectedDisputeDetail
      : (filteredQueue.find((i) => i.id === activeDisputeId) || null);
  }, [selectedDisputeDetail, activeDisputeId, filteredQueue]);

  const handleSelectDispute = async (id) => {
    setActiveDisputeId(id);
    const detail = await loadDisputeDetail(id);
    if (detail) setSelectedDisputeDetail(detail);
  };

  const handleSendMessage = async (disputeId, payload) => {
    const updated = await addEvidence(disputeId, payload);
    if (updated) setSelectedDisputeDetail(updated);
    else handleSelectDispute(disputeId);
  };

  const openDecision = (d) => { setModalDispute(d); setModalType('decision'); };
  const openEscalate = (d) => { setModalDispute(d); setModalType('escalate'); };
  const openTransfer = (d) => { setModalDispute(d); setModalType('transfer'); loadCcStaff(); };

  if (modalType === 'create') {
    return (
      <div className="disputes-container">
        <CreateDisputeWizard onCancel={() => setModalType(null)} onSubmit={createDispute} />
      </div>
    );
  }

  return (
    <div className="disputes-container">
      <DisputeTopHeader
        viewMode={viewMode}
        setViewMode={setViewMode}
        onRefresh={handleRefreshDisputes}
        isArbitrator={isArbitrator}
        onOpenCreate={() => setModalType('create')}
      />

      {viewMode === 'SPLIT' ? (
        <div className="dispute-split-layout">
          <DisputeQueueSidebar
            queue={filteredQueue}
            selectedId={currentDispute?.id}
            onSelect={handleSelectDispute}
            queueFilter={queueFilter}
            setQueueFilter={setQueueFilter}
            queueScope={queueScope}
            setQueueScope={(scope) => { setQueueScope(scope); setQueueFilter('ALL'); }}
            totalCount={queue.length}
            resolvedCount={resolvedQueue.length}
            myCount={isArbitrator ? myQueue.length : myDisputes.length}
            riskCount={riskCount}
            unreadCount={unreadTotalCount}
            searchTerm={searchTerm}
            setSearchTerm={setSearchTerm}
            showScopeSwitcher={isArbitrator}
          />
          <div className="dispute-split-workspace">
            {currentDispute ? (
              <DisputeConversationWorkspace
                dispute={currentDispute}
                onBack={() => setViewMode('TABLE')}
                onAssign={assignDispute}
                onDecide={openDecision}
                onEscalate={openEscalate}
                onTransfer={openTransfer}
                onSendMessage={handleSendMessage}
                isSending={loading}
                isArbitrator={isArbitrator}
              />
            ) : (
              <div className="empty-workspace-state">
                <span>{filteredQueue.length === 0 ? 'Hiện không có đơn khiếu nại nào.' : 'Chọn một đơn khiếu nại từ danh sách bên trái.'}</span>
              </div>
            )}
          </div>
        </div>
      ) : (
        <DisputeQueueTable
          queue={filteredQueue}
          loading={loading}
          isArbitrator={isArbitrator}
          onSelect={(id) => { handleSelectDispute(id); setViewMode('SPLIT'); }}
          onAssign={assignDispute}
          onDecide={openDecision}
          onTransfer={openTransfer}
        />
      )}

      {modalType === 'decision' && modalDispute && (
        <DisputeDecisionModal dispute={modalDispute} onClose={() => setModalType(null)} onSubmit={issueDecision} />
      )}
      {modalType === 'escalate' && modalDispute && (
        <EscalateModal dispute={modalDispute} onClose={() => setModalType(null)} onSubmit={escalateDispute} />
      )}
      {modalType === 'transfer' && modalDispute && (
        <TransferDisputeModal dispute={modalDispute} staff={ccStaff} onClose={() => setModalType(null)} onSubmit={assignDispute} />
      )}
    </div>
  );
};

export default DisputeCenterView;
