import {
  DISPUTE_STATUS_CONFIG,
  DISPUTE_TYPE_LABELS,
  DISPUTE_OUTCOME_LABELS,
  DISPUTE_EVIDENCE_LABELS,
} from '../types/dispute.constants';

const DisputeDetailModal = ({
  dispute,
  onClose,
  onAddEvidence,
  onDecide,
  onEscalate,
}) => {
  if (!dispute) return null;

  const statusConfig = DISPUTE_STATUS_CONFIG[dispute.status] || {
    label: dispute.status,
    badgeClass: 'badge-status-open',
  };
  const typeLabel = DISPUTE_TYPE_LABELS[dispute.type] || dispute.type;
  const outcomeLabel = DISPUTE_OUTCOME_LABELS[dispute.outcome] || dispute.outcome || 'Chưa có kết luận';

  const canAddEvidence = dispute.status === 'OPEN' || dispute.status === 'INVESTIGATING';
  const canEscalate = dispute.status === 'RESOLVED' || dispute.status === 'UNVERIFIED';
  const canDecide = dispute.status === 'INVESTIGATING' || dispute.status === 'ESCALATED';

  return (
    <div className="dispute-modal-overlay" onClick={onClose}>
      <div className="dispute-modal" onClick={(e) => e.stopPropagation()}>
        <div className="dispute-modal-header">
          <div>
            <h3>Chi tiết Tranh chấp</h3>
            <span style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
              ID: <code>{dispute.id}</code>
            </span>
          </div>
          <button type="button" className="dispute-modal-close" onClick={onClose}>✕</button>
        </div>

        <div className="dispute-modal-body">
          {/* Metadata Row */}
          <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'center' }}>
            <span className={`dispute-badge ${statusConfig.badgeClass}`}>{statusConfig.label}</span>
            <span style={{ fontSize: '0.85rem', fontWeight: 600 }}>{typeLabel}</span>
            {dispute.slaDeadline && (
              <span style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
                Hạn xử lý (SLA): {new Date(dispute.slaDeadline).toLocaleString('vi-VN')}
              </span>
            )}
          </div>

          {/* Parties & Assignment */}
          <div style={{ padding: '0.85rem', background: 'var(--color-bg-subtle)', borderRadius: 'var(--radius-md)', fontSize: '0.82rem', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem' }}>
            <div><strong>Ca làm việc (Assignment):</strong> <br /><code>{dispute.assignmentId}</code></div>
            <div><strong>Người phụ trách (CC):</strong> <br /><code>{dispute.assignedTo || 'Chưa gán'}</code></div>
            <div><strong>Bên khiếu nại:</strong> <br /><code>{dispute.complainantId}</code></div>
            <div><strong>Bên bị đơn:</strong> <br /><code>{dispute.respondentId}</code></div>
          </div>

          {/* Title & Description */}
          <div className="dispute-field-group">
            <label>Tiêu đề khiếu nại</label>
            <div style={{ fontWeight: 600, color: 'var(--color-text-main)' }}>{dispute.title}</div>
          </div>
          <div className="dispute-field-group">
            <label>Nội dung mô tả tình huống</label>
            <div style={{ fontSize: '0.88rem', background: 'var(--color-bg-subtle)', padding: '0.75rem', borderRadius: 'var(--radius-md)', whiteSpace: 'pre-line' }}>
              {dispute.description}
            </div>
          </div>

          {/* Evidence List */}
          <div className="dispute-field-group">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <label>Bằng chứng đính kèm ({dispute.evidences?.length || 0})</label>
              {canAddEvidence && (
                <button
                  type="button"
                  className="btn-quick-fill"
                  style={{ margin: 0 }}
                  onClick={() => onAddEvidence(dispute)}
                >
                  + Nạp thêm bằng chứng
                </button>
              )}
            </div>
            {(!dispute.evidences || dispute.evidences.length === 0) ? (
              <div style={{ fontSize: '0.82rem', color: 'var(--color-text-muted)', fontStyle: 'italic' }}>
                Chưa có bằng chứng nào được tải lên.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                {dispute.evidences.map((ev) => (
                  <div key={ev.id} className="evidence-chip">
                    <div>
                      <strong>[{DISPUTE_EVIDENCE_LABELS[ev.fileType] || ev.fileType}]</strong> {ev.label || 'Bằng chứng'}
                      <div style={{ fontSize: '0.72rem', color: 'var(--color-text-muted)' }}>
                        Nộp bởi: <code>{ev.party?.substring(0, 8)}...</code> lúc {new Date(ev.createdAt).toLocaleString('vi-VN')}
                      </div>
                    </div>
                    <a href={ev.fileUrl} target="_blank" rel="noreferrer" style={{ color: 'var(--color-primary)', textDecoration: 'underline' }}>
                      Xem liên kết
                    </a>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Decision Section */}
          {dispute.outcome && (
            <div style={{ padding: '0.85rem', background: 'var(--color-success-bg)', border: '1px solid var(--color-success-border)', borderRadius: 'var(--radius-md)' }}>
              <div style={{ fontWeight: 700, color: 'var(--color-success-text)', marginBottom: '0.35rem' }}>
                Kết luận giải quyết: {outcomeLabel}
              </div>
              <div style={{ fontSize: '0.85rem', color: 'var(--color-text-body)', whiteSpace: 'pre-line' }}>
                {dispute.decisionRationale}
              </div>
              {dispute.rsPenaltyApplied && (
                <div style={{ marginTop: '0.4rem', fontSize: '0.8rem', color: 'var(--color-danger-text)', fontWeight: 600 }}>
                  ⚠️ Đã tự động áp dụng khấu trừ −10 điểm RS của Worker (BR-55)
                </div>
              )}
              {dispute.resolution && (
                <div style={{ marginTop: '0.2rem', fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
                  Ghi chú: {dispute.resolution}
                </div>
              )}
            </div>
          )}

          {/* Escalation Section */}
          {dispute.escalationReason && (
            <div style={{ padding: '0.85rem', background: '#fae8ff', border: '1px solid #f0abfc', borderRadius: 'var(--radius-md)' }}>
              <div style={{ fontWeight: 700, color: '#86198f', marginBottom: '0.35rem' }}>
                Kháng nghị lên Ban Quản trị viên (Admin)
              </div>
              <div style={{ fontSize: '0.85rem', color: 'var(--color-text-body)' }}>
                {dispute.escalationReason}
              </div>
              {dispute.finalDecisionRationale && (
                <div style={{ marginTop: '0.5rem', paddingTop: '0.5rem', borderTop: '1px dashed #d8b4fe', fontSize: '0.85rem' }}>
                  <strong>Phán quyết cuối cùng của Admin:</strong>
                  <div>{dispute.finalDecisionRationale}</div>
                </div>
              )}
            </div>
          )}
        </div>

        <div className="dispute-modal-footer">
          {canDecide && (
            <button
              type="button"
              className="btn-quick-fill"
              style={{ color: 'var(--color-success)', borderColor: 'var(--color-success)', padding: '0.5rem 1rem' }}
              onClick={() => onDecide(dispute)}
            >
              Ra phán quyết
            </button>
          )}
          {canEscalate && (
            <button
              type="button"
              className="btn-quick-fill"
              style={{ color: '#86198f', borderColor: '#86198f', padding: '0.5rem 1rem' }}
              onClick={() => onEscalate(dispute)}
            >
              Kháng nghị lên Admin
            </button>
          )}
          <button
            type="button"
            className="btn-quick-fill"
            style={{ padding: '0.5rem 1rem', background: 'var(--color-bg-subtle)' }}
            onClick={onClose}
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
};

export default DisputeDetailModal;

