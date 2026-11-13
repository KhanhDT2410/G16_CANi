import React, { useState, useEffect } from 'react';
import { showToast } from '../../../components/common/Toast';
import { WAGE_RECOMMENDATIONS_MAP, calculateShiftRiskAnalysis } from '../hooks/useShifts';

/**
 * EditShiftModal
 * UC-18: Chỉnh sửa Ca làm việc
 */
const EditShiftModal = ({
  isOpen,
  onClose,
  shift,
  roles = [],
  onSubmit,
  submitting,
}) => {
  const defaultRole = roles[0] || 'Lễ tân / Check-in';

  const [formData, setFormData] = useState({
    jobType: defaultRole,
    description: '',
    startTime: '',
    endTime: '',
    requiredWorkers: 2,
    hourlyWage: 50000,
    allowance: 0,
    minReliabilityScore: 60,
    dressCode: '',
  });

  useEffect(() => {
    if (shift) {
      const formatIsoToInput = (iso) => {
        if (!iso) return '';
        try {
          return new Date(iso).toISOString().slice(0, 16);
        } catch {
          return '';
        }
      };

      setFormData({
        jobType: shift.jobType || defaultRole,
        description: shift.description || '',
        startTime: formatIsoToInput(shift.startTime) || '2026-11-20T08:00',
        endTime: formatIsoToInput(shift.endTime) || '2026-11-20T12:00',
        requiredWorkers: shift.requiredWorkers || 2,
        hourlyWage: shift.hourlyWage || 50000,
        allowance: shift.allowance || 0,
        minReliabilityScore: shift.minReliabilityScore || 60,
        dressCode: shift.dressCode || '',
      });
    }
  }, [shift, defaultRole]);

  if (!isOpen || !shift) return null;

  const handleSubmit = (e) => {
    e.preventDefault();
    if (Number(formData.allowance) < 0) {
      showToast('Phụ cấp thêm không được là số âm!', 'error');
      return;
    }
    onSubmit(shift.shiftId, {
      ...formData,
      startTime: new Date(formData.startTime).toISOString(),
      endTime: new Date(formData.endTime).toISOString(),
      requiredWorkers: Math.max(1, Number(formData.requiredWorkers) || 1),
      hourlyWage: Math.max(10000, Number(formData.hourlyWage) || 10000),
      allowance: Math.max(0, Number(formData.allowance) || 0),
      minReliabilityScore: Number(formData.minReliabilityScore),
    });
  };

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
          <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 700, color: '#1e293b' }}>Chỉnh Sửa Ca Làm Việc</h3>
          <button
            type="button"
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              fontSize: '0.9rem',
              cursor: 'pointer',
              color: '#64748b',
              fontWeight: 600,
            }}
          >
            Đóng
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="eo-form-group">
            <label>Vị Trí Công Việc *</label>
            <input
              type="text"
              list="edit-shift-roles-list"
              className="eo-form-input"
              placeholder="Nhập vị trí hoặc chọn gợi ý..."
              value={formData.jobType}
              onChange={(e) => setFormData({ ...formData, jobType: e.target.value })}
              required
            />
            <datalist id="edit-shift-roles-list">
              {roles.map((role) => (
                <option key={role} value={role} />
              ))}
            </datalist>
          </div>

          <div className="eo-form-group">
            <label>Mô Tả Nhiệm Vụ</label>
            <textarea
              rows="2"
              className="eo-form-textarea"
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div className="eo-form-group">
              <label>Thời Gian Bắt Đầu *</label>
              <input
                type="datetime-local"
                required
                className="eo-form-input"
                value={formData.startTime}
                onChange={(e) => setFormData({ ...formData, startTime: e.target.value })}
              />
            </div>

            <div className="eo-form-group">
              <label>Thời Gian Kết Thúc *</label>
              <input
                type="datetime-local"
                required
                className="eo-form-input"
                value={formData.endTime}
                onChange={(e) => setFormData({ ...formData, endTime: e.target.value })}
              />
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
            <div className="eo-form-group">
              <label>Số Nhân Sự Cần Tuyển *</label>
              <input
                type="number"
                min="1"
                required
                className="eo-form-input"
                value={formData.requiredWorkers}
                onChange={(e) => setFormData({ ...formData, requiredWorkers: e.target.value })}
              />
            </div>

            <div className="eo-form-group">
              <label>Lương/Giờ (VND) *</label>
              <input
                type="number"
                step="5000"
                min="20000"
                required
                className="eo-form-input"
                value={formData.hourlyWage}
                onChange={(e) => setFormData({ ...formData, hourlyWage: e.target.value })}
              />
            </div>

            <div className="eo-form-group">
              <label>Phụ Cấp (VNĐ)</label>
              <input
                type="number"
                min="0"
                step="5000"
                className="eo-form-input"
                value={formData.allowance}
                onKeyDown={(e) => {
                  if (e.key === '-' || e.key === 'e' || e.key === 'E') {
                    e.preventDefault();
                  }
                }}
                onChange={(e) => {
                  const raw = parseFloat(e.target.value);
                  setFormData({ ...formData, allowance: isNaN(raw) || raw < 0 ? 0 : raw });
                }}
              />
            </div>
          </div>

          {(() => {
            const wageRec = WAGE_RECOMMENDATIONS_MAP[formData.jobType] || { min: 40000, max: 60000, recommended: 45000 };
            const riskInfo = calculateShiftRiskAnalysis(formData);
            return (
              <div style={{ marginBottom: '0.85rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                <div style={{ fontSize: '0.78rem', color: '#1e3a8a', backgroundColor: '#eff6ff', padding: '0.5rem 0.75rem', borderRadius: '0.4rem', border: '1px solid #bfdbfe' }}>
                  Gợi ý mức lương: <strong>{wageRec.min.toLocaleString()}đ - {wageRec.max.toLocaleString()}đ/giờ</strong> (Khuyên dùng: <strong>{wageRec.recommended.toLocaleString()}đ/giờ</strong>)
                </div>
                <div
                  style={{
                    fontSize: '0.78rem',
                    padding: '0.5rem 0.75rem',
                    borderRadius: '0.4rem',
                    backgroundColor: riskInfo.level === 'HIGH' ? '#fef2f2' : riskInfo.level === 'MEDIUM' ? '#fffbeb' : '#f0fdf4',
                    border: riskInfo.level === 'HIGH' ? '1px solid #fecaca' : riskInfo.level === 'MEDIUM' ? '1px solid #fde68a' : '1px solid #bbf7d0',
                    color: riskInfo.level === 'HIGH' ? '#b91c1c' : riskInfo.level === 'MEDIUM' ? '#b45309' : '#15803d',
                    fontWeight: 600,
                  }}
                >
                  Cảnh báo Risk Score (Nguy cơ khó tìm người): {riskInfo.riskScore}/100 — {riskInfo.label}
                </div>
              </div>
            );
          })()}

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div className="eo-form-group">
              <label>Điểm Uy Tín Tối Thiểu (1-100)</label>
              <input
                type="number"
                min="0"
                max="100"
                className="eo-form-input"
                value={formData.minReliabilityScore}
                onChange={(e) => setFormData({ ...formData, minReliabilityScore: e.target.value })}
              />
            </div>

            <div className="eo-form-group">
              <label>Trang Phục Yêu Cầu (Dress Code)</label>
              <input
                type="text"
                placeholder="Ví dụ: Áo sơ mi trắng, quần tây đen"
                className="eo-form-input"
                value={formData.dressCode}
                onChange={(e) => setFormData({ ...formData, dressCode: e.target.value })}
              />
            </div>
          </div>

          <div className="eo-modal-footer">
            <button type="button" className="eo-btn-secondary" onClick={onClose}>
              Hủy Bỏ
            </button>
            <button type="submit" className="eo-btn-primary" disabled={submitting}>
              {submitting ? 'Đang lưu...' : 'Lưu Ca Làm Việc'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default EditShiftModal;
