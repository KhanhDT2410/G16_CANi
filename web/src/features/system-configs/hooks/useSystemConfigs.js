import { useState, useEffect, useCallback, useMemo } from 'react';
import { systemConfigApi } from '../api/systemConfigApi';
import { SYSTEM_CONFIG_METAS, getConfigMeta } from '../types/systemConfig.constants';
import { showToast } from '../../../components/common/Toast';

/**
 * Custom Hook quản lý dữ liệu và thao tác cho Tham Số Hệ Thống (ADM-09).
 * Hợp nhất thông minh dữ liệu backend và danh mục nghiệp vụ sàn.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const useSystemConfigs = () => {
  const [configs, setConfigs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [singleSavingKey, setSingleSavingKey] = useState(null);
  const [dirtyValues, setDirtyValues] = useState({});
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [editingConfig, setEditingConfig] = useState(null);

  // Hợp nhất dữ liệu từ API và các tham số nghiệp vụ mặc định
  const mergeConfigs = useCallback((backendData = []) => {
    const backendMap = new Map(backendData.map((item) => [item.key, item]));
    const merged = [];

    // 1. Thêm toàn bộ các tham số định nghĩa trong METAS (đảm bảo đủ 4 nhóm nghiệp vụ)
    Object.keys(SYSTEM_CONFIG_METAS).forEach((key) => {
      const meta = SYSTEM_CONFIG_METAS[key];
      if (backendMap.has(key)) {
        merged.push(backendMap.get(key));
        backendMap.delete(key);
      } else {
        const localSaved = localStorage.getItem(`cani_cfg_${key}`);
        merged.push({
          key,
          value: localSaved !== null ? localSaved : meta.defaultValue,
          description: meta.hint,
          isBusinessParam: true,
        });
      }
    });

    // 2. Thêm bất kỳ key nào khác mà backend có nhưng chưa có trong METAS
    backendMap.forEach((item) => {
      merged.push(item);
    });

    return merged;
  }, []);

  const fetchConfigs = useCallback(async () => {
    setLoading(true);
    try {
      const data = await systemConfigApi.getConfigs();
      setConfigs(mergeConfigs(data || []));
      setDirtyValues({});
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải danh sách cấu hình hệ thống';
      showToast(msg, 'error');
      setConfigs(mergeConfigs([]));
    } finally {
      setLoading(false);
    }
  }, [mergeConfigs]);

  useEffect(() => {
    fetchConfigs();
  }, [fetchConfigs]);

  const handleValueChange = (key, newValue) => {
    const original = configs.find((c) => c.key === key);
    if (!original) return;

    setDirtyValues((prev) => {
      const current = prev[key] || { value: original.value, description: original.description };
      const updated = { ...current, value: newValue };

      if (updated.value === original.value && updated.description === original.description) {
        const next = { ...prev };
        delete next[key];
        return next;
      }
      return { ...prev, [key]: updated };
    });
  };

  const handleResetChanges = () => {
    setDirtyValues({});
    showToast('Đã hủy bỏ toàn bộ các thay đổi chưa lưu', 'info');
  };

  const handleSaveSingle = async (key) => {
    const original = configs.find((c) => c.key === key);
    if (!original) return;

    const dirty = dirtyValues[key];
    const val = dirty ? dirty.value : original.value;
    const desc = dirty?.description !== undefined ? dirty.description : original.description;

    if (!val || val.trim() === '') {
      showToast(`Giá trị cấu hình [${key}] không được để trống`, 'error');
      return;
    }

    setSingleSavingKey(key);
    try {
      if (original.configId) {
        const updated = await systemConfigApi.updateConfig(key, { value: val.trim(), description: desc });
        setConfigs((prev) => prev.map((c) => (c.key === key ? updated : c)));
      } else {
        localStorage.setItem(`cani_cfg_${key}`, val.trim());
        setConfigs((prev) => prev.map((c) => (c.key === key ? { ...c, value: val.trim(), description: desc } : c)));
      }

      setDirtyValues((prev) => {
        const next = { ...prev };
        delete next[key];
        return next;
      });
      showToast(`Đã lưu cấu hình [${key}] thành công`, 'success');
    } catch (err) {
      const msg = err.response?.data?.message || `Lưu cấu hình [${key}] thất bại`;
      showToast(msg, 'error');
    } finally {
      setSingleSavingKey(null);
    }
  };

  const handleSaveBulk = async () => {
    const dirtyKeys = Object.keys(dirtyValues);
    if (dirtyKeys.length === 0) return;

    setSaving(true);
    try {
      const backendItems = [];
      dirtyKeys.forEach((key) => {
        const orig = configs.find((c) => c.key === key);
        const dirty = dirtyValues[key];
        if (orig?.configId) {
          backendItems.push({ key, value: dirty.value.trim(), description: dirty.description || orig.description });
        } else {
          localStorage.setItem(`cani_cfg_${key}`, dirty.value.trim());
        }
      });

      if (backendItems.length > 0) {
        await systemConfigApi.bulkUpdateConfigs({ configs: backendItems });
      }

      setConfigs((prev) =>
        prev.map((c) => {
          if (dirtyValues[c.key]) {
            return { ...c, value: dirtyValues[c.key].value.trim(), description: dirtyValues[c.key].description || c.description };
          }
          return c;
        })
      );
      setDirtyValues({});
      showToast(`Đã cập nhật thành công ${dirtyKeys.length} tham số hệ thống`, 'success');
    } catch (err) {
      showToast(err.response?.data?.message || 'Cập nhật hàng loạt thất bại', 'error');
    } finally {
      setSaving(false);
    }
  };

  const openEditModal = (config) => {
    const dirty = dirtyValues[config.key];
    setEditingConfig({
      ...config,
      value: dirty ? dirty.value : config.value,
      description: dirty?.description !== undefined ? dirty.description : config.description,
    });
  };

  const handleModalSave = async (key, payload) => {
    await handleSaveSingle(key);
    setEditingConfig(null);
  };

  const filteredConfigs = useMemo(() => {
    return configs.filter((c) => {
      const meta = getConfigMeta(c.key);
      const matchCategory = selectedCategory === 'ALL' || meta.category === selectedCategory;
      const q = searchTerm.toLowerCase().trim();
      const matchSearch =
        !q ||
        c.key.toLowerCase().includes(q) ||
        (meta.label && meta.label.toLowerCase().includes(q)) ||
        (c.description && c.description.toLowerCase().includes(q)) ||
        String(c.value).toLowerCase().includes(q);

      return matchCategory && matchSearch;
    });
  }, [configs, selectedCategory, searchTerm]);

  return {
    configs,
    filteredConfigs,
    loading,
    saving,
    singleSavingKey,
    dirtyValues,
    dirtyCount: Object.keys(dirtyValues).length,
    searchTerm,
    selectedCategory,
    editingConfig,
    setSearchTerm,
    setSelectedCategory,
    handleValueChange,
    handleResetChanges,
    handleSaveSingle,
    handleSaveBulk,
    openEditModal,
    closeEditModal: () => setEditingConfig(null),
    handleModalSave,
    fetchConfigs,
  };
};
