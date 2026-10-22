import React, { useState, useEffect, useRef } from 'react';
import './PromptModal.css';

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

  useEffect(() => {
    const handleKeyDown = (e) => {
      if (!isOpen) return;
      if (e.key === 'Escape') {
        onCancel();
      } else if (e.key === 'Enter' && !isMultiline && (!needInput || value.trim())) {
        onConfirm(value);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, value, needInput, isMultiline, onCancel, onConfirm]);

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
            disabled={needInput && !value.trim()}
          >
            {confirmText}
          </button>
        </div>
      </div>
    </div>
  );
};

export default PromptModal;
