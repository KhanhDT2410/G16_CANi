import React, { useState, useRef } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';
import { WAGE_RECOMMENDATIONS_MAP, calculateShiftRiskAnalysis } from '../hooks/useShifts';

/**
 * ShiftCreateModal
 * Modal thêm ca làm việc kèm gợi ý định mức lương Wage Matrix 4D, mô tả công việc và checklist (No Emoji)
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
}) => {
  const [newChecklistItem, setNewChecklistItem] = useState('');
  const [uploadingImg, setUploadingImg] = useState(false);
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

  const totalCost = calculateShiftTotal ? calculateShiftTotal() : 0;
  const checklistList = Array.isArray(newShift.checklistItems) ? newShift.checklistItems : [];

  const handleAddChecklist = (e) => {
    e?.preventDefault();
    if (!newChecklistItem.trim()) return;
    setNewShift((prev) => ({
      ...prev,
      checklistItems: [...(prev.checklistItems || []), newChecklistItem.trim()],
    }));
    setNewChecklistItem('');
  };

  const handleRemoveChecklist = (indexToRemove) => {
    setNewShift((prev) => ({
      ...prev,
      checklistItems: (prev.checklistItems || []).filter((_, idx) => idx !== indexToRemove),
    }));
  };

  const handleQuickAddChecklist = (item) => {
    if (checklistList.includes(item)) return;
    setNewShift((prev) => ({
      ...prev,
      checklistItems: [...(prev.checklistItems || []), item],
    }));
  };

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box" style={{ maxWidth: '640px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
          <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 700 }}>
            Thêm Ca Làm Việc & Tiêu Chí Tuyển Dụng
          </h3>
          <button
            type="button"
            onClick={onClose}
            style={{ background: 'none', border: 'none', fontSize: '1rem', cursor: 'pointer', color: '#64748b', fontWeight: 600 }}
          >
            Đóng
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
              onChange={(e) => onRoleChange ? onRoleChange(e.target.value) : setNewShift({ ...newShift, jobType: e.target.value })}
              required
            />
            <datalist id="create-shift-roles-list">
              {roles.map((role) => (
                <option key={role} value={role} />
              ))}
            </datalist>
          </div>

          {/* Hình ảnh minh họa ca làm / Đồng phục - CHỈ TẢI ẢNH TỪ MÁY TÍNH */}
          <div className="eo-form-group">
            <label style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>Ảnh Minh Họa Ca Làm / Đồng Phục (Tùy chọn)</span>
              <span style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 400 }}>Chỉ tải ảnh từ máy tính (JPG, PNG - Tối đa 5MB)</span>
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

              {newShift.imageUrl ? (
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <img
                    src={newShift.imageUrl}
                    alt="Preview"
                    style={{ width: '42px', height: '42px', borderRadius: '6px', objectFit: 'cover', border: '1px solid #cbd5e1' }}
                    onError={(e) => { e.target.style.display = 'none'; }}
                  />
                  <span style={{ fontSize: '0.78rem', color: '#15803d', fontWeight: 600 }}>Đã tải ảnh lên</span>
                  <button
                    type="button"
                    onClick={() => setNewShift({ ...newShift, imageUrl: '' })}
                    style={{ background: 'none', border: 'none', color: '#dc2626', cursor: 'pointer', fontSize: '0.8rem', fontWeight: 600 }}
                  >
                    Gỡ ảnh
                  </button>
                </div>
              ) : (
                <span style={{ fontSize: '0.78rem', color: '#64748b' }}>Chưa chọn ảnh từ máy tính</span>
              )}
            </div>
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
                onChange={(e) => setNewShift({ ...newShift, startTime: e.target.value })}
              />
            </div>
            <div className="eo-form-group">
              <label>Thời Gian Kết Thúc *</label>
              <input
                type="datetime-local"
                required
                className="eo-form-input"
                value={newShift.endTime}
                onChange={(e) => setNewShift({ ...newShift, endTime: e.target.value })}
              />
            </div>
          </div>

          <div className="eo-form-row">
            <div className="eo-form-group">
              <label>Số Lượng Nhân Sự Cần Tuyển *</label>
              <input
                type="number"
                min="1"
                required
                className="eo-form-input"
                value={newShift.requiredWorkers}
                onChange={(e) => setNewShift({ ...newShift, requiredWorkers: Math.max(1, parseInt(e.target.value, 10) || 1) })}
              />
            </div>
            <div className="eo-form-group">
              <label>Thù Lao Giờ (VNĐ/giờ) *</label>
              <input
                type="number"
                min="10000"
                step="5000"
                required
                className="eo-form-input"
                value={newShift.hourlyWage}
                onChange={(e) => setNewShift({ ...newShift, hourlyWage: Math.max(0, parseFloat(e.target.value) || 0) })}
              />
            </div>
          </div>

          {/* GỢI Ý MỨC LƯƠNG THỊ TRƯỜNG (WAGE RECOMMENDATION) & CẢNH BÁO RISK SCORE */}
          {(() => {
            const recMap = Object.keys(wageRecommendationsMap).length > 0 ? wageRecommendationsMap : WAGE_RECOMMENDATIONS_MAP;
            const wageRec = recMap[newShift.jobType] || { min: 40000, max: 60000, recommended: 45000 };
            const riskInfo = calculateShiftRiskAnalysis(newShift);
            return (
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
                    <strong>Gợi ý mức lương (Wage Recommendation):</strong>{' '}
                    Khung thị trường <strong>{wageRec.min.toLocaleString()}đ - {wageRec.max.toLocaleString()}đ/giờ</strong>{' '}
                    (Đề xuất tối ưu: <strong>{wageRec.recommended.toLocaleString()}đ/giờ</strong>)
                  </div>
                  <div style={{ display: 'flex', gap: '0.35rem', flexWrap: 'wrap' }}>
                    <button
                      type="button"
                      onClick={() => setNewShift({ ...newShift, hourlyWage: wageRec.min })}
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
                      Sàn: {wageRec.min.toLocaleString()}đ
                    </button>
                    <button
                      type="button"
                      onClick={() => setNewShift({ ...newShift, hourlyWage: wageRec.recommended })}
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
                      Khuyên dùng: {wageRec.recommended.toLocaleString()}đ
                    </button>
                    <button
                      type="button"
                      onClick={() => setNewShift({ ...newShift, hourlyWage: wageRec.max })}
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
                      Thu hút nhanh: {wageRec.max.toLocaleString()}đ
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
                      Cảnh báo Risk Score (Nguy cơ khó tìm nhân sự): {riskInfo.riskScore}/100 — {riskInfo.label}
                    </span>
                    {riskInfo.level !== 'LOW' && (
                      <button
                        type="button"
                        onClick={() =>
                          setNewShift({
                            ...newShift,
                            hourlyWage: Math.max(Number(newShift.hourlyWage) || 0, wageRec.recommended),
                            maxDistanceKm: Math.max(Number(newShift.maxDistanceKm) || 15, 15),
                          })
                        }
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
            );
          })()}

          <div className="eo-form-row">
            <div className="eo-form-group">
              <label>Phụ Cấp Thêm (VNĐ - Không âm)</label>
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
                onChange={(e) => setNewShift({ ...newShift, maxDistanceKm: Math.max(1, parseFloat(e.target.value) || 15) })}
              />
            </div>
          </div>

          {/* Checklist công việc */}
          <div className="eo-form-group" style={{ backgroundColor: '#f8fafc', padding: '1rem', borderRadius: '0.5rem', border: '1px solid #e2e8f0' }}>
            <label style={{ fontWeight: 600, color: '#334155', display: 'flex', justifyContent: 'space-between' }}>
              <span>Checklist Đầu Việc Cho Ca Làm (Quy Trình Kiểm Tra)</span>
              <span style={{ fontSize: '0.8rem', color: '#64748b' }}>{checklistList.length} đầu việc</span>
            </label>
            <p style={{ fontSize: '0.8rem', color: '#64748b', margin: '0.2rem 0 0.6rem' }}>
              Worker sẽ phải tích xác nhận các đầu việc này trong app di động CANi khi thực hiện ca.
            </p>

            <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.75rem' }}>
              <input
                type="text"
                className="eo-form-input"
                style={{ flex: 1 }}
                placeholder="Nhập đầu việc mới (VD: Có mặt trước 20 phút...)"
                value={newChecklistItem}
                onChange={(e) => setNewChecklistItem(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    handleAddChecklist();
                  }
                }}
              />
              <button
                type="button"
                className="eo-btn-secondary"
                style={{ whiteSpace: 'nowrap', padding: '0.5rem 0.85rem' }}
                onClick={handleAddChecklist}
              >
                + Thêm
              </button>
            </div>

            {/* Gợi ý checklist nhanh */}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem', marginBottom: '0.75rem' }}>
              <span style={{ fontSize: '0.75rem', color: '#64748b', alignSelf: 'center' }}>Gợi ý:</span>
              {[
                'Có mặt trước 20 phút nhận thẻ',
                'Quét mã QR Check-in',
                'Đồng phục sơ mi trắng quần sẫm màu',
                'Báo cáo quản lý trước khi ra về',
              ].map((suggestItem) => (
                <button
                  key={suggestItem}
                  type="button"
                  onClick={() => handleQuickAddChecklist(suggestItem)}
                  style={{
                    fontSize: '0.75rem',
                    padding: '0.2rem 0.5rem',
                    borderRadius: '4px',
                    border: '1px solid #cbd5e1',
                    background: '#ffffff',
                    cursor: 'pointer',
                    color: '#475569',
                  }}
                >
                  + {suggestItem}
                </button>
              ))}
            </div>

            {/* Danh sách items đã thêm */}
            {checklistList.length > 0 ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
                {checklistList.map((item, idx) => (
                  <div
                    key={idx}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      backgroundColor: '#ffffff',
                      border: '1px solid #e2e8f0',
                      borderRadius: '0.375rem',
                      padding: '0.4rem 0.75rem',
                      fontSize: '0.85rem',
                    }}
                  >
                    <span>
                      <strong style={{ color: '#2563eb', marginRight: '0.4rem' }}>#{idx + 1}</strong>
                      {item}
                    </span>
                    <button
                      type="button"
                      onClick={() => handleRemoveChecklist(idx)}
                      style={{
                        background: 'none',
                        border: 'none',
                        color: '#ef4444',
                        cursor: 'pointer',
                        fontWeight: 'bold',
                        padding: '0 0.25rem',
                      }}
                      title="Xóa đầu việc"
                    >
                      X
                    </button>
                  </div>
                ))}
              </div>
            ) : (
              <div style={{ fontSize: '0.8rem', color: '#94a3b8', fontStyle: 'italic', textAlign: 'center', padding: '0.5rem' }}>
                Chưa có đầu việc checklist nào. Hãy thêm để Worker nắm rõ nhiệm vụ.
              </div>
            )}
          </div>

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
