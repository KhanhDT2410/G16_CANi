import React, { useState, useRef } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';
import { WAGE_RECOMMENDATIONS_MAP, calculateShiftRiskAnalysis } from '../hooks/useShifts';
import ShiftChecklistEditor from './ShiftChecklistEditor';

/**
 * ShiftCreateModal
 * Modal thêm ca làm việc kèm gợi ý lương, lựa chọn tính theo giờ/theo ca, cảnh báo khó tìm nhân sự & checklist kéo thả
 */
const ShiftCreateModal = ({
  isOpen,
  onClose,
  onSubmit,
  newShift,
  setNewShift,
  roles,
  wageRecommendationsMap = {},
  onRoleChange,
  shiftDurationHours = 4,
  calculateShiftTotal,
  skillTaxonomy = [],
}) => {
  const [uploadingImg, setUploadingImg] = useState(false);
  const [wageMode, setWageMode] = useState('HOURLY'); // 'HOURLY' | 'PER_SHIFT'
  const [shiftPackageWage, setShiftPackageWage] = useState(
    Math.round(Number(newShift?.hourlyWage || 50000) * Math.max(0.5, Number(shiftDurationHours || 4)))
  );
  const fileInputRef = useRef(null);

  const handleImageUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      showToast('Vui lòng chọn tệp hình ảnh (JPG, PNG, WEBP, GIF)', 'error');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      showToast('Dung lượng ảnh tối đa là 5MB', 'error');
      return;
    }
    setUploadingImg(true);
    try {
      const res = await eoService.uploadImage(file);
      const url = res.url || res;
      setNewShift((prev) => ({ ...prev, imageUrl: url }));
      showToast('Tải ảnh ca làm việc thành công!', 'success');
    } catch (err) {
      showToast('Lỗi khi tải ảnh: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setUploadingImg(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  if (!isOpen) return null;

  const safeHours = Math.max(0.5, Number(shiftDurationHours || 4));
  const totalCost = calculateShiftTotal ? calculateShiftTotal() : 0;

  const handleSwitchWageMode = (mode) => {
    setWageMode(mode);
    if (mode === 'PER_SHIFT') {
      setShiftPackageWage(Math.round(Number(newShift.hourlyWage || 50000) * safeHours));
    }
  };

  const handlePackageWageChange = (val) => {
    const pkg = Math.max(0, parseFloat(val) || 0);
    setShiftPackageWage(pkg);
    const computedHourly = Math.max(0, Math.round(pkg / safeHours));
    setNewShift((prev) => ({
      ...prev,
      hourlyWage: computedHourly,
    }));
  };

  const recMap = Object.keys(wageRecommendationsMap).length > 0 ? wageRecommendationsMap : WAGE_RECOMMENDATIONS_MAP;
  const rawRec = recMap[newShift.jobType] || { min: 40000, max: 60000, defaultWage: 45000, recommended: 45000 };
  const wageRec = {
    min: Number(rawRec.min || 40000),
    max: Number(rawRec.max || 60000),
    recommended: Number(rawRec.recommended || rawRec.defaultWage || 45000),
  };
  const riskInfo = calculateShiftRiskAnalysis(newShift);

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box" style={{ maxWidth: '680px', maxHeight: '90vh', overflowY: 'auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
          <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 700 }}>
            Thêm Ca Làm Việc & Tiêu Chí Tuyển Dụng
          </h3>
          <button
            type="button"
            onClick={onClose}
            style={{ background: 'none', border: 'none', fontSize: '1rem', cursor: 'pointer', color: '#64748b', fontWeight: 600 }}
          >
            Đóng ✕
          </button>
        </div>

        <form onSubmit={onSubmit}>
          <div className="eo-form-group">
            <label>Vị Trí / Vai Trò Cần Tuyển *</label>
            <input
              type="text"
              list="create-shift-roles-list"
              className="eo-form-input"
              placeholder="Nhập vị trí hoặc chọn gợi ý (VD: Phục vụ bàn, Lễ tân, MC, Kỹ thuật...)"
              value={newShift.jobType}
              onChange={(e) =>
                onRoleChange ? onRoleChange(e.target.value) : setNewShift({ ...newShift, jobType: e.target.value })
              }
              required
            />
            <datalist id="create-shift-roles-list">
              {roles.map((role) => (
                <option key={role} value={role} />
              ))}
            </datalist>
          </div>

          {/* Hình ảnh minh họa ca làm / Đồng phục - Nằm trong 1 div riêng cho ảnh to rõ */}
          <div className="eo-form-group">
            <label style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>Ảnh Minh Họa Ca Làm / Đồng Phục (Tùy chọn)</span>
              <span style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 400 }}>
                Hỗ trợ JPG, PNG, WEBP, GIF (Tối đa 5MB)
              </span>
            </label>
            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
              <input
                type="file"
                ref={fileInputRef}
                accept="image/*"
                style={{ display: 'none' }}
                onChange={handleImageUpload}
              />
              <button
                type="button"
                disabled={uploadingImg}
                onClick={() => fileInputRef.current?.click()}
                className="eo-btn-secondary"
                style={{ fontSize: '0.825rem', padding: '0.45rem 0.95rem', whiteSpace: 'nowrap', fontWeight: 600 }}
              >
                {uploadingImg ? 'Đang tải ảnh lên...' : 'Tải ảnh từ máy tính'}
              </button>
              {!newShift.imageUrl && (
                <span style={{ fontSize: '0.78rem', color: '#64748b' }}>Chưa chọn ảnh từ máy tính</span>
              )}
            </div>

            {newShift.imageUrl && (
              <div
                style={{
                  marginTop: '0.65rem',
                  borderRadius: '0.625rem',
                  border: '1px solid #cbd5e1',
                  overflow: 'hidden',
                  backgroundColor: '#f8fafc',
                }}
              >
                <img
                  src={newShift.imageUrl}
                  alt="Ảnh minh họa ca làm"
                  style={{
                    width: '100%',
                    maxHeight: '220px',
                    objectFit: 'cover',
                    display: 'block',
                  }}
                  onError={(e) => {
                    e.target.style.display = 'none';
                  }}
                />
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    padding: '0.45rem 0.85rem',
                    backgroundColor: '#ffffff',
                    borderTop: '1px solid #e2e8f0',
                  }}
                >
                  <span style={{ fontSize: '0.8rem', color: '#15803d', fontWeight: 600 }}>
                    Đã tải ảnh minh họa ca làm việc
                  </span>
                  <button
                    type="button"
                    onClick={() => setNewShift({ ...newShift, imageUrl: '' })}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: '#dc2626',
                      cursor: 'pointer',
                      fontSize: '0.8rem',
                      fontWeight: 700,
                    }}
                  >
                    Gỡ ảnh
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* Mô tả công việc */}
          <div className="eo-form-group">
            <label>Mô Tả Nhiệm Vụ Công Việc *</label>
            <textarea
              rows="2"
              required
              className="eo-form-textarea"
              placeholder="VD: Hỗ trợ đón tiếp khách mời, phát tài liệu chương trình, hướng dẫn chỗ ngồi..."
              value={newShift.description || ''}
              onChange={(e) => setNewShift({ ...newShift, description: e.target.value })}
            />
          </div>

          <div className="eo-form-row">
            <div className="eo-form-group">
              <label>Thời Gian Bắt Đầu *</label>
              <input
                type="datetime-local"
                required
                className="eo-form-input"
                value={newShift.startTime}
                onChange={(e) => {
                  const val = e.target.value;
                  const s = new Date(val);
                  const en = new Date(newShift.endTime);
                  const dur = !isNaN(s.getTime()) && !isNaN(en.getTime()) && en > s ? parseFloat(((en - s) / 3600000).toFixed(1)) : safeHours;
                  if (wageMode === 'PER_SHIFT') {
                    const computed = Math.max(20000, Math.round(Number(shiftPackageWage || 0) / Math.max(0.5, dur)));
                    setNewShift({ ...newShift, startTime: val, hourlyWage: computed });
                  } else {
                    setNewShift({ ...newShift, startTime: val });
                  }
                }}
              />
            </div>
            <div className="eo-form-group">
              <label>Thời Gian Kết Thúc *</label>
              <input
                type="datetime-local"
                required
                className="eo-form-input"
                value={newShift.endTime}
                onChange={(e) => {
                  const val = e.target.value;
                  const s = new Date(newShift.startTime);
                  const en = new Date(val);
                  const dur = !isNaN(s.getTime()) && !isNaN(en.getTime()) && en > s ? parseFloat(((en - s) / 3600000).toFixed(1)) : safeHours;
                  if (wageMode === 'PER_SHIFT') {
                    const computed = Math.max(20000, Math.round(Number(shiftPackageWage || 0) / Math.max(0.5, dur)));
                    setNewShift({ ...newShift, endTime: val, hourlyWage: computed });
                  } else {
                    setNewShift({ ...newShift, endTime: val });
                  }
                }}
              />
            </div>
          </div>

          {/* Giờ mở cửa điểm danh (check-in gate) */}
          <div className="eo-form-group">
            <label>Thời Gian Mở Điểm Danh (Tùy chọn)</label>
            <input
              type="datetime-local"
              className="eo-form-input"
              value={newShift.arrivalTime || ''}
              onChange={(e) => setNewShift({ ...newShift, arrivalTime: e.target.value })}
            />
            <div style={{ fontSize: '0.74rem', color: '#64748b', marginTop: '0.25rem' }}>
              Người lao động chỉ được điểm danh từ thời điểm này. Để trống = mở cùng giờ bắt đầu ca.
            </div>
          </div>

          {/* Trang phục + Kỹ năng bắt buộc */}
          <div className="eo-form-row">
            <div className="eo-form-group">
              <label>Trang Phục (Dress Code)</label>
              <input
                type="text"
                className="eo-form-input"
                placeholder="VD: Áo sơ mi trắng, quần tây đen"
                value={newShift.dressCode || ''}
                onChange={(e) => setNewShift({ ...newShift, dressCode: e.target.value })}
              />
            </div>
          </div>

          <div className="eo-form-group">
            <label>Kỹ Năng Bắt Buộc (chọn nhiều)</label>
            {skillTaxonomy.length > 0 ? (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem', marginTop: '0.35rem' }}>
                {skillTaxonomy.map((sk) => {
                  const checked = Array.isArray(newShift.requiredSkills) && newShift.requiredSkills.includes(sk.skillId);
                  return (
                    <button
                      key={sk.skillId}
                      type="button"
                      onClick={() => {
                        const base = Array.isArray(newShift.requiredSkills) ? newShift.requiredSkills : [];
                        const next = checked ? base.filter((id) => id !== sk.skillId) : [...base, sk.skillId];
                        setNewShift({ ...newShift, requiredSkills: next });
                      }}
                      style={{
                        fontSize: '0.76rem',
                        padding: '0.25rem 0.6rem',
                        borderRadius: '0.45rem',
                        border: checked ? '1.5px solid #2563eb' : '1px solid #cbd5e1',
                        backgroundColor: checked ? '#dbeafe' : '#ffffff',
                        color: checked ? '#1d4ed8' : '#475569',
                        fontWeight: checked ? 700 : 500,
                        cursor: 'pointer',
                      }}
                    >
                      {checked ? '✓ ' : ''}{sk.name}
                    </button>
                  );
                })}
              </div>
            ) : (
              <div style={{ fontSize: '0.74rem', color: '#94a3b8', marginTop: '0.3rem' }}>
                Chưa có danh mục kỹ năng để chọn (bỏ qua trường này).
              </div>
            )}
            <div style={{ fontSize: '0.74rem', color: '#64748b', marginTop: '0.3rem' }}>
              Hệ thống chỉ mời ứng viên có đúng kỹ năng này (lọc cứng trong matching).
            </div>
          </div>

          {/* Chọn tính theo giờ hoặc theo ca */}
          <div className="eo-form-group" style={{ marginBottom: '0.6rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem', flexWrap: 'wrap', gap: '0.5rem' }}>
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

            <div className="eo-form-row" style={{ marginBottom: 0 }}>
              <div className="eo-form-group" style={{ marginBottom: 0 }}>
                <label>Số Lượng Nhân Sự Cần Tuyển *</label>
                <input
                  type="number"
                  min="1"
                  required
                  className="eo-form-input"
                  value={newShift.requiredWorkers}
                  onChange={(e) =>
                    setNewShift({ ...newShift, requiredWorkers: Math.max(1, parseInt(e.target.value, 10) || 1) })
                  }
                />
              </div>

              {wageMode === 'HOURLY' ? (
                <div className="eo-form-group" style={{ marginBottom: 0 }}>
                  <label>Thù Lao Theo Giờ (VNĐ/giờ) *</label>
                  <input
                    type="number"
                    min="20000"
                    step="5000"
                    required
                    className="eo-form-input"
                    value={newShift.hourlyWage}
                    onChange={(e) => {
                      const val = Math.max(0, parseFloat(e.target.value) || 0);
                      setNewShift({ ...newShift, hourlyWage: val });
                      setShiftPackageWage(Math.round(val * safeHours));
                    }}
                  />
                  <div style={{ fontSize: '0.74rem', color: '#2563eb', marginTop: '0.25rem', fontWeight: 600 }}>
                    = {Math.round(Number(newShift.hourlyWage || 0) * safeHours).toLocaleString()}đ / ca ({safeHours}h)
                  </div>
                </div>
              ) : (
                <div className="eo-form-group" style={{ marginBottom: 0 }}>
                  <label>Mức Lương Trọn Gói Theo Ca (VNĐ/ca) *</label>
                  <input
                    type="number"
                    min="20000"
                    step="10000"
                    required
                    className="eo-form-input"
                    value={shiftPackageWage}
                    onChange={(e) => handlePackageWageChange(e.target.value)}
                  />
                  <div style={{ fontSize: '0.74rem', color: '#2563eb', marginTop: '0.25rem', fontWeight: 600 }}>
                    Quy đổi: ~{Number(newShift.hourlyWage || 0).toLocaleString()}đ/giờ ({safeHours}h)
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* GỢI Ý MỨC LƯƠNG THỊ TRƯỜNG & CẢNH BÁO KHÓ TÌM NHÂN SỰ */}
          <div style={{ marginBottom: '0.85rem', display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
            <div
              style={{
                padding: '0.65rem 0.9rem',
                borderRadius: '0.5rem',
                backgroundColor: '#eff6ff',
                border: '1px solid #bfdbfe',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '0.5rem',
              }}
            >
              <div style={{ fontSize: '0.8rem', color: '#1e3a8a' }}>
                <strong>Gợi ý mức lương:</strong>{' '}
                Khung thị trường <strong>{((wageRec && wageRec.min) || 40000).toLocaleString()}đ - {((wageRec && wageRec.max) || 60000).toLocaleString()}đ/giờ</strong>{' '}
                (Khuyên dùng: <strong>{((wageRec && wageRec.recommended) || 45000).toLocaleString()}đ/giờ</strong>)
              </div>
              <div style={{ display: 'flex', gap: '0.35rem', flexWrap: 'wrap' }}>
                <button
                  type="button"
                  onClick={() => {
                    const minVal = (wageRec && wageRec.min) || 40000;
                    setNewShift({ ...newShift, hourlyWage: minVal });
                    setShiftPackageWage(Math.round(minVal * safeHours));
                  }}
                  style={{
                    fontSize: '0.73rem',
                    padding: '0.2rem 0.55rem',
                    borderRadius: '0.35rem',
                    border: '1px solid #93c5fd',
                    backgroundColor: Number(newShift.hourlyWage) === wageRec.min ? '#2563eb' : '#ffffff',
                    color: Number(newShift.hourlyWage) === wageRec.min ? '#ffffff' : '#1d4ed8',
                    fontWeight: 600,
                    cursor: 'pointer',
                  }}
                >
                  Sàn: {((wageRec && wageRec.min) || 40000).toLocaleString()}đ
                </button>
                <button
                  type="button"
                  onClick={() => {
                    const recVal = (wageRec && wageRec.recommended) || 45000;
                    setNewShift({ ...newShift, hourlyWage: recVal });
                    setShiftPackageWage(Math.round(recVal * safeHours));
                  }}
                  style={{
                    fontSize: '0.73rem',
                    padding: '0.2rem 0.55rem',
                    borderRadius: '0.35rem',
                    border: '1px solid #2563eb',
                    backgroundColor: Number(newShift.hourlyWage) === wageRec.recommended ? '#2563eb' : '#dbeafe',
                    color: Number(newShift.hourlyWage) === wageRec.recommended ? '#ffffff' : '#1e40af',
                    fontWeight: 700,
                    cursor: 'pointer',
                  }}
                >
                  Khuyên dùng: {((wageRec && wageRec.recommended) || 45000).toLocaleString()}đ
                </button>
                <button
                  type="button"
                  onClick={() => {
                    const maxVal = (wageRec && wageRec.max) || 60000;
                    setNewShift({ ...newShift, hourlyWage: maxVal });
                    setShiftPackageWage(Math.round(maxVal * safeHours));
                  }}
                  style={{
                    fontSize: '0.73rem',
                    padding: '0.2rem 0.55rem',
                    borderRadius: '0.35rem',
                    border: '1px solid #93c5fd',
                    backgroundColor: Number(newShift.hourlyWage) === wageRec.max ? '#2563eb' : '#ffffff',
                    color: Number(newShift.hourlyWage) === wageRec.max ? '#ffffff' : '#1d4ed8',
                    fontWeight: 600,
                    cursor: 'pointer',
                  }}
                >
                  Thu hút nhanh: {((wageRec && wageRec.max) || 60000).toLocaleString()}đ
                </button>
              </div>
            </div>

            <div
              style={{
                padding: '0.7rem 0.95rem',
                borderRadius: '0.5rem',
                backgroundColor:
                  riskInfo.level === 'HIGH'
                    ? '#fef2f2'
                    : riskInfo.level === 'MEDIUM'
                    ? '#fffbeb'
                    : '#f0fdf4',
                border:
                  riskInfo.level === 'HIGH'
                    ? '1px solid #fecaca'
                    : riskInfo.level === 'MEDIUM'
                    ? '1px solid #fde68a'
                    : '1px solid #bbf7d0',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
                <span
                  style={{
                    fontSize: '0.82rem',
                    fontWeight: 700,
                    color:
                      riskInfo.level === 'HIGH'
                        ? '#b91c1c'
                        : riskInfo.level === 'MEDIUM'
                        ? '#b45309'
                        : '#15803d',
                  }}
                >
                  {riskInfo.level === 'HIGH'
                    ? 'Khó tìm nhân sự'
                    : riskInfo.level === 'MEDIUM'
                    ? 'Khá khó tìm nhân sự'
                    : 'Dễ tìm nhân sự'}
                </span>
                {riskInfo.level !== 'LOW' && (
                  <button
                    type="button"
                    onClick={() => {
                      const nextWage = Math.max(Number(newShift.hourlyWage) || 0, wageRec.recommended);
                      setNewShift({
                        ...newShift,
                        hourlyWage: nextWage,
                        maxDistanceKm: Math.max(Number(newShift.maxDistanceKm) || 15, 15),
                      });
                      setShiftPackageWage(Math.round(nextWage * safeHours));
                    }}
                    style={{
                      fontSize: '0.74rem',
                      padding: '0.2rem 0.65rem',
                      borderRadius: '0.35rem',
                      border: 'none',
                      backgroundColor: riskInfo.level === 'HIGH' ? '#dc2626' : '#d97706',
                      color: '#ffffff',
                      fontWeight: 700,
                      cursor: 'pointer',
                    }}
                  >
                    Tối ưu ngay để dễ tìm người
                  </button>
                )}
              </div>
              {riskInfo.reasons.length > 0 && (
                <ul style={{ margin: '0.35rem 0 0', paddingLeft: '1.1rem', fontSize: '0.78rem', color: '#475569' }}>
                  {riskInfo.reasons.map((r, i) => (
                    <li key={i}>{r}</li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          <div className="eo-form-row">
            <div className="eo-form-group">
              <label>Phụ Cấp Thêm (VNĐ)</label>
              <input
                type="number"
                min="0"
                step="10000"
                className="eo-form-input"
                value={newShift.allowance}
                onKeyDown={(e) => {
                  if (e.key === '-' || e.key === 'e' || e.key === 'E') {
                    e.preventDefault();
                  }
                }}
                onChange={(e) => {
                  const raw = parseFloat(e.target.value);
                  setNewShift({ ...newShift, allowance: isNaN(raw) || raw < 0 ? 0 : raw });
                }}
              />
            </div>
            <div className="eo-form-group">
              <label>Bán Kính Quét Ứng Viên (km)</label>
              <input
                type="number"
                min="1"
                max="50"
                className="eo-form-input"
                value={newShift.maxDistanceKm}
                onChange={(e) =>
                  setNewShift({ ...newShift, maxDistanceKm: Math.max(1, parseFloat(e.target.value) || 15) })
                }
              />
            </div>
          </div>

          {/* Checklist Đầu Việc Cho Ca Này */}
          <ShiftChecklistEditor
            items={newShift.checklistItems || []}
            onChange={(updatedItems) => setNewShift((prev) => ({ ...prev, checklistItems: updatedItems }))}
          />

          {/* Chi phí ước tính hiển thị realtime */}
          <div className="shift-cost-summary-box">
            <div className="shift-cost-header">
              <span>DỰ TOÁN THÙ LAO CA LÀM NÀY:</span>
              <span className="shift-cost-hours">
                {shiftDurationHours} giờ x {newShift.requiredWorkers || 1} nhân sự
              </span>
            </div>
            <div className="shift-cost-total">
              {totalCost.toLocaleString()} đ
            </div>
            <div className="shift-cost-formula">
              = ({shiftDurationHours}h x {Number(newShift.requiredWorkers || 1)} người x {Number(newShift.hourlyWage || 0).toLocaleString()}đ/h) + {Number(newShift.allowance || 0).toLocaleString()}đ phụ cấp
            </div>
            <div className="shift-cost-note">
              Thù lao này Ban tổ chức thanh toán trực tiếp cho nhân sự sau ca làm việc.
            </div>
          </div>

          <div className="eo-modal-footer">
            <button
              type="button"
              className="eo-btn-secondary"
              onClick={onClose}
            >
              Hủy Bỏ
            </button>
            <button type="submit" className="eo-btn-primary">
              Lưu Ca Làm Việc
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default ShiftCreateModal;
