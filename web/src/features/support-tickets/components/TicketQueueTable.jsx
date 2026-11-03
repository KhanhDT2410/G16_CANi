import {
  TICKET_STATUS_CONFIG,
  TICKET_CATEGORY_CONFIG,
  TICKET_PRIORITY_CONFIG,
} from '../types/ticket.constants';

export const TicketQueueTable = ({ tickets, onOpenDetail, onAssign, loading }) => {
  const formatDateTime = (isoStr) => {
    if (!isoStr) return '—';
    return new Date(isoStr).toLocaleString('vi-VN', {
      hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit',
    });
  };

  if (loading && (!tickets || tickets.length === 0)) {
    return <div className="ticket-empty-state">Đang tải danh sách hàng đợi phiếu hỗ trợ...</div>;
  }

  if (!tickets || tickets.length === 0) {
    return <div className="ticket-empty-state">Không có phiếu hỗ trợ nào trong hàng đợi.</div>;
  }

  return (
    <div className="tickets-table-wrapper">
      <table className="tickets-table">
        <thead>
          <tr>
            <th>Mã Phiếu</th>
            <th>Danh Mục</th>
            <th>Độ Ưu Tiên</th>
            <th>Tiêu Đề</th>
            <th>Trạng Thái</th>
            <th>Hạn SLA</th>
            <th>Chuyên Viên</th>
            <th style={{ textAlign: 'right' }}>Thao Tác</th>
          </tr>
        </thead>
        <tbody>
          {tickets.map((t) => {
            const statusConfig = TICKET_STATUS_CONFIG[t.status] || { label: t.status, badgeClass: '' };
            const priorityConfig = TICKET_PRIORITY_CONFIG[t.priority] || { label: t.priority, badgeClass: '' };
            const categoryConfig = TICKET_CATEGORY_CONFIG[t.category] || { label: t.category };

            return (
              <tr key={t.id}>
                <td>
                  <span className="ticket-reference">{t.referenceNo}</span>
                </td>
                <td>{categoryConfig.label}</td>
                <td>
                  <span className={`ticket-badge ${priorityConfig.badgeClass}`}>
                    {priorityConfig.label}
                  </span>
                </td>
                <td style={{ maxWidth: '280px', fontWeight: 500 }}>
                  <div style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {t.subject}
                  </div>
                </td>
                <td>
                  <span className={`ticket-badge ${statusConfig.badgeClass}`}>
                    {statusConfig.label}
                  </span>
                </td>
                <td>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem' }}>
                    <span>{formatDateTime(t.slaDeadline)}</span>
                    {t.slaAtRisk && (
                      <span className="ticket-badge-risk" style={{ alignSelf: 'flex-start' }}>
                        SLA Nguy Cơ
                      </span>
                    )}
                  </div>
                </td>
                <td style={{ fontSize: '0.82rem', color: '#64748b' }}>
                  {t.assignedTo ? t.assignedTo.substring(0, 8) + '...' : 'Chưa nhận'}
                </td>
                <td style={{ textAlign: 'right' }}>
                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.4rem' }}>
                    {t.status === 'OPEN' && (
                      <button
                        type="button"
                        className="ticket-btn ticket-btn-secondary ticket-btn-sm"
                        onClick={() => onAssign(t.id)}
                      >
                        Tiếp Nhận
                      </button>
                    )}
                    <button
                      type="button"
                      className="ticket-btn ticket-btn-primary ticket-btn-sm"
                      onClick={() => onOpenDetail(t.id)}
                    >
                      Chi Tiết
                    </button>
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

export default TicketQueueTable;

