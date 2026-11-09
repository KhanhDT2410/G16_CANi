import { useState, useEffect, useCallback, useMemo } from 'react';
import { systemConfigApi } from '../api/systemConfigApi';
import { getConfigMeta } from '../types/systemConfig.constants';
import { showToast } from '../../../components/common/Toast';

export const useSystemConfigs = () => {
  const [configs, setConfigs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [singleSavingKey, setSingleSavingKey] = useState(null);
  const [dirtyValues, setDirtyValues] = useState({});
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [editingConfig, setEditingConfig] = useState(null);

  const fetchConfigs = useCallback(async () => {
    setLoading(true);
    try {
      const data = await systemConfigApi.getConfigs();
      setConfigs(data || []);
      setDirtyValues({});
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải danh sách cấu hình hệ thống';
      showToast(msg, 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchConfigs();
  }, [fetchConfigs]);

  // Thay đổi giá trị nhập liệu trực tiếp trên bảng/thẻ
  const handleValueChange = (key, newValue) => {
    const original = configs.find((c) => c.key === key);
    if (!original) return;

    setDirtyValues((prev) => {
      const current = prev[key] || { value: original.value, description: original.description };
      const updated = { ...current, value: newValue };

      // Nếu giá trị giống hệt ban đầu thì xóa khỏi dirty
      if (updated.value === original.value && updated.description === original.description) {
        const next = { ...prev };
        delete next[key];
        return next;
      }

      return { ...prev, [key]: updated };
    });
  };

  // Hủy toàn bộ thay đổi chưa lưu
  const handleResetChanges = () => {
    setDirtyValues({});
    showToast('Đã hủy bỏ toàn bộ các thay đổi chưa lưu', 'info');
  };

  // Lưu 1 tham số cấu hình đơn lẻ
  const handleSaveSingle = async (key) => {
    const original = configs.find((c) => c.key === key);
    if (!original) return;

    const dirty = dirtyValues[key];
    const valueToSave = dirty ? dirty.value : original.value;
    const descToSave = dirty?.description !== undefined ? dirty.description : original.description;

    if (!valueToSave || valueToSave.trim() === '') {
      showToast(`Giá trị cấu hình [${key}] không được để trống`, 'error');
      return;
    }

    setSingleSavingKey(key);
    try {
      const updated = await systemConfigApi.updateConfig(key, {
        value: valueToSave.trim(),
        description: descToSave,
      });

      setConfigs((prev) => prev.map((c) => (c.key === key ? updated : c)));
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

  // Lưu toàn bộ các tham số đã thay đổi (Bulk Update)
  const handleSaveBulk = async () => {
    const dirtyKeys = Object.keys(dirtyValues);
    if (dirtyKeys.length === 0) {
      showToast('Không có thay đổi nào cần lưu', 'info');
      return;
    }

    // Kiểm tra validation không được để trống
    for (const key of dirtyKeys) {
      if (!dirtyValues[key].value || dirtyValues[key].value.trim() === '') {
        showToast(`Giá trị cấu hình [${key}] không được để trống`, 'error');
        return;
      }
    }

    setSaving(true);
    try {
      const items = dirtyKeys.map((key) => ({
        key,
        value: dirtyValues[key].value.trim(),
        description: dirtyValues[key].description,
      }));

      const updatedList = await systemConfigApi.bulkUpdateConfigs({ items });

      // Cập nhật lại state danh sách cấu hình
      setConfigs((prev) => {
        const map = new Map(updatedList.map((item) => [item.key, item]));
        return prev.map((c) => map.get(c.key) || c);
      });

      setDirtyValues({});
      showToast(`Đã cập nhật thành công ${updatedList.length} tham số hệ thống`, 'success');
    } catch (err) {
      const msg = err.response?.data?.message || 'Cập nhật hàng loạt cấu hình thất bại';
      showToast(msg, 'error');
    } finally {
      setSaving(false);
    }
  };

  // Modal chi tiết
  const openEditModal = (config) => {
    const dirty = dirtyValues[config.key];
    setEditingConfig({
      ...config,
      value: dirty ? dirty.value : config.value,
      description: dirty?.description !== undefined ? dirty.description : config.description,
    });
  };

  const closeEditModal = () => {
    setEditingConfig(null);
  };

  const handleModalSave = async (key, payload) => {
    setSingleSavingKey(key);
    try {
      const updated = await systemConfigApi.updateConfig(key, payload);
      setConfigs((prev) => prev.map((c) => (c.key === key ? updated : c)));
      setDirtyValues((prev) => {
        const next = { ...prev };
        delete next[key];
        return next;
      });
      closeEditModal();
      showToast(`Đã cập nhật cấu hình [${key}] thành công`, 'success');
    } catch (err) {
      const msg = err.response?.data?.message || `Cập nhật cấu hình thất bại`;
      showToast(msg, 'error');
    } finally {
      setSingleSavingKey(null);
    }
  };

  // Lọc theo search và category
  const filteredConfigs = useMemo(() => {
    return configs.filter((c) => {
      const meta = getConfigMeta(c.key);
      const matchCategory =
        selectedCategory === 'ALL' || meta.category === selectedCategory;

      const q = searchTerm.toLowerCase().trim();
      const matchSearch =
        !q ||
        c.key.toLowerCase().includes(q) ||
        (meta.label && meta.label.toLowerCase().includes(q)) ||
        (c.description && c.description.toLowerCase().includes(q)) ||
        c.value.toLowerCase().includes(q);

      return matchCategory && matchSearch;
    });
  }, [configs, selectedCategory, searchTerm]);

  const dirtyCount = Object.keys(dirtyValues).length;
  const hasChanges = dirtyCount > 0;

  return {
    configs,
    filteredConfigs,
    loading,
    saving,
    singleSavingKey,
    dirtyValues,
    dirtyCount,
    hasChanges,
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
    closeEditModal,
    handleModalSave,
    fetchConfigs,
  };
};

