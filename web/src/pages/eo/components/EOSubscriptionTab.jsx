import React from 'react';
import { useSubscription } from '../hooks/useSubscription';
import PayOSQrModal from './PayOSQrModal';
import './EOSubscriptionTab.css';

const formatCurrency = (val) => new Intl.NumberFormat('vi-VN').format(val || 0);

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

function getRecruitmentModeLabel(isActive, planType) {
  if (!isActive) return 'Mời Trực Tiếp';
  switch (planType) {
    case 'ENTERPRISE':
      return 'Ghép Đôi Ưu Tiên + Cứu Hộ';
    case 'MATCHING_PLUS':
      return 'Ghép Đôi Tiêu Chuẩn';
    case 'FREE_TRIAL':
      return 'Ghép Đôi Dùng Thử';
    default:
      return 'Mời Trực Tiếp';
  }
}

function getHeroStatusMeta(currentSubscription, isCurrentActive) {
  if (isCurrentActive) {
    return {
      variant: 'status-active',
      label: `Đang hoạt động: ${currentSubscription.planName}`,
    };
  }
  if (currentSubscription?.subscriptionId && currentSubscription.remainingEvents === 0) {
    return {
      variant: 'status-consumed',
      label: 'Đã hết hạn mức sự kiện',
    };
  }
  if (currentSubscription?.subscriptionId && currentSubscription.isExpired) {
    return {
      variant: 'status-expired',
      label: 'Gói dịch vụ đã hết hạn',
    };
  }
  return {
    variant: 'status-none',
    label: 'Chưa có gói dịch vụ',
  };
}

function getPlanTierMeta(plan) {
  if (plan.planType === 'MATCHING_PLUS') {
    return {
      cardClass: 'tier-featured',
      badgeText: 'Khuyên dùng',
      badgeClass: 'badge-featured',
    };
  }
  if (plan.planType === 'ENTERPRISE') {
    return {
      cardClass: 'tier-enterprise',
      badgeText: 'Quy mô lớn',
      badgeClass: 'badge-enterprise',
    };
  }
  if (plan.price === 0 || plan.planType === 'FREE_TRIAL') {
    return {
      cardClass: 'tier-free',
      badgeText: 'Miễn phí',
      badgeClass: 'badge-free',
    };
  }
  return {
    cardClass: '',
    badgeText: null,
    badgeClass: '',
  };
}

function CalendarIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="4" width="18" height="18" rx="2.5" />
      <line x1="16" y1="2" x2="16" y2="6" />
      <line x1="8" y1="2" x2="8" y2="6" />
      <line x1="3" y1="10" x2="21" y2="10" />
      <path d="M9 16l2 2 4-4" />
    </svg>
  );
}

function LayersIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <polygon points="12 2 2 7 12 12 22 7 12 2" />
      <polyline points="2 12 12 17 22 12" />
      <polyline points="2 17 12 22 22 17" />
    </svg>
  );
}

function QuotaIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
      <path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <polyline points="20 6 9 17 4 12" />
    </svg>
  );
}

function TicketEmptyIcon() {
  return (
    <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M2 9a3 3 0 0 1 0 6v2a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-2a3 3 0 0 1 0-6V7a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2Z" />
      <path d="M13 5v2" />
      <path d="M13 17v2" />
      <path d="M13 11v2" />
    </svg>
  );
}

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

  const isCurrentActive = Boolean(
    currentSubscription?.subscriptionId &&
      currentSubscription.status === 'ACTIVE' &&
      currentSubscription.remainingEvents > 0
  );

  const statusMeta = getHeroStatusMeta(currentSubscription, isCurrentActive);
  const recruitmentLabel = getRecruitmentModeLabel(isCurrentActive, currentSubscription?.planType);
  const expiryValue =
    currentSubscription?.expiresAt || currentSubscription?.endDate
      ? formatDate(currentSubscription.expiresAt || currentSubscription.endDate)
      : 'Theo số lượng sự kiện';

  const activeHistoryCount = history.filter((item) => item.status === 'ACTIVE').length;
  const totalPaidAmount = history.reduce(
    (sum, item) => sum + Number(item.price ?? item.amountPaid ?? 0),
    0
  );

  return (
    <div className="eo-subscription-container">
      {/* 1. HERO BANNER (Dark stage + 3D VIP pass artwork) */}
      <section className="sub-hero-banner" aria-label="Thông tin gói dịch vụ sự kiện">
        <div className="sub-hero-overlay" />

        <div className="sub-hero-content">
          <div className="sub-hero-top">
            <div className="sub-hero-heading">
              <span className="sub-hero-eyebrow">GÓI DỊCH VỤ SỰ KIỆN</span>
              <h2>
                {isCurrentActive
                  ? `Đang sử dụng ${currentSubscription.planName}`
                  : 'Chọn gói phù hợp cho tổ chức của bạn'}
              </h2>
              <p className="sub-hero-subtitle">
                {isCurrentActive
                  ? 'Tài khoản đã kích hoạt ghép đôi nhân sự tự động theo bán kính vị trí và quản lý ca làm hiệu quả trên CANi.'
                  : 'Quản lý sự kiện, tuyển dụng nhân sự và điều phối ca làm hiệu quả trên CANi.'}
              </p>
            </div>

            <div className="sub-hero-status-wrap">
              <span className={`sub-hero-status-pill ${statusMeta.variant}`}>
                {isCurrentActive && <span className="status-pulse-dot" />}
                {statusMeta.label}
              </span>
            </div>
          </div>

          <div className="sub-hero-metrics">
            <div className="sub-hero-metric">
              <div className="sub-hero-metric-icon">
                <CalendarIcon />
              </div>
              <div className="sub-hero-metric-text">
                <span className={`sub-hero-metric-value ${isCurrentActive ? 'is-active' : ''}`}>
                  {currentSubscription?.remainingEvents ?? 0}
                </span>
                <span className="sub-hero-metric-label">Sự kiện còn lại</span>
              </div>
            </div>

            <div className="sub-hero-metric">
              <div className="sub-hero-metric-icon">
                <LayersIcon />
              </div>
              <div className="sub-hero-metric-text">
                <span className="sub-hero-metric-value">{recruitmentLabel}</span>
                <span className="sub-hero-metric-label">Chế độ tuyển dụng</span>
              </div>
            </div>

            <div className="sub-hero-metric">
              <div className="sub-hero-metric-icon">
                <QuotaIcon />
              </div>
              <div className="sub-hero-metric-text">
                <span className="sub-hero-metric-value">{expiryValue}</span>
                <span className="sub-hero-metric-label">
                  {currentSubscription?.subscriptionId
                    ? `Giới hạn sử dụng • Mã ${currentSubscription.subscriptionId.substring(0, 8)}`
                    : 'Giới hạn sử dụng'}
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* 2. SUB-NAVIGATION TABS */}
      <div className="sub-tabs-bar" role="tablist" aria-label="Điều hướng gói dịch vụ">
        <button
          type="button"
          role="tab"
          aria-selected={activeSubTab === 'plans'}
          className={`sub-tab-btn ${activeSubTab === 'plans' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('plans')}
        >
          Danh Mục Gói Dịch Vụ
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeSubTab === 'history'}
          className={`sub-tab-btn ${activeSubTab === 'history' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('history')}
        >
          <span>Lịch Sử Giao Dịch</span>
          <span className="sub-tab-count">{history.length}</span>
        </button>
      </div>

      {/* 3. SUB-TAB: PLANS & PURCHASE */}
      {activeSubTab === 'plans' && (
        <section className="sub-plans-section" aria-label="Danh sách gói dịch vụ">
          {loading ? (
            <div className="sub-loading-state">
              <div className="sub-loading-spinner" />
              <span>Đang tải danh mục gói cước từ máy chủ...</span>
            </div>
          ) : (
            <div className="sub-pricing-grid">
              {plans.map((plan) => {
                const tier = getPlanTierMeta(plan);
                const isFree = plan.price === 0 || plan.planType === 'FREE_TRIAL';
                const isActiveThisPlan =
                  isCurrentActive && currentSubscription?.planType === plan.planType;
                const maxEvents = plan.maxEvents || plan.eventQuota || 1;
                const pricePerEvent =
                  !isFree && maxEvents > 1 ? Math.round(Number(plan.price) / maxEvents) : null;

                return (
                  <article
                    key={plan.planType}
                    className={`pricing-card ${tier.cardClass} ${isActiveThisPlan ? 'is-current-plan' : ''}`}
                  >
                    <div className="pricing-top-badges">
                      {tier.badgeText && (
                        <span className={`pricing-badge ${tier.badgeClass}`}>
                          {tier.badgeText}
                        </span>
                      )}
                      {isActiveThisPlan && (
                        <span className="pricing-current-tag">Đang sử dụng</span>
                      )}
                    </div>

                    <div className="pricing-header">
                      <h3 className="pricing-title">{plan.name}</h3>
                      <p className="pricing-desc">{plan.description}</p>
                    </div>

                    <div className="pricing-cost">
                      <div className="pricing-cost-main">
                        <span className="cost-amount">{formatCurrency(plan.price)}</span>
                        <span className="cost-currency">đ</span>
                        <span className="cost-period">/ {maxEvents} sự kiện</span>
                      </div>
                      {pricePerEvent !== null && (
                        <span className="pricing-unit-hint">
                          Chỉ khoảng {formatCurrency(pricePerEvent)} đ / sự kiện
                        </span>
                      )}
                    </div>

                    <div className="pricing-quota-chips">
                      <span className="quota-chip">{maxEvents} sự kiện</span>
                      {plan.maxShifts ? (
                        <span className="quota-chip">{plan.maxShifts} ca làm</span>
                      ) : null}
                      {plan.maxWorkers ? (
                        <span className="quota-chip">{plan.maxWorkers} nhân sự</span>
                      ) : null}
                      {plan.durationDays ? (
                        <span className="quota-chip">{plan.durationDays} ngày</span>
                      ) : null}
                    </div>

                    <ul className="pricing-features">
                      {(plan.features || []).map((feat, idx) => (
                        <li key={idx}>
                          <span className="feat-check-badge">
                            <CheckIcon />
                          </span>
                          <span>{feat}</span>
                        </li>
                      ))}
                    </ul>

                    <div className="pricing-actions">
                      {isFree ? (
                        currentSubscription?.planType === 'FREE_TRIAL' ? (
                          <div className="pricing-disabled-note">
                            {currentSubscription.remainingEvents > 0
                              ? 'Đang kích hoạt gói dùng thử'
                              : 'Đã sử dụng hết lượt trải nghiệm'}
                          </div>
                        ) : currentSubscription?.subscriptionId ? (
                          <div className="pricing-disabled-note">
                            Đang sử dụng gói trả phí
                          </div>
                        ) : (
                          <button
                            type="button"
                            className="sub-cta-btn cta-free"
                            disabled={purchasingPlan !== null}
                            onClick={() => handlePurchasePayOS(plan.planType)}
                          >
                            {purchasingPlan === plan.planType
                              ? 'Đang kích hoạt...'
                              : 'Kích Hoạt Trải Nghiệm (0 đ)'}
                          </button>
                        )
                      ) : (
                        <button
                          type="button"
                          className={`sub-cta-btn ${
                            plan.planType === 'MATCHING_PLUS'
                              ? 'cta-featured'
                              : plan.planType === 'ENTERPRISE'
                              ? 'cta-enterprise'
                              : 'cta-primary'
                          }`}
                          disabled={purchasingPlan !== null}
                          onClick={() => handlePurchasePayOS(plan.planType)}
                        >
                          {purchasingPlan === plan.planType
                            ? 'Đang chuyển sang PayOS...'
                            : isActiveThisPlan
                            ? 'Mua Thêm Lượt Qua PayOS VietQR'
                            : 'Thanh Toán Qua PayOS VietQR'}
                        </button>
                      )}
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </section>
      )}

      {/* 4. SUB-TAB: TRANSACTION HISTORY */}
      {activeSubTab === 'history' && (
        <section className="sub-history-section" aria-label="Lịch sử giao dịch gói dịch vụ">
          {history.length > 0 && (
            <div className="sub-history-summary">
              <div className="history-stat-box">
                <span className="history-stat-label">Tổng giao dịch</span>
                <strong className="history-stat-value">{history.length}</strong>
              </div>
              <div className="history-stat-box">
                <span className="history-stat-label">Gói đang hiệu lực</span>
                <strong className="history-stat-value is-emerald">{activeHistoryCount}</strong>
              </div>
              <div className="history-stat-box">
                <span className="history-stat-label">Tổng chi phí đã thanh toán</span>
                <strong className="history-stat-value">{formatCurrency(totalPaidAmount)} đ</strong>
              </div>
            </div>
          )}

          <div className="sub-history-container">
            {history.length === 0 ? (
              <div className="empty-history">
                <div className="empty-history-icon">
                  <TicketEmptyIcon />
                </div>
                <h3>Chưa có giao dịch mua gói nào</h3>
                <p>Hãy chọn một gói dịch vụ phù hợp để mở khóa ghép đôi nhân sự tự động.</p>
                <button
                  type="button"
                  className="sub-cta-btn cta-featured empty-history-cta"
                  onClick={() => setActiveSubTab('plans')}
                >
                  Khám phá các gói dịch vụ
                </button>
              </div>
            ) : (
              <table className="sub-table">
                <thead>
                  <tr>
                    <th>Mã Giao Dịch</th>
                    <th>Gói Cước</th>
                    <th>Số Tiền</th>
                    <th>Cổng Thanh Toán</th>
                    <th>Số Sự Kiện</th>
                    <th>Thời Gian Mua</th>
                    <th>Trạng Thái</th>
                  </tr>
                </thead>
                <tbody>
                  {history.map((item, idx) => (
                    <tr key={item.subscriptionId || idx}>
                      <td className="txn-ref-cell">
                        {item.transactionRef ||
                          (item.subscriptionId ? item.subscriptionId.substring(0, 8) : 'GÓI-CANI')}
                      </td>
                      <td>
                        <strong className="txn-plan-name">{item.planName}</strong>
                        <div className="txn-plan-type">{item.planType}</div>
                      </td>
                      <td className="txn-amount-cell">
                        {formatCurrency(item.price ?? item.amountPaid ?? 0)} đ
                      </td>
                      <td>
                        <span className="txn-gateway-pill">PayOS VietQR</span>
                      </td>
                      <td>
                        <span
                          className={`txn-quota-text ${
                            item.remainingEvents > 0 ? 'has-quota' : 'no-quota'
                          }`}
                        >
                          {item.remainingEvents} / {item.maxEvents || item.eventsQuota || 1} còn lại
                        </span>
                      </td>
                      <td className="txn-date-cell">
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
        </section>
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
