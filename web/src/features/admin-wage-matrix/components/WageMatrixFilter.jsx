import React, { useState } from 'react';
import { CITY_OPTIONS } from '../types/wageMatrix.constants';

export const WageMatrixFilter = ({
  filters,
  onApplyFilters,
  onResetFilters,
  onOpenCreate,
  loading,
}) => {
  const [localFilters, setLocalFilters] = useState(filters);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setLocalFilters((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onApplyFilters(localFilters);
  };

  const handleReset = () => {
    setLocalFilters({ city: '', jobType: '', isActive: '' });
    onResetFilters();
  };

  return (
    <div className="wage-matrix-filter-card">
      <form onSubmit={handleSubmit} className="wage-matrix-filter-form">
        <div className="filter-group">
          <label htmlFor="filter-city">Khu Vực / Thành Phố</label>
          <select
            id="filter-city"
            name="city"
            value={localFilters.city}
            onChange={handleChange}
            className="filter-select"
          >
            <option value="">Tất cả thành phố</option>
            {CITY_OPTIONS.map((c) => (
              <option key={c.value} value={c.value}>
                {c.label}
              </option>
            ))}
          </select>
        </div>

        <div className="filter-group">
          <label htmlFor="filter-job-type">Vị Trí Công Việc</label>
          <input
            id="filter-job-type"
            type="text"
            name="jobType"
            placeholder="VD: Phục vụ bàn, Lễ tân..."
            value={localFilters.jobType}
            onChange={handleChange}
            className="filter-input"
          />
        </div>

        <div className="filter-group">
          <label htmlFor="filter-status">Trạng Thái</label>
          <select
            id="filter-status"
            name="isActive"
            value={localFilters.isActive}
            onChange={handleChange}
            className="filter-select"
          >
            <option value="">Tất cả trạng thái</option>
            <option value="true">Đang áp dụng</option>
            <option value="false">Tạm ngưng</option>
          </select>
        </div>

        <div className="filter-buttons">
          <button type="submit" className="btn-filter-submit" disabled={loading}>
            Tìm Kiếm
          </button>
          <button
            type="button"
            onClick={handleReset}
            className="btn-filter-reset"
            disabled={loading}
          >
            Đặt Lại
          </button>
        </div>
      </form>

      <div className="filter-actions-right">
        <button
          type="button"
          onClick={onOpenCreate}
          className="btn-create-wage"
        >
          Thêm Mức Lương Mới
        </button>
      </div>
    </div>
  );
};
