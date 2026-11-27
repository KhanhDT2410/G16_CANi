import React, { useState, useEffect } from 'react';
import { REGION_OPTIONS, JOB_TIER_OPTIONS } from '../types/wageMatrix.constants';

export const WageMatrixFilter = ({
  filters,
  onApplyFilters,
  onResetFilters,
  onOpenCreate,
  loading,
}) => {
  const [localFilters, setLocalFilters] = useState(filters);

  useEffect(() => {
    setLocalFilters(filters);
  }, [filters]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setLocalFilters((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onApplyFilters(localFilters);
  };

  const handleReset = () => {
    const empty = { regionCode: '', jobTier: '', isActive: '' };
    setLocalFilters(empty);
    onResetFilters();
  };

  return (
    <div className="wage-matrix-filter-card">
      <form onSubmit={handleSubmit} className="wage-matrix-filter-form">
        <div className="filter-group">
          <label htmlFor="filter-region">Vùng Lương (NĐ 293/2025)</label>
          <select
            id="filter-region"
            name="regionCode"
            value={localFilters.regionCode}
            onChange={handleChange}
            className="filter-select"
          >
            <option value="">Tất cả 4 vùng</option>
            {REGION_OPTIONS.map((r) => (
              <option key={r.value} value={r.value}>
                {r.label}
              </option>
            ))}
          </select>
        </div>

        <div className="filter-group">
          <label htmlFor="filter-job-tier">Nhóm Công Việc (Job Tier)</label>
          <select
            id="filter-job-tier"
            name="jobTier"
            value={localFilters.jobTier}
            onChange={handleChange}
            className="filter-select"
          >
            <option value="">Tất cả 6 nhóm bậc</option>
            {JOB_TIER_OPTIONS.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
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
            Lọc Dữ Liệu
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
          Thêm Cấu Hình Mới
        </button>
      </div>
    </div>
  );
};
