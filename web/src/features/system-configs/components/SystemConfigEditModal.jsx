import { useState, useEffect } from 'react';
import { getConfigMeta } from '../types/systemConfig.constants';

const SystemConfigEditModal = ({ config, onClose, onSave, isSaving }) => {
  const [value, setValue] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (config) {
      setValue(config.value || '');
      setDescription(config.description || '');
      setError('');
    }
  }, [config]);

  if (!config) return null;

  const meta = getConfigMeta(config.key);

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!value || value.trim() === '') {
      setError('Giá trị cấu hình không được để trống');
      return;
    }
    setError('');
    onSave(config.key, {
      value: value.trim(),
      description: description ? description.trim() : null,
    });
  };

  return (
    <div className="sys-config-modal-overlay" onClick={onClose}>
      <div
        className="sys-config-modal-card"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <div className="sys-config-modal-header">
          <div>
            <h3 className="modal-title">Chỉnh Sửa Tham Số Hệ Thống</h3>
            <span className="modal-key-badge">{config.key}</span>
          </div>
          <button
            type="button"
            className="btn-modal-close"
            onClick={onClose}
            disabled={isSaving}
          >
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="sys-config-modal-body">
            <div className="form-group">
              <label className="form-label">Tên hiển thị</label>
              <input
                type="text"
                className="form-control form-control-readonly"
                value={meta.label}
                readOnly
              />
            </div>

            <div className="form-group">
              <label className="form-label">
                Giá trị cấu hình <span className="text-danger">*</span>
                {meta.unit && <span className="unit-hint">({meta.unit})</span>}
              </label>
              <input
                type={meta.type || 'text'}
                step={meta.step || 'any'}
                className={`form-control ${error ? 'is-invalid' : ''}`}
                value={value}
                onChange={(e) => {
                  setValue(e.target.value);
                  if (error) setError('');
                }}
                disabled={isSaving}
                autoFocus
              />
              {error && <span className="field-error">{error}</span>}
              {meta.hint && <span className="field-hint">{meta.hint}</span>}
            </div>

            <div className="form-group">
              <label className="form-label">Mô tả chi tiết / Ghi chú</label>
              <textarea
                rows={3}
                className="form-control textarea-control"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Nhập ghi chú hoặc mô tả về tham số cấu hình này..."
                disabled={isSaving}
              />
            </div>
          </div>

          <div className="sys-config-modal-footer">
            <button
              type="button"
              className="btn-modal-cancel"
              onClick={onClose}
              disabled={isSaving}
            >
              Hủy
            </button>
            <button
              type="submit"
              className="btn-modal-submit"
              disabled={isSaving}
            >
              {isSaving ? 'Đang lưu...' : 'Lưu thay đổi'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default SystemConfigEditModal;
