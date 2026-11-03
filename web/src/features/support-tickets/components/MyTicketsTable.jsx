import {
  TICKET_STATUS_CONFIG,
  TICKET_CATEGORY_CONFIG,
} from '../types/ticket.constants';

export const MyTicketsTable = ({ tickets, onOpenDetail, loading }) => {
  const formatDateTime = (isoStr) => {
    if (!isoStr) return '—';
    return new Date(isoStr).toLocaleString('vi-VN', {
      hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit',
    });
  };

  if (loading && (!tickets || tickets.length === 0)) {
    return <div className="ticket-empty-state">Đang tải danh sách phiếu hỗ trợ của bạn...</div>;
  }

  if (!tickets || tickets.length === 0) {
    return <div className="ticket-empty-state">Bạn chưa gửi yêu cầu hỗ trợ nào.</div>;
  }

  return (
    <div className="tickets-table-wrapper">
      <table className="tickets-table">
        <thead>
          <tr>
            <th>Mã Phiếu</th>
            <th>Danh Mục</th>
            <th>Tiêu Đề</th>
            <th>Trạng Thái</th>
            <th>Thời Gian Gửi</th>
            <th style={{ textAlign: 'right' }}>Thao Tác</th>
          </tr>
        </thead>
        <tbody>
          {tickets.map((t) => {
            const statusConfig = TICKET_STATUS_CONFIG[t.status] || { label: t.status, badgeClass: '' };
            const categoryConfig = TICKET_CATEGORY_CONFIG[t.category] || { label: t.category };

            return (
              <tr key={t.id}>
                <td>
                  <span className="ticket-reference">{t.referenceNo}</span>
                </td>
                <td>{categoryConfig.label}</td>
                <td style={{ maxWidth: '320px', fontWeight: 500 }}>
                  <div style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {t.subject}
                  </div>
                </td>
                <td>
                  <span className={`ticket-badge ${statusConfig.badgeClass}`}>
                    {statusConfig.label}
                  </span>
                </td>
                <td>{formatDateTime(t.createdAt)}</td>
                <td style={{ textAlign: 'right' }}>
                  <button
                    type="button"
                    className="ticket-btn ticket-btn-primary ticket-btn-sm"
                    onClick={() => onOpenDetail(t.id)}
                  >
                    Xem Chi Tiết
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};

export default MyTicketsTable;

