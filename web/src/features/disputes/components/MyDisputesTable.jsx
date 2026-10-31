import { DISPUTE_STATUS_CONFIG, DISPUTE_TYPE_LABELS } from '../types/dispute.constants';

const MyDisputesTable = ({ disputes, loading, onSelect, onAddEvidence, onEscalate }) => {
  if (loading) {
    return <div className="dispute-empty">Đang tải lịch sử tranh chấp của bạn...</div>;
  }

  if (!disputes || disputes.length === 0) {
    return (
      <div className="dispute-empty">
        <p>Bạn chưa tham gia hoặc nộp đơn tranh chấp nào.</p>
      </div>
    );
  }

  return (
    <div className="dispute-table-card">
      <table className="dispute-table">
        <thead>
          <tr>
            <th>Mã đơn</th>
            <th>Danh mục</th>
            <th>Tiêu đề</th>
            <th>Ngày nộp</th>
            <th>Trạng thái</th>
            <th style={{ textAlign: 'right' }}>Thao tác</th>
          </tr>
        </thead>
        <tbody>
          {disputes.map((item) => {
            const statusConfig = DISPUTE_STATUS_CONFIG[item.status] || {
              label: item.status,
              badgeClass: 'badge-status-open',
            };
            const typeLabel = DISPUTE_TYPE_LABELS[item.type] || item.type;
            const created = item.createdAt ? new Date(item.createdAt).toLocaleString('vi-VN') : '—';
            const canAddEvidence = item.status === 'OPEN' || item.status === 'INVESTIGATING';
            const canEscalate = item.status === 'RESOLVED' || item.status === 'UNVERIFIED';

            return (
              <tr key={item.id}>
                <td>
                  <code style={{ fontSize: '0.8rem' }}>{item.id?.substring(0, 8)}...</code>
                </td>
                <td>
                  <span style={{ fontWeight: 500 }}>{typeLabel}</span>
                </td>
                <td>{item.title}</td>
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
                      onClick={() => onSelect(item.id)}
                    >
                      Chi tiết
                    </button>
                    {canAddEvidence && (
                      <button
                        type="button"
                        className="btn-quick-fill"
                        style={{ margin: 0, color: 'var(--color-primary)', borderColor: 'var(--color-primary)' }}
                        onClick={() => onAddEvidence(item)}
                      >
                        + Bằng chứng
                      </button>
                    )}
                    {canEscalate && (
                      <button
                        type="button"
                        className="btn-quick-fill"
                        style={{ margin: 0, color: '#86198f', borderColor: '#86198f' }}
                        onClick={() => onEscalate(item)}
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
  );
};

export default MyDisputesTable;

