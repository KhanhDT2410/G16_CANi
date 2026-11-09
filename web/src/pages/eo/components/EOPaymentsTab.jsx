export default function EOPaymentsTab({ payments, onOpenPaymentModal, onOpenDisputeModal, onOpenVoucherModal }) {
  return (
    <section className="eo-payments-section animate-fade-in-up">
      {/* Thanh Tiêu Đề & Thao Tác Xuất Phiếu Chi Kế Toán */}
      <div
        className="eo-widget-card"
        style={{
          padding: '1.25rem',
          marginBottom: '1rem',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '1rem',
        }}
      >
        <div>
          <h3 style={{ margin: 0, fontSize: '1.15rem', fontWeight: 800, color: '#0f172a' }}>
            Khai Báo Lương & Quyết Toán Non-Escrow (Mục 11 SRS)
          </h3>
          <p style={{ margin: '0.25rem 0 0', fontSize: '0.85rem', color: '#64748b' }}>
            Đối soát chấm công GPS QR, thanh toán trực tiếp sau ca và xuất chứng từ chi chuẩn kế toán.
          </p>
        </div>

        <button
          type="button"
          onClick={onOpenVoucherModal}
          style={{
            backgroundColor: '#059669',
            color: '#ffffff',
            border: 'none',
            borderRadius: 8,
            padding: '0.55rem 1.25rem',
            fontSize: '0.85rem',
            fontWeight: 700,
            cursor: 'pointer',
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.5rem',
            boxShadow: '0 2px 8px rgba(5, 150, 105, 0.25)',
          }}
          title="Xuất phiếu chi thù lao Mẫu số 02-TT / NĐ 52/2024/NĐ-CP"
        >
          Xuất Phiếu Chi Thù Lao (PDF / In)
        </button>
      </div>

      <div className="eo-table-container">
        <table className="eo-table">
          <thead>
            <tr>
              <th>Mã Phân Công</th>
              <th>Nhân Sự (Worker)</th>
              <th>Vị Trí / Ca</th>
              <th>Thù Lao Thỏa Thuận</th>
              <th>Đã Khai Báo</th>
              <th>Phương Thức</th>
              <th>Mã GD / Biên Lai</th>
              <th>Trạng Thái</th>
              <th>Thao Tác</th>
            </tr>
          </thead>
          <tbody>
            {payments.map((p) => (
              <tr key={p.paymentId}>
                <td
                  style={{ fontWeight: 600, fontFamily: 'monospace', fontSize: '0.85rem' }}
                  title={p.assignmentId}
                >
                  {p.assignmentId?.length > 14 ? `${p.assignmentId.substring(0, 8)}...` : p.assignmentId}
                </td>
                <td>{p.workerName}</td>
                <td>{p.jobType}</td>
                <td style={{ fontWeight: 600 }}>
                  {p.agreedAmount.toLocaleString()} đ
                </td>
                <td style={{ color: p.declaredAmount > 0 ? '#059669' : '#9ca3af', fontWeight: 600 }}>
                  {p.declaredAmount > 0 ? `${p.declaredAmount.toLocaleString()} đ` : 'Chưa trả'}
                </td>
                <td>{p.paymentMethod || '—'}</td>
                <td style={{ fontSize: '0.85rem', color: '#64748b' }}>
                  {p.externalRef || 'Chưa có'}
                </td>
                <td>
                  <span
                    className={`payment-status-badge ${
                      p.status === 'CONFIRMED_FULL'
                        ? 'badge-confirmed'
                        : p.status === 'DECLARED_PENDING_WORKER'
                        ? 'badge-pending'
                        : 'badge-unpaid'
                    }`}
                  >
                    {p.status === 'CONFIRMED_FULL'
                      ? 'Đã hoàn tất'
                      : p.status === 'DECLARED_PENDING_WORKER'
                      ? 'Chờ Worker xác nhận'
                      : 'Chưa khai báo'}
                  </span>
                </td>
                <td>
                  <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap' }}>
                    {p.status !== 'CONFIRMED_FULL' && (
                      <button
                        type="button"
                        className="btn-declare-payment"
                        onClick={() => onOpenPaymentModal(p)}
                      >
                        Khai Báo Trả Lương
                      </button>
                    )}
                    <button
                      type="button"
                      style={{
                        background: '#fee2e2',
                        color: '#b91c1c',
                        border: '1px solid #fca5a5',
                        borderRadius: 6,
                        padding: '4px 8px',
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        cursor: 'pointer',
                      }}
                      onClick={() => onOpenDisputeModal && onOpenDisputeModal(p)}
                      title="Gửi khiếu nại tranh chấp lên Customer Care (UC-96)"
                    >
                      Khiếu Nại
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
