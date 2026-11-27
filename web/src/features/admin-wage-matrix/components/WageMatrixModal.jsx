import React, { useState, useEffect, useMemo } from 'react';
import { createPortal } from 'react-dom';
import {
  REGION_OPTIONS,
  JOB_TIER_OPTIONS,
} from '../types/wageMatrix.constants';

export const WageMatrixModal = ({
  isOpen,
  mode = 'create',
  initialData = null,
  regions = [],
  onClose,
  onSubmit,
  submitting,
}) => {
  const defaultTier = JOB_TIER_OPTIONS[0];
  const [formData, setFormData] = useState({
    regionCode: 'REGION_1',
    jobTier: defaultTier.value,
    tierLabel: defaultTier.defaultLabel,
    description: defaultTier.defaultDescription,
    minMultiplier: String(defaultTier.minMultiplier),
    recommendedMultiplier: String(defaultTier.recommendedMultiplier),
    maxMultiplier: String(defaultTier.maxMultiplier),
    overrideBaseWage: '',
    isActive: true,
  });
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = originalOverflow;
    };
  }, [isOpen, onClose]);

  useEffect(() => {
    if (initialData && mode === 'edit') {
      setFormData({
        regionCode: initialData.regionCode || 'REGION_1',
        jobTier: initialData.jobTier || defaultTier.value,
        tierLabel: initialData.tierLabel || '',
        description: initialData.description || '',
        minMultiplier: initialData.minMultiplier != null ? String(initialData.minMultiplier) : '1.00',
        recommendedMultiplier:
          initialData.recommendedMultiplier != null ? String(initialData.recommendedMultiplier) : '1.15',
        maxMultiplier: initialData.maxMultiplier != null ? String(initialData.maxMultiplier) : '1.30',
        overrideBaseWage: initialData.overrideBaseWage != null ? String(initialData.overrideBaseWage) : '',
        isActive: initialData.isActive !== false,
      });
    } else {
      setFormData({
        regionCode: 'REGION_1',
        jobTier: defaultTier.value,
        tierLabel: defaultTier.defaultLabel,
        description: defaultTier.defaultDescription,
        minMultiplier: String(defaultTier.minMultiplier),
        recommendedMultiplier: String(defaultTier.recommendedMultiplier),
        maxMultiplier: String(defaultTier.maxMultiplier),
        overrideBaseWage: '',
        isActive: true,
      });
    }
    setError('');
  }, [initialData, mode, isOpen, defaultTier]);

  const activeRegion = useMemo(() => {
    const found = regions.find((r) => r.code === formData.regionCode);
    if (found) {
      return {
        floor: Number(found.legalFloorHourly || 25500),
        marketMultiplier: Number(found.marketMultiplier || 1),
        name: found.name,
      };
    }
    const opt = REGION_OPTIONS.find((r) => r.value === formData.regionCode) || REGION_OPTIONS[0];
    return { floor: opt.floor, marketMultiplier: 1, name: opt.label };
  }, [regions, formData.regionCode]);

  const preview = useMemo(() => {
    const base = activeRegion.floor * activeRegion.marketMultiplier;
    const minM = parseFloat(formData.minMultiplier) || 1;
    const recM = parseFloat(formData.recommendedMultiplier) || 1;
    const maxM = parseFloat(formData.maxMultiplier) || 1;
    const override = parseFloat(formData.overrideBaseWage);
    return {
      minWage: Math.round((base * minM) / 100) * 100,
      suggestedWage:
        Number.isFinite(override) && override > 0
          ? Math.round(override / 1000) * 1000
          : Math.round((base * recM) / 1000) * 1000,
      maxWage: Math.round((base * maxM) / 100) * 100,
    };
  }, [activeRegion, formData]);

  if (!isOpen || typeof document === 'undefined') return null;

  const handleTierChange = (e) => {
    const nextTierCode = e.target.value;
    const meta = JOB_TIER_OPTIONS.find((t) => t.value === nextTierCode);
    setFormData((prev) => ({
      ...prev,
      jobTier: nextTierCode,
      ...(mode === 'create' && meta
        ? {
            tierLabel: meta.defaultLabel,
            description: meta.defaultDescription,
            minMultiplier: String(meta.minMultiplier),
            recommendedMultiplier: String(meta.recommendedMultiplier),
            maxMultiplier: String(meta.maxMultiplier),
          }
        : {}),
    }));
  };

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.tierLabel.trim()) {
      setError('Vui lòng nhập tên hiển thị nhóm công việc.');
      return;
    }
    const minM = parseFloat(formData.minMultiplier);
    const recM = parseFloat(formData.recommendedMultiplier);
    const maxM = parseFloat(formData.maxMultiplier);
    if (isNaN(minM) || isNaN(recM) || isNaN(maxM) || minM < 1 || minM > recM || recM > maxM) {
      setError('Hệ số phải hợp lệ và thỏa mãn: 1.00 <= tối thiểu <= khuyên dùng <= tối đa.');
      return;
    }

    const rawOverride = String(formData.overrideBaseWage || '').trim();
    const overrideNum = rawOverride ? parseFloat(rawOverride) : null;
    if (overrideNum !== null && (isNaN(overrideNum) || overrideNum < activeRegion.floor)) {
      setError(
        `Đơn giá ghim cố định không được thấp hơn sàn pháp lý của ${activeRegion.name} (${activeRegion.floor.toLocaleString('vi-VN')} đ/giờ).`
      );
      return;
    }

    setError('');
    onSubmit({
      regionCode: formData.regionCode,
      jobTier: formData.jobTier,
      tierLabel: formData.tierLabel.trim(),
      description: formData.description.trim() || null,
      minMultiplier: minM,
      recommendedMultiplier: recM,
      maxMultiplier: maxM,
      overrideBaseWage: overrideNum,
      isActive: formData.isActive,
    });
  };

  const modalNode = (
    <div className="wage-modal-backdrop" onClick={onClose} role="dialog" aria-modal="true">
      <div className="wage-modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="wage-modal-header">
          <h2 className="wage-modal-title">
            {mode === 'create' ? 'Thêm Cấu Hình Region × JobTier' : 'Chỉnh Sửa Cấu Hình Region × JobTier'}
          </h2>
          <button type="button" className="btn-modal-close" onClick={onClose}>✕</button>
        </div>

        <form onSubmit={handleSubmit} className="wage-modal-form">
          {error && <div className="wage-modal-error">{error}</div>}

          <div className="modal-form-grid">
            <div className="modal-field">
              <label>Vùng Lương (NĐ 293/2025) <span className="req">*</span></label>
              <select
                name="regionCode"
                value={formData.regionCode}
                onChange={handleChange}
                disabled={mode === 'edit'}
                required
              >
                {REGION_OPTIONS.map((r) => (
                  <option key={r.value} value={r.value}>{r.label}</option>
                ))}
              </select>
            </div>

            <div className="modal-field">
              <label>Nhóm Công Việc (Job Tier) <span className="req">*</span></label>
              <select
                name="jobTier"
                value={formData.jobTier}
                onChange={handleTierChange}
                disabled={mode === 'edit'}
                required
              >
                {JOB_TIER_OPTIONS.map((t) => (
                  <option key={t.value} value={t.value}>{t.label}</option>
                ))}
              </select>
            </div>

            <div className="modal-field full-width">
              <label>Tên Hiển Thị Nhóm <span className="req">*</span></label>
              <input
                type="text"
                name="tierLabel"
                value={formData.tierLabel}
                onChange={handleChange}
                required
              />
            </div>

            <div className="modal-field full-width">
              <label>Mô Tả Công Việc Đại Diện</label>
              <input
                type="text"
                name="description"
                value={formData.description}
                onChange={handleChange}
                placeholder="VD: Lễ tân, check-in, phục vụ bàn..."
              />
            </div>

            <div className="modal-field">
              <label>Hệ Số Tối Thiểu <span className="req">*</span></label>
              <input
                type="number"
                name="minMultiplier"
                step="0.05"
                min="1"
                value={formData.minMultiplier}
                onChange={handleChange}
                required
              />
            </div>

            <div className="modal-field">
              <label>Hệ Số Khuyên Dùng <span className="req">*</span></label>
              <input
                type="number"
                name="recommendedMultiplier"
                step="0.05"
                min="1"
                value={formData.recommendedMultiplier}
                onChange={handleChange}
                required
              />
            </div>

            <div className="modal-field">
              <label>Hệ Số Tối Đa <span className="req">*</span></label>
              <input
                type="number"
                name="maxMultiplier"
                step="0.05"
                min="1"
                value={formData.maxMultiplier}
                onChange={handleChange}
                required
              />
            </div>

            <div className="modal-field">
              <label>Ghim Giá Cố Định (tùy chọn)</label>
              <input
                type="number"
                name="overrideBaseWage"
                step="1000"
                min={activeRegion.floor}
                placeholder="Để trống nếu tính theo hệ số"
                value={formData.overrideBaseWage}
                onChange={handleChange}
              />
            </div>

            <div
              className="modal-field full-width"
              style={{
                background: '#f8fafc',
                border: '1px solid #e2e8f0',
                borderRadius: '8px',
                padding: '0.65rem 0.85rem',
                fontSize: '0.82rem',
                color: '#334155',
              }}
            >
              <strong>Xem trước đơn giá giờ ban ngày ({activeRegion.name}):</strong>{' '}
              Khoảng {preview.minWage.toLocaleString('vi-VN')}đ – {preview.maxWage.toLocaleString('vi-VN')}đ ·{' '}
              <span style={{ color: '#2563eb', fontWeight: 700 }}>
                Đề xuất: {preview.suggestedWage.toLocaleString('vi-VN')} đ/giờ
              </span>
            </div>

            <div className="modal-field checkbox-field full-width">
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  name="isActive"
                  checked={formData.isActive}
                  onChange={handleChange}
                />
                Áp dụng ngay vào thuật toán gợi ý giá thị trường
              </label>
            </div>
          </div>

          <div className="wage-modal-actions">
            <button type="button" className="btn-modal-cancel" onClick={onClose} disabled={submitting}>
              Hủy Bỏ
            </button>
            <button type="submit" className="btn-modal-submit" disabled={submitting}>
              {submitting ? 'Đang Lưu...' : mode === 'create' ? 'Tạo Mới' : 'Cập Nhật'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );

  return createPortal(modalNode, document.body);
};
