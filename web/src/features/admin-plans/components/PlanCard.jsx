import React from 'react';
import { CORE_PLAN_FEATURES } from '../types/adminPlans.constants';

export default function PlanCard({ plan, onEdit, onDelete, isDeleting }) {
  const formatCurrency = (val) => {
    return new Intl.NumberFormat('vi-VN').format(val || 0);
  };

  const parseFeaturesList = (featuresJson) => {
    if (!featuresJson) return [];
    if (Array.isArray(featuresJson)) return featuresJson;
    try {
      const parsed = typeof featuresJson === 'string' ? JSON.parse(featuresJson) : featuresJson;
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  };

  const features = parseFeaturesList(plan.features);
  const isFree = Number(plan.price) === 0;

  const getPlanTypeBadge = (type) => {
    switch (type) {
      case 'ENTERPRISE':
        return { label: 'Doanh Nghiệp (Pro)', cls: 'badge-enterprise' };
      case 'FREE_TRIAL':
        return { label: 'Dùng Thử (Trial)', cls: 'badge-trial' };
      case 'INVITE_ONLY':
        return { label: 'Mời Trực Tiếp', cls: 'badge-invite' };
      default:
        return { label: 'Tiêu Chuẩn (Standard)', cls: 'badge-matching' };
    }
  };

  const badgeInfo = getPlanTypeBadge(plan.planType);

  return (
    <div className={`admin-plan-card ${plan.planType === 'ENTERPRISE' ? 'card-pro' : ''}`}>
      <div className="plan-card-header">
        <div className="plan-header-top">
          <span className={`plan-type-pill ${badgeInfo.cls}`}>{badgeInfo.label}</span>
          <span className="plan-id-code">{plan.planType}</span>
        </div>
        <h3 className="plan-name">{plan.planName}</h3>
        <div className="plan-price-row">
          {isFree ? (
            <span className="plan-price free-text">0 đ (Miễn phí)</span>
          ) : (
            <>
              <span className="plan-price">{formatCurrency(plan.price)}</span>
              <span className="plan-currency">đ</span>
            </>
          )}
          <span className="plan-period">/ {plan.durationDays || 30} ngày</span>
        </div>
      </div>

      <div className="plan-quotas-box">
        <div className="quota-row">
          <span className="quota-label">Hạn mức sự kiện:</span>
          <strong className="quota-val">{plan.maxEvents} sự kiện</strong>
        </div>
        <div className="quota-row">
          <span className="quota-label">Hạn mức ca làm:</span>
          <strong className="quota-val">{plan.maxShifts || 0} ca</strong>
        </div>
        <div className="quota-row">
          <span className="quota-label">Hạn mức nhân sự:</span>
          <strong className="quota-val">{plan.maxWorkers || 0} người</strong>
        </div>
        <div className="quota-row">
          <span className="quota-label">Matching Radar:</span>
          <strong className="quota-val highlight-blue">
            {plan.baseMatchingQuota || plan.maxEvents} lượt
          </strong>
        </div>
      </div>

      <div className="plan-features-box">
        <div className="features-title">Tính năng gói dịch vụ:</div>
        <ul className="features-list">
          {features.map((feat, idx) => {
            const isCore = CORE_PLAN_FEATURES.includes(feat);
            return (
              <li key={idx} className={isCore ? 'feat-core' : 'feat-extended'}>
                <span className="feat-bullet" />
                <span>{feat}</span>
              </li>
            );
          })}
        </ul>
      </div>

      <div className="plan-card-actions">
        <button
          type="button"
          className="btn-plan-edit"
          onClick={() => onEdit(plan)}
        >
          Sửa gói / Đổi giá
        </button>
        <button
          type="button"
          className="btn-plan-delete"
          onClick={() => onDelete(plan)}
          disabled={isDeleting}
          title="Xóa gói nếu chưa có Ban tổ chức nào sử dụng"
        >
          Xóa
        </button>
      </div>
    </div>
  );
}
