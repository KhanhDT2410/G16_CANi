import React, { useState, useEffect } from 'react';
import { showToast } from '../../../components/common/Toast';
import {
  WAGE_RECOMMENDATIONS_MAP,
  calculateShiftRiskAnalysis,
  normalizeChecklistItems,
  serializeChecklistItems,
} from '../hooks/useShifts';
import ShiftChecklistEditor from './ShiftChecklistEditor';
import ShiftSkillSelector from './ShiftSkillSelector';

/**
 * EditShiftModal
 * UC-18: Chỉnh sửa Ca làm việc (Hỗ trợ tính lương theo giờ / theo ca & Checklist kéo thả + Evidence)
 */
const EditShiftModal = ({
  isOpen,
  onClose,
  shift,
  roles = [],
  onSubmit,
  submitting,
  skillTaxonomy = [],
}) => {
  const defaultRole = roles[0] || 'Lễ tân / Check-in';

  const [wageMode, setWageMode] = useState('HOURLY'); // 'HOURLY' | 'PER_SHIFT'
  const [shiftPackageWage, setShiftPackageWage] = useState(200000);

  const [formData, setFormData] = useState({
    jobType: defaultRole,
    description: '',
    startTime: '',
    endTime: '',
    arrivalTime: '',
    requiredWorkers: 2,
    hourlyWage: 50000,
    allowance: 0,
    minReliabilityScore: 60,
    dressCode: '',
    requiredSkills: [],
    checklistItems: [],
  });

  const getDurationHours = (startStr, endStr) => {
    try {
      const s = new Date(startStr);
      const e = new Date(endStr);
      const diff = (e - s) / (1000 * 60 * 60);
      return diff > 0 ? parseFloat(diff.toFixed(1)) : 4;
    } catch {
      return 4;
    }
  };

  useEffect(() => {
    if (shift) {
      const formatIsoToInput = (iso) => {
        if (!iso) return '';
        try {
          const d = new Date(iso);
          if (isNaN(d.getTime())) return '';
          const year = d.getFullYear();
          const month = String(d.getMonth() + 1).padStart(2, '0');
          const day = String(d.getDate()).padStart(2, '0');
          const hours = String(d.getHours()).padStart(2, '0');
          const minutes = String(d.getMinutes()).padStart(2, '0');
          return `${year}-${month}-${day}T${hours}:${minutes}`;
        } catch {
          return '';
        }
      };

      const sTime = formatIsoToInput(shift.startTime) || '2026-11-20T08:00';
      const eTime = formatIsoToInput(shift.endTime) || '2026-11-20T12:00';
      const hWage = Number(shift.hourlyWage || 50000);
      const dur = getDurationHours(sTime, eTime);

      setWageMode('HOURLY');
      setShiftPackageWage(Math.round(hWage * dur));

      // arrivalTime: hiển thị rỗng nếu bằng giờ bắt đầu ca (mặc định)
      const defaultArrival = shift.arrivalTime && shift.startTime
        ? new Date(shift.arrivalTime).getTime() === new Date(shift.startTime).getTime()
          ? ''
          : formatIsoToInput(shift.arrivalTime)
        : '';

      setFormData({
        jobType: shift.jobType || defaultRole,
        description: shift.description || '',
        startTime: sTime,
        endTime: eTime,
        arrivalTime: defaultArrival,
        requiredWorkers: shift.requiredWorkers || 2,
        hourlyWage: hWage,
        allowance: shift.allowance || 0,
        minReliabilityScore: shift.minReliabilityScore || 60,
        dressCode: shift.dressCode || '',
        requiredSkills: Array.isArray(shift.requiredSkills) ? shift.requiredSkills : [],
        checklistItems: normalizeChecklistItems(shift.checklistItems || shift.checklist),
      });
    }
  }, [shift, defaultRole]);

  if (!isOpen || !shift) return null;

  const durationHours = getDurationHours(formData.startTime, formData.endTime);

  const handleTimeChange = (field, value) => {
    const nextStart = field === 'startTime' ? value : formData.startTime;
    const nextEnd = field === 'endTime' ? value : formData.endTime;
    const nextDur = getDurationHours(nextStart, nextEnd);

    if (wageMode === 'PER_SHIFT') {
      const computedHourly = Math.max(20000, Math.round(Number(shiftPackageWage || 0) / Math.max(0.5, nextDur)));
      setFormData((prev) => ({
        ...prev,
        [field]: value,
        hourlyWage: computedHourly,
      }));
    } else {
      setFormData((prev) => ({
        ...prev,
        [field]: value,
      }));
      setShiftPackageWage(Math.round(Number(formData.hourlyWage || 0) * nextDur));
    }
  };

  const handleSwitchWageMode = (mode) => {
    setWageMode(mode);
    if (mode === 'PER_SHIFT') {
      setShiftPackageWage(Math.round(Number(formData.hourlyWage || 50000) * durationHours));
    }
  };

  const handlePackageWageChange = (val) => {
    const pkg = Math.max(0, parseFloat(val) || 0);
    setShiftPackageWage(pkg);
    const computedHourly = Math.max(0, Math.round(pkg / Math.max(0.5, durationHours)));
    setFormData((prev) => ({
      ...prev,
      hourlyWage: computedHourly,
    }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (Number(formData.allowance) < 0) {
      showToast('Phụ cấp thêm không được là số âm!', 'error');
      return;
    }
    if (Number(formData.hourlyWage) < 20000) {
      showToast('Mức thù lao quy đổi tối thiểu là 20.000đ/giờ!', 'error');
      return;
    }
    const sDate = new Date(formData.startTime);
    const eDate = new Date(formData.endTime);
    if (isNaN(sDate.getTime()) || isNaN(eDate.getTime()) || sDate >= eDate) {
      showToast('Thời gian kết thúc ca phải sau thời gian bắt đầu!', 'error');
      return;
    }
    if (formData.arrivalTime) {
      const aDate = new Date(formData.arrivalTime);
      if (isNaN(aDate.getTime()) || aDate < sDate || aDate > eDate) {
        showToast('Thời gian mở điểm danh phải nằm trong khoảng giờ của ca làm việc!', 'error');
        return;
      }
    }

    onSubmit(shift.shiftId, {
      ...formData,
      startTime: sDate.toISOString(),
      endTime: eDate.toISOString(),
      arrivalTime: formData.arrivalTime ? new Date(formData.arrivalTime).toISOString() : null,
      requiredSkills: Array.isArray(formData.requiredSkills) ? formData.requiredSkills : [],
      dressCode: formData.dressCode || '',
      requiredWorkers: Math.max(1, Number(formData.requiredWorkers) || 1),
      hourlyWage: Math.max(20000, Number(formData.hourlyWage) || 20000),
      allowance: Math.max(0, Number(formData.allowance) || 0),
      minReliabilityScore: Number(formData.minReliabilityScore || 60),
      checklist: serializeChecklistItems(formData.checklistItems),
    });
  };

  const rawRec = WAGE_RECOMMENDATIONS_MAP[formData.jobType] || {
    min: 40000,
    max: 60000,
    defaultWage: 50000,
    recommended: 50000,
  };
  const wageRec = {
    min: Number(rawRec.min || 40000),
    max: Number(rawRec.max || 60000),
    recommended: Number(rawRec.recommended || rawRec.defaultWage || 50000),
  };
  const riskInfo = calculateShiftRiskAnalysis(formData);

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box" style={{ maxWidth: '720px', maxHeight: '90vh', overflowY: 'auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
          <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 700, color: '#1e293b' }}>
            Chỉnh Sửa Ca Làm Việc
          </h3>
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
            Đóng ✕
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
                onChange={(e) => handleTimeChange('startTime', e.target.value)}
              />
            </div>

            <div className="eo-form-group">
              <label>Thời Gian Kết Thúc *</label>
              <input
                type="datetime-local"
                required
                className="eo-form-input"
                value={formData.endTime}
                onChange={(e) => handleTimeChange('endTime', e.target.value)}
              />
            </div>
          </div>

          {/* Giờ mở cửa điểm danh (check-in gate) */}
          <div className="eo-form-group">
            <label>Thời Gian Mở Điểm Danh (Tùy chọn)</label>
            <input
              type="datetime-local"
              className="eo-form-input"
              value={formData.arrivalTime || ''}
              onChange={(e) => setFormData({ ...formData, arrivalTime: e.target.value })}
            />
            <div style={{ fontSize: '0.74rem', color: '#64748b', marginTop: '0.25rem' }}>
              Người lao động chỉ được điểm danh từ thời điểm này. Để trống = mở cùng giờ bắt đầu ca.
            </div>
          </div>

          {/* Kỹ năng bắt buộc (DYNAMIC CUSTOM SKILLS + TAXONOMY) */}
          <ShiftSkillSelector
            selectedSkillIds={formData.requiredSkills}
            onChange={(nextSkills) => setFormData({ ...formData, requiredSkills: nextSkills })}
            taxonomy={skillTaxonomy}
          />

          {/* Tùy chọn cách tính lương: Theo giờ hoặc Theo ca */}
          <div className="eo-form-group" style={{ marginBottom: '0.75rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.45rem', flexWrap: 'wrap', gap: '0.5rem' }}>
              <label style={{ margin: 0 }}>Hình Thức Tính Thù Lao *</label>
              <div style={{ display: 'inline-flex', backgroundColor: '#f1f5f9', padding: '3px', borderRadius: '8px', border: '1px solid #cbd5e1' }}>
                <button
                  type="button"
                  onClick={() => handleSwitchWageMode('HOURLY')}
                  style={{
                    padding: '0.3rem 0.75rem',
                    fontSize: '0.78rem',
                    fontWeight: 700,
                    borderRadius: '6px',
                    border: 'none',
                    cursor: 'pointer',
                    backgroundColor: wageMode === 'HOURLY' ? '#2563eb' : 'transparent',
                    color: wageMode === 'HOURLY' ? '#ffffff' : '#475569',
                  }}
                >
                  Tính theo giờ (VNĐ/giờ)
                </button>
                <button
                  type="button"
                  onClick={() => handleSwitchWageMode('PER_SHIFT')}
                  style={{
                    padding: '0.3rem 0.75rem',
                    fontSize: '0.78rem',
                    fontWeight: 700,
                    borderRadius: '6px',
                    border: 'none',
                    cursor: 'pointer',
                    backgroundColor: wageMode === 'PER_SHIFT' ? '#2563eb' : 'transparent',
                    color: wageMode === 'PER_SHIFT' ? '#ffffff' : '#475569',
                  }}
                >
                  Tính theo ca (Trọn gói/ca)
                </button>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
              <div className="eo-form-group" style={{ marginBottom: 0 }}>
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

              {wageMode === 'HOURLY' ? (
                <div className="eo-form-group" style={{ marginBottom: 0 }}>
                  <label>Thù Lao Theo Giờ (VNĐ/h) *</label>
                  <input
                    type="number"
                    step="5000"
                    min="20000"
                    required
                    className="eo-form-input"
                    value={formData.hourlyWage}
                    onChange={(e) => {
                      const val = Math.max(0, parseFloat(e.target.value) || 0);
                      setFormData({ ...formData, hourlyWage: val });
                      setShiftPackageWage(Math.round(val * durationHours));
                    }}
                  />
                  <div style={{ fontSize: '0.72rem', color: '#2563eb', marginTop: '0.25rem', fontWeight: 600 }}>
                    = {Math.round(Number(formData.hourlyWage || 0) * durationHours).toLocaleString()}đ / ca ({durationHours}h)
                  </div>
                </div>
              ) : (
                <div className="eo-form-group" style={{ marginBottom: 0 }}>
                  <label>Lương Trọn Gói Ca (VNĐ/ca) *</label>
                  <input
                    type="number"
                    step="10000"
                    min="20000"
                    required
                    className="eo-form-input"
                    value={shiftPackageWage}
                    onChange={(e) => handlePackageWageChange(e.target.value)}
                  />
                  <div style={{ fontSize: '0.72rem', color: '#2563eb', marginTop: '0.25rem', fontWeight: 600 }}>
                    ~ {Number(formData.hourlyWage || 0).toLocaleString()}đ/giờ ({durationHours}h)
                  </div>
                </div>
              )}

              <div className="eo-form-group" style={{ marginBottom: 0 }}>
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
          </div>

          <div style={{ marginBottom: '0.85rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <div
              style={{
                fontSize: '0.78rem',
                color: '#1e3a8a',
                backgroundColor: '#eff6ff',
                padding: '0.5rem 0.75rem',
                borderRadius: '0.4rem',
                border: '1px solid #bfdbfe',
              }}
            >
              Gợi ý mức lương: <strong>{((wageRec && wageRec.min) || 40000).toLocaleString()}đ - {((wageRec && wageRec.max) || 60000).toLocaleString()}đ/giờ</strong> (Khuyên dùng:{' '}
              <strong>{((wageRec && wageRec.recommended) || 50000).toLocaleString()}đ/giờ</strong>)
            </div>
            <div
              style={{
                fontSize: '0.78rem',
                padding: '0.55rem 0.75rem',
                borderRadius: '0.4rem',
                backgroundColor:
                  riskInfo.level === 'HIGH' ? '#fef2f2' : riskInfo.level === 'MEDIUM' ? '#fffbeb' : '#f0fdf4',
                border:
                  riskInfo.level === 'HIGH'
                    ? '1px solid #fecaca'
                    : riskInfo.level === 'MEDIUM'
                    ? '1px solid #fde68a'
                    : '1px solid #bbf7d0',
                color: riskInfo.level === 'HIGH' ? '#b91c1c' : riskInfo.level === 'MEDIUM' ? '#b45309' : '#15803d',
                fontWeight: 700,
              }}
            >
              {riskInfo.level === 'HIGH'
                ? 'Khó tìm nhân sự'
                : riskInfo.level === 'MEDIUM'
                ? 'Khá khó tìm nhân sự'
                : 'Dễ tìm nhân sự'}
              {riskInfo.reasons.length > 0 && (
                <ul style={{ margin: '0.35rem 0 0', paddingLeft: '1.1rem', fontWeight: 500, color: '#475569' }}>
                  {riskInfo.reasons.map((r, i) => (
                    <li key={i}>{r}</li>
                  ))}
                </ul>
              )}
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

          {/* Checklist Đầu Việc Cho Ca Này */}
          <ShiftChecklistEditor
            items={formData.checklistItems}
            onChange={(updatedItems) => setFormData((prev) => ({ ...prev, checklistItems: updatedItems }))}
          />

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
