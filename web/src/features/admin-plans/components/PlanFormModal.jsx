import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { CORE_PLAN_FEATURES, PLAN_TYPE_OPTIONS, DEFAULT_PLAN_FORM } from '../types/adminPlans.constants';
import CoreFeaturesChecklist from './CoreFeaturesChecklist';

export default function PlanFormModal({ isOpen, isCreating, plan, onClose, onSave, saving }) {
  const [formData, setFormData] = useState(DEFAULT_PLAN_FORM);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isOpen) return;
    if (plan && !isCreating) {
      let existingFeats = [];
      try {
        existingFeats = typeof plan.features === 'string' ? JSON.parse(plan.features) : (plan.features || []);
      } catch {
        existingFeats = [];
      }
      const activeCore = CORE_PLAN_FEATURES.filter((f) => existingFeats.includes(f));
      const extended = existingFeats.filter((f) => !CORE_PLAN_FEATURES.includes(f));

      setFormData({
        planName: plan.planName || '',
        planType: plan.planType || 'MATCHING_PLUS',
        price: plan.price ?? 99000,
        durationDays: plan.durationDays || 30,
        maxEvents: plan.maxEvents || 5,
        maxShifts: plan.maxShifts || 25,
        maxWorkers: plan.maxWorkers || 80,
        baseMatchingQuota: plan.baseMatchingQuota || plan.maxEvents || 5,
        selectedCoreFeatures: activeCore.length > 0 ? activeCore : [...CORE_PLAN_FEATURES],
        extendedFeaturesText: extended.join('\n'),
      });
    } else {
      setFormData(DEFAULT_PLAN_FORM);
    }
    setError('');
  }, [isOpen, isCreating, plan]);

  if (!isOpen) return null;

  const handleToggleCoreFeature = (feat) => {
    setFormData((prev) => {
      const exists = prev.selectedCoreFeatures.includes(feat);
      return {
        ...prev,
        selectedCoreFeatures: exists
          ? prev.selectedCoreFeatures.filter((f) => f !== feat)
          : [...prev.selectedCoreFeatures, feat],
      };
    });
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.planName.trim()) return setError('Vui lòng nhập tên gói cước');
    if (formData.price < 0) return setError('Đơn giá không được âm');
    if (formData.durationDays < 1) return setError('Thời hạn sử dụng tối thiểu 1 ngày');
    if (formData.maxEvents < 1) return setError('Hạn mức sự kiện tối thiểu 1 sự kiện');

    const extendedList = formData.extendedFeaturesText.split('\n').map((l) => l.trim()).filter(Boolean);
    onSave({
      planName: formData.planName.trim(),
      planType: formData.planType,
      price: Number(formData.price),
      durationDays: Number(formData.durationDays),
      maxEvents: Number(formData.maxEvents),
      maxShifts: Number(formData.maxShifts || 0),
      maxWorkers: Number(formData.maxWorkers || 0),
      baseMatchingQuota: Number(formData.baseMatchingQuota || formData.maxEvents),
      features: [...formData.selectedCoreFeatures, ...extendedList],
    });
  };

  const modalNode = (
    <div className="plan-modal-overlay" onClick={onClose}>
      <div className="plan-modal-box animate-modal-pop" onClick={(e) => e.stopPropagation()}>
        <div className="plan-modal-header">
          <div>
            <h3 className="modal-title">{isCreating ? 'Tạo Gói Dịch Vụ Mới' : 'Cập Nhật Gói Cước & Đơn Giá'}</h3>
            <p className="modal-subtitle">Thiết lập đơn giá và hạn mức tài nguyên cho Ban tổ chức sự kiện</p>
          </div>
          <button type="button" className="btn-modal-close" onClick={onClose} disabled={saving}>
            Đóng
          </button>
        </div>

        <form onSubmit={handleSubmit} className="plan-modal-form">
          {error && <div className="modal-error-alert">{error}</div>}

          <div className="form-grid-2">
            <div className="form-group">
              <label className="form-label">Tên gói cước <span className="req">*</span></label>
              <input
                type="text"
                className="form-input"
                placeholder="VD: Gói Tiêu Chuẩn (Standard)"
                value={formData.planName}
                onChange={(e) => setFormData({ ...formData, planName: e.target.value })}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label">Phân loại gói <span className="req">*</span></label>
              <select
                className="form-select"
                value={formData.planType}
                onChange={(e) => setFormData({ ...formData, planType: e.target.value })}
              >
                {PLAN_TYPE_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>{opt.label} ({opt.value})</option>
                ))}
              </select>
            </div>
          </div>

          <div className="form-grid-2">
            <div className="form-group">
              <label className="form-label">Đơn giá niêm yết (VNĐ) <span className="req">*</span></label>
              <input
                type="number"
                min="0"
                step="1000"
                className="form-input"
                value={formData.price}
                onChange={(e) => setFormData({ ...formData, price: e.target.value })}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label">Thời hạn sử dụng (ngày) <span className="req">*</span></label>
              <input
                type="number"
                min="1"
                className="form-input"
                value={formData.durationDays}
                onChange={(e) => setFormData({ ...formData, durationDays: e.target.value })}
                required
              />
            </div>
          </div>

          <div className="form-grid-4">
            <div className="form-group">
              <label className="form-label">Max Sự Kiện <span className="req">*</span></label>
              <input
                type="number"
                min="1"
                className="form-input"
                value={formData.maxEvents}
                onChange={(e) => setFormData({ ...formData, maxEvents: e.target.value })}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label">Max Ca Làm</label>
              <input
                type="number"
                min="0"
                className="form-input"
                value={formData.maxShifts}
                onChange={(e) => setFormData({ ...formData, maxShifts: e.target.value })}
              />
            </div>
            <div className="form-group">
              <label className="form-label">Max Nhân Sự</label>
              <input
                type="number"
                min="0"
                className="form-input"
                value={formData.maxWorkers}
                onChange={(e) => setFormData({ ...formData, maxWorkers: e.target.value })}
              />
            </div>
            <div className="form-group">
              <label className="form-label">Lượt Matching</label>
              <input
                type="number"
                min="0"
                className="form-input"
                value={formData.baseMatchingQuota}
                onChange={(e) => setFormData({ ...formData, baseMatchingQuota: e.target.value })}
              />
            </div>
          </div>

          <div className="form-section-title">4 Tính năng Cốt Lõi (Kích hoạt tiêu Quota gói):</div>
          <CoreFeaturesChecklist
            selectedFeatures={formData.selectedCoreFeatures}
            onToggleFeature={handleToggleCoreFeature}
          />

          <div className="form-group" style={{ marginTop: '0.75rem' }}>
            <label className="form-label">Tính năng mở rộng / Bổ sung (Mỗi dòng 1 tính năng):</label>
            <textarea
              rows={2}
              className="form-textarea"
              placeholder="VD: Mở khóa Gọi Nhân Sự Khẩn Cấp (Emergency Dispatch)&#10;Kênh CSKH riêng 24/7"
              value={formData.extendedFeaturesText}
              onChange={(e) => setFormData({ ...formData, extendedFeaturesText: e.target.value })}
            />
          </div>

          <div className="plan-modal-actions">
            <button type="button" className="btn-modal-cancel" onClick={onClose} disabled={saving}>Hủy Bỏ</button>
            <button type="submit" className="btn-modal-submit" disabled={saving}>
              {saving ? 'Đang lưu CSDL...' : isCreating ? 'Tạo Gói Dịch Vụ' : 'Lưu Thay Đổi'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );

  return typeof document !== 'undefined' ? createPortal(modalNode, document.body) : modalNode;
}
