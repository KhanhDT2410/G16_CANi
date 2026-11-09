import SystemConfigCard from './SystemConfigCard';

const SystemConfigList = ({
  configs,
  dirtyValues,
  loading,
  onValueChange,
  onSaveSingle,
  onOpenEditModal,
  singleSavingKey,
}) => {
  if (loading) {
    return (
      <div className="sys-config-loading-state">
        <div className="sys-config-spinner" />
        <p>Đang tải danh sách tham số cấu hình...</p>
      </div>
    );
  }

  if (configs.length === 0) {
    return (
      <div className="sys-config-empty-state">
        <h3>Không tìm thấy tham số cấu hình</h3>
        <p>Thử thay đổi từ khóa tìm kiếm hoặc chọn danh mục khác.</p>
      </div>
    );
  }

  return (
    <div className="sys-config-grid">
      {configs.map((config) => (
        <SystemConfigCard
          key={config.key}
          config={config}
          dirty={dirtyValues[config.key]}
          onValueChange={onValueChange}
          onSaveSingle={onSaveSingle}
          onOpenEditModal={onOpenEditModal}
          isSaving={singleSavingKey === config.key}
        />
      ))}
    </div>
  );
};

export default SystemConfigList;
