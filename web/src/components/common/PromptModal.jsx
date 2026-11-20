import React, { useState, useEffect, useRef } from 'react';
import './PromptModal.css';

let dialogHandler = null;

/**
 * Hiển thị hộp thoại xác nhận bất đồng bộ thay thế window.confirm()
 * @returns {Promise<boolean>}
 */
// eslint-disable-next-line react-refresh/only-export-components
export const showConfirm = (optionsOrMessage) => {
  const opts = typeof optionsOrMessage === 'string'
    ? { title: 'Xác nhận thao tác', description: optionsOrMessage }
    : (optionsOrMessage || {});
  if (!dialogHandler) {
    return Promise.resolve(window.confirm(opts.description || opts.title || 'Xác nhận?'));
  }
  return new Promise((resolve) => {
    dialogHandler({ ...opts, needInput: false, resolve });
  });
};

/**
 * Hiển thị hộp thoại nhập liệu bất đồng bộ thay thế window.prompt()
 * @returns {Promise<string | null>}
 */
// eslint-disable-next-line react-refresh/only-export-components
export const showPrompt = (optionsOrMessage, defaultValue = '') => {
  const opts = typeof optionsOrMessage === 'string'
    ? { title: 'Nhập thông tin', description: optionsOrMessage, initialValue: defaultValue }
    : (optionsOrMessage || {});
  if (!dialogHandler) {
    return Promise.resolve(window.prompt(opts.description || opts.title || '', opts.initialValue || ''));
  }
  return new Promise((resolve) => {
    dialogHandler({ ...opts, needInput: true, resolve });
  });
};

/**
 * PromptModal - Modal xác nhận hoặc nhập liệu thay thế cho window.confirm() và window.prompt()
 * Mang lại trải nghiệm mượt mà, không block Event Loop của trình duyệt, hỗ trợ phím Escape và Enter.
 */
export const PromptModal = ({
  isOpen,
  title,
  description,
  type = 'info', // 'info' | 'warning' | 'danger' | 'success'
  confirmText = 'Xác nhận',
  cancelText = 'Hủy bỏ',
  needInput = false,
  inputLabel = '',
  inputPlaceholder = '',
  initialValue = '',
  isMultiline = false,
  minLength = 0,
  allowEmpty = false,
  onConfirm,
  onCancel,
}) => {
  const [value, setValue] = useState(initialValue);
  const inputRef = useRef(null);

  useEffect(() => {
    if (isOpen) {
      setValue(initialValue);
      setTimeout(() => {
        if (inputRef.current) {
          inputRef.current.focus();
        }
      }, 50);
    }
  }, [isOpen, initialValue]);

  const trimmedLen = (value || '').trim().length;
  const isInvalidInput = needInput && ((!allowEmpty && trimmedLen === 0) || (minLength > 0 && trimmedLen < minLength));

  useEffect(() => {
    const handleKeyDown = (e) => {
      if (!isOpen) return;
      if (e.key === 'Escape') {
        onCancel();
      } else if (e.key === 'Enter' && !isMultiline && !isInvalidInput) {
        onConfirm(value);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, value, isMultiline, isInvalidInput, onCancel, onConfirm]);

  if (!isOpen) return null;

  const getIcon = () => {
    switch (type) {
      case 'danger':
        return '✕';
      case 'warning':
        return '⚠';
      case 'success':
        return '✓';
      case 'info':
      default:
        return 'ℹ';
    }
  };

  const handleConfirmClick = () => {
    onConfirm(value);
  };

  return (
    <div className="cani-modal-backdrop" onClick={onCancel}>
      <div className="cani-modal-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="cani-modal-header">
          <div className={`cani-modal-icon-badge ${type}`}>{getIcon()}</div>
          <div>
            <h3 className="cani-modal-title">{title}</h3>
            {description && <p className="cani-modal-desc">{description}</p>}
          </div>
        </div>

        {needInput && (
          <div className="cani-modal-input-group">
            {inputLabel && <label className="cani-modal-input-label">{inputLabel}</label>}
            {isMultiline ? (
              <textarea
                ref={inputRef}
                rows={3}
                className="cani-modal-textarea"
                placeholder={inputPlaceholder}
                value={value}
                onChange={(e) => setValue(e.target.value)}
              />
            ) : (
              <input
                ref={inputRef}
                type="text"
                className="cani-modal-input"
                placeholder={inputPlaceholder}
                value={value}
                onChange={(e) => setValue(e.target.value)}
              />
            )}
          </div>
        )}

        <div className="cani-modal-footer">
          <button type="button" className="cani-modal-btn-cancel" onClick={onCancel}>
            {cancelText}
          </button>
            <button
            type="button"
            className={`cani-modal-btn-confirm ${type === 'danger' ? 'danger' : type === 'warning' ? 'warning' : 'primary'}`}
            onClick={handleConfirmClick}
            disabled={isInvalidInput}
          >
            {confirmText}
          </button>
        </div>
        {needInput && minLength > 0 && trimmedLen < minLength && (
          <div className="cani-modal-validation-hint">
            Cần tối thiểu {minLength} ký tự ({trimmedLen}/{minLength})
          </div>
        )}
      </div>
    </div>
  );
};

export const PromptDialogHost = () => {
  const [dialog, setDialog] = useState(null);

  useEffect(() => {
    dialogHandler = (opts) => setDialog(opts);
    return () => {
      dialogHandler = null;
    };
  }, []);

  if (!dialog) return null;

  const handleConfirm = (inputVal) => {
    const resolve = dialog.resolve;
    const isInput = dialog.needInput;
    setDialog(null);
    resolve(isInput ? inputVal : true);
  };

  const handleCancel = () => {
    const resolve = dialog.resolve;
    const isInput = dialog.needInput;
    setDialog(null);
    resolve(isInput ? null : false);
  };

  return (
    <PromptModal
      isOpen={true}
      title={dialog.title}
      description={dialog.description}
      type={dialog.type || 'info'}
      confirmText={dialog.confirmText || 'Xác nhận'}
      cancelText={dialog.cancelText || 'Hủy bỏ'}
      needInput={Boolean(dialog.needInput)}
      inputLabel={dialog.inputLabel || ''}
      inputPlaceholder={dialog.inputPlaceholder || ''}
      initialValue={dialog.initialValue ?? ''}
      isMultiline={Boolean(dialog.isMultiline)}
      minLength={dialog.minLength || 0}
      allowEmpty={Boolean(dialog.allowEmpty)}
      onConfirm={handleConfirm}
      onCancel={handleCancel}
    />
  );
};

export default PromptModal;
