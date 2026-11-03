import React, { useState } from 'react';

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
            style={{ background: 'none', border: 'none', fontSize: '1.25rem', cursor: 'pointer', color: '#9ca3af' }}
          >
            Dong
          </button>
        </div>

        <form onSubmit={onSubmit}>
          <div className="eo-form-group">
            <label>Vị Trí / Vai Trò Cần Tuyển *</label>
            <select
              className="eo-form-select"
              value={newShift.jobType}
              onChange={(e) => onRoleChange ? onRoleChange(e.target.value) : setNewShift({ ...newShift, jobType: e.target.value })}
            >
              {roles.map((role) => (
                <option key={role} value={role}>
                  {role}
                </option>
              ))}
            </select>

            {/* Gợi ý Ma trận Lương Wage Matrix 4D */}
            {wageRecommendationsMap[newShift.jobType] && (
              <div className="wage-recommendation-badge animate-fade-in-up" style={{ marginTop: '0.5rem' }}>
                <div>
                  <strong>Gợi ý Ma trận Lương (Wage Matrix 4D):</strong>{' '}
                  <span className="wage-range">
                    {wageRecommendationsMap[newShift.jobType].min.toLocaleString()} đ -{' '}
                    {wageRecommendationsMap[newShift.jobType].max.toLocaleString()} đ / giờ
                  </span>
                  <div className="wage-subnote">
                    Đặc điểm: {wageRecommendationsMap[newShift.jobType].note}
                  </div>
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
                onChange={(e) => setNewShift({ ...newShift, requiredWorkers: parseInt(e.target.value) || 1 })}
              />
            </div>
            <div className="eo-form-group">
              <label>Thù Lao Giờ (VNĐ/giờ) *</label>
              <input
                type="number"
                min="20000"
                step="5000"
                required
                className="eo-form-input"
                value={newShift.hourlyWage}
                onChange={(e) => setNewShift({ ...newShift, hourlyWage: parseFloat(e.target.value) || 0 })}
              />
            </div>
          </div>

          <div className="eo-form-row">
            <div className="eo-form-group">
              <label>Phụ Cấp Thêm (VNĐ)</label>
              <input
                type="number"
                step="10000"
                className="eo-form-input"
                value={newShift.allowance}
                onChange={(e) => setNewShift({ ...newShift, allowance: parseFloat(e.target.value) || 0 })}
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
                onChange={(e) => setNewShift({ ...newShift, maxDistanceKm: parseFloat(e.target.value) || 15 })}
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
              Thù lao này EO thanh toán trực tiếp cho Worker sau ca làm (Mô hình Non-Escrow - CANi không thu cọc lương).
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
