import { useSystemConfigs } from '../hooks/useSystemConfigs';
import SystemConfigHeader from './SystemConfigHeader';
import SystemConfigList from './SystemConfigList';
import SystemConfigEditModal from './SystemConfigEditModal';
import './SystemConfig.css';

const SystemConfigView = () => {
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

  return (
    <div className="sys-config-container">
      <SystemConfigHeader
        totalCount={configs.length}
        dirtyCount={dirtyCount}
        searchTerm={searchTerm}
        onSearchChange={setSearchTerm}
        selectedCategory={selectedCategory}
        onCategoryChange={setSelectedCategory}
        onSaveBulk={handleSaveBulk}
        onResetChanges={handleResetChanges}
        onRefresh={fetchConfigs}
        saving={saving}
        loading={loading}
      />

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

