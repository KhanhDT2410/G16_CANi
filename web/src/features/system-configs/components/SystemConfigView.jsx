import { useMemo, useEffect } from 'react';
import { useSystemConfigs } from '../hooks/useSystemConfigs';
import { getConfigMeta } from '../types/systemConfig.constants';
import SystemConfigHeader from './SystemConfigHeader';
import SystemConfigList from './SystemConfigList';
import SystemConfigEditModal from './SystemConfigEditModal';
import MatchingFormulaCard from './MatchingFormulaCard';
import './SystemConfig.css';

/**
 * Màn hình Quản trị Tham số hệ thống & Quy tắc nghiệp vụ sàn (ADM-09).
 * Hỗ trợ điều hướng danh mục từ thanh Sidebar bên trái (xổ xuống theo nhóm).
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const SystemConfigView = ({ activeCategory, onCategoryChange }) => {
  const {
    configs,
    filteredConfigs,
    loading,
    saving,
    singleSavingKey,
    dirtyValues,
    dirtyCount,
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
  } = useSystemConfigs();

  const currentCategory = activeCategory || selectedCategory;

  // Đồng bộ danh mục được chọn từ Sidebar
  useEffect(() => {
    if (activeCategory && activeCategory !== selectedCategory) {
      setSelectedCategory(activeCategory);
    }
  }, [activeCategory, selectedCategory, setSelectedCategory]);

  const categoryCounts = useMemo(() => {
    const counts = { ALL: configs.length, MATCHING: 0, OPERATION: 0, FINANCIAL: 0, GENERAL: 0 };
    configs.forEach((c) => {
      const meta = getConfigMeta(c.key);
      if (counts[meta.category] !== undefined) {
        counts[meta.category] += 1;
      }
    });
    return counts;
  }, [configs]);

  const handleCategorySwitch = (catKey) => {
    setSelectedCategory(catKey);
    if (onCategoryChange) {
      onCategoryChange(catKey);
    }
  };

  return (
    <div className="sys-config-container">
      <SystemConfigHeader
        totalCount={configs.length}
        dirtyCount={dirtyCount}
        searchTerm={searchTerm}
        onSearchChange={setSearchTerm}
        selectedCategory={currentCategory}
        onCategoryChange={handleCategorySwitch}
        categoryCounts={categoryCounts}
        onSaveBulk={handleSaveBulk}
        onResetChanges={handleResetChanges}
        onRefresh={fetchConfigs}
        saving={saving}
        loading={loading}
      />

      {/* Hiển thị thẻ mô phỏng công thức Matching khi xem Tất cả hoặc xem nhóm Matching (ADM-09) */}
      {(currentCategory === 'ALL' || currentCategory === 'MATCHING') && (
        <MatchingFormulaCard configs={configs} dirtyValues={dirtyValues} />
      )}

      <SystemConfigList
        configs={filteredConfigs}
        dirtyValues={dirtyValues}
        loading={loading}
        onValueChange={handleValueChange}
        onSaveSingle={handleSaveSingle}
        onOpenEditModal={openEditModal}
        singleSavingKey={singleSavingKey}
      />

      <SystemConfigEditModal
        config={editingConfig}
        onClose={closeEditModal}
        onSave={handleModalSave}
        isSaving={singleSavingKey === editingConfig?.key}
      />
    </div>
  );
};

export default SystemConfigView;
