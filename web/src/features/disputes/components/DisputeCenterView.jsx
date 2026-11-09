import { useState, useMemo, useEffect } from 'react';
import { useAuth } from '../../auth';
import { useDisputes } from '../hooks/useDisputes';
import DisputeQueueSidebar from './DisputeQueueSidebar';
import DisputeConversationWorkspace from './DisputeConversationWorkspace';
import DisputeQueueTable from './DisputeQueueTable';
import CreateDisputeModal from './CreateDisputeModal';
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
    queue, myQueue, ccStaff, loading,
    loadQueue, loadCcStaff, loadDisputeDetail,
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

  const scopedList = queueScope === 'MINE' ? myQueue : queue;
  const riskCount = useMemo(() => queue.filter((item) => item.slaAtRisk).length, [queue]);

  const filteredQueue = useMemo(() => {
    let list = scopedList;
    if (queueFilter === 'OPEN') list = list.filter((i) => i.status === 'OPEN');
    else if (queueFilter === 'INVESTIGATING') list = list.filter((i) => i.status === 'INVESTIGATING');
    else if (queueFilter === 'ESCALATED') list = list.filter((i) => i.status === 'ESCALATED');
    else if (queueFilter === 'SLA_RISK') list = list.filter((i) => i.slaAtRisk);

    if (searchTerm.trim()) {
      const q = searchTerm.toLowerCase();
      list = list.filter((i) => i.title?.toLowerCase().includes(q) || i.id?.toLowerCase().includes(q));
    }
    return list;
  }, [scopedList, queueFilter, searchTerm]);

  // Tự động chọn đơn đầu tiên khi có dữ liệu từ CSDL
  useEffect(() => {
    if (filteredQueue.length > 0 && (!activeDisputeId || !filteredQueue.some((i) => i.id === activeDisputeId))) {
      setActiveDisputeId(filteredQueue[0].id);
    }
  }, [filteredQueue, activeDisputeId]);

  // Chi tiết đơn đang hiển thị trong khung hội thoại
  const currentDispute = useMemo(() => {
    if (!activeDisputeId) return null;
    if (selectedDisputeDetail && selectedDisputeDetail.id === activeDisputeId) {
      return selectedDisputeDetail;
    }
    return filteredQueue.find((i) => i.id === activeDisputeId) || null;
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

  return (
    <div className="disputes-container">
      {/* Top Bar Switcher */}
      <div className="disputes-header">
        <div>
          <h1 className="disputes-title">
            Trung Tâm Xử Lý Tranh Chấp & Khiếu Nại
          </h1>
          <p className="disputes-subtitle">
            Không gian đối thoại trực tiếp giữa Người lao động, Nhà tổ chức sự kiện và Trọng tài viên CSKH.
          </p>
        </div>
        <div className="disputes-actions-group">
          <div className="view-mode-toggle">
            <button
              type="button"
              className={`toggle-btn ${viewMode === 'SPLIT' ? 'active' : ''}`}
              onClick={() => setViewMode('SPLIT')}
            >
              Không gian hội thoại
            </button>
            <button
              type="button"
              className={`toggle-btn ${viewMode === 'TABLE' ? 'active' : ''}`}
              onClick={() => setViewMode('TABLE')}
            >
              Bảng hàng đợi
            </button>
          </div>
          <button type="button" className="dispute-btn-secondary" onClick={() => loadQueue()}>
            Làm mới
          </button>
        </div>
      </div>

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
            totalCount={queue.length}
            myCount={myQueue.length}
            riskCount={riskCount}
            searchTerm={searchTerm}
            setSearchTerm={setSearchTerm}
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
                <span>
                  {filteredQueue.length === 0
                    ? 'Hiện không có đơn khiếu nại nào trong hàng đợi.'
                    : 'Chọn một đơn khiếu nại từ danh sách bên trái để xem nội dung trao đổi.'}
                </span>
              </div>
            )}
          </div>
        </div>
      ) : (
        /* CHẾ ĐỘ 2: BẢNG HÀNG ĐỢI */
        <DisputeQueueTable
          queue={filteredQueue}
          loading={loading}
          onSelect={(id) => {
            handleSelectDispute(id);
            setViewMode('SPLIT');
          }}
          onAssign={assignDispute}
          onDecide={openDecision}
          onTransfer={openTransfer}
        />
      )}

      {modalType === 'create' && <CreateDisputeModal onClose={() => setModalType(null)} onSubmit={createDispute} />}
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
