export default function EOPaymentsTab({ payments, onOpenPaymentModal }) {
  return (
    <section className="eo-payments-section animate-fade-in-up">
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
                <td style={{ fontWeight: 600 }}>{p.assignmentId}</td>
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
                      ? '✓ Đã hoàn tất'
                      : p.status === 'DECLARED_PENDING_WORKER'
                      ? '⏳ Chờ Worker xác nhận'
                      : '⚠️ Chưa khai báo'}
                  </span>
                </td>
                <td>
                  {p.status !== 'CONFIRMED_FULL' && (
                    <button
                      type="button"
                      className="btn-declare-payment"
                      onClick={() => onOpenPaymentModal(p)}
                    >
                      Khai Báo Trả Lương
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
