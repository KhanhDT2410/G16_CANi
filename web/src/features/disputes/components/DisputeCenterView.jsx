import { useState, useMemo } from 'react';
import { useAuth } from '../../auth';
import { useDisputes } from '../hooks/useDisputes';
import DisputeQueueTable from './DisputeQueueTable';
import MyDisputesTable from './MyDisputesTable';
import DisputeDetailModal from './DisputeDetailModal';
import CreateDisputeModal from './CreateDisputeModal';
import AddEvidenceModal from './AddEvidenceModal';
import DisputeDecisionModal from './DisputeDecisionModal';
import EscalateModal from './EscalateModal';
import TransferDisputeModal from './TransferDisputeModal';
import './Disputes.css';

export const DisputeCenterView = () => {
  const { user } = useAuth();
  const isArbitrator = user?.role === 'ADMIN' || user?.role === 'CUSTOMER_CARE';

  const {
    queue,
    myQueue,
    ccStaff,
    myDisputes,
    selectedDispute,
    loading,
    loadQueue,
    loadMyQueue,
    loadCcStaff,
    loadMyDisputes,
    loadDisputeDetail,
    createDispute,
    addEvidence,
    assignDispute,
    issueDecision,
    escalateDispute,
  } = useDisputes(isArbitrator ? 'queue' : 'my');

  const [modalType, setModalType] = useState(null);
  const [modalDispute, setModalDispute] = useState(null);
  const [searchId, setSearchId] = useState('');
  const [queueFilter, setQueueFilter] = useState('ALL');
  const [queueScope, setQueueScope] = useState('ALL');

  const escalatedCount = useMemo(
    () => queue.filter((item) => item.status === 'ESCALATED').length,
    [queue]
  );
  const riskCount = useMemo(() => queue.filter((item) => item.slaAtRisk).length, [queue]);

  // Hàng đợi hiển thị: phạm vi (tất cả / của tôi) + bộ lọc trạng thái
  const scopedQueue = queueScope === 'MINE' ? myQueue : queue;

  const filteredQueue = useMemo(() => {
    if (!isArbitrator) return [];
    let list = scopedQueue;
    if (queueFilter === 'OPEN') list = list.filter((item) => item.status === 'OPEN');
    else if (queueFilter === 'INVESTIGATING') list = list.filter((item) => item.status === 'INVESTIGATING');
    else if (queueFilter === 'ESCALATED') list = list.filter((item) => item.status === 'ESCALATED');
    else if (queueFilter === 'SLA_RISK') list = list.filter((item) => item.slaAtRisk);
    return list;
  }, [scopedQueue, queueFilter, isArbitrator]);

  const handleOpenDetail = async (id) => {
    const detail = await loadDisputeDetail(id);
    if (detail) {
      setModalDispute(detail);
      setModalType('detail');
    }
  };

  const openEvidence = (d) => { setModalDispute(d); setModalType('evidence'); };
  const openDecision = (d) => { setModalDispute(d); setModalType('decision'); };
  const openEscalate = (d) => { setModalDispute(d); setModalType('escalate'); };
  const openTransfer = (d) => { setModalDispute(d); setModalType('transfer'); loadCcStaff(); };

  return (
    <div className="disputes-container">
      {/* Header */}
      <div className="disputes-header">
        <div>
          <h1 className="disputes-title">
            {isArbitrator ? 'Hàng Đợi Xử Lý Tranh Chấp & Khiếu Nại' : 'Tranh Chấp Của Tôi'}
          </h1>
          <p className="disputes-subtitle">
            {isArbitrator
              ? 'Tiếp nhận hồ sơ, đối soát bằng chứng, tự động khấu trừ điểm uy tín RS (BR-55) và kiểm soát thời hạn SLA (BR-53/54).'
              : 'Theo dõi tiến trình giải quyết khiếu nại ca làm việc và bổ sung bằng chứng theo quy định.'}
          </p>
        </div>
        <div className="disputes-actions-group">
          {!isArbitrator && (
            <button
              type="button"
              className="dispute-btn-primary"
              onClick={() => setModalType('create')}
            >
              Nộp đơn khiếu nại (UC-96)
            </button>
          )}
          <button
            type="button"
            className="dispute-btn-secondary"
            onClick={() => (isArbitrator ? loadQueue() : loadMyDisputes())}
          >
            Làm mới
          </button>
        </div>
      </div>

      {/* Tra cứu nhanh theo ID */}
      <form onSubmit={(e) => { e.preventDefault(); if (searchId.trim()) handleOpenDetail(searchId.trim()); }} className="dispute-search-form">
        <input
          type="text"
          className="dispute-input"
          placeholder="Nhập mã tranh chấp (UUID) để tra cứu hồ sơ trực tiếp..."
          value={searchId}
          onChange={(e) => setSearchId(e.target.value)}
        />
        <button type="submit" className="dispute-btn-secondary">
          Tra cứu
        </button>
      </form>

      {/* Tabs lọc hàng đợi cho Admin/CSKH HOẶC Danh sách cá nhân cho Worker/EO */}
      {isArbitrator ? (
        <>
          {/* Phạm vi: toàn bộ đội CC hay chỉ những đơn được giao cho tôi */}
          <div className="disputes-tabs">
            <button
              type="button"
              className={`disputes-tab-btn ${queueScope === 'ALL' ? 'active' : ''}`}
              onClick={() => setQueueScope('ALL')}
            >
              <span>Toàn bộ hàng đợi ({queue.length})</span>
            </button>
            <button
              type="button"
              className={`disputes-tab-btn ${queueScope === 'MINE' ? 'active' : ''}`}
              onClick={() => {
                setQueueScope('MINE');
                loadMyQueue();
              }}
            >
              <span>Được giao tôi ({myQueue.length})</span>
            </button>
          </div>

          <div className="disputes-tabs">
          <button type="button" className={`disputes-tab-btn ${queueFilter === 'ALL' ? 'active' : ''}`} onClick={() => setQueueFilter('ALL')}>
            <span>Tất cả ({scopedQueue.length})</span>
          </button>
          <button type="button" className={`disputes-tab-btn ${queueFilter === 'OPEN' ? 'active' : ''}`} onClick={() => setQueueFilter('OPEN')}>
            <span>Chờ nhận ({scopedQueue.filter((q) => q.status === 'OPEN').length})</span>
          </button>
          <button type="button" className={`disputes-tab-btn ${queueFilter === 'INVESTIGATING' ? 'active' : ''}`} onClick={() => setQueueFilter('INVESTIGATING')}>
            <span>Đang điều tra ({scopedQueue.filter((q) => q.status === 'INVESTIGATING').length})</span>
          </button>
          <button type="button" className={`disputes-tab-btn ${queueFilter === 'ESCALATED' ? 'active' : ''}`} onClick={() => setQueueFilter('ESCALATED')}>
            <span>Kháng nghị Admin ({scopedQueue.filter((q) => q.status === 'ESCALATED').length})</span>
          </button>
          {riskCount > 0 && (
            <button type="button" className={`disputes-tab-btn ${queueFilter === 'SLA_RISK' ? 'active' : ''}`} onClick={() => setQueueFilter('SLA_RISK')}>
              <span className="tab-danger-text">SLA Khẩn cấp ({riskCount})</span>
            </button>
          )}
          </div>
        </>
      ) : (
        <div className="disputes-tabs">
          <button type="button" className="disputes-tab-btn active">
            <span>Danh sách khiếu nại đã gửi ({myDisputes.length})</span>
          </button>
        </div>
      )}

      {/* Nội dung bảng */}
      {isArbitrator ? (
        <DisputeQueueTable
          queue={filteredQueue}
          loading={loading}
          onSelect={handleOpenDetail}
          onAssign={(id) => assignDispute(id)}
          onDecide={openDecision}
          onTransfer={openTransfer}
        />
      ) : (
        <MyDisputesTable
          disputes={myDisputes}
          loading={loading}
          onSelect={handleOpenDetail}
          onAddEvidence={openEvidence}
          onEscalate={openEscalate}
        />
      )}

      {/* Modals */}
      {modalType === 'detail' && (
        <DisputeDetailModal
          dispute={modalDispute || selectedDispute}
          onClose={() => setModalType(null)}
          onAddEvidence={openEvidence}
          onDecide={openDecision}
          onEscalate={openEscalate}
          onTransfer={isArbitrator ? openTransfer : undefined}
        />
      )}
      {modalType === 'create' && <CreateDisputeModal onClose={() => setModalType(null)} onSubmit={createDispute} />}
      {modalType === 'evidence' && modalDispute && <AddEvidenceModal dispute={modalDispute} onClose={() => setModalType(null)} onSubmit={addEvidence} />}
      {modalType === 'decision' && modalDispute && <DisputeDecisionModal dispute={modalDispute} onClose={() => setModalType(null)} onSubmit={issueDecision} />}
      {modalType === 'escalate' && modalDispute && <EscalateModal dispute={modalDispute} onClose={() => setModalType(null)} onSubmit={escalateDispute} />}
      {modalType === 'transfer' && modalDispute && (
        <TransferDisputeModal
          dispute={modalDispute}
          staff={ccStaff}
          onClose={() => setModalType(null)}
          onSubmit={assignDispute}
        />
      )}
    </div>
  );
};

export default DisputeCenterView;
