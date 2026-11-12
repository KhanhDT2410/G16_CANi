import {
  TICKET_STATUS_CONFIG,
  TICKET_CATEGORY_CONFIG,
} from '../types/ticket.constants';

/**
 * Bảng hàng đợi Phiếu Hỗ Trợ Khách Hàng cho CSKH / Quản trị viên.
 * - Hiển thị danh tính khách hàng (Tên, SĐT, Email).
 * - Mã phiếu xuống dòng gọn gàng theo chiều dọc, tối ưu diện tích.
 * - Cột Thao Tác căn sát lề trái với thứ tự nút: Xem Phiếu -> Chat Trực Tiếp -> Tiếp Nhận.
 * - Cảnh báo SLA "Sắp hết hạn" nhỏ gọn, tinh tế.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const TicketQueueTable = ({
  tickets,
  onOpenDetail,
  onOpenChat,
  onAssign,
  onOpenTransferModal,
  loading,
}) => {
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
            <th style={{ width: '85px' }}>Mã Phiếu</th>
            <th style={{ minWidth: '150px' }}>Khách Hàng (SĐT / Email)</th>
            <th style={{ minWidth: '110px' }}>Danh Mục</th>
            <th style={{ minWidth: '180px' }}>Tiêu Đề Yêu Cầu</th>
            <th style={{ width: '90px' }}>Trạng Thái</th>
            <th style={{ width: '105px' }}>Hạn SLA</th>
            <th style={{ minWidth: '120px' }}>Chuyên Viên</th>
            <th style={{ textAlign: 'left', minWidth: '190px' }}>Thao Tác</th>
          </tr>
        </thead>
        <tbody>
          {tickets.map((t) => {
            const statusConfig = TICKET_STATUS_CONFIG[t.status] || { label: t.status, badgeClass: '' };
            const categoryConfig = TICKET_CATEGORY_CONFIG[t.category] || { label: t.category };

            return (
              <tr key={t.id}>
                {/* Mã phiếu xuống dòng gọn gàng theo chiều dọc */}
                <td style={{ width: '85px' }}>
                  <div className="ticket-ref-cell">
                    {t.referenceNo ? (
                      t.referenceNo.split('-').map((part, idx, arr) => (
                        <span key={idx} className="ticket-ref-line">
                          {part}{idx < arr.length - 1 ? '-' : ''}
                        </span>
                      ))
                    ) : (
                      <span className="ticket-reference">—</span>
                    )}
                  </div>
                </td>

                {/* Cột thông tin khách hàng: Tên, SĐT, Email */}
                <td>
                  <div className="ticket-customer-cell">
                    <div className="ticket-customer-name">{t.userName || 'Khách hàng'}</div>
                    <div className="ticket-customer-contact">
                      <span>SĐT: {t.userPhone || '—'}</span>
                      <span className="ticket-customer-sep">·</span>
                      <span>{t.userEmail || '—'}</span>
                    </div>
                  </div>
                </td>

                <td>{categoryConfig.label}</td>

                {/* Tiêu đề yêu cầu: Tự động xuống dòng tự nhiên */}
                <td style={{ minWidth: '180px', maxWidth: '320px' }}>
                  <div className="ticket-subject-cell">
                    {t.subject}
                  </div>
                </td>

                <td>
                  <span className={`ticket-badge ${statusConfig.badgeClass}`}>
                    {statusConfig.label}
                  </span>
                </td>

                {/* Hạn SLA kèm cảnh báo nhỏ nhắn "Sắp hết hạn" */}
                <td>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem', whiteSpace: 'nowrap' }}>
                    <span style={{ fontSize: '0.84rem', color: '#1e293b' }}>
                      {formatDateTime(t.slaDeadline)}
                    </span>
                    {t.slaAtRisk && (
                      <span className="ticket-badge-sla-warning">
                        Sắp hết hạn
                      </span>
                    )}
                  </div>
                </td>

                {/* Chuyên viên tiếp nhận: Tên to đậm, phân quyền ( CSKH ) ở dòng dưới */}
                <td style={{ minWidth: '120px' }}>
                  {t.assignedName || (t.assignedTo && t.assignedTo !== 'Chưa nhận') ? (
                    <div className="ticket-staff-cell">
                      <div className="ticket-staff-name">
                        {t.assignedName || t.assignedTo}
                      </div>
                      <div className="ticket-staff-role">
                        {t.assignedRole ? `( ${t.assignedRole} )` : '( CSKH )'}
                      </div>
                    </div>
                  ) : (
                    <span className="ticket-staff-unassigned">Chưa nhận</span>
                  )}
                </td>

                {/* Nút thao tác: Sát về lề trái; Thứ tự: Xem Phiếu -> Chat Trực Tiếp -> Tiếp Nhận */}
                <td style={{ textAlign: 'left' }}>
                  <div style={{ display: 'flex', justifyContent: 'flex-start', alignItems: 'center', gap: '0.4rem', flexWrap: 'wrap' }}>
                    <button
                      type="button"
                      className="ticket-btn ticket-btn-secondary ticket-btn-sm"
                      onClick={() => onOpenDetail(t.id)}
                    >
                      Xem Phiếu
                    </button>
                    <button
                      type="button"
                      className="ticket-btn ticket-btn-chat ticket-btn-sm"
                      onClick={() => onOpenChat(t)}
                    >
                      Chat Trực Tiếp
                    </button>
                    {t.status === 'OPEN' && !t.assignedTo && onAssign && (
                      <button
                        type="button"
                        className="ticket-btn ticket-btn-secondary ticket-btn-sm"
                        onClick={() => onAssign(t.id)}
                      >
                        Tiếp Nhận
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

export default TicketQueueTable;
