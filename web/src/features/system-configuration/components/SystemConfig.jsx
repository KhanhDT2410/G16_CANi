import { useState } from 'react';
import { useSystemConfig } from '../hooks/useSystemConfig';
import { ConfigForm } from './ConfigForm';
import { CONFIG_TABS } from '../types/systemConfig.constants';
import { showToast } from '../../../components/common/Toast';
import '../systemConfig.css';

/**
 * W-41: System Configuration – Screen (SRS 3.4.3.5 · UC-114/115/116)
 *
 * Trang cấu hình tập trung cho Admin với 7 tab dạng pill segmented:
 * Pricing · Matching · Attendance · Rating · RS Thresholds · Skill Point ·
 * Feature Flags & Maintenance. Save theo từng tab + nhật ký kiểm toán (BR-70).
 * Critical toggles (maintenance.enabled) yêu cầu xác nhận mức độ cao (UC-116 step 3a).
 */

const ConfirmModal = ({ open, message, onConfirm, onCancel }) => {
  if (!open) return null;
  return (
    <div className="scf-modal-backdrop" onClick={onCancel}>
      <div className="scf-modal-card animate-scale-in" onClick={(e) => e.stopPropagation()}>
        <div className="scf-modal-icon">⚠️</div>
        <h3 className="scf-modal-title">Xác nhận can thiệp quan trọng</h3>
        <p className="scf-modal-text">{message}</p>
        <div className="scf-modal-actions">
          <button type="button" className="scf-modal-btn secondary" onClick={onCancel}>
            Hủy
          </button>
          <button type="button" className="scf-modal-btn danger" onClick={onConfirm}>
            Xác nhận
          </button>
        </div>
      </div>
    </div>
  );
};

const AuditLogPanel = ({ logs }) => (
  <div className="scf-audit-panel">
    <div className="scf-audit-head">
      <h3 className="scf-audit-title">📜 Nhật Ký Kiểm Toán Cấu Hình (BR-70)</h3>
      <span className="scf-audit-count">{logs.length} bản ghi</span>
    </div>
    <div className="scf-audit-list">
      {logs.length === 0 ? (
        <div className="scf-audit-empty">Chưa có bản ghi kiểm toán cấu hình.</div>
      ) : (
        logs.map((log) => (
          <div key={log.id} className="scf-audit-item">
            <span className="scf-audit-ts">{log.ts}</span>
            <span className="scf-audit-admin">{log.admin}</span>
            <span className="scf-audit-target" title={log.target}>{log.target}</span>
            <span className="scf-audit-diff">
              <span className="scf-audit-old">{log.old}</span>
              <span className="scf-audit-arrow">→</span>
              <span className="scf-audit-new">{log.next}</span>
            </span>
            <span className="scf-audit-msg">{log.msg}</span>
          </div>
        ))
      )}
    </div>
  </div>
);

export const SystemConfig = () => {
  const {
    loading,
    savingTab,
    auditLogs,
    lastSaved,
    dirtyByTab,
    getFieldValue,
    updateField,
    saveTab,
    invalidateCache,
  } = useSystemConfig();

  const [activeTab, setActiveTab] = useState('pricing');
  const [errors, setErrors] = useState({});
  const [confirmAction, setConfirmAction] = useState(null); // { key, next }

  const tab = CONFIG_TABS.find((t) => t.id === activeTab);

  /** Bấm Save của tab đang mở: validate → save → toast MSG. */
  const handleSave = async () => {
    const result = await saveTab(tab);
    if (result.ok) {
      setErrors({});
      showToast(result.message, 'success');
    } else {
      setErrors(result.errors);
      showToast(result.message, 'error');
    }
  };

  /** Bấm toggle critical: hiện modal xác nhận trước khi đổi giá trị (UC-116 3a). */
  const handleToggle = (key, value, isCritical) => {
    if (isCritical) {
      setConfirmAction({ key, next: value });
    } else {
      updateField(key, value);
    }
  };

  const confirmCritical = () => {
    if (confirmAction) updateField(confirmAction.key, confirmAction.next);
    setConfirmAction(null);
    showToast('Đã bật chế độ bảo trì. Người dùng sẽ được thông báo (UC-117).', 'warning');
  };

  return (
    <div className="scf-dashboard">
      {/* Header */}
      <div className="scf-header">
        <div>
          <h1 className="scf-title">
            Cấu Hình Hệ Thống
            <span className="scf-badge">ADMIN · W-41</span>
          </h1>
          <p className="scf-subtitle">
            Điều chỉnh hành vi nền tảng theo từng nhóm — không cần triển khai lại mã nguồn (UC-114/115/116).
          </p>
        </div>
        <div className="scf-header-actions">
          {lastSaved && (
            <span className="scf-last-saved">
              Đã lưu {lastSaved.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
            </span>
          )}
          <button type="button" className={`scf-save-btn ${savingTab === activeTab ? 'busy' : ''}`} onClick={handleSave} disabled={loading || savingTab !== null}>
            {savingTab === activeTab ? 'Đang lưu…' : '💾 Lưu Cấu Hình'}
          </button>
        </div>
      </div>

      {loading ? (
        <div className="scf-loading">
          <div className="skeleton" style={{ height: '280px', borderRadius: 14 }} />
        </div>
      ) : (
        <>
          {/* Pill Segmented Tab Switcher */}
          <div className="scf-tabs" role="tablist" aria-label="Nhóm cấu hình">
            {CONFIG_TABS.map((t) => (
              <button
                key={t.id}
                role="tab"
                aria-selected={activeTab === t.id}
                className={`scf-tab-btn ${activeTab === t.id ? 'active' : ''}`}
                onClick={() => { setActiveTab(t.id); setErrors({}); }}
              >
                <span className="scf-tab-icon">{t.icon}</span>
                {t.label}
                {dirtyByTab[t.id] && <span className="scf-dot" title="Chưa lưu" />}
              </button>
            ))}
          </div>

          {/* Tab Form */}
          <div className="scf-grid">
            <ConfigForm
              tab={tab}
              errors={errors}
              getFieldValue={(k) => getFieldValue(k)}
              onToggle={(key, value, critical) => handleToggle(key, value, critical)}
              updateField={(k, v) => updateField(k, v)}
              onInvalidateCache={invalidateCache}
            />
            <AuditLogPanel logs={auditLogs} />
          </div>
        </>
      )}

      <ConfirmModal
        open={!!confirmAction}
        message="Tắt tính năng / bật chế độ bảo trì sẽ ảnh hưởng ngay lập tức đến người dùng trên toàn nền tảng. Bạn có chắc chắn muốn tiếp tục?"
        onConfirm={confirmCritical}
        onCancel={() => setConfirmAction(null)}
      />
    </div>
  );
};
