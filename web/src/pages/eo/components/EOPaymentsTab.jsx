import React, { useState } from 'react';
import DisputeResolutionDetailModal from './DisputeResolutionDetailModal';

export default function EOPaymentsTab({
  payments = [],
  loading = false,
  error = null,
  onOpenPaymentModal,
  onOpenDisputeModal,
  onOpenVoucherModal,
  onNavigateToDisputes,
}) {
  const [selectedDisputePayment, setSelectedDisputePayment] = useState(null);

  const renderStatusBadge = (p) => {
    const isDisputeResolved = p.disputeStatus === 'RESOLVED' || p.disputeStatus === 'FINAL_RESOLVED';
    const isDisputeActive = p.status === 'DISPUTED' ||
      (p.disputeStatus && p.disputeStatus !== 'RESOLVED' && p.disputeStatus !== 'FINAL_RESOLVED');

    if (isDisputeResolved) {
      return (
        <div>
          <span className="payment-status-badge badge-resolved">Đã phân xử</span>
          {p.disputeOutcome === 'EO_FAULT' && <div className="dispute-outcome-pill pill-eo-fault">Lỗi EO (Cần trả)</div>}
          {p.disputeOutcome === 'WORKER_FAULT' && <div className="dispute-outcome-pill pill-worker-fault">Lỗi Worker (-10 RS)</div>}
          {p.disputeOutcome === 'PARTIAL' && <div className="dispute-outcome-pill pill-partial">Chia sẻ 2 bên</div>}
          {p.disputeOutcome === 'UNVERIFIED' && <div className="dispute-outcome-pill pill-unverified">Không xác minh</div>}
        </div>
      );
    }
    if (isDisputeActive) return <span className="payment-status-badge badge-disputed">Đang tranh chấp</span>;
    if (p.status === 'CONFIRMED_FULL') return <span className="payment-status-badge badge-confirmed">Đã hoàn tất</span>;
    if (p.status === 'DECLARED_PENDING_WORKER') return <span className="payment-status-badge badge-pending">Chờ Worker xác nhận</span>;
    if (p.status === 'CONFIRMED_PARTIAL') return <span className="payment-status-badge badge-partial">Xác nhận một phần</span>;
    if (p.status === 'UNVERIFIED') return <span className="payment-status-badge badge-unverified">Chưa xác minh</span>;
    return <span className="payment-status-badge badge-unpaid">Chưa khai báo</span>;
  };

  const renderActions = (p, isDisputeActive, isDisputeResolved) => (
    <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap' }}>
      {isDisputeActive && (
        <>
          <button type="button" className="btn-dispute-track" onClick={() => setSelectedDisputePayment(p)}>
            Theo Dõi Tranh Chấp
          </button>
          {onNavigateToDisputes && (
            <button type="button" className="btn-dispute-nav" onClick={() => onNavigateToDisputes(p.disputeId || p.assignmentId)}>
              Đến CSKH &rarr;
            </button>
          )}
        </>
      )}
      {isDisputeResolved && (
        <>
          <button type="button" className="btn-dispute-result" onClick={() => setSelectedDisputePayment(p)}>
            Xem Phán Quyết
          </button>
          {p.disputeOutcome === 'EO_FAULT' && (
            <button type="button" className="btn-declare-payment" onClick={() => onOpenPaymentModal(p)}>
              Trả Lương Bổ Sung
            </button>
          )}
        </>
      )}
      {!isDisputeActive && !isDisputeResolved && (
        <>
          {p.status !== 'CONFIRMED_FULL' && (
            <button type="button" className="btn-declare-payment" onClick={() => onOpenPaymentModal(p)}>
              Khai Báo Trả Lương
            </button>
          )}
          <button
            type="button"
            style={{ background: '#fee2e2', color: '#b91c1c', border: '1px solid #fca5a5', borderRadius: 6, padding: '4px 8px', fontSize: '0.75rem', fontWeight: 600, cursor: 'pointer' }}
            onClick={() => onOpenDisputeModal && onOpenDisputeModal(p)}
          >
            Khiếu Nại
          </button>
        </>
      )}
    </div>
  );

  return (
    <section className="eo-payments-section animate-fade-in-up">
      <div className="eo-widget-card" style={{ padding: '1.25rem', marginBottom: '1rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h3 style={{ margin: 0, fontSize: '1.15rem', fontWeight: 800, color: '#0f172a' }}>
            Khai Báo Lương & Quyết Toán Thù Lao Nhân Sự
          </h3>
          <p style={{ margin: '0.25rem 0 0', fontSize: '0.85rem', color: '#64748b' }}>
            Đối soát chấm công GPS QR, thanh toán trực tiếp sau ca và xuất chứng từ chi chuẩn kế toán.
          </p>
        </div>
        <button
          type="button"
          onClick={onOpenVoucherModal}
          style={{ backgroundColor: '#059669', color: '#ffffff', border: 'none', borderRadius: 8, padding: '0.55rem 1.25rem', fontSize: '0.85rem', fontWeight: 700, cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: '0.5rem', boxShadow: '0 2px 8px rgba(5, 150, 105, 0.25)' }}
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
            {loading ? (
              <tr><td colSpan={9} style={{ textAlign: 'center', padding: '2.5rem 1rem', color: '#64748b' }}>Đang tải dữ liệu thanh toán...</td></tr>
            ) : error ? (
              <tr><td colSpan={9} style={{ textAlign: 'center', padding: '2.5rem 1rem', color: '#dc2626', fontWeight: 600 }}>{error}</td></tr>
            ) : payments.length === 0 ? (
              <tr>
                <td colSpan={9} style={{ textAlign: 'center', padding: '2.75rem 1rem', color: '#64748b' }}>
                  <div style={{ fontWeight: 700, color: '#334155', marginBottom: '0.35rem' }}>Chưa có khoản thanh toán nào được ghi nhận</div>
                  <div style={{ fontSize: '0.84rem' }}>Mở một ca làm việc đã có nhân sự điểm danh để khai báo và đối soát thù lao trực tiếp.</div>
                </td>
              </tr>
            ) : (
              payments.map((p) => {
                const isDisputeResolved = p.disputeStatus === 'RESOLVED' || p.disputeStatus === 'FINAL_RESOLVED';
                const isDisputeActive = p.status === 'DISPUTED' ||
                  (p.disputeStatus && p.disputeStatus !== 'RESOLVED' && p.disputeStatus !== 'FINAL_RESOLVED');
                return (
                  <tr key={p.paymentId}>
                    <td style={{ fontWeight: 600, fontFamily: 'monospace', fontSize: '0.85rem' }} title={p.assignmentId}>
                      {p.assignmentId?.length > 14 ? `${p.assignmentId.substring(0, 8)}...` : p.assignmentId}
                    </td>
                    <td>{p.workerName}</td>
                    <td>{p.jobType}</td>
                    <td style={{ fontWeight: 600 }}>{(p.agreedAmount != null ? p.agreedAmount : 0).toLocaleString()} đ</td>
                    <td style={{ color: p.declaredAmount > 0 ? '#059669' : '#9ca3af', fontWeight: 600 }}>
                      {p.declaredAmount > 0 ? `${(p.declaredAmount || 0).toLocaleString()} đ` : 'Chưa trả'}
                    </td>
                    <td>{p.paymentMethod || '—'}</td>
                    <td style={{ fontSize: '0.85rem', color: '#64748b' }}>{p.externalRef || 'Chưa có'}</td>
                    <td>{renderStatusBadge(p)}</td>
                    <td>{renderActions(p, isDisputeActive, isDisputeResolved)}</td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      <DisputeResolutionDetailModal
        isOpen={Boolean(selectedDisputePayment)}
        payment={selectedDisputePayment}
        onClose={() => setSelectedDisputePayment(null)}
        onNavigateToDisputes={onNavigateToDisputes}
        onOpenPaymentDeclaration={onOpenPaymentModal}
      />
    </section>
  );
}
