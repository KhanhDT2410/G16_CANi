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
import './Disputes.css';

export const DisputeCenterView = () => {
  const { user } = useAuth();
  const isArbitrator = user?.role === 'ADMIN' || user?.role === 'CUSTOMER_CARE';

  const {
    queue,
    myDisputes,
    selectedDispute,
    loading,
    loadQueue,
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

  const riskCount = useMemo(() => queue.filter((item) => item.slaAtRisk).length, [queue]);

  const filteredQueue = useMemo(() => {
    if (!isArbitrator) return [];
    if (queueFilter === 'OPEN') return queue.filter((item) => item.status === 'OPEN');
    if (queueFilter === 'INVESTIGATING') return queue.filter((item) => item.status === 'INVESTIGATING');
    if (queueFilter === 'ESCALATED') return queue.filter((item) => item.status === 'ESCALATED');
    if (queueFilter === 'SLA_RISK') return queue.filter((item) => item.slaAtRisk);
    return queue;
  }, [queue, queueFilter, isArbitrator]);

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

  return (
    <div className="disputes-container">
      {/* Header */}
      <div className="disputes-header">
        <div>
          <h1 className="disputes-title">
            ⚖️ {isArbitrator ? 'Hàng Đợi Xử Lý Tranh Chấp & Khiếu Nại' : 'Tranh Chấp Của Tôi'}
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
              className="btn-quick-fill"
              style={{ padding: '0.6rem 1.1rem', color: '#fff', background: 'var(--color-primary)', borderColor: 'transparent' }}
              onClick={() => setModalType('create')}
            >
              + Nộp đơn khiếu nại (UC-96)
            </button>
          )}
          <button
            type="button"
            className="btn-quick-fill"
            style={{ padding: '0.6rem 1rem', background: 'var(--color-bg-subtle)' }}
            onClick={() => (isArbitrator ? loadQueue() : loadMyDisputes())}
          >
            🔄 Làm mới
          </button>
        </div>
      </div>

      {/* Tra cứu nhanh theo ID */}
      <form onSubmit={(e) => { e.preventDefault(); if (searchId.trim()) handleOpenDetail(searchId.trim()); }} style={{ display: 'flex', gap: '0.5rem', maxWidth: '520px' }}>
        <input
          type="text"
          className="dispute-input"
          placeholder="Nhập mã tranh chấp (UUID) để tra cứu hồ sơ trực tiếp..."
          value={searchId}
          onChange={(e) => setSearchId(e.target.value)}
        />
        <button type="submit" className="btn-quick-fill" style={{ margin: 0, padding: '0.65rem 1.1rem' }}>
          Tra cứu
        </button>
      </form>

      {/* Tabs lọc hàng đợi cho Admin/CSKH HOẶC Danh sách cá nhân cho Worker/EO */}
      {isArbitrator ? (
        <div className="disputes-tabs">
          <button type="button" className={`disputes-tab-btn ${queueFilter === 'ALL' ? 'active' : ''}`} onClick={() => setQueueFilter('ALL')}>
            <span>Tất cả ({queue.length})</span>
          </button>
          <button type="button" className={`disputes-tab-btn ${queueFilter === 'OPEN' ? 'active' : ''}`} onClick={() => setQueueFilter('OPEN')}>
            <span>Chờ nhận ({queue.filter((q) => q.status === 'OPEN').length})</span>
          </button>
          <button type="button" className={`disputes-tab-btn ${queueFilter === 'INVESTIGATING' ? 'active' : ''}`} onClick={() => setQueueFilter('INVESTIGATING')}>
            <span>Đang điều tra ({queue.filter((q) => q.status === 'INVESTIGATING').length})</span>
          </button>
          <button type="button" className={`disputes-tab-btn ${queueFilter === 'ESCALATED' ? 'active' : ''}`} onClick={() => setQueueFilter('ESCALATED')}>
            <span>Kháng nghị Admin ({queue.filter((q) => q.status === 'ESCALATED').length})</span>
          </button>
          {riskCount > 0 && (
            <button type="button" className={`disputes-tab-btn ${queueFilter === 'SLA_RISK' ? 'active' : ''}`} onClick={() => setQueueFilter('SLA_RISK')}>
              <span style={{ color: 'var(--color-danger)' }}>⚠️ SLA Khẩn cấp ({riskCount})</span>
            </button>
          )}
        </div>
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
        />
      )}
      {modalType === 'create' && <CreateDisputeModal onClose={() => setModalType(null)} onSubmit={createDispute} />}
      {modalType === 'evidence' && modalDispute && <AddEvidenceModal dispute={modalDispute} onClose={() => setModalType(null)} onSubmit={addEvidence} />}
      {modalType === 'decision' && modalDispute && <DisputeDecisionModal dispute={modalDispute} onClose={() => setModalType(null)} onSubmit={issueDecision} />}
      {modalType === 'escalate' && modalDispute && <EscalateModal dispute={modalDispute} onClose={() => setModalType(null)} onSubmit={escalateDispute} />}
    </div>
  );
};

export default DisputeCenterView;
