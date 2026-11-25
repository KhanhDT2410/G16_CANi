import React, { useState, useMemo } from 'react';
import DisputeResolutionDetailModal from './DisputeResolutionDetailModal';
import { resolveAssetUrl, buildWorkerVietQrUrl } from '../utils/resolveAssetUrl';

export default function EOPaymentsTab({
  payments = [],
  events = [],
  loading = false,
  error = null,
  onRefresh,
  onOpenPaymentModal,
  onOpenDisputeModal,
  onOpenVoucherModal,
  onNavigateToDisputes,
}) {
  const [selectedDisputePayment, setSelectedDisputePayment] = useState(null);
  const [previewImage, setPreviewImage] = useState(null);
  const [eventFilter, setEventFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');

  const filteredPayments = useMemo(() => {
    return payments.filter((p) => {
      if (eventFilter !== 'ALL' && p.eventId !== eventFilter) return false;
      if (statusFilter === 'ALL') return true;
      const isDisputeActive = p.status === 'DISPUTED' ||
        (p.disputeStatus && p.disputeStatus !== 'RESOLVED' && p.disputeStatus !== 'FINAL_RESOLVED');
      if (statusFilter === 'DISPUTED') return isDisputeActive;
      return p.status === statusFilter;
    });
  }, [payments, eventFilter, statusFilter]);

  const renderStatusBadge = (p) => {
    const isDisputeResolved = p.disputeStatus === 'RESOLVED' || p.disputeStatus === 'FINAL_RESOLVED';
    const isDisputeActive = p.status === 'DISPUTED' ||
      (p.disputeStatus && p.disputeStatus !== 'RESOLVED' && p.disputeStatus !== 'FINAL_RESOLVED');

    if (isDisputeResolved) {
      return (
        <div>
          <span className="payment-status-badge badge-resolved">Đã phân xử</span>
          {p.disputeOutcome === 'EO_FAULT' && <div className="dispute-outcome-pill pill-eo-fault">Lỗi Nhà tổ chức</div>}
          {p.disputeOutcome === 'WORKER_FAULT' && <div className="dispute-outcome-pill pill-worker-fault">Lỗi Nhân sự</div>}
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
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' }}>
          <select
            className="filter-select"
            value={eventFilter}
            onChange={(e) => setEventFilter(e.target.value)}
            aria-label="Lọc theo sự kiện"
          >
            <option value="ALL">Tất cả sự kiện ({payments.length})</option>
            {events.map((evt) => (
              <option key={evt.eventId} value={evt.eventId}>
                {evt.name}
              </option>
            ))}
          </select>
          <select
            className="filter-select"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            aria-label="Lọc theo trạng thái thanh toán"
          >
            <option value="ALL">Tất cả trạng thái</option>
            <option value="NOT_DECLARED">Chưa khai báo</option>
            <option value="DECLARED_PENDING_WORKER">Chờ Worker xác nhận</option>
            <option value="CONFIRMED_FULL">Đã hoàn tất</option>
            <option value="CONFIRMED_PARTIAL">Xác nhận một phần</option>
            <option value="DISPUTED">Đang tranh chấp</option>
          </select>
          {onRefresh && (
            <button
              type="button"
              onClick={onRefresh}
              disabled={loading}
              style={{ background: '#f8fafc', color: '#334155', border: '1px solid #cbd5e1', borderRadius: 8, padding: '0.52rem 0.9rem', fontSize: '0.84rem', fontWeight: 700, cursor: loading ? 'not-allowed' : 'pointer' }}
            >
              {loading ? 'Đang tải...' : 'Làm Mới'}
            </button>
          )}
          <button
            type="button"
            onClick={onOpenVoucherModal}
            disabled={filteredPayments.length === 0}
            title={filteredPayments.length === 0 ? 'Cần có ít nhất 1 khoản thanh toán để xuất phiếu chi' : 'Xuất Phiếu Chi Thù Lao (PDF / In)'}
            style={{ backgroundColor: filteredPayments.length === 0 ? '#94a3b8' : '#059669', color: '#ffffff', border: 'none', borderRadius: 8, padding: '0.55rem 1.25rem', fontSize: '0.85rem', fontWeight: 700, cursor: filteredPayments.length === 0 ? 'not-allowed' : 'pointer', display: 'inline-flex', alignItems: 'center', gap: '0.5rem', boxShadow: filteredPayments.length === 0 ? 'none' : '0 2px 8px rgba(5, 150, 105, 0.25)' }}
          >
            Xuất Phiếu Chi Thù Lao (PDF / In)
          </button>
        </div>
      </div>

      <div className="eo-table-container">
        <table className="eo-table">
          <thead>
            <tr>
              <th>Mã Phân Công</th>
              <th>Nhân Sự</th>
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
              <tr>
                <td colSpan={9} style={{ textAlign: 'center', padding: '2.5rem 1rem', color: '#dc2626', fontWeight: 600 }}>
                  <div>{error}</div>
                  {onRefresh && (
                    <button type="button" onClick={onRefresh} style={{ marginTop: '0.6rem', background: '#fee2e2', color: '#b91c1c', border: '1px solid #fca5a5', borderRadius: 6, padding: '0.35rem 0.9rem', fontWeight: 700, cursor: 'pointer' }}>
                      Thử lại
                    </button>
                  )}
                </td>
              </tr>
            ) : filteredPayments.length === 0 ? (
              <tr>
                <td colSpan={9} style={{ textAlign: 'center', padding: '2.75rem 1rem', color: '#64748b' }}>
                  <div style={{ fontWeight: 700, color: '#334155', marginBottom: '0.35rem' }}>
                    {payments.length === 0
                      ? 'Chưa có khoản thanh toán nào được ghi nhận'
                      : 'Không có khoản thanh toán phù hợp bộ lọc'}
                  </div>
                  <div style={{ fontSize: '0.84rem' }}>
                    {payments.length === 0
                      ? 'Khoản thanh toán sẽ tự động được tạo khi Worker xác nhận nhận ca hoặc điểm danh làm việc.'
                      : 'Thử chọn lại sự kiện hoặc trạng thái khác để xem các khoản thanh toán hiện có.'}
                  </div>
                </td>
              </tr>
            ) : (
              filteredPayments.map((p) => {
                const isDisputeResolved = p.disputeStatus === 'RESOLVED' || p.disputeStatus === 'FINAL_RESOLVED';
                const isDisputeActive = p.status === 'DISPUTED' ||
                  (p.disputeStatus && p.disputeStatus !== 'RESOLVED' && p.disputeStatus !== 'FINAL_RESOLVED');
                return (
                  <tr key={p.paymentId}>
                    <td style={{ fontWeight: 600, fontFamily: 'monospace', fontSize: '0.85rem' }} title={p.assignmentId}>
                      {p.assignmentId?.length > 14 ? `${p.assignmentId.substring(0, 8)}...` : p.assignmentId}
                    </td>
                    <td>{p.workerName}</td>
                    <td>
                      <div style={{ fontWeight: 600, color: '#0f172a' }}>{p.jobType || 'Nhân sự sự kiện'}</div>
                      {p.eventName && (
                        <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: 2 }}>{p.eventName}</div>
                      )}
                    </td>
                    <td style={{ fontWeight: 600 }}>{(p.agreedAmount != null ? p.agreedAmount : 0).toLocaleString()} đ</td>
                    <td style={{ color: p.declaredAmount > 0 ? '#059669' : '#9ca3af', fontWeight: 600 }}>
                      {p.declaredAmount > 0 ? `${(p.declaredAmount || 0).toLocaleString()} đ` : 'Chưa trả'}
                    </td>
                    <td>{p.paymentMethod === 'BANK_TRANSFER' ? 'Chuyển khoản' : p.paymentMethod === 'CASH' ? 'Tiền mặt' : (p.paymentMethod || '—')}</td>
                    <td style={{ fontSize: '0.82rem', color: '#475569' }}>
                      <div>{p.externalRef || 'Chưa có'}</div>
                      <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginTop: 4 }}>
                        {p.paymentProofUrl && (
                          <button
                            type="button"
                            onClick={() => setPreviewImage({
                              url: resolveAssetUrl(p.paymentProofUrl),
                              title: `Chứng từ thanh toán cho ${p.workerName}${p.externalRef ? ' (' + p.externalRef + ')' : ''}`,
                            })}
                            style={{ background: '#eff6ff', color: '#1d4ed8', border: '1px solid #bfdbfe', borderRadius: 4, padding: '2px 6px', fontSize: '0.72rem', fontWeight: 700, cursor: 'pointer' }}
                          >
                            Xem chứng từ
                          </button>
                        )}
                        {buildWorkerVietQrUrl(p) && (
                          <button
                            type="button"
                            onClick={() => setPreviewImage({
                              url: buildWorkerVietQrUrl(p),
                              title: `Mã VietQR nhận lương của ${p.workerName}`,
                            })}
                            style={{ background: '#f5f3ff', color: '#6d28d9', border: '1px solid #ddd6fe', borderRadius: 4, padding: '2px 6px', fontSize: '0.72rem', fontWeight: 700, cursor: 'pointer' }}
                          >
                            QR
                          </button>
                        )}
                      </div>
                    </td>
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

      {previewImage && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.85)',
            zIndex: 10000,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '1.5rem',
          }}
          onClick={() => setPreviewImage(null)}
        >
          <div style={{ color: '#ffffff', fontWeight: 700, marginBottom: '1rem', fontSize: '1.05rem' }}>
            {previewImage.title}
          </div>
          <img
            src={previewImage.url}
            alt={previewImage.title}
            style={{ maxWidth: '90vw', maxHeight: '80vh', borderRadius: '12px', backgroundColor: '#ffffff' }}
            onClick={(e) => e.stopPropagation()}
          />
          <button
            type="button"
            onClick={() => setPreviewImage(null)}
            style={{ marginTop: '1rem', background: '#ffffff', color: '#0f172a', border: 'none', borderRadius: '8px', padding: '0.5rem 1.25rem', fontWeight: 700, cursor: 'pointer' }}
          >
            Đóng ảnh
          </button>
        </div>
      )}
    </section>
  );
}
