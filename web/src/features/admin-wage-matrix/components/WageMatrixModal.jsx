import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import {
  CITY_OPTIONS,
  TIME_SLOT_OPTIONS,
  LEAD_TIME_OPTIONS,
  COMMON_JOB_TYPES,
} from '../types/wageMatrix.constants';

export const WageMatrixModal = ({
  isOpen,
  mode = 'create',
  initialData = null,
  onClose,
  onSubmit,
  submitting,
}) => {
  const [formData, setFormData] = useState({
    city: 'TP_HCM',
    jobType: '',
    timeSlot: 'DAYTIME_WEEKDAY',
    leadTime: 'STANDARD',
    wagePerHour: '',
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
        city: initialData.city || 'TP_HCM',
        jobType: initialData.jobType || '',
        timeSlot: initialData.timeSlot || 'DAYTIME_WEEKDAY',
        leadTime: initialData.leadTime || 'STANDARD',
        wagePerHour: initialData.wagePerHour ? String(initialData.wagePerHour) : '',
        isActive: initialData.isActive !== false,
      });
    } else {
      setFormData({
        city: 'TP_HCM',
        jobType: '',
        timeSlot: 'DAYTIME_WEEKDAY',
        leadTime: 'STANDARD',
        wagePerHour: '',
        isActive: true,
      });
    }
    setError('');
  }, [initialData, mode, isOpen]);

  if (!isOpen || typeof document === 'undefined') return null;

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.jobType.trim()) {
      setError('Vui lòng nhập vị trí công việc.');
      return;
    }
    const wageNum = parseFloat(formData.wagePerHour);
    if (isNaN(wageNum) || wageNum <= 0) {
      setError('Mức thù lao mỗi giờ phải lớn hơn 0 VNĐ.');
      return;
    }
    setError('');
    onSubmit({
      ...formData,
      jobType: formData.jobType.trim(),
      wagePerHour: wageNum,
    });
  };

  const modalNode = (
    <div className="wage-modal-backdrop" onClick={onClose} role="dialog" aria-modal="true">
      <div className="wage-modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="wage-modal-header">
          <h2 className="wage-modal-title">
            {mode === 'create' ? 'Thêm Mới Mức Thù Lao Chuẩn' : 'Chỉnh Sửa Mức Thù Lao'}
          </h2>
          <button type="button" className="btn-modal-close" onClick={onClose}>✕</button>
        </div>

        <form onSubmit={handleSubmit} className="wage-modal-form">
          {error && <div className="wage-modal-error">{error}</div>}

          <div className="modal-form-grid">
            <div className="modal-field">
              <label>Khu Vực / Thành Phố <span className="req">*</span></label>
              <select name="city" value={formData.city} onChange={handleChange} required>
                {CITY_OPTIONS.map((c) => (
                  <option key={c.value} value={c.value}>{c.label}</option>
                ))}
              </select>
            </div>

            <div className="modal-field full-width">
              <label>Vị Trí Công Việc <span className="req">*</span></label>
              <input
                type="text"
                name="jobType"
                placeholder="Nhập hoặc chọn gợi ý bên dưới"
                value={formData.jobType}
                onChange={handleChange}
                required
              />
              <div className="job-type-suggestions">
                <span className="suggestion-label">Gợi ý nhanh:</span>
                {COMMON_JOB_TYPES.slice(0, 5).map((job) => (
                  <button
                    key={job}
                    type="button"
                    className="btn-job-chip"
                    onClick={() => setFormData((p) => ({ ...p, jobType: job }))}
                  >
                    {job}
                  </button>
                ))}
              </div>
            </div>

            <div className="modal-field">
              <label>Khung Giờ Áp Dụng <span className="req">*</span></label>
              <select name="timeSlot" value={formData.timeSlot} onChange={handleChange} required>
                {TIME_SLOT_OPTIONS.map((s) => (
                  <option key={s.value} value={s.value}>{s.label}</option>
                ))}
              </select>
            </div>

            <div className="modal-field">
              <label>Độ Khẩn Cấp (Lead Time) <span className="req">*</span></label>
              <select name="leadTime" value={formData.leadTime} onChange={handleChange} required>
                {LEAD_TIME_OPTIONS.map((l) => (
                  <option key={l.value} value={l.value}>{l.label}</option>
                ))}
              </select>
            </div>

            <div className="modal-field">
              <label>Mức Thù Lao (VNĐ / giờ) <span className="req">*</span></label>
              <input
                type="number"
                name="wagePerHour"
                step="1000"
                min="1000"
                placeholder="VD: 45000"
                value={formData.wagePerHour}
                onChange={handleChange}
                required
              />
              <span className="field-hint">
                {formData.wagePerHour ? `≈ ${Number(formData.wagePerHour).toLocaleString('vi-VN')} đ/giờ` : 'Nhập đơn giá VNĐ'}
              </span>
            </div>

            <div className="modal-field checkbox-field">
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
