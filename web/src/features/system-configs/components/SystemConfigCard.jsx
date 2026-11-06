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
      case 'FINANCIAL':
        return 'badge-cat-financial';
      case 'OPERATION':
        return 'badge-cat-operation';
      default:
        return 'badge-cat-general';
    }
  };

  return (
    <div className={`sys-config-card ${isDirty ? 'card-dirty' : ''}`}>
      {/* Card Header */}
      <div className="sys-config-card-header">
        <div className="sys-config-card-badges">
          <span className={`cat-pill ${getCategoryBadgeClass(meta.category)}`}>
            {meta.category === 'FINANCIAL'
              ? 'Tài chính'
              : meta.category === 'OPERATION'
              ? 'Vận hành'
              : 'Hệ thống'}
          </span>
          {isDirty && <span className="dirty-pill">Chưa lưu</span>}
        </div>
        <button
          type="button"
          className="btn-card-edit-detail"
          onClick={() => onOpenEditModal(config)}
          title="Chỉnh sửa chi tiết & mô tả"
        >
          ⚙ Chi tiết
        </button>
      </div>

      {/* Title & Key */}
      <h3 className="sys-config-card-title">{meta.label}</h3>
      <div className="sys-config-card-key">{config.key}</div>

      {/* Description / Hint */}
      <p className="sys-config-card-desc">
        {config.description || meta.hint}
      </p>

      {/* Value Input Area */}
      <div className="sys-config-input-group">
        <div className="sys-config-input-wrapper">
          <input
            type={meta.type || 'text'}
            step={meta.step || 'any'}
            className={`sys-config-val-input ${isDirty ? 'input-modified' : ''}`}
            value={currentValue}
            onChange={(e) => onValueChange(config.key, e.target.value)}
            disabled={isSaving}
          />
          {meta.unit && <span className="sys-config-unit-label">{meta.unit}</span>}
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
