import { useState, useMemo } from 'react';
import { useAuth } from '../../../features/auth';
import {
  useDisputes,
  DisputeDetailModal,
  AddEvidenceModal,
  EscalateModal,
  CreateDisputeModal,
  DISPUTE_STATUS_CONFIG,
  DISPUTE_TYPE_LABELS,
} from '../../../features/disputes';
import '../../../features/disputes/components/Disputes.css';

export default function EODisputesPanel() {
  const { user } = useAuth();
  const {
    myDisputes,
    loading,
    loadMyDisputes,
    loadDisputeDetail,
    selectedDispute,
    setSelectedDispute,
    createDispute,
    addEvidence,
    escalateDispute,
  } = useDisputes('my');

  const [filterRole, setFilterRole] = useState('ALL'); // 'ALL' | 'RESPONDENT' | 'COMPLAINANT'
  const [evidenceTarget, setEvidenceTarget] = useState(null);
  const [escalateTarget, setEscalateTarget] = useState(null);
  const [showCreate, setShowCreate] = useState(false);

  const currentUserId = user?.userId ? String(user.userId) : null;

  const counts = useMemo(() => {
    let respondent = 0;
    let complainant = 0;
    (myDisputes || []).forEach((d) => {
      if (currentUserId && String(d.respondentId) === currentUserId) respondent++;
      if (currentUserId && String(d.complainantId) === currentUserId) complainant++;
    });
    return { all: (myDisputes || []).length, respondent, complainant };
  }, [myDisputes, currentUserId]);

  const filteredDisputes = useMemo(() => {
    if (!myDisputes) return [];
    if (filterRole === 'RESPONDENT') {
      return myDisputes.filter((d) => currentUserId && String(d.respondentId) === currentUserId);
    }
    if (filterRole === 'COMPLAINANT') {
      return myDisputes.filter((d) => currentUserId && String(d.complainantId) === currentUserId);
    }
    return myDisputes;
  }, [myDisputes, filterRole, currentUserId]);

  return (
    <div className="animate-fade-in" style={{ padding: '0.25rem 0' }}>
      {/* Header Bar */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.75rem', marginBottom: '1.25rem' }}>
        <div>
          <h3 style={{ margin: 0, fontSize: '1.15rem', fontWeight: 800, color: '#0f172a' }}>
            Quản Lý Khiếu Nại & Tranh Chấp 3 Bên
          </h3>
          <p style={{ margin: '0.25rem 0 0', fontSize: '0.85rem', color: '#64748b' }}>
            Tiếp nhận yêu cầu giải trình từ nhân sự, bổ sung chứng từ đối soát và kháng nghị phán quyết.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button
            type="button"
            className="btn-primary"
            style={{ padding: '0.55rem 1.25rem', fontSize: '0.85rem', fontWeight: 700, borderRadius: 8 }}
            onClick={() => setShowCreate(true)}
          >
            Tạo Đơn Khiếu Nại Mới
          </button>
          <button
            type="button"
            className="btn-quick-fill"
            style={{ padding: '0.55rem 1rem', fontSize: '0.85rem', borderRadius: 8, margin: 0 }}
            onClick={loadMyDisputes}
            title="Làm mới danh sách"
          >
            Làm mới
          </button>
        </div>
      </div>

      {/* Sub-filter buttons */}
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '0.75rem' }}>
        <button
          type="button"
          onClick={() => setFilterRole('ALL')}
          style={{
            padding: '6px 14px',
            borderRadius: 6,
            border: 'none',
            fontSize: '0.85rem',
            fontWeight: 600,
            cursor: 'pointer',
            background: filterRole === 'ALL' ? '#0f172a' : '#f1f5f9',
            color: filterRole === 'ALL' ? '#ffffff' : '#475569',
          }}
        >
          Tất cả ({counts.all})
        </button>
        <button
          type="button"
          onClick={() => setFilterRole('RESPONDENT')}
          style={{
            padding: '6px 14px',
            borderRadius: 6,
            border: 'none',
            fontSize: '0.85rem',
            fontWeight: 600,
            cursor: 'pointer',
            background: filterRole === 'RESPONDENT' ? '#dc2626' : '#fee2e2',
            color: filterRole === 'RESPONDENT' ? '#ffffff' : '#b91c1c',
          }}
        >
          Cần giải trình từ nhân sự ({counts.respondent})
        </button>
        <button
          type="button"
          onClick={() => setFilterRole('COMPLAINANT')}
          style={{
            padding: '6px 14px',
            borderRadius: 6,
            border: 'none',
            fontSize: '0.85rem',
            fontWeight: 600,
            cursor: 'pointer',
            background: filterRole === 'COMPLAINANT' ? '#2563eb' : '#eff6ff',
            color: filterRole === 'COMPLAINANT' ? '#ffffff' : '#1d4ed8',
          }}
        >
          Khiếu nại đã gửi ({counts.complainant})
        </button>
      </div>

      {/* Table */}
      {loading ? (
        <div className="dispute-empty">Đang tải danh sách khiếu nại...</div>
      ) : filteredDisputes.length === 0 ? (
        <div className="dispute-empty">
          <p style={{ fontWeight: 600, margin: 0 }}>Không có hồ sơ tranh chấp nào trong mục này</p>
          <span style={{ fontSize: '0.82rem', color: '#94a3b8' }}>
            Mọi khiếu nại liên quan đến ca làm việc, điểm danh và thù lao sẽ xuất hiện tại đây.
          </span>
        </div>
      ) : (
        <div className="dispute-table-card">
          <table className="dispute-table">
            <thead>
              <tr>
                <th>Mã đơn</th>
                <th>Vai trò</th>
                <th>Danh mục</th>
                <th>Tiêu đề</th>
                <th>Ngày tạo</th>
                <th>Trạng thái</th>
                <th style={{ textAlign: 'right' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredDisputes.map((item) => {
                const isRespondent = currentUserId && String(item.respondentId) === currentUserId;
                const statusConfig = DISPUTE_STATUS_CONFIG[item.status] || {
                  label: item.status,
                  badgeClass: 'badge-status-open',
                };
                const typeLabel = DISPUTE_TYPE_LABELS[item.type] || item.type;
                const created = item.createdAt ? new Date(item.createdAt).toLocaleDateString('vi-VN') : '—';
                const canAddEvidence = item.status === 'OPEN' || item.status === 'INVESTIGATING';
                const canEscalate = item.status === 'RESOLVED' || item.status === 'UNVERIFIED';

                return (
                  <tr key={item.id}>
                    <td>
                      <code style={{ fontSize: '0.8rem' }}>{item.id?.substring(0, 8)}</code>
                    </td>
                    <td>
                      <span
                        style={{
                          fontSize: '0.72rem',
                          fontWeight: 700,
                          padding: '2px 8px',
                          borderRadius: 4,
                          background: isRespondent ? '#fef3c7' : '#f1f5f9',
                          color: isRespondent ? '#b45309' : '#475569',
                        }}
                      >
                        {isRespondent ? 'Bị khiếu nại' : 'Người gửi đơn'}
                      </span>
                    </td>
                    <td>
                      <span style={{ fontWeight: 600 }}>{typeLabel}</span>
                    </td>
                    <td style={{ maxWidth: 240, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {item.title}
                    </td>
                    <td>{created}</td>
                    <td>
                      <span className={`dispute-badge ${statusConfig.badgeClass}`}>
                        {statusConfig.label}
                      </span>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.4rem' }}>
                        <button
                          type="button"
                          className="btn-quick-fill"
                          style={{ margin: 0 }}
                          onClick={() => loadDisputeDetail(item.id)}
                        >
                          Chi tiết
                        </button>
                        {canAddEvidence && (
                          <button
                            type="button"
                            className="btn-quick-fill"
                            style={{ margin: 0, color: '#0284c7', borderColor: '#bae6fd' }}
                            onClick={() => setEvidenceTarget(item)}
                            title="Nộp ảnh/tài liệu đối chất"
                          >
                            Nạp bằng chứng
                          </button>
                        )}
                        {canEscalate && (
                          <button
                            type="button"
                            className="btn-quick-fill"
                            style={{ margin: 0, color: '#dc2626', borderColor: '#fca5a5' }}
                            onClick={() => setEscalateTarget(item)}
                            title="Kháng nghị lên Quản trị viên"
                          >
                            Kháng nghị
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Modals */}
      {selectedDispute && (
        <DisputeDetailModal
          dispute={selectedDispute}
          onClose={() => setSelectedDispute(null)}
          onAddEvidence={(d) => { setSelectedDispute(null); setEvidenceTarget(d); }}
          onEscalate={(d) => { setSelectedDispute(null); setEscalateTarget(d); }}
        />
      )}

      {evidenceTarget && (
        <AddEvidenceModal
          dispute={evidenceTarget}
          onClose={() => setEvidenceTarget(null)}
          onSubmit={addEvidence}
        />
      )}

      {escalateTarget && (
        <EscalateModal
          dispute={escalateTarget}
          onClose={() => setEscalateTarget(null)}
          onSubmit={escalateDispute}
        />
      )}

      {showCreate && (
        <CreateDisputeModal
          onClose={() => setShowCreate(false)}
          onSubmit={createDispute}
        />
      )}
    </div>
  );
}
