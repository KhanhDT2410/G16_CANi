import React from 'react';
import { useSubscription } from '../hooks/useSubscription';
import PayOSQrModal from './PayOSQrModal';
import './EOSubscriptionTab.css';

export default function EOSubscriptionTab() {
  const {
    plans,
    currentSubscription,
    history,
    loading,
    purchasingPlan,
    activeSubTab,
    setActiveSubTab,
    payOSModalData,
    setPayOSModalData,
    handlePurchaseVNPay,
    handlePurchasePayOS,
    handleConfirmPayOS,
    handleSimulateDemo,
  } = useSubscription();

  const formatCurrency = (val) => {
    return new Intl.NumberFormat('vi-VN').format(val || 0);
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '—';
    try {
      return new Date(dateStr).toLocaleString('vi-VN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return dateStr;
    }
  };

  const isCurrentActive =
    currentSubscription &&
    currentSubscription.status === 'ACTIVE' &&
    currentSubscription.remainingEvents > 0;

  return (
    <div className="eo-subscription-container">
      {/* 1. HERO QUOTA STATUS CARD */}
      <div className="sub-current-card">
        <div className="sub-current-header">
          <div className="sub-current-title-group">
            <h2>Gói Dịch Vụ & Hạn Mức Điều Phối</h2>
            <p className="sub-current-subtitle">
              Mô hình thanh toán theo Sự kiện (Pay-per-Event Quota). Tuân thủ mô hình Non-Escrow & Nghị định 52/2024/NĐ-CP.
            </p>
          </div>
          <div>
            {isCurrentActive ? (
              <span className="sub-status-pill status-active">
                Đang Hoạt Động: {currentSubscription.planName}
              </span>
            ) : currentSubscription && currentSubscription.status === 'CONSUMED' ? (
              <span className="sub-status-pill status-consumed">
                Đã Sử Dụng Hết Hạn Mức
              </span>
            ) : (
              <span className="sub-status-pill status-none">
                Chưa Có Gói Kích Hoạt
              </span>
            )}
          </div>
        </div>

        <div className="sub-metrics-row">
          <div className="sub-metric-item">
            <span className="sub-metric-label">Số sự kiện còn lại</span>
            <span className={`sub-metric-val ${isCurrentActive ? 'val-highlight' : ''}`}>
              {currentSubscription?.remainingEvents ?? 0} <small style={{ fontSize: '0.9rem', color: '#94a3b8' }}>Event</small>
            </span>
            <span className="sub-metric-hint">
              {isCurrentActive ? 'Đủ điều kiện xuất bản sự kiện & ca làm' : 'Cần nạp thêm gói để xuất bản sự kiện'}
            </span>
          </div>

          <div className="sub-metric-item">
            <span className="sub-metric-label">Chế độ tuyển dụng</span>
            <span className="sub-metric-val" style={{ fontSize: '1.15rem' }}>
              {currentSubscription?.planType === 'MATCHING_PLUS'
                ? 'Matching Radar AI + Mời'
                : currentSubscription?.planType === 'INVITE_ONLY'
                ? 'Mời Trực Tiếp (Invite Mode)'
                : 'Chưa kích hoạt'}
            </span>
            <span className="sub-metric-hint">
              {currentSubscription?.planType === 'MATCHING_PLUS'
                ? 'Tự động quét nhân sự bán kính 15km'
                : 'Phân phối qua link / QR bảo mật'}
            </span>
          </div>

          <div className="sub-metric-item">
            <span className="sub-metric-label">Hạn sử dụng</span>
            <span className="sub-metric-val" style={{ fontSize: '1.15rem', color: '#e2e8f0' }}>
              {currentSubscription?.endDate ? formatDate(currentSubscription.endDate) : 'Vô thời hạn (theo Quota)'}
            </span>
            <span className="sub-metric-hint">
              Mã giao dịch: {currentSubscription?.transactionRef || 'N/A'}
            </span>
          </div>
        </div>
      </div>

      {/* 2. SUB-NAVIGATION TABS (W-06 SPEC) */}
      <div className="sub-tabs-bar">
        <button
          type="button"
          className={`sub-tab-btn ${activeSubTab === 'plans' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('plans')}
        >
          Danh Mục Gói Cước (UC-10, UC-11)
        </button>
        <button
          type="button"
          className={`sub-tab-btn ${activeSubTab === 'history' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('history')}
        >
          Lịch Sử Giao Dịch ({history.length})
        </button>
      </div>

      {/* 3. SUB-TAB: PLANS & PURCHASE (UC-10, UC-11) */}
      {activeSubTab === 'plans' && (
        <>
          <div className="demo-sandbox-notice">
            <div className="demo-notice-content">
              <h4>Môi Trường Thử Nghiệm VNPay Sandbox Gateway & Non-Escrow Compliance</h4>
              <p>
                Hệ thống CANi tích hợp Cổng thanh toán <strong>PayOS VietQR</strong> và <strong>VNPay Sandbox</strong>. Khi chọn hình thức thanh toán, bạn có thể quét mã VietQR tự động khớp tiền hoặc bấm <strong>"[Demo] Giả lập thanh toán"</strong> để kích hoạt gói ngay lập tức phục vụ demo hội đồng.
              </p>
            </div>
          </div>

          {loading ? (
            <div style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>
              Đang tải danh mục gói cước từ máy chủ...
            </div>
          ) : (
            <div className="sub-pricing-grid">
              {plans.map((plan) => {
                const isMatching = plan.planType === 'MATCHING_PLUS';

                return (
                  <div
                    key={plan.planType}
                    className={`pricing-card ${isMatching ? 'featured' : ''}`}
                  >
                    {isMatching && <div className="pricing-badge">Khuyên Dùng</div>}

                    <div className="pricing-header">
                      <h3 className="pricing-title">{plan.name}</h3>
                      <p className="pricing-desc">{plan.description}</p>
                    </div>

                    <div className="pricing-cost">
                      <span className="cost-amount">{formatCurrency(plan.price)}</span>
                      <span className="cost-currency">VNĐ</span>
                      <span className="cost-period">/ Sự kiện</span>
                    </div>

                    <ul className="pricing-features">
                      {(plan.features || []).map((feat, idx) => (
                        <li key={idx}>
                          <span className="feat-icon">-</span>
                          <span>{feat}</span>
                        </li>
                      ))}
                    </ul>

                    <div className="pricing-actions">
                      <button
                        type="button"
                        className="btn-purchase-vnpay"
                        style={{ background: '#059669' }}
                        disabled={purchasingPlan !== null}
                        onClick={() => handlePurchasePayOS(plan.planType)}
                      >
                        {purchasingPlan === plan.planType ? (
                          'Đang khởi tạo PayOS...'
                        ) : (
                          'Quét Mã VietQR (PayOS MBBank)'
                        )}
                      </button>

                      <button
                        type="button"
                        className="btn-purchase-vnpay"
                        style={{ background: '#0284c7' }}
                        disabled={purchasingPlan !== null}
                        onClick={() => handlePurchaseVNPay(plan.planType)}
                      >
                        {purchasingPlan === plan.planType ? (
                          'Đang kết nối VNPay...'
                        ) : (
                          `Thanh toán VNPay Sandbox (${formatCurrency(plan.price)} đ)`
                        )}
                      </button>

                      <button
                        type="button"
                        className="btn-demo-simulate"
                        disabled={purchasingPlan !== null}
                        onClick={() => handleSimulateDemo(plan.planType)}
                        title="Dành riêng cho Hội đồng chấm đồ án: Giả lập thanh toán thành công tức thì"
                      >
                        [Demo Sandbox] Giả Lập Thành Công
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </>
      )}

      {/* 4. SUB-TAB: TRANSACTION HISTORY (UC-12) */}
      {activeSubTab === 'history' && (
        <div className="sub-history-container">
          {history.length === 0 ? (
            <div className="empty-history">
              <p>Chưa có lịch sử giao dịch mua gói cước nào.</p>
              <button
                type="button"
                className="btn-purchase-vnpay"
                style={{ display: 'inline-flex', marginTop: '0.75rem' }}
                onClick={() => setActiveSubTab('plans')}
              >
                Khám phá các gói cước ngay
              </button>
            </div>
          ) : (
            <table className="sub-table">
              <thead>
                <tr>
                  <th>Mã Giao Dịch</th>
                  <th>Gói Cước</th>
                  <th>Số Tiền</th>
                  <th>Cổng TT</th>
                  <th>Quota Sự Kiện</th>
                  <th>Thời Gian Mua</th>
                  <th>Trạng Thái</th>
                </tr>
              </thead>
              <tbody>
                {history.map((item) => (
                  <tr key={item.subscriptionId}>
                    <td style={{ fontFamily: 'monospace', fontWeight: 600 }}>
                      {item.transactionRef || item.subscriptionId.substring(0, 8)}
                    </td>
                    <td>
                      <strong>{item.planName}</strong>
                      <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                        {item.planType}
                      </div>
                    </td>
                    <td style={{ fontWeight: 700, color: '#0f172a' }}>
                      {formatCurrency(item.amountPaid)} đ
                    </td>
                    <td>
                      <span>VNPay / PayOS</span>
                    </td>
                    <td>
                      <span style={{ fontWeight: 600, color: item.remainingEvents > 0 ? '#059669' : '#64748b' }}>
                        {item.remainingEvents} / {item.eventsQuota || 1} còn lại
                      </span>
                    </td>
                    <td style={{ fontSize: '0.85rem', color: '#64748b' }}>
                      {formatDate(item.startDate)}
                    </td>
                    <td>
                      <span
                        className={`sub-status-pill ${
                          item.status === 'ACTIVE'
                            ? 'status-active'
                            : item.status === 'CONSUMED'
                            ? 'status-consumed'
                            : 'status-none'
                        }`}
                      >
                        {item.status === 'ACTIVE'
                          ? 'Đang hoạt động'
                          : item.status === 'CONSUMED'
                          ? 'Đã dùng hết'
                          : item.status === 'EXPIRED'
                          ? 'Hết hạn'
                          : item.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* MODAL VIETQR PAYOS TỰ ĐỘNG KHỚP TIỀN MBBANK */}
      <PayOSQrModal
        isOpen={Boolean(payOSModalData)}
        onClose={() => setPayOSModalData(null)}
        data={payOSModalData}
        onConfirm={handleConfirmPayOS}
      />
    </div>
  );
}
