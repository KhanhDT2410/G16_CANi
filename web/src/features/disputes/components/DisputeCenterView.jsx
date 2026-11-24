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
    queue, myQueue, myDisputes, ccStaff, loading,
    loadQueue, loadMyDisputes, loadCcStaff, loadDisputeDetail,
    createDispute, addEvidence, assignDispute,
    issueDecision, escalateDispute,
  } = useDisputes(isArbitrator ? 'queue' : 'my');

  const [activeDisputeId, setActiveDisputeId] = useState(null);
  const [selectedDisputeDetail, setSelectedDisputeDetail] = useState(null);
  const [viewMode, setViewMode] = useState('SPLIT'); // 'SPLIT' | 'TABLE'
  const [modalType, setModalType] = useState(null);
  const [modalDispute, setModalDispute] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [queueFilter, setQueueFilter] = useState('ALL');
  const [queueScope, setQueueScope] = useState('ALL');

  const scopedList = !isArbitrator ? myDisputes : queueScope === 'MINE' ? myQueue : queue;
  const riskCount = useMemo(() => scopedList.filter((item) => item.slaAtRisk).length, [scopedList]);
  const unreadTotalCount = useMemo(
    () => scopedList.filter((item) => (item.unreadCount || 0) > 0 || (item.workerUnreadCount || 0) > 0 || (item.eoUnreadCount || 0) > 0).length,
    [scopedList]
  );
  const handleRefreshDisputes = () => (isArbitrator ? loadQueue() : loadMyDisputes());

  const filteredQueue = useMemo(() => {
    let list = scopedList;
    if (queueFilter === 'UNREAD') {
      list = list.filter((i) => (i.unreadCount || 0) > 0 || (i.workerUnreadCount || 0) > 0 || (i.eoUnreadCount || 0) > 0);
    } else if (queueFilter === 'OPEN') list = list.filter((i) => i.status === 'OPEN');
    else if (queueFilter === 'INVESTIGATING') list = list.filter((i) => i.status === 'INVESTIGATING');
    else if (queueFilter === 'ESCALATED') list = list.filter((i) => i.status === 'ESCALATED');
    else if (queueFilter === 'SLA_RISK') list = list.filter((i) => i.slaAtRisk);

    if (searchTerm.trim()) {
      const q = searchTerm.toLowerCase();
      list = list.filter((i) => i.title?.toLowerCase().includes(q) || i.id?.toLowerCase().includes(q));
    }

    return [...list].sort((a, b) => {
      // 1. Đơn có tin nhắn mới chưa đọc luôn nhảy lên đầu danh sách
      const aUnread = (a.unreadCount || 0) > 0 || (a.workerUnreadCount || 0) > 0 || (a.eoUnreadCount || 0) > 0;
      const bUnread = (b.unreadCount || 0) > 0 || (b.workerUnreadCount || 0) > 0 || (b.eoUnreadCount || 0) > 0;
      if (aUnread !== bUnread) return aUnread ? -1 : 1;

      // 2. Ca có tin nhắn mới nhất (lastMessageAt DESC) luôn lên trước
      const aMsg = a.lastMessageAt ? new Date(a.lastMessageAt).getTime() : 0;
      const bMsg = b.lastMessageAt ? new Date(b.lastMessageAt).getTime() : 0;
      if (aMsg !== bMsg) return bMsg - aMsg;

      // 3. Nếu chưa có tin nhắn, ca có rủi ro SLA ưu tiên trước
      if (a.slaAtRisk !== b.slaAtRisk) return a.slaAtRisk ? -1 : 1;

      // 4. Mới tạo lên trước (createdAt DESC)
      const aCreated = a.createdAt ? new Date(a.createdAt).getTime() : 0;
      const bCreated = b.createdAt ? new Date(b.createdAt).getTime() : 0;
      return bCreated - aCreated;
    });
  }, [scopedList, queueFilter, searchTerm]);

  // Tự động làm mới danh sách định kỳ để bắt kịp tin nhắn mới theo thời gian thực
  useEffect(() => {
    const timer = setInterval(() => {
      if (typeof document === 'undefined' || document.visibilityState === 'visible') {
        if (isArbitrator) {
          loadQueue();
        } else {
          loadMyDisputes();
        }
      }
    }, 4000);
    return () => clearInterval(timer);
  }, [isArbitrator, loadQueue, loadMyDisputes]);

  // Tự động chọn đơn đầu tiên khi có dữ liệu từ CSDL
  useEffect(() => {
    if (filteredQueue.length > 0 && (!activeDisputeId || !filteredQueue.some((i) => i.id === activeDisputeId))) {
      setActiveDisputeId(filteredQueue[0].id);
    }
  }, [filteredQueue, activeDisputeId]);

  // Chi tiết đơn đang hiển thị trong khung hội thoại
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
        <CreateDisputeWizard
          onCancel={() => setModalType(null)}
          onSubmit={createDispute}
        />
      </div>
    );
  }

  return (
    <div className="disputes-container">
      {/* Thanh công cụ chuyển đổi chế độ & thao tác */}
      <DisputeTopHeader
        viewMode={viewMode}
        setViewMode={setViewMode}
        onRefresh={handleRefreshDisputes}
        isArbitrator={isArbitrator}
        onOpenCreate={() => setModalType('create')}
      />

      {/* CHẾ ĐỘ 1: GIAO DIỆN HỘI THOẠI SPLIT-VIEW (ADM-03) */}
      {viewMode === 'SPLIT' ? (
        <div className="dispute-split-layout">
          <DisputeQueueSidebar
            queue={filteredQueue}
            selectedId={currentDispute?.id}
            onSelect={handleSelectDispute}
            queueFilter={queueFilter}
            setQueueFilter={setQueueFilter}
            queueScope={queueScope}
            setQueueScope={setQueueScope}
            totalCount={scopedList.length}
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
        /* CHẾ ĐỘ 2: BẢNG HÀNG ĐỢI */
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
