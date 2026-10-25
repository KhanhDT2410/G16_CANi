import React from 'react';

/**
 * ShiftCreateModal
 * Modal thêm ca làm việc kèm gợi ý định mức lương Wage Matrix 4D
 */
const ShiftCreateModal = ({
  isOpen,
  onClose,
  onSubmit,
  newShift,
  setNewShift,
  roles,
  wageRecommendationsMap,
  onRoleChange,
  shiftDurationHours,
  shiftTotal,
}) => {
  if (!isOpen) return null;

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box">
        <h3>Thêm Ca Làm Việc & Định Mức Lương</h3>
        <form onSubmit={onSubmit}>
          <div className="eo-form-group">
            <label>Vị Trí / Vai Trò Cần Tuyển *</label>
            <select
              className="eo-form-select"
              value={newShift.jobType}
              onChange={(e) => onRoleChange(e.target.value)}
            >
              {roles.map((role) => (
                <option key={role} value={role}>
                  {role}
                </option>
              ))}
            </select>

            {/* Gợi ý Ma trận Lương Wage Matrix 4D */}
            {wageRecommendationsMap[newShift.jobType] && (
              <div className="wage-recommendation-badge animate-fade-in-up">
                <span className="badge-icon">💡</span>
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
              <label>Số Lượng Worker Cần Tuyển *</label>
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
              <label>Cự Ly Bán Kính Tối Đa (km)</label>
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

          {/* Chi phí ước tính hiển thị realtime */}
          <div className="shift-cost-summary-box">
            <div className="shift-cost-header">
              <span>DỰ TOÁN THÙ LAO CA LÀM NÀY:</span>
              <span className="shift-cost-hours">
                ⏱️ {shiftDurationHours} giờ &times; {newShift.requiredWorkers || 1} nhân sự
              </span>
            </div>
            <div className="shift-cost-total">
              {shiftTotal.toLocaleString()} đ
            </div>
            <div className="shift-cost-formula">
              = ({shiftDurationHours}h &times; {Number(newShift.requiredWorkers || 1)} người &times; {Number(newShift.hourlyWage || 0).toLocaleString()}đ/h) + {Number(newShift.allowance || 0).toLocaleString()}đ phụ cấp
            </div>
            <div className="shift-cost-note">
              💡 Thù lao này EO thanh toán trực tiếp cho Worker sau ca làm (Mô hình Non-Escrow - CANi không thu cọc lương).
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
