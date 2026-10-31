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

export const DisputeCenterView = ({ showTestSubmission = false }) => {
  const { user } = useAuth();
  const isAdminOrCC = user?.role === 'ADMIN' || user?.role === 'CUSTOMER_CARE';

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
  } = useDisputes('queue');

  const [modalType, setModalType] = useState(null); // 'detail' | 'create' | 'evidence' | 'decision' | 'escalate'
  const [modalDispute, setModalDispute] = useState(null);
  const [searchId, setSearchId] = useState('');
  const [devTestMode, setDevTestMode] = useState(showTestSubmission);

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
            ⚖️ Trung Tâm Giải Quyết Tranh Chấp & Khiếu Nại (Dispute Center)
          </h1>
          <p className="disputes-subtitle">
            Tiếp nhận hồ sơ, đối soát bằng chứng, tự động khấu trừ điểm uy tín RS (BR-55) và kiểm soát thời hạn SLA (BR-53/54).
          </p>
        </div>
        <div className="disputes-actions-group">
          {(!isAdminOrCC || devTestMode) && (
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
              + {isAdminOrCC ? '🧪 Giả lập nộp đơn (Test)' : 'Nộp đơn khiếu nại (UC-96)'}
            </button>
          )}
          {isAdminOrCC && (
            <button
              type="button"
              className="btn-quick-fill"
              style={{
                padding: '0.6rem 0.85rem',
                fontSize: '0.8rem',
                background: devTestMode ? '#fef3c7' : 'transparent',
                color: devTestMode ? '#b45309' : 'var(--color-text-muted)',
                borderColor: devTestMode ? '#f59e0b' : 'var(--color-border)',
              }}
              onClick={() => setDevTestMode((prev) => !prev)}
              title="Chế độ giả lập nộp đơn dành cho kiểm thử chức năng"
            >
              {devTestMode ? '🧪 Tắt Test Sandbox' : '🧪 Bật Test Sandbox'}
            </button>
          )}
          <button
            type="button"
            className="btn-quick-fill"
            style={{ padding: '0.6rem 1rem', background: 'var(--color-bg-subtle)', fontSize: '0.85rem' }}
            onClick={() => (activeTab === 'queue' ? loadQueue() : loadMyDisputes())}
          >
            🔄 Tải lại
          </button>
        </div>
      </div>

      {/* Tra cứu nhanh theo ID */}
      <form onSubmit={handleSearchById} style={{ display: 'flex', gap: '0.5rem', maxWidth: '480px' }}>
        <input
          type="text"
          className="dispute-input"
          placeholder="Nhập ID tranh chấp (UUID) để tra cứu trực tiếp..."
          value={searchId}
          onChange={(e) => setSearchId(e.target.value)}
        />
        <button type="submit" className="btn-quick-fill" style={{ margin: 0 }}>
          Tìm
        </button>
      </form>

      {/* Tabs */}
      <div className="disputes-tabs">
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
        {(!isAdminOrCC || devTestMode) && (
          <button
            type="button"
            className={`disputes-tab-btn ${activeTab === 'my' ? 'active' : ''}`}
            onClick={() => setActiveTab('my')}
          >
            <span>{isAdminOrCC ? '🧪 Đơn cá nhân (Test Sandbox)' : 'Tranh chấp của tôi (UC-104)'}</span>
            <span className="tab-badge-count">{myDisputes.length}</span>
          </button>
        )}
      </div>

      {/* Tab content */}
      {activeTab === 'queue' ? (
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

