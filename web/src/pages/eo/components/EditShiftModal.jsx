import React, { useState, useEffect } from 'react';

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
    onSubmit(shift.shiftId, {
      ...formData,
      startTime: new Date(formData.startTime).toISOString(),
      endTime: new Date(formData.endTime).toISOString(),
      requiredWorkers: Number(formData.requiredWorkers),
      hourlyWage: Number(formData.hourlyWage),
      allowance: Number(formData.allowance),
      minReliabilityScore: Number(formData.minReliabilityScore),
    });
  };

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h3 style={{ margin: 0 }}>✏️ Chỉnh Sửa Ca Làm Việc</h3>
          <button type="button" onClick={onClose} style={{ background: 'none', border: 'none', fontSize: '1.2rem', cursor: 'pointer', color: '#64748b' }}>✕</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="eo-form-group">
            <label>Vị Trí Công Việc *</label>
            <select
              className="eo-form-input"
              value={formData.jobType}
              onChange={(e) => setFormData({ ...formData, jobType: e.target.value })}
            >
              {roles.map((role) => (
                <option key={role} value={role}>{role}</option>
              ))}
            </select>
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
              <label>Phụ Cấp (VND)</label>
              <input
                type="number"
                step="5000"
                className="eo-form-input"
                value={formData.allowance}
                onChange={(e) => setFormData({ ...formData, allowance: e.target.value })}
              />
            </div>
          </div>

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

          <div className="eo-form-actions">
            <button type="button" className="eo-btn-cancel" onClick={onClose}>
              Hủy
            </button>
            <button type="submit" className="eo-btn-submit" disabled={submitting}>
              {submitting ? 'Đang lưu...' : 'Lưu Ca Làm Việc'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default EditShiftModal;
