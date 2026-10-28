/**
 * W-41 - Renderer dạng data-driven cho một tab cấu hình.
 * Đọc CONFIG_TABS schema, render sections + fields (number, toggle, text, datetime, action).
 * Giữ UI gọn & tái sử dụng cho mọi tab (YAGNI: không cần component riêng per tab).
 */
import { useMemo } from 'react';

/** Number input với unit & format VNĐ. */
const NumberField = ({ field, value, error, onChange }) => {
  const numeric = typeof value === 'number' && Number.isFinite(value);
  const isVnd = field.unit === 'VND';
  return (
    <div className={`scf-field ${error ? 'has-error' : ''}`}>
      <label className="scf-field-label" htmlFor={`scf-${field.key}`}>{field.label}</label>
      <div className="scf-input-wrap">
        <input
          id={`scf-${field.key}`}
          type="number"
          className="scf-input"
          value={numeric ? value : ''}
          min={field.min}
          max={field.max}
          step={field.step}
          onChange={(e) => onChange(e.target.value === '' ? '' : Number(e.target.value))}
        />
        {isVnd && (
          <span className="scf-unit">
            {numeric ? value.toLocaleString('vi-VN') : '0'} đ
          </span>
        )}
        {!isVnd && field.unit && <span className="scf-unit">{field.unit}</span>}
      </div>
      {field.help && <span className="scf-help">{field.help}</span>}
      {error && <span className="scf-error">{error}</span>}
    </div>
  );
};

/** Toggle switch (chips: feature flags, hành vi hệ thống). */
const ToggleField = ({ field, value, onChange }) => (
  <div className="scf-field scf-field-toggle">
    <div>
      <div className="scf-field-label">{field.label}</div>
      {field.help && <div className="scf-help">{field.help}</div>}
    </div>
    <button
      type="button"
      role="switch"
      aria-checked={!!value}
      className={`scf-toggle ${value ? 'on' : ''}`}
      onClick={() => onChange(!value)}
    >
      <span className="scf-toggle-thumb" />
    </button>
  </div>
);

const TextField = ({ field, value, onChange }) => (
  <div className="scf-field scf-field-text">
    <label className="scf-field-label" htmlFor={`scf-${field.key}`}>{field.label}</label>
    <input
      id={`scf-${field.key}`}
      type="text"
      className="scf-input scf-input-text"
      placeholder={field.placeholder}
      value={typeof value === 'string' ? value : ''}
      onChange={(e) => onChange(e.target.value)}
    />
    {field.help && <span className="scf-help">{field.help}</span>}
  </div>
);

const DateTimeField = ({ field, value, onChange }) => (
  <div className="scf-field scf-field-text">
    <label className="scf-field-label" htmlFor={`scf-${field.key}`}>{field.label}</label>
    <input
      id={`scf-${field.key}`}
      type="datetime-local"
      className="scf-input scf-input-text"
      value={typeof value === 'string' ? value : ''}
      onChange={(e) => onChange(e.target.value)}
    />
  </div>
);

/** Nút hành động đặc biệt (vd: vô hiệu hóa cache ngay). */
const ActionField = ({ field, onAction }) => (
  <div className="scf-field scf-field-toggle">
    <div>
      <div className="scf-field-label">{field.label}</div>
    </div>
    <button type="button" className="scf-action-btn" onClick={onAction}>
      {field.actionLabel}
    </button>
  </div>
);

export const ConfigForm = ({ tab, errors, getFieldValue, updateField, onToggle, onInvalidateCache }) => {
  const total = useMemo(
    () => tab.sections.reduce((n, s) => n + s.fields.length, 0),
    [tab]
  );

  return (
    <div className="scf-panel animate-fade-in-up">
      <div className="scf-panel-head">
        <span className="scf-panel-icon">{tab.icon}</span>
        <div>
          <h2 className="scf-panel-title">{tab.title}</h2>
          <p className="scf-panel-sub">{tab.description}</p>
        </div>
        <span className="scf-panel-count">{total} mục</span>
      </div>

      <div className="scf-sections">
        {tab.sections.map((section) => (
          <section key={section.title} className="scf-section">
            <h3 className="scf-section-title">{section.title}</h3>
            {section.description && <p className="scf-section-desc">{section.description}</p>}
            <div className="scf-field-grid">
              {section.fields.map((field) => {
                const value = getFieldValue(field.key);
                switch (field.type) {
                  case 'toggle':
                    return (
                      <ToggleField
                        key={field.key}
                        field={field}
                        value={value}
                        onChange={(v) => onToggle(field.key, v, field.critical)}
                      />
                    );
                  case 'text':
                    return <TextField key={field.key} field={field} value={value} onChange={(v) => updateField(field.key, v)} />;
                  case 'datetime':
                    return <DateTimeField key={field.key} field={field} value={value} onChange={(v) => updateField(field.key, v)} />;
                  case 'action':
                    return <ActionField key={field.key} field={field} onAction={onInvalidateCache} />;
                  default:
                    return (
                      <NumberField
                        key={field.key}
                        field={field}
                        value={value}
                        error={errors?.[field.key]}
                        onChange={(v) => updateField(field.key, v)}
                      />
                    );
                }
              })}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
};
