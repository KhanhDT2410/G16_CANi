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
    handlePurchasePayOS,
    handleConfirmPayOS,
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
    Boolean(currentSubscription.subscriptionId) &&
    currentSubscription.status === 'ACTIVE' &&
    currentSubscription.remainingEvents > 0;

  return (
    <div className="eo-subscription-container">
      {/* 1. HERO QUOTA STATUS CARD */}
      <div className="sub-current-card">
        <div className="sub-current-header">
          <div className="sub-current-title-group">
            <h2>Gói Dịch Vụ Sự Kiện</h2>
            <p className="sub-current-subtitle">
              Quản lý số lượng sự kiện và phương thức tuyển dụng nhân sự trên nền tảng CANi.
            </p>
          </div>
          <div>
            {isCurrentActive ? (
              <span className="sub-status-pill status-active">
                Đang Hoạt Động: {currentSubscription.planName}
              </span>
            ) : currentSubscription && currentSubscription.subscriptionId && currentSubscription.remainingEvents === 0 ? (
              <span className="sub-status-pill status-consumed">
                Đã Hết Hạn Mức Gói
              </span>
            ) : currentSubscription && currentSubscription.subscriptionId && currentSubscription.isExpired ? (
              <span className="sub-status-pill status-expired">
                Gói Đã Hết Hạn
              </span>
            ) : (
              <span className="sub-status-pill status-none">
                Chưa có gói ghép đôi (Chế độ mời miễn phí)
              </span>
            )}
          </div>
        </div>

        <div className="sub-metrics-row">
          <div className="sub-metric-item">
            <span className="sub-metric-label">Số sự kiện còn lại</span>
            <span className={`sub-metric-val ${isCurrentActive ? 'val-highlight' : ''}`}>
              {currentSubscription?.remainingEvents ?? 0} <small style={{ fontSize: '0.9rem', color: '#94a3b8' }}>sự kiện</small>
            </span>
            <span className="sub-metric-hint">
              {isCurrentActive ? 'Đủ điều kiện xuất bản sự kiện ghép đôi tự động' : 'Chế độ mời trực tiếp miễn phí không giới hạn. Mua gói để bật ghép đôi tự động.'}
            </span>
          </div>

          <div className="sub-metric-item">
            <span className="sub-metric-label">Chế độ tuyển dụng</span>
            <span className="sub-metric-val" style={{ fontSize: '1.15rem' }}>
              {isCurrentActive
                ? (currentSubscription?.planType === 'ENTERPRISE'
                  ? 'Ghép Đôi Ưu Tiên + Cứu Hộ'
                  : currentSubscription?.planType === 'MATCHING_PLUS'
                  ? 'Ghép Đôi Tiêu Chuẩn'
                  : currentSubscription?.planType === 'FREE_TRIAL'
                  ? 'Ghép Đôi Dùng Thử'
                  : 'Mời Trực Tiếp')
                : 'Mời Trực Tiếp (Miễn Phí)'}
            </span>
            <span className="sub-metric-hint">
              {isCurrentActive
                ? 'Tự động kết nối nhân sự theo bán kính vị trí'
                : 'Tìm kiếm và gửi lời mời không giới hạn'}
            </span>
          </div>

          <div className="sub-metric-item">
            <span className="sub-metric-label">Hạn sử dụng</span>
            <span className="sub-metric-val" style={{ fontSize: '1.15rem', color: '#e2e8f0' }}>
              {currentSubscription?.expiresAt || currentSubscription?.endDate
                ? formatDate(currentSubscription.expiresAt || currentSubscription.endDate)
                : 'Theo số lượng sự kiện'}
            </span>
            <span className="sub-metric-hint">
              Mã gói: {currentSubscription?.subscriptionId ? currentSubscription.subscriptionId.substring(0, 8) : 'N/A'}
            </span>
          </div>
        </div>
      </div>

      {/* 2. SUB-NAVIGATION TABS */}
      <div className="sub-tabs-bar">
        <button
          type="button"
          className={`sub-tab-btn ${activeSubTab === 'plans' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('plans')}
        >
          Danh Mục Gói Dịch Vụ
        </button>
        <button
          type="button"
          className={`sub-tab-btn ${activeSubTab === 'history' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('history')}
        >
          Lịch Sử Giao Dịch ({history.length})
        </button>
      </div>

      {/* 3. SUB-TAB: PLANS & PURCHASE */}
      {activeSubTab === 'plans' && (
        <>
          {loading ? (
            <div style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>
              Đang tải danh mục gói cước từ máy chủ...
            </div>
          ) : (
            <div className="sub-pricing-grid">
              {plans.map((plan) => {
                const isMatching = plan.planType === 'MATCHING_PLUS';
                const isFree = plan.price === 0;

                return (
                  <div
                    key={plan.planType}
                    className={`pricing-card ${isMatching ? 'featured' : ''}`}
                  >
                    {isMatching && <div className="pricing-badge">Khuyên Dùng</div>}
                    {isFree && <div className="pricing-badge" style={{ background: '#10b981' }}>Miễn Phí</div>}

                    <div className="pricing-header">
                      <h3 className="pricing-title">{plan.name}</h3>
                      <p className="pricing-desc">{plan.description}</p>
                    </div>

                    <div className="pricing-cost">
                      <span className="cost-amount">{formatCurrency(plan.price)}</span>
                      <span className="cost-currency">VNĐ</span>
                      <span className="cost-period">/ {plan.maxEvents ? `${plan.maxEvents} sự kiện` : (plan.eventQuota ? `${plan.eventQuota} sự kiện` : 'gói')}</span>
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
                      {isFree ? (
                        currentSubscription?.planType === 'FREE_TRIAL' ? (
                          <div
                            style={{
                              textAlign: 'center',
                              padding: '0.75rem 1rem',
                              background: '#f8fafc',
                              borderRadius: '10px',
                              border: '1px dashed #cbd5e1',
                              color: '#64748b',
                              fontSize: '0.875rem',
                              fontWeight: 600,
                              lineHeight: 1.4,
                            }}
                          >
                            {currentSubscription.remainingEvents > 0
                              ? 'Đang kích hoạt gói dùng thử'
                              : 'Đã sử dụng hết lượt trải nghiệm'}
                          </div>
                        ) : currentSubscription?.subscriptionId ? (
                          <div
                            style={{
                              textAlign: 'center',
                              padding: '0.75rem 1rem',
                              background: '#f8fafc',
                              borderRadius: '10px',
                              border: '1px dashed #cbd5e1',
                              color: '#64748b',
                              fontSize: '0.875rem',
                              fontWeight: 600,
                            }}
                          >
                            Đang sử dụng gói trả phí
                          </div>
                        ) : (
                          <button
                            type="button"
                            className="btn-purchase-vnpay"
                            style={{ background: '#10b981', width: '100%' }}
                            disabled={purchasingPlan !== null}
                            onClick={() => handlePurchasePayOS(plan.planType)}
                          >
                            {purchasingPlan === plan.planType
                              ? 'Đang kích hoạt...'
                              : 'Kích Hoạt Trải Nghiệm (0 VNĐ)'}
                          </button>
                        )
                      ) : (
                        <button
                          type="button"
                          className="btn-purchase-vnpay"
                          style={{ background: '#2563eb', width: '100%' }}
                          disabled={purchasingPlan !== null}
                          onClick={() => handlePurchasePayOS(plan.planType)}
                        >
                          {purchasingPlan === plan.planType
                            ? 'Đang chuyển sang PayOS...'
                            : 'Thanh toán qua PayOS VietQR'}
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </>
      )}

      {/* 4. SUB-TAB: TRANSACTION HISTORY */}
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
                  <th>Số Sự Kiện</th>
                  <th>Thời Gian Mua</th>
                  <th>Trạng Thái</th>
                </tr>
              </thead>
              <tbody>
                {history.map((item, idx) => (
                  <tr key={item.subscriptionId || idx}>
                    <td style={{ fontFamily: 'monospace', fontWeight: 600 }}>
                      {item.transactionRef || (item.subscriptionId ? item.subscriptionId.substring(0, 8) : 'GÓI-CANI')}
                    </td>
                    <td>
                      <strong>{item.planName}</strong>
                      <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                        {item.planType}
                      </div>
                    </td>
                    <td style={{ fontWeight: 700, color: '#0f172a' }}>
                      {formatCurrency(item.price ?? item.amountPaid ?? 0)} đ
                    </td>
                    <td>
                      <span>PayOS VietQR</span>
                    </td>
                    <td>
                      <span style={{ fontWeight: 600, color: item.remainingEvents > 0 ? '#059669' : '#64748b' }}>
                        {item.remainingEvents} / {item.maxEvents || item.eventsQuota || 1} còn lại
                      </span>
                    </td>
                    <td style={{ fontSize: '0.85rem', color: '#64748b' }}>
                      {formatDate(item.createdAt || item.startsAt || item.startDate)}
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
