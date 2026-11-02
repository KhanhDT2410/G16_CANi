import { useState } from 'react';
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
    activeTab,
    setActiveTab,
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

  const [modalType, setModalType] = useState(null); // 'detail' | 'create' | 'evidence' | 'decision' | 'escalate'
  const [modalDispute, setModalDispute] = useState(null);
  const [searchId, setSearchId] = useState('');

  const riskCount = queue.filter((item) => item.slaAtRisk).length;

  const handleOpenDetail = async (id) => {
    const detail = await loadDisputeDetail(id);
    if (detail) {
      setModalDispute(detail);
      setModalType('detail');
    }
  };

  const handleOpenEvidence = (dispute) => {
    setModalDispute(dispute);
    setModalType('evidence');
  };

  const handleOpenDecision = (dispute) => {
    setModalDispute(dispute);
    setModalType('decision');
  };

  const handleOpenEscalate = (dispute) => {
    setModalDispute(dispute);
    setModalType('escalate');
  };

  const handleSearchById = (e) => {
    e.preventDefault();
    if (searchId.trim()) {
      handleOpenDetail(searchId.trim());
    }
  };

  return (
    <div className="disputes-container">
      {/* Header */}
      <div className="disputes-header">
        <div>
          <h1 className="disputes-title">
            ⚖️ Trung Tâm Giải Quyết Tranh Chấp & Khiếu Nại
          </h1>
          <p className="disputes-subtitle">
            Tiếp nhận hồ sơ, đối soát bằng chứng, tự động khấu trừ điểm uy tín RS (BR-55) và kiểm soát thời hạn SLA (BR-53/54).
          </p>
        </div>
        <div className="disputes-actions-group">
          {!isArbitrator && (
            <button
              type="button"
              className="btn-quick-fill"
              style={{
                padding: '0.6rem 1.1rem',
                color: '#fff',
                background: 'var(--color-primary)',
                borderColor: 'transparent',
                fontSize: '0.85rem',
              }}
              onClick={() => setModalType('create')}
            >
              + Nộp đơn khiếu nại (UC-96)
            </button>
          )}
          <button
            type="button"
            className="btn-quick-fill"
            style={{ padding: '0.6rem 1rem', background: 'var(--color-bg-subtle)', fontSize: '0.85rem' }}
            onClick={() => (activeTab === 'queue' ? loadQueue() : loadMyDisputes())}
          >
            🔄 Làm mới
          </button>
        </div>
      </div>

      {/* Tra cứu nhanh theo ID */}
      <form onSubmit={handleSearchById} style={{ display: 'flex', gap: '0.5rem', maxWidth: '520px' }}>
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

      {/* Tabs */}
      <div className="disputes-tabs">
        {isArbitrator && (
          <button
            type="button"
            className={`disputes-tab-btn ${activeTab === 'queue' ? 'active' : ''}`}
            onClick={() => setActiveTab('queue')}
          >
            <span>Hàng đợi xử lý (UC-109)</span>
            <span className={`tab-badge-count ${riskCount > 0 ? 'risk' : ''}`}>
              {queue.length} {riskCount > 0 && `(⚠️ ${riskCount} gấp)`}
            </span>
          </button>
        )}
        <button
          type="button"
          className={`disputes-tab-btn ${activeTab === 'my' ? 'active' : ''}`}
          onClick={() => setActiveTab('my')}
        >
          <span>Lịch sử tranh chấp của tôi (UC-104)</span>
          <span className="tab-badge-count">{myDisputes.length}</span>
        </button>
      </div>

      {/* Tab content */}
      {activeTab === 'queue' && isArbitrator ? (
        <DisputeQueueTable
          queue={queue}
          loading={loading}
          onSelect={handleOpenDetail}
          onAssign={(id) => assignDispute(id)}
          onDecide={handleOpenDecision}
        />
      ) : (
        <MyDisputesTable
          disputes={myDisputes}
          loading={loading}
          onSelect={handleOpenDetail}
          onAddEvidence={handleOpenEvidence}
          onEscalate={handleOpenEscalate}
        />
      )}

      {/* Modals */}
      {modalType === 'detail' && (
        <DisputeDetailModal
          dispute={modalDispute || selectedDispute}
          onClose={() => setModalType(null)}
          onAddEvidence={handleOpenEvidence}
          onDecide={handleOpenDecision}
          onEscalate={handleOpenEscalate}
        />
      )}

      {modalType === 'create' && (
        <CreateDisputeModal
          onClose={() => setModalType(null)}
          onSubmit={createDispute}
        />
      )}

      {modalType === 'evidence' && modalDispute && (
        <AddEvidenceModal
          dispute={modalDispute}
          onClose={() => setModalType(null)}
          onSubmit={addEvidence}
        />
      )}

      {modalType === 'decision' && modalDispute && (
        <DisputeDecisionModal
          dispute={modalDispute}
          onClose={() => setModalType(null)}
          onSubmit={issueDecision}
        />
      )}

      {modalType === 'escalate' && modalDispute && (
        <EscalateModal
          dispute={modalDispute}
          onClose={() => setModalType(null)}
          onSubmit={escalateDispute}
        />
      )}
    </div>
  );
};

export default DisputeCenterView;
