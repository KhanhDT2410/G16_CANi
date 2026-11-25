import { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import './Toast.css';

let toastHandler = null;

// eslint-disable-next-line react-refresh/only-export-components
export const showToast = (message, type = 'success', duration = 3500) => {
  if (toastHandler) {
    toastHandler(message, type, duration);
  }
};

export const ToastContainer = () => {
  const [toasts, setToasts] = useState([]);

  useEffect(() => {
    toastHandler = (message, type, duration) => {
      const id = Date.now() + Math.random();
      setToasts((prev) => [...prev, { id, message, type }]);

      setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
      }, duration);
    };

    return () => {
      toastHandler = null;
    };
  }, []);

  const removeToast = (id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  const getIcon = (type) => {
    switch (type) {
      case 'success':
        return '✓';
      case 'error':
        return '✕';
      case 'warning':
        return '⚠';
      default:
        return 'ℹ';
    }
  };

  if (toasts.length === 0) return null;

  return createPortal(
    <div className="toast-container" role="region" aria-live="polite">
      {toasts.map((t) => (
        <div key={t.id} className={`toast-item ${t.type}`}>
          <span className="toast-icon">{getIcon(t.type)}</span>
          <span className="toast-content">{t.message}</span>
          <button className="toast-close-btn" onClick={() => removeToast(t.id)}>
            ×
          </button>
        </div>
      ))}
    </div>,
    document.body
  );
};
