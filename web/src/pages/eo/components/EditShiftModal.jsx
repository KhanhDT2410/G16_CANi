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
import ShiftArrivalTimeField from './ShiftArrivalTimeField';
import { getShiftEditRestrictions, validateShiftArrivalTime, validateShiftTimeRange } from '../utils/eventHelpers';
import { validateJobType, validateDuplicateShift, validateShiftWageAgainstFloor } from '../utils/shiftValidation';

/**
 * EditShiftModal
 * UC-18: Chỉnh sửa Ca làm việc (Hỗ trợ tính lương theo giờ / theo ca & Checklist kéo thả + Evidence)
 */
const EditShiftModal = ({
  isOpen,
  onClose,
  shift,
  event = null,
  roles = [],
  onSubmit,
  submitting,
  skillTaxonomy = [],
  existingShifts = [],
}) => {
  const defaultRole = roles[0] || 'Lễ tân / Check-in';

  const [wageMode, setWageMode] = useState('HOURLY'); // 'HOURLY' | 'PER_SHIFT'
  const [shiftPackageWage, setShiftPackageWage] = useState(200000);

  // Field validation error states (EO-008: Sharp high-contrast validation)
  const [jobTypeError, setJobTypeError] = useState(null);
  const [timeError, setTimeError] = useState(null);
  const [wageError, setWageError] = useState(null);
  const [workersError, setWorkersError] = useState(null);
  const [allowanceError, setAllowanceError] = useState(null);
  const [formError, setFormError] = useState(null);

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
    performedSkillId: null,
    skillRequirements: [],
    customSkillNotes: '',
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
      setJobTypeError(null);
      setTimeError(null);
      setWageError(null);
      setWorkersError(null);
      setAllowanceError(null);
      setFormError(null);

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
        performedSkillId: shift.performedSkillId || null,
        skillRequirements: Array.isArray(shift.skillRequirements) ? shift.skillRequirements : [],
        customSkillNotes: shift.customSkillNotes || '',
        checklistItems: normalizeChecklistItems(shift.checklistItems || shift.checklist),
      });
    }
  }, [shift, defaultRole]);

  if (!isOpen || !shift) return null;

  const editRestrictions = getShiftEditRestrictions(shift, event);
  const { lockTimeAndMoney, minRequiredWorkers, confirmedWorkers } = editRestrictions;
  const durationHours = getDurationHours(formData.startTime, formData.endTime);

  const handleTimeChange = (field, value) => {
    if (lockTimeAndMoney) return;
    const nextStart = field === 'startTime' ? value : formData.startTime;
    const nextEnd = field === 'endTime' ? value : formData.endTime;
    const nextDur = getDurationHours(nextStart, nextEnd);

    const tErr = validateShiftTimeRange(nextStart, nextEnd, shift?.eventStartTime, shift?.eventEndTime, {
      allowPastStart: true,
    });
    setTimeError(tErr);
    if (formError && !tErr) setFormError(null);

    let nextHourly = formData.hourlyWage;
    if (wageMode === 'PER_SHIFT') {
      nextHourly = Math.max(1, Math.round(Number(shiftPackageWage || 0) / Math.max(0.5, nextDur)));
      setFormData((prev) => ({
        ...prev,
        [field]: value,
        hourlyWage: nextHourly,
      }));
    } else {
      setFormData((prev) => ({
        ...prev,
        [field]: value,
      }));
      setShiftPackageWage(Math.round(Number(formData.hourlyWage || 0) * nextDur));
    }

    const wageCheck = validateShiftWageAgainstFloor({
      hourlyWage: nextHourly,
      venueAddress: shift?.locationAddress || '',
      venueLat: shift?.locationLat,
      venueLng: shift?.locationLng,
    });
    setWageError(wageCheck.isValid ? null : wageCheck.error);
  };

  const handleSwitchWageMode = (mode) => {
    if (lockTimeAndMoney) return;
    setWageMode(mode);
    if (mode === 'PER_SHIFT') {
      setShiftPackageWage(Math.round(Number(formData.hourlyWage || 50000) * durationHours));
    }
  };

  const handlePackageWageChange = (val) => {
    if (lockTimeAndMoney) return;
    let raw = typeof val === 'string' ? val.replace(/^0+(?=\d)/, '') : String(val ?? '');
    const pkg = raw === '' ? '' : Math.max(0, parseFloat(raw) || 0);
    setShiftPackageWage(pkg);
    const computedHourly = Math.max(0, Math.round((Number(pkg) || 0) / Math.max(0.5, durationHours)));
    setFormData((prev) => ({
      ...prev,
      hourlyWage: computedHourly,
    }));
    const wageCheck = validateShiftWageAgainstFloor({
      hourlyWage: computedHourly,
      venueAddress: shift?.locationAddress || '',
      venueLat: shift?.locationLat,
      venueLng: shift?.locationLng,
    });
    setWageError(wageCheck.isValid ? null : wageCheck.error);
    if (formError && wageCheck.isValid) setFormError(null);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (submitting) return;

    const jErr =
      validateJobType(formData.jobType) ||
      validateDuplicateShift(formData, existingShifts, { excludeShiftId: shift?.shiftId });
    const tErr = lockTimeAndMoney
      ? null
      : validateShiftTimeRange(formData.startTime, formData.endTime, shift?.eventStartTime, shift?.eventEndTime, {
          allowPastStart: true,
        });
    const aErr = lockTimeAndMoney
      ? null
      : formData.arrivalTime
      ? validateShiftArrivalTime(formData.arrivalTime, formData.startTime)
      : null;
    const wageCheck = validateShiftWageAgainstFloor({
      hourlyWage: lockTimeAndMoney ? Number(shift.hourlyWage || 0) : formData.hourlyWage,
      venueAddress: shift?.locationAddress || '',
      venueLat: shift?.locationLat,
      venueLng: shift?.locationLng,
    });
    const wErr = lockTimeAndMoney ? null : wageCheck.isValid ? null : wageCheck.error;
    const parsedWorkers = Number(formData.requiredWorkers);
    const wkErr = !formData.requiredWorkers || parsedWorkers < 1
      ? 'Số nhân sự cần tuyển tối thiểu là 1 người.'
      : parsedWorkers < minRequiredWorkers
      ? `Ca đã vào giờ diễn ra, số nhân sự cần tuyển không được nhỏ hơn ${confirmedWorkers} người đã nhận ca.`
      : null;
    const alErr = lockTimeAndMoney
      ? null
      : Number(formData.allowance) < 0
      ? 'Phụ cấp thêm không được là số âm!'
      : null;

    setJobTypeError(jErr);
    setTimeError(tErr);
    setWageError(wErr);
    setWorkersError(wkErr);
    setAllowanceError(alErr);

    const firstError = jErr || tErr || aErr || wErr || wkErr || alErr;
    if (firstError) {
      setFormError(firstError);
      showToast(firstError, 'error');
      return;
    }

    setFormError(null);
    const sDate = new Date(formData.startTime);
    const eDate = new Date(formData.endTime);

    const parsedWorkersCount = Math.max(minRequiredWorkers, Number(formData.requiredWorkers) || 1);
    onSubmit(shift.shiftId, {
      ...formData,
      startTime: lockTimeAndMoney && shift.startTime ? shift.startTime : sDate.toISOString(),
      endTime: lockTimeAndMoney && shift.endTime ? shift.endTime : eDate.toISOString(),
      arrivalTime: lockTimeAndMoney
        ? shift.arrivalTime || null
        : formData.arrivalTime
        ? new Date(formData.arrivalTime).toISOString()
        : null,
      requiredSkills: Array.isArray(formData.requiredSkills) ? formData.requiredSkills : [],
      performedSkillId: formData.performedSkillId || null,
      skillRequirements: Array.isArray(formData.skillRequirements) ? formData.skillRequirements : [],
      customSkillNotes: formData.customSkillNotes?.trim() || null,
      dressCode: formData.dressCode || '',
      requiredWorkers: parsedWorkersCount,
      hourlyWage: lockTimeAndMoney ? Number(shift.hourlyWage || 0) : Number(formData.hourlyWage || 0),
      allowance: lockTimeAndMoney ? Math.max(0, Number(shift.allowance || 0)) : Math.max(0, Number(formData.allowance || 0)),
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
      <div className="eo-modal-box eo-modal-box--wide" style={{ maxWidth: '1060px', maxHeight: '90vh', overflowY: 'auto' }}>
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
              fontSize: '0.95rem',
              cursor: 'pointer',
              color: '#64748b',
              fontWeight: 600,
              padding: '0.25rem 0.5rem',
              borderRadius: '6px',
            }}
            aria-label="Đóng popup"
          >
            Đóng
          </button>
        </div>

        {lockTimeAndMoney && (
          <div
            style={{
              marginBottom: '1rem',
              padding: '0.75rem 0.95rem',
              borderRadius: '8px',
              backgroundColor: '#eff6ff',
              border: '1px solid #bfdbfe',
              color: '#1e3a8a',
              fontSize: '0.82rem',
              lineHeight: 1.5,
            }}
          >
            <strong>Ca làm việc đã vào giờ diễn ra:</strong> Hệ thống khóa chỉnh sửa <strong>Thời gian</strong> và{' '}
            <strong>Số tiền (thù lao / phụ cấp)</strong> để giữ đúng cam kết với nhân sự. Bạn vẫn có thể điều chỉnh{' '}
            <strong>Số nhân sự cần tuyển</strong> (ví dụ tăng thêm để tuyển bổ sung)
            {confirmedWorkers > 0 ? ` — tối thiểu ${minRequiredWorkers} người đã nhận ca.` : '.'}
          </div>
        )}

        {formError && (
          <div className="eo-form-alert-error" role="alert">
            <div>
              <strong>Dữ liệu chưa hợp lệ:</strong> {formError}
            </div>
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="eo-form-group">
            <label>Vị Trí Công Việc *</label>
            <input
              type="text"
              list="edit-shift-roles-list"
              className={`eo-form-input ${jobTypeError ? 'has-error' : ''}`}
              style={{
                borderColor: jobTypeError ? '#ef4444' : undefined,
                boxShadow: jobTypeError ? '0 0 0 2px rgba(239, 68, 68, 0.2)' : undefined,
              }}
              placeholder="Nhập vị trí hoặc chọn gợi ý..."
              value={formData.jobType}
              onChange={(e) => {
                const val = e.target.value;
                setFormData({ ...formData, jobType: val });
                if (jobTypeError) setJobTypeError(null);
                if (formError) setFormError(null);
              }}
              onBlur={() => setJobTypeError(validateJobType(formData.jobType))}
              required
            />
            <datalist id="edit-shift-roles-list">
              {roles.map((role) => (
                <option key={role} value={role} />
              ))}
            </datalist>
            {jobTypeError && (
              <div className="eo-field-error ecw-field-error">
                <span>{jobTypeError}</span>
              </div>
            )}
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
            <div className="eo-form-group" style={{ marginBottom: timeError ? '0.25rem' : '1.15rem' }}>
              <label>Thời Gian Bắt Đầu * {lockTimeAndMoney && <span style={{ fontSize: '0.72rem', color: '#64748b', fontWeight: 500 }}>(Đã khóa)</span>}</label>
              <input
                type="datetime-local"
                required
                disabled={lockTimeAndMoney}
                className={`eo-form-input ${timeError ? 'has-error' : ''}`}
                style={{
                  borderColor: timeError ? '#ef4444' : undefined,
                  boxShadow: timeError ? '0 0 0 2px rgba(239, 68, 68, 0.2)' : undefined,
                  backgroundColor: lockTimeAndMoney ? '#f1f5f9' : undefined,
                  color: lockTimeAndMoney ? '#64748b' : undefined,
                  cursor: lockTimeAndMoney ? 'not-allowed' : undefined,
                }}
                value={formData.startTime}
                onChange={(e) => handleTimeChange('startTime', e.target.value)}
              />
            </div>

            <div className="eo-form-group" style={{ marginBottom: timeError ? '0.25rem' : '1.15rem' }}>
              <label>Thời Gian Kết Thúc * {lockTimeAndMoney && <span style={{ fontSize: '0.72rem', color: '#64748b', fontWeight: 500 }}>(Đã khóa)</span>}</label>
              <input
                type="datetime-local"
                required
                disabled={lockTimeAndMoney}
                className={`eo-form-input ${timeError ? 'has-error' : ''}`}
                style={{
                  borderColor: timeError ? '#ef4444' : undefined,
                  boxShadow: timeError ? '0 0 0 2px rgba(239, 68, 68, 0.2)' : undefined,
                  backgroundColor: lockTimeAndMoney ? '#f1f5f9' : undefined,
                  color: lockTimeAndMoney ? '#64748b' : undefined,
                  cursor: lockTimeAndMoney ? 'not-allowed' : undefined,
                }}
                value={formData.endTime}
                onChange={(e) => handleTimeChange('endTime', e.target.value)}
              />
            </div>
          </div>
          {timeError && (
            <div className="eo-field-error ecw-field-error" style={{ marginTop: '-0.1rem', marginBottom: '1rem' }}>
              <span>{timeError}</span>
            </div>
          )}

          {/* Giờ yêu cầu có mặt trước ca (Arrival Time) */}
          <ShiftArrivalTimeField
            startTime={formData.startTime}
            arrivalTime={formData.arrivalTime}
            onChange={(val) => !lockTimeAndMoney && setFormData({ ...formData, arrivalTime: val })}
            error={lockTimeAndMoney ? null : validateShiftArrivalTime(formData.arrivalTime, formData.startTime)}
            disabled={lockTimeAndMoney}
          />

          {/* Yêu cầu kỹ năng V3 */}
          <ShiftSkillSelector
            jobType={formData.jobType}
            selectedSkillIds={formData.requiredSkills}
            onChange={(nextSkills) => setFormData({ ...formData, requiredSkills: nextSkills })}
            performedSkillId={formData.performedSkillId}
            onPerformedSkillChange={(nextId) => setFormData((prev) => ({ ...prev, performedSkillId: nextId }))}
            skillRequirements={formData.skillRequirements}
            onSkillRequirementsChange={(nextReqs) => setFormData((prev) => ({ ...prev, skillRequirements: nextReqs }))}
            customSkillNotes={formData.customSkillNotes}
            onCustomSkillNotesChange={(notes) => setFormData((prev) => ({ ...prev, customSkillNotes: notes }))}
            taxonomy={skillTaxonomy}
          />

          {/* Tùy chọn cách tính lương: Theo giờ hoặc Theo ca */}
          <div className="eo-form-group" style={{ marginBottom: '0.75rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.45rem', flexWrap: 'wrap', gap: '0.5rem' }}>
              <label style={{ margin: 0 }}>
                Hình Thức Tính Thù Lao * {lockTimeAndMoney && <span style={{ fontSize: '0.72rem', color: '#64748b', fontWeight: 500 }}>(Đã khóa tiền)</span>}
              </label>
              <div style={{ display: 'inline-flex', backgroundColor: '#f1f5f9', padding: '3px', borderRadius: '8px', border: '1px solid #cbd5e1', opacity: lockTimeAndMoney ? 0.7 : 1 }}>
                <button
                  type="button"
                  disabled={lockTimeAndMoney}
                  onClick={() => handleSwitchWageMode('HOURLY')}
                  style={{
                    padding: '0.3rem 0.75rem',
                    fontSize: '0.78rem',
                    fontWeight: 700,
                    borderRadius: '6px',
                    border: 'none',
                    cursor: lockTimeAndMoney ? 'not-allowed' : 'pointer',
                    backgroundColor: wageMode === 'HOURLY' ? '#2563eb' : 'transparent',
                    color: wageMode === 'HOURLY' ? '#ffffff' : '#475569',
                  }}
                >
                  Tính theo giờ (VNĐ/giờ)
                </button>
                <button
                  type="button"
                  disabled={lockTimeAndMoney}
                  onClick={() => handleSwitchWageMode('PER_SHIFT')}
                  style={{
                    padding: '0.3rem 0.75rem',
                    fontSize: '0.78rem',
                    fontWeight: 700,
                    borderRadius: '6px',
                    border: 'none',
                    cursor: lockTimeAndMoney ? 'not-allowed' : 'pointer',
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
                  min={minRequiredWorkers}
                  required
                  className={`eo-form-input ${workersError ? 'has-error' : ''}`}
                  style={{
                    borderColor: workersError ? '#ef4444' : lockTimeAndMoney ? '#2563eb' : undefined,
                    boxShadow: workersError ? '0 0 0 2px rgba(239, 68, 68, 0.2)' : undefined,
                  }}
                  value={formData.requiredWorkers ?? ''}
                  onFocus={(e) => e.target.select()}
                  onKeyDown={(e) => {
                    if (e.key === '-' || e.key === 'e') e.preventDefault();
                  }}
                  onChange={(e) => {
                    let raw = e.target.value.replace(/^0+(?=\d)/, '');
                    if (e.target.value !== raw) e.target.value = raw;
                    if (raw === '') {
                      setFormData({ ...formData, requiredWorkers: '' });
                      setWorkersError(`Số nhân sự cần tuyển tối thiểu là ${minRequiredWorkers} người.`);
                      return;
                    }
                    const parsed = parseInt(raw, 10);
                    const val = isNaN(parsed) ? '' : Math.max(0, parsed);
                    setFormData({
                      ...formData,
                      requiredWorkers: val,
                    });
                    if (val >= minRequiredWorkers) {
                      setWorkersError(null);
                      if (formError) setFormError(null);
                    } else if (val < 1) {
                      setWorkersError('Số nhân sự cần tuyển tối thiểu là 1 người.');
                    } else {
                      setWorkersError(`Ca đã vào giờ diễn ra, không thể giảm thấp hơn ${confirmedWorkers} người đã nhận ca.`);
                    }
                  }}
                  onBlur={() => {
                    if (!formData.requiredWorkers || Number(formData.requiredWorkers) < minRequiredWorkers) {
                      setFormData((prev) => ({ ...prev, requiredWorkers: minRequiredWorkers }));
                      setWorkersError(null);
                    }
                  }}
                />
                {workersError ? (
                  <div className="eo-field-error ecw-field-error">
                    <span>{workersError}</span>
                  </div>
                ) : lockTimeAndMoney ? (
                  <div style={{ fontSize: '0.72rem', color: '#0369a1', marginTop: '0.25rem', fontWeight: 600 }}>
                    Đã nhận {confirmedWorkers} người • Tăng số lượng để tuyển thêm
                  </div>
                ) : null}
              </div>

              {wageMode === 'HOURLY' ? (
                <div className="eo-form-group" style={{ marginBottom: 0 }}>
                  <label>Thù Lao Theo Giờ (VNĐ/h) *</label>
                  <input
                    type="number"
                    step="any"
                    min="1"
                    required
                    disabled={lockTimeAndMoney}
                    className={`eo-form-input ${wageError ? 'has-error' : ''}`}
                    style={{
                      borderColor: wageError ? '#ef4444' : undefined,
                      boxShadow: wageError ? '0 0 0 2px rgba(239, 68, 68, 0.2)' : undefined,
                      backgroundColor: lockTimeAndMoney ? '#f1f5f9' : undefined,
                      color: lockTimeAndMoney ? '#64748b' : undefined,
                      cursor: lockTimeAndMoney ? 'not-allowed' : undefined,
                    }}
                    value={formData.hourlyWage ?? ''}
                    onFocus={(e) => e.target.select()}
                    onKeyDown={(e) => {
                      if (e.key === '-' || e.key === 'e') e.preventDefault();
                    }}
                    onChange={(e) => {
                      if (lockTimeAndMoney) return;
                      let raw = e.target.value.replace(/^0+(?=\d)/, '');
                      if (e.target.value !== raw) e.target.value = raw;
                      const val = raw === '' ? '' : Math.max(0, parseFloat(raw));
                      setFormData({ ...formData, hourlyWage: val });
                      setShiftPackageWage(Math.round((Number(val) || 0) * durationHours));
                      const wageCheck = validateShiftWageAgainstFloor({
                        hourlyWage: Number(val) || 0,
                        venueAddress: shift?.locationAddress || '',
                        venueLat: shift?.locationLat,
                        venueLng: shift?.locationLng,
                      });
                      setWageError(wageCheck.isValid ? null : wageCheck.error);
                      if (formError && wageCheck.isValid) setFormError(null);
                    }}
                    onBlur={() => {
                      if (lockTimeAndMoney) return;
                      if (formData.hourlyWage === '' || isNaN(formData.hourlyWage)) {
                        setFormData((prev) => ({ ...prev, hourlyWage: 0 }));
                      }
                      const wageCheck = validateShiftWageAgainstFloor({
                        hourlyWage: Number(formData.hourlyWage) || 0,
                        venueAddress: shift?.locationAddress || '',
                        venueLat: shift?.locationLat,
                        venueLng: shift?.locationLng,
                      });
                      setWageError(wageCheck.isValid ? null : wageCheck.error);
                    }}
                  />
                  {wageError ? (
                    <div className="eo-field-error ecw-field-error">
                      <span>{wageError}</span>
                    </div>
                  ) : (
                    <div style={{ fontSize: '0.72rem', color: '#2563eb', marginTop: '0.25rem', fontWeight: 600 }}>
                      = {Math.round(Number(formData.hourlyWage || 0) * durationHours).toLocaleString()}đ / ca ({durationHours}h)
                    </div>
                  )}
                </div>
              ) : (
                <div className="eo-form-group" style={{ marginBottom: 0 }}>
                  <label>Lương Trọn Gói Ca (VNĐ/ca) *</label>
                  <input
                    type="number"
                    step="any"
                    min="1"
                    required
                    disabled={lockTimeAndMoney}
                    className={`eo-form-input ${wageError ? 'has-error' : ''}`}
                    style={{
                      borderColor: wageError ? '#ef4444' : undefined,
                      boxShadow: wageError ? '0 0 0 2px rgba(239, 68, 68, 0.2)' : undefined,
                      backgroundColor: lockTimeAndMoney ? '#f1f5f9' : undefined,
                      color: lockTimeAndMoney ? '#64748b' : undefined,
                      cursor: lockTimeAndMoney ? 'not-allowed' : undefined,
                    }}
                    value={shiftPackageWage ?? ''}
                    onFocus={(e) => e.target.select()}
                    onKeyDown={(e) => {
                      if (e.key === '-' || e.key === 'e') e.preventDefault();
                    }}
                    onChange={(e) => {
                      if (lockTimeAndMoney) return;
                      let raw = e.target.value.replace(/^0+(?=\d)/, '');
                      if (e.target.value !== raw) e.target.value = raw;
                      handlePackageWageChange(raw);
                    }}
                    onBlur={() => {
                      if (lockTimeAndMoney) return;
                      if (shiftPackageWage === '' || isNaN(shiftPackageWage)) {
                        setShiftPackageWage(0);
                      }
                      const wageCheck = validateShiftWageAgainstFloor({
                        hourlyWage: Number(formData.hourlyWage) || 0,
                        venueAddress: shift?.locationAddress || '',
                        venueLat: shift?.locationLat,
                        venueLng: shift?.locationLng,
                      });
                      setWageError(wageCheck.isValid ? null : wageCheck.error);
                    }}
                  />
                  {wageError ? (
                    <div className="eo-field-error ecw-field-error">
                      <span>{wageError}</span>
                    </div>
                  ) : (
                    <div style={{ fontSize: '0.72rem', color: '#2563eb', marginTop: '0.25rem', fontWeight: 600 }}>
                      ~ {Number(formData.hourlyWage || 0).toLocaleString()}đ/giờ ({durationHours}h)
                    </div>
                  )}
                </div>
              )}

              <div className="eo-form-group" style={{ marginBottom: 0 }}>
                <label>Phụ Cấp (VNĐ)</label>
                <input
                  type="number"
                  min="0"
                  step="5000"
                  disabled={lockTimeAndMoney}
                  className={`eo-form-input ${allowanceError ? 'has-error' : ''}`}
                  style={{
                    borderColor: allowanceError ? '#ef4444' : undefined,
                    boxShadow: allowanceError ? '0 0 0 2px rgba(239, 68, 68, 0.2)' : undefined,
                    backgroundColor: lockTimeAndMoney ? '#f1f5f9' : undefined,
                    color: lockTimeAndMoney ? '#64748b' : undefined,
                    cursor: lockTimeAndMoney ? 'not-allowed' : undefined,
                  }}
                  value={formData.allowance ?? ''}
                  onFocus={(e) => e.target.select()}
                  onKeyDown={(e) => {
                    if (e.key === '-' || e.key === 'e' || e.key === 'E') {
                      e.preventDefault();
                    }
                  }}
                  onChange={(e) => {
                    if (lockTimeAndMoney) return;
                    let raw = e.target.value.replace(/^0+(?=\d)/, '');
                    if (e.target.value !== raw) e.target.value = raw;
                    const val = raw === '' ? '' : parseFloat(raw);
                    setFormData({ ...formData, allowance: val });
                    if (val !== '' && !isNaN(val) && val < 0) {
                      setAllowanceError('Phụ cấp thêm không được là số âm!');
                    } else {
                      setAllowanceError(null);
                      if (formError) setFormError(null);
                    }
                  }}
                  onBlur={() => {
                    if (lockTimeAndMoney) return;
                    if (formData.allowance === '' || isNaN(formData.allowance)) {
                      setFormData((prev) => ({ ...prev, allowance: 0 }));
                      setAllowanceError(null);
                    }
                  }}
                />
                {allowanceError && (
                  <div className="eo-field-error ecw-field-error">
                    <span>{allowanceError}</span>
                  </div>
                )}
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
              <label>Trang Phục Yêu Cầu</label>
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
            jobType={formData.jobType}
          />

          <div className="eo-modal-footer">
            <button
              type="button"
              className="eo-btn-secondary"
              onClick={onClose}
              disabled={submitting}
            >
              Hủy Bỏ
            </button>
            <button
              type="submit"
              className="eo-btn-primary"
              disabled={submitting}
            >
              {submitting ? 'Đang lưu...' : 'Lưu Ca Làm Việc'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default EditShiftModal;
