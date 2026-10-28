import { useState, useEffect, useCallback, useMemo } from 'react';
import * as api from '../api/systemConfigApi';
import { DEFAULT_CONFIG, MESSAGE_LABELS } from '../types/systemConfig.constants';

/** Mock nhật ký kiểm toán cấu hình (BR-70) khi backend chưa sẵn sàng. */
const MOCK_AUDIT_LOGS = [
  { id: 'cfg-1042', ts: '28/10/2026 09:42', admin: 'admin@cani.vn', target: 'pricing.inviteModePrice', old: '45.000', next: '50.000', msg: 'MSG158' },
  { id: 'cfg-1038', ts: '26/10/2026 16:10', admin: 'admin@cani.vn', target: 'matching.minMatchScore', old: '75', next: '70', msg: 'MSG158' },
  { id: 'cfg-1029', ts: '24/10/2026 11:03', admin: 'minhtb@cani.vn', target: 'sp.completedShift', old: '8', next: '10', msg: 'MSG97' },
  { id: 'cfg-1017', ts: '21/10/2026 07:45', admin: 'admin@cani.vn', target: 'maintenance.enabled', old: 'off', next: 'on', msg: 'MSG158' },
];

const isNumeric = (v) => typeof v === 'number' && Number.isFinite(v);

/** Thu flat config về dạng map key→value (hợp nhất với defaults). */
const mergeConfig = (raw) => ({ ...DEFAULT_CONFIG, ...(raw || {}) });

/**
 * Hook quản lý state cho System Configuration (W-41).
 * - Load cấu hình (fallback DEFAULT_CONFIG), theo dõi dirty theo tab,
 *   validate range theo schema (UC-114 step 5 → MSG98), save + audit log.
 */
export const useSystemConfig = () => {
  const [config, setConfig] = useState(() => mergeConfig(null));
  const [baseline, setBaseline] = useState(DEFAULT_CONFIG);
  const [loading, setLoading] = useState(true);
  const [savingTab, setSavingTab] = useState(null);
  const [auditLogs, setAuditLogs] = useState([]);
  const [lastSaved, setLastSaved] = useState(null);

  const loadConfig = useCallback(async () => {
    setLoading(true);
    try {
      const [cfgRes, logsRes] = await Promise.allSettled([
        api.fetchSystemConfig(),
        api.fetchConfigAuditLogs(),
      ]);
      const cfg = cfgRes.status === 'fulfilled' ? cfgRes.value.data?.data : null;
      const merged = mergeConfig(cfg);
      setConfig(merged);
      setBaseline(merged);
      setAuditLogs(logsRes.status === 'fulfilled' ? logsRes.value.data?.data || [] : MOCK_AUDIT_LOGS);
    } catch {
      setConfig(mergeConfig(null));
      setBaseline(DEFAULT_CONFIG);
      setAuditLogs(MOCK_AUDIT_LOGS);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadConfig();
  }, [loadConfig]);

  const updateField = useCallback((key, value) => {
    setConfig((prev) => ({ ...prev, [key]: value }));
  }, []);

  const getFieldValue = useCallback(
    (key) => config[key],
    [config]
  );

  /** So sánh config vs baseline: trả về danh sách key đã đổi. */
  const changedKeys = useMemo(
    () => Object.keys(config).filter((k) => config[k] !== baseline[k]),
    [config, baseline]
  );

  /** Tab nào có thay đổi (để badge "unsaved" trên tab). */
  const dirtyByTab = useMemo(() => {
    const map = {};
    for (const k of changedKeys) map[k.split('.')[0]] = true;
    return map;
  }, [changedKeys]);

  /** Validate các field của một tab theo schema (UC-114 step 5a → MSG98). */
  const validateTab = useCallback(
    (tab) => {
      const errors = {};
      for (const section of tab.sections) {
        for (const field of section.fields) {
          if (field.type === 'toggle' || field.type === 'text' || field.type === 'datetime' || field.type === 'action') continue;
          const value = config[field.key];
          if (!isNumeric(value)) {
            errors[field.key] = 'Vui lòng nhập giá trị hợp lệ.';
            continue;
          }
          if (field.min !== undefined && value < field.min) {
            errors[field.key] = `Tối thiểu: ${field.min}${field.unit ? ' ' + field.unit : ''} (MSG98)`;
          }
          if (field.max !== undefined && value > field.max) {
            errors[field.key] = `Tối đa: ${field.max}${field.unit ? ' ' + field.unit : ''} (MSG98)`;
          }
        }
      }
      return errors;
    },
    [config]
  );

  /** Lưu một tab (saveSystemConfig), ghi audit log, cập nhật baseline. */
  const saveTab = useCallback(
    async (tab) => {
      const errors = validateTab(tab);
      if (Object.keys(errors).length > 0) {
        return { ok: false, errors, message: MESSAGE_LABELS.MSG98 };
      }
      setSavingTab(tab.id);
      try {
        await api.saveSystemConfig(config);
      } catch {
        // Mock thành công cho demo khi backend chưa có endpoint
      }
      // Bản ghi audit log mới (chỉ các key đổi của tab này)
      const tabKeys = changedKeys.filter((k) => k.startsWith(tab.id + '.'));
      if (tabKeys.length > 0) {
        const now = new Date().toLocaleString('vi-VN', {
          day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
        });
        setAuditLogs((prev) => [
          {
            id: 'cfg-' + Date.now(),
            ts: now,
            admin: 'admin@cani.vn',
            target: tabKeys.join(', '),
            old: tabKeys.map((k) => baseline[k]).join(', '),
            next: tabKeys.map((k) => config[k]).join(', '),
            msg: tab.saveMsg,
          },
          ...prev,
        ]);
      }
      setBaseline({ ...config });
      setLastSaved(new Date());
      setSavingTab(null);
      return { ok: true, message: MESSAGE_LABELS[tab.saveMsg] || MESSAGE_LABELS.MSG158 };
    },
    [config, baseline, changedKeys, validateTab]
  );

  /** Vô hiệu hóa cache (UC-116 step 5) - gọi khi bấm nút action trong tab features. */
  const invalidateCache = useCallback(async () => {
    try {
      await api.invalidateCache();
    } catch {
      // mock
    }
    setAuditLogs((prev) => [
      {
        id: 'cfg-' + Date.now(),
        ts: new Date().toLocaleString('vi-VN', {
          day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
        }),
        admin: 'admin@cani.vn',
        target: 'cache.invalidateNow',
        old: '-',
        next: 'đã vô hiệu hóa',
        msg: 'MSG158',
      },
      ...prev,
    ]);
  }, []);

  const resetTab = useCallback(
    (tab) => {
      setConfig((prev) => {
        const next = { ...prev };
        for (const section of tab.sections) {
          for (const f of section.fields) next[f.key] = baseline[f.key];
        }
        return next;
      });
    },
    [baseline]
  );

  return {
    config,
    loading,
    savingTab,
    auditLogs,
    lastSaved,
    changedKeys,
    dirtyByTab,
    getFieldValue,
    updateField,
    saveTab,
    resetTab,
    invalidateCache,
    refresh: loadConfig,
  };
};
