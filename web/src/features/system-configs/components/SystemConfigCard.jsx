import { getConfigMeta } from '../types/systemConfig.constants';

const SystemConfigCard = ({
  config,
  dirty,
  onValueChange,
  onSaveSingle,
  onOpenEditModal,
  isSaving,
}) => {
  const meta = getConfigMeta(config.key);
  const currentValue = dirty ? dirty.value : config.value;
  const isDirty = dirty !== undefined && dirty.value !== config.value;
  const isNumeric = meta.type === 'number';

  const handleStep = (direction) => {
    if (isSaving) return;
    const step = parseFloat(meta.step) || 1;
    const current = parseFloat(currentValue);
    const base = isNaN(current) ? (parseFloat(meta.defaultValue) || 0) : current;
    const min = meta.min !== undefined ? meta.min : 0;
    const max = meta.max !== undefined ? meta.max : Infinity;

    let next = base + direction * step;
    if (min !== undefined && next < min) next = min;
    if (max !== undefined && next > max) next = max;

    const stepDecimals = meta.step && meta.step.includes('.')
      ? meta.step.split('.')[1].length
      : (String(step).includes('.') ? String(step).split('.')[1].length : 0);

    const formatted = stepDecimals > 0 ? next.toFixed(stepDecimals) : String(Math.round(next));
    onValueChange(config.key, formatted);
  };

  const formatDate = (isoString) => {
    if (!isoString) return 'Chưa cập nhật';
    try {
      const d = new Date(isoString);
      return d.toLocaleString('vi-VN', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  const getCategoryBadgeClass = (category) => {
    switch (category) {
      case 'MATCHING':
        return 'badge-cat-matching';
      case 'FINANCIAL':
        return 'badge-cat-financial';
      case 'OPERATION':
        return 'badge-cat-operation';
      default:
        return 'badge-cat-general';
    }
  };

  const getCategoryLabel = (category) => {
    switch (category) {
      case 'MATCHING':
        return 'Ghép nối';
      case 'FINANCIAL':
        return 'Tài chính';
      case 'OPERATION':
        return 'Vận hành';
      default:
        return 'Hệ thống';
    }
  };

  return (
    <div className={`sys-config-card ${isDirty ? 'card-dirty' : ''}`}>
      {/* Card Header */}
      <div className="sys-config-card-header">
        <div className="sys-config-card-badges">
          <span className={`cat-pill ${getCategoryBadgeClass(meta.category)}`}>
            {getCategoryLabel(meta.category)}
          </span>
          {isDirty && <span className="dirty-pill">Chưa lưu</span>}
        </div>
        <button
          type="button"
          className="btn-card-edit-detail"
          onClick={() => onOpenEditModal(config)}
          title="Chỉnh sửa chi tiết & mô tả"
        >
          Chi tiết
        </button>
      </div>

      {/* Title */}
      <h3 className="sys-config-card-title">{meta.label}</h3>

      {/* Description / Hint */}
      <p className="sys-config-card-desc">
        {config.description || meta.hint}
      </p>

      {/* Value Input Area with Split Stepper Bar */}
      <div className="sys-config-input-group">
        <div className={`sys-config-stepper-bar ${isDirty ? 'bar-dirty' : ''}`}>
          {/* Left zone: Value input + Unit label */}
          <div className="stepper-left-section">
            <input
              type={meta.type || 'text'}
              step={meta.step || 'any'}
              className="sys-config-stepper-input"
              value={currentValue}
              onChange={(e) => onValueChange(config.key, e.target.value)}
              disabled={isSaving}
            />
            {meta.unit && <span className="sys-config-unit-text">{meta.unit}</span>}
          </div>

          {/* Right zone: Divider line + Stepper buttons for numeric parameters */}
          {isNumeric && (
            <>
              <div className="stepper-vertical-divider" />
              <div className="stepper-actions-section">
                <button
                  type="button"
                  className="btn-stepper-action"
                  onClick={() => handleStep(-1)}
                  disabled={isSaving}
                  title="Giảm giá trị"
                  aria-label="Giảm"
                >
                  -
                </button>
                <button
                  type="button"
                  className="btn-stepper-action"
                  onClick={() => handleStep(1)}
                  disabled={isSaving}
                  title="Tăng giá trị"
                  aria-label="Tăng"
                >
                  +
                </button>
              </div>
            </>
          )}
        </div>

        {isDirty && (
          <button
            type="button"
            className="btn-card-save-single"
            onClick={() => onSaveSingle(config.key)}
            disabled={isSaving}
          >
            {isSaving ? '...' : 'Lưu'}
          </button>
        )}
      </div>

      {/* Footer Info */}
      <div className="sys-config-card-footer">
        <span className="sys-config-updated-time">
          Cập nhật: {formatDate(config.updatedAt)}
        </span>
      </div>
    </div>
  );
};

export default SystemConfigCard;
