import { DISPUTE_STATUS_CONFIG, DISPUTE_TYPE_LABELS } from '../types/dispute.constants';

const DisputeQueueTable = ({ queue, loading, onSelect, onAssign, onDecide }) => {
  if (loading) {
    return <div className="dispute-empty">Đang tải dữ liệu hàng đợi...</div>;
  }

  if (!queue || queue.length === 0) {
    return (
      <div className="dispute-empty">
        <p>Hiện không có đơn khiếu nại nào đang mở trong hàng đợi CSKH.</p>
      </div>
    );
  }

  return (
    <div className="dispute-table-card">
      <table className="dispute-table">
        <thead>
          <tr>
            <th>Mã đơn</th>
            <th>Loại tranh chấp</th>
            <th>Tiêu đề</th>
            <th>SLA Deadline</th>
            <th>Trạng thái</th>
            <th>Người phụ trách</th>
            <th style={{ textAlign: 'right' }}>Thao tác</th>
          </tr>
        </thead>
        <tbody>
          {queue.map((item) => {
            const statusConfig = DISPUTE_STATUS_CONFIG[item.status] || {
              label: item.status,
              badgeClass: 'badge-status-open',
            };
            const typeLabel = DISPUTE_TYPE_LABELS[item.type] || item.type;
            const slaDate = item.slaDeadline ? new Date(item.slaDeadline).toLocaleString('vi-VN') : '—';

            return (
              <tr key={item.id}>
                <td>
                  <code style={{ fontSize: '0.8rem' }}>{item.id?.substring(0, 8)}...</code>
                </td>
                <td>
                  <span style={{ fontWeight: 500 }}>{typeLabel}</span>
                </td>
                <td>
                  <span title={item.title}>{item.title}</span>
                </td>
                <td>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem' }}>
                    <span>{slaDate}</span>
                    {item.slaAtRisk && (
                      <span className="sla-risk-badge">⚠️ Gấp (&lt;24h)</span>
                    )}
                  </div>
                </td>
                <td>
                  <span className={`dispute-badge ${statusConfig.badgeClass}`}>
                    {statusConfig.label}
                  </span>
                </td>
                <td>
                  {item.assignedTo ? (
                    <code style={{ fontSize: '0.8rem' }}>{item.assignedTo.substring(0, 8)}...</code>
                  ) : (
                    <span style={{ color: 'var(--color-warning)', fontStyle: 'italic' }}>Chưa gán</span>
                  )}
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
                    {item.status === 'OPEN' && (
                      <button
                        type="button"
                        className="btn-quick-fill"
                        style={{ margin: 0, color: 'var(--color-info)', borderColor: 'var(--color-info)' }}
                        onClick={() => onAssign(item.id)}
                      >
                        Nhận xử lý
                      </button>
                    )}
                    {(item.status === 'INVESTIGATING' || item.status === 'ESCALATED') && (
                      <button
                        type="button"
                        className="btn-quick-fill"
                        style={{ margin: 0, color: 'var(--color-success)', borderColor: 'var(--color-success)' }}
                        onClick={() => onDecide(item)}
                      >
                        Phán quyết
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

export default DisputeQueueTable;

